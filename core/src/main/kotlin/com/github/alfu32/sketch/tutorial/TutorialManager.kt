package com.github.alfu32.sketch.tutorial

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

data class TutorialFileEntry(
    var path: String = "",
    var fileName: String = "",
    var name: String = "",
    var stepCount: Int = 0
)

data class TutorialStep(
    var action: String = "",
    var message: String = ""
)

data class TutorialScript(
    var name: String = "",
    var createdAt: String = "",
    var steps: MutableList<TutorialStep> = mutableListOf()
)

enum class TutorialMode {
    IDLE,
    RECORDING,
    PLAYING,
    PAUSED
}

data class TutorialUiState(
    val mode: TutorialMode = TutorialMode.IDLE,
    val tutorials: List<TutorialFileEntry> = emptyList(),
    val activeTutorialPath: String? = null,
    val activeTutorialName: String? = null,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentMessage: String = "",
    val expectedAction: String? = null,
    val currentStepMatched: Boolean = false,
    val messageVisible: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false
)

class TutorialManager(
    private val tutorialsDirProvider: () -> File
) {
    private val json = Json().apply {
        setOutputType(JsonWriter.OutputType.json)
        ignoreUnknownFields = true
        setUsePrototypes(false)
    }
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private var mode = TutorialMode.IDLE
    private var recordingScript: TutorialScript? = null
    private var recordingFile: File? = null
    private var playingScript: TutorialScript? = null
    private var playingFile: File? = null
    private var currentStepIndex = 0
    private var currentStepMatched = false
    private val queuedActions = ArrayDeque<String>()

    fun uiState(): TutorialUiState {
        val files = listTutorials()
        val activePath = recordingFile?.absolutePath ?: playingFile?.absolutePath
        val activeName = recordingScript?.name ?: playingScript?.name
        val currentStep = currentStep()
        val totalSteps = playingScript?.steps?.size ?: recordingScript?.steps?.size ?: 0
        val displayStepIndex = when (mode) {
            TutorialMode.RECORDING -> totalSteps
            TutorialMode.PLAYING, TutorialMode.PAUSED -> {
                if (currentStep == null) totalSteps else (currentStepIndex + 1).coerceAtMost(totalSteps)
            }
            TutorialMode.IDLE -> 0
        }
        return TutorialUiState(
            mode = mode,
            tutorials = files,
            activeTutorialPath = activePath,
            activeTutorialName = activeName,
            currentStepIndex = displayStepIndex,
            totalSteps = totalSteps,
            currentMessage = currentStep?.message ?: "",
            expectedAction = currentStep?.action,
            currentStepMatched = currentStepMatched,
            messageVisible = mode == TutorialMode.PLAYING || mode == TutorialMode.PAUSED,
            canGoPrevious = (mode == TutorialMode.PLAYING || mode == TutorialMode.PAUSED) && currentStepIndex > 0,
            canGoNext = (mode == TutorialMode.PLAYING || mode == TutorialMode.PAUSED) && currentStep != null
        )
    }

    fun startRecording(): File {
        stopPlayback()
        val dir = ensureTutorialsDir()
        val now = LocalDateTime.now()
        val file = File(dir, "tut_${now.format(timestampFormatter)}.json")
        recordingFile = file
        recordingScript = TutorialScript(
            name = file.nameWithoutExtension,
            createdAt = now.toString(),
            steps = mutableListOf()
        )
        mode = TutorialMode.RECORDING
        saveRecordingScript()
        return file
    }

    fun stopRecording(): File? {
        val file = recordingFile
        if (mode == TutorialMode.RECORDING) {
            saveRecordingScript()
        }
        recordingScript = null
        recordingFile = null
        if (mode == TutorialMode.RECORDING) {
            mode = TutorialMode.IDLE
        }
        return file
    }

    fun startPlayback(path: String): Boolean {
        stopRecording()
        val file = File(path)
        val script = loadScript(file) ?: return false
        playingFile = file
        playingScript = script
        currentStepIndex = 0
        currentStepMatched = false
        queuedActions.clear()
        mode = TutorialMode.PLAYING
        return true
    }

    fun togglePausePlayback() {
        when (mode) {
            TutorialMode.PLAYING -> mode = TutorialMode.PAUSED
            TutorialMode.PAUSED -> {
                mode = TutorialMode.PLAYING
                consumeQueuedActions()
            }
            else -> Unit
        }
    }

    fun stopPlayback() {
        playingFile = null
        playingScript = null
        currentStepIndex = 0
        currentStepMatched = false
        queuedActions.clear()
        if (mode == TutorialMode.PLAYING || mode == TutorialMode.PAUSED) {
            mode = TutorialMode.IDLE
        }
    }

    fun observeAction(actionId: String, label: String? = null) {
        when (mode) {
            TutorialMode.RECORDING -> recordStep(actionId, label)
            TutorialMode.PLAYING -> {
                if (tryMatchCurrentStep(actionId)) {
                    goToNextStep()
                }
            }
            TutorialMode.PAUSED, TutorialMode.IDLE -> Unit
        }
    }

    fun goToNextStep(): Boolean {
        if (mode != TutorialMode.PLAYING && mode != TutorialMode.PAUSED) {
            return false
        }
        if (currentStep() == null) {
            return false
        }
        currentStepIndex += 1
        currentStepMatched = false
        if (currentStep() == null) {
            stopPlayback()
            return true
        }
        if (mode == TutorialMode.PLAYING) {
            consumeQueuedActions()
        }
        return true
    }

    fun goToPreviousStep(): Boolean {
        if (mode != TutorialMode.PLAYING && mode != TutorialMode.PAUSED) {
            return false
        }
        if (currentStepIndex <= 0) {
            return false
        }
        currentStepIndex -= 1
        currentStepMatched = false
        queuedActions.clear()
        return true
    }

    fun markCurrentStepDone(): Boolean = goToNextStep()

    private fun consumeQueuedActions() {
        while (mode == TutorialMode.PLAYING && !currentStepMatched && queuedActions.isNotEmpty()) {
            val queued = queuedActions.removeFirst()
            if (tryMatchCurrentStep(queued)) {
                return
            }
        }
    }

    private fun tryMatchCurrentStep(actionId: String): Boolean {
        val step = currentStep() ?: return false
        if (step.action == actionId) {
            currentStepMatched = true
            return true
        }
        return false
    }

    private fun currentStep(): TutorialStep? = playingScript?.steps?.getOrNull(currentStepIndex)

    private fun recordStep(actionId: String, label: String?) {
        val script = recordingScript ?: return
        script.steps.add(
            TutorialStep(
                action = actionId,
                message = defaultMessageFor(actionId, label)
            )
        )
        saveRecordingScript()
    }

    private fun saveRecordingScript() {
        val file = recordingFile ?: return
        val script = recordingScript ?: return
        val dir = ensureTutorialsDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        file.writeText(json.prettyPrint(script))
    }

    private fun loadScript(file: File): TutorialScript? {
        return try {
            if (!file.exists() || !file.isFile) {
                null
            } else {
                json.fromJson(TutorialScript::class.java, file.readText())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun listTutorials(): List<TutorialFileEntry> {
        val dir = ensureTutorialsDir()
        val files = dir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        } ?: return emptyList()
        return files
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name.lowercase(Locale.US) })
            .map { file ->
                val script = loadScript(file)
                TutorialFileEntry(
                    path = file.absolutePath,
                    fileName = file.name,
                    name = script?.name?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                    stepCount = script?.steps?.size ?: 0
                )
            }
    }

    private fun ensureTutorialsDir(): File {
        val dir = tutorialsDirProvider()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun defaultMessageFor(actionId: String, label: String?): String {
        val display = label?.trim().orEmpty().ifBlank { fallbackLabelFor(actionId) }
        return when {
            actionId.startsWith("tool.start.") -> "Click the $display button."
            actionId.startsWith("tool.end.") -> "Finish the $display tool."
            actionId.startsWith("tool.select.") -> display
            actionId.startsWith("guide.") -> display
            actionId.startsWith("ui.action.") -> "Click the $display button."
            else -> "Perform: $display"
        }
    }

    private fun fallbackLabelFor(actionId: String): String {
        val tail = actionId.substringAfterLast('.', actionId)
        return tail
            .split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
            .ifBlank { actionId }
    }
}
