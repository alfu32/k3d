# 1. Codex Task Checklist

Use this as a **step-by-step execution plan**.

---

## Phase 1 — Console Runtime

* [ ] Add (or verify) dependency on `org.apache.groovy:groovy`
* [ ] Create a **ConsoleGroovyRuntime**

    * [ ] Persistent `Binding`
    * [ ] Persistent `GroovyClassLoader`
    * [ ] Persistent `GroovyShell`
* [ ] Ensure this runtime is **distinct from plugin Groovy runtime**
* [ ] Bind required symbols:

    * [ ] `app`
    * [ ] `scene`
    * [ ] `selection`
    * [ ] `console`

---

## Phase 2 — LibGDX Dispatch Bridge

* [ ] Add `app.run {}` API
* [ ] Implement it using `Gdx.app.postRunnable`
* [ ] Ensure **all console-triggered mutations go through this API**
* [ ] Document that direct mutation outside `run {}` is unsupported

---

## Phase 3 — Console Thread & Lifecycle

* [ ] Parse CLI arguments
* [ ] If `--edit <file>` is present:

    * [ ] Start LibGDX GUI
    * [ ] Spawn **dedicated console thread**
* [ ] Ensure console thread:

    * [ ] Owns stdin
    * [ ] Does not block render thread
* [ ] Shut down console cleanly on app exit or EOF

---

## Phase 4 — TUI Layout

* [ ] Implement **two-pane TUI**

    * [ ] Top: Output / Listing pane (scrollable)
    * [ ] Bottom: Command editor (multiline)
* [ ] Ensure:

    * [ ] Independent scrolling for output pane
    * [ ] Editor retains focus by default
    * [ ] Output pane does not steal focus

---

## Phase 5 — Command Editor

* [ ] Implement multiline editor buffer
* [ ] Support:

    * [ ] Cursor movement
    * [ ] Mouse selection (if supported)
    * [ ] Syntax highlighting
* [ ] Ensure editor buffer is never destroyed implicitly

---

## Phase 6 — Multiline Evaluation Logic

* [ ] Accumulate editor buffer as a single string
* [ ] On submit:

    * [ ] Attempt `GroovyShell.evaluate`
    * [ ] If parse incomplete → stay in editor
    * [ ] If success → execute, clear editor
    * [ ] If syntax/runtime error → show error, keep buffer
* [ ] Do NOT implement custom parsing heuristics

---

## Phase 7 — History System

* [ ] Implement immutable history list
* [ ] On each successful submission:

    * [ ] Append full source as new entry
* [ ] History navigation rules:

    * [ ] Empty editor → ↑ / ↓ navigate history
    * [ ] Non-empty editor → ↑ / ↓ move cursor
    * [ ] Explicit history recall via Ctrl/Alt modifiers
* [ ] Ensure recalling history does NOT modify stored entries

---

## Phase 8 — History Persistence

* [ ] Persist history to disk
* [ ] Use app config directory
* [ ] Implement max size limit (default: 500 entries)
* [ ] Prune oldest entries on load or shutdown

---

## Phase 9 — Output Handling

* [ ] Print evaluation results to output pane
* [ ] Print exceptions with stack traces
* [ ] Implement `console.log`, `console.dir`, `console.type`
* [ ] Ensure output auto-scrolls only if user has not scrolled up

---

## Phase 10 — Validation

* [ ] GUI and console run concurrently
* [ ] Multiline `app.run {}` works
* [ ] History persists across restarts
* [ ] No LibGDX crashes due to thread violations
* [ ] Plugin Groovy remains unaffected

---

# 2. Minimal Class / Module Skeleton (Kotlin)

This is **structure only**. No implementation details.

---

```kotlin
// console/ConsoleGroovyRuntime.kt
class ConsoleGroovyRuntime(
    appFacade: AppFacade,
    sceneFacade: SceneFacade,
    selectionFacade: SelectionFacade
) {
    val binding: Binding
    val classLoader: GroovyClassLoader
    val shell: GroovyShell
}
```

```kotlin
// console/AppFacade.kt
class AppFacade(
    private val application: Application
) {
    fun run(block: () -> Unit)
}
```

```kotlin
// console/ConsoleUtils.kt
class ConsoleUtils {
    fun log(vararg args: Any?)
    fun dir(obj: Any?)
    fun type(obj: Any?)
}
```

```kotlin
// console/ConsoleThread.kt
class ConsoleThread(
    private val runtime: ConsoleGroovyRuntime,
    private val tui: ConsoleTui
) : Thread() {
    override fun run()
}
```

```kotlin
// tui/ConsoleTui.kt
class ConsoleTui {
    val outputPane: OutputPane
    val editorPane: EditorPane
    val history: HistoryManager

    fun render()
    fun handleInput(event: InputEvent)
}
```

```kotlin
// tui/EditorPane.kt
class EditorPane {
    val buffer: StringBuilder
    var cursorPosition: Int

    fun insert(text: String)
    fun delete()
    fun moveCursor(delta: Int)
    fun clear()
}
```

```kotlin
// tui/OutputPane.kt
class OutputPane {
    fun append(text: String)
    fun scroll(delta: Int)
}
```

```kotlin
// tui/HistoryManager.kt
class HistoryManager(
    private val maxEntries: Int = 500
) {
    fun add(entry: String)
    fun previous(): String?
    fun next(): String?
    fun loadFromDisk()
    fun saveToDisk()
}
```

```kotlin
// tui/InputEvent.kt
sealed class InputEvent {
    data class Key(val keyCode: Int, val modifiers: Int) : InputEvent()
    data class Mouse(val x: Int, val y: Int, val button: Int) : InputEvent()
}
```

---

# 3. Formal TUI Interaction State Machine

This defines **exact behavior**.
Codex should treat this as authoritative.

---

## States

```
IDLE
EDITING
EXECUTING
HISTORY_NAVIGATION
OUTPUT_SCROLL
```

---

## State Definitions

### IDLE

* Editor buffer is empty
* Cursor at position 0

**Transitions**

* Any character input → EDITING
* ↑ / ↓ → HISTORY_NAVIGATION
* Mouse scroll in output → OUTPUT_SCROLL

---

### EDITING

* Editor buffer contains text
* Multiline editing allowed

**Transitions**

* Enter (buffer incomplete) → EDITING
* Enter (buffer complete) → EXECUTING
* ↑ / ↓ → cursor movement
* Ctrl/Alt + ↑ / ↓ → HISTORY_NAVIGATION
* Mouse scroll output → OUTPUT_SCROLL

---

### EXECUTING

* Editor buffer frozen
* Groovy evaluation in progress

**Transitions**

* Success → IDLE
* Error → EDITING (buffer preserved)

---

### HISTORY_NAVIGATION

* History entry loaded into editor buffer
* Entry is editable

**Transitions**

* Any edit → EDITING
* Enter → EXECUTING
* Esc → IDLE

---

### OUTPUT_SCROLL

* Output pane has focus for scrolling

**Transitions**

* Any editor key → EDITING or IDLE
* Mouse click editor → EDITING

---

## Invariants (Must Always Hold)

* Editor buffer is never destroyed implicitly
* History entries are immutable
* Execution only occurs from EXECUTING state
* LibGDX mutations only occur inside `app.run {}`

---

## End Condition

* Application exit → all states terminate
* Console thread shuts down cleanly

---

### Final Note (for Codex)

This is **not exploratory**.
The design is settled.
Implement **exactly as specified**, preferring correctness and reuse of Groovy and LibGDX APIs over clever abstractions.
