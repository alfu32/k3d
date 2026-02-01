package com.github.alfu32.sketch.console

import com.github.alfu32.sketch.model.GroupScene
import groovy.lang.Binding
import groovy.lang.GroovyClassLoader
import groovy.lang.GroovyShell

class ConsoleGroovyRuntime(
    appFacade: AppFacade,
    sceneFacade: GroupScene,
    selectionFacade: SelectionFacade,
    consoleUtils: ConsoleUtils
) {
    val binding: Binding = Binding().apply {
        setProperty("app", appFacade)
        setProperty("scene", sceneFacade)
        setProperty("selection", selectionFacade)
        setProperty("console", consoleUtils)
    }
    val classLoader: GroovyClassLoader = GroovyClassLoader(javaClass.classLoader)
    val shell: GroovyShell = GroovyShell(classLoader, binding)
}
