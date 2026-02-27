# Credits

This project stands on a number of open-source libraries, tools, and assets.
Below is a curated list of the major components used in the Gradle build, with
authors, short descriptions, and license references.

## Authors

- Cristian Alferaru — creator and maintainer of Octodraw.

## Libraries

- Kotlin (JetBrains)  
  A modern, expressive programming language used for the core application.  
  https://kotlinlang.org/  
  License: Apache-2.0  
  https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt

- Kotlin Coroutines (JetBrains)  
  Lightweight concurrency primitives used for async workflows.  
  https://github.com/Kotlin/kotlinx.coroutines  
  License: Apache-2.0  
  https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt

- libGDX (Badlogic Games)  
  High-performance game and graphics framework powering the renderer and input.  
  https://libgdx.com/  
  License: Apache-2.0  
  https://github.com/libgdx/libgdx/blob/master/LICENSE

- libGDX LWJGL3 backend + platform natives (Badlogic Games)  
  Desktop OpenGL backend and native bindings.  
  https://github.com/libgdx/libgdx  
  License: Apache-2.0  
  https://github.com/libgdx/libgdx/blob/master/LICENSE

- libGDX FreeType (Badlogic Games)  
  Font rasterization support for libGDX.  
  https://github.com/libgdx/libgdx  
  License: Apache-2.0  
  https://github.com/libgdx/libgdx/blob/master/LICENSE

- libGDX Tools (Badlogic Games)  
  Utility tooling used in the build pipeline.  
  https://github.com/libgdx/libgdx  
  License: Apache-2.0  
  https://github.com/libgdx/libgdx/blob/master/LICENSE

- KTX (libKTX authors)  
  Kotlin-first libGDX extensions for math, JSON, and UI integration.  
  https://github.com/libktx/ktx  
  License: Apache-2.0  
  https://github.com/libktx/ktx/blob/master/LICENSE

- VisUI (Kotcrab)  
  UI widgets and styles for libGDX.  
  https://github.com/kotcrab/vis-ui  
  License: Apache-2.0  
  https://github.com/kotcrab/vis-ui/blob/master/LICENSE

- libgdx-utils (Tommy Ettinger)  
  Utility extensions used alongside libGDX.  
  https://github.com/tommyettinger/libgdx-utils  
  License: Apache-2.0  
  https://github.com/tommyettinger/libgdx-utils/blob/master/LICENSE

- Apache Groovy (The Apache Software Foundation)  
  Scripting engine for plugins and automation.  
  https://groovy-lang.org/  
  License: Apache-2.0  
  https://github.com/apache/groovy/blob/master/LICENSE

- libGDX ANGLE backend (Badlogic Games / ANGLE)  
  Optional OpenGL ES translation layer for improved compatibility.  
  https://github.com/libgdx/libgdx  
  License: Apache-2.0 for libGDX wrapper; ANGLE is BSD-3-Clause.  
  https://github.com/libgdx/libgdx/blob/master/LICENSE  
  https://chromium.googlesource.com/angle/angle/+/main/LICENSE

- gdx-svmhelper (Berstanio)  
  Native-image helper annotations and extensions (optional).  
  https://github.com/berstanio/gdx-svmhelper  
  License: Apache-2.0  
  https://github.com/berstanio/gdx-svmhelper/blob/main/LICENSE

## Tools & Build System

- Gradle  
  Build automation system used for compilation and packaging.  
  https://gradle.org/  
  License: Apache-2.0  
  https://github.com/gradle/gradle/blob/master/LICENSE

- Kotlin Gradle Plugin (JetBrains)  
  Kotlin build integration.  
  https://kotlinlang.org/docs/gradle.html  
  License: Apache-2.0  
  https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt

- Construo (fourlastor)  
  Native packaging support for libGDX.  
  https://github.com/fourlastor/construo  
  License: MIT  
  https://github.com/fourlastor/construo/blob/main/LICENSE

- OpenJDK / Temurin Runtime (Adoptium)  
  Bundled runtime distribution for packaged builds.  
  https://adoptium.net/  
  License: GPL-2.0 with Classpath Exception  
  https://openjdk.org/legal/gplv2+ce.html

- jpackage (OpenJDK)  
  Packaging tool used for MSI/MSIX builds.  
  https://docs.oracle.com/en/java/javase/21/jpackage/  
  License: GPL-2.0 with Classpath Exception  
  https://openjdk.org/legal/gplv2+ce.html

## Assets

- UI icons, application icon, and in-app graphics  
  Created for Octodraw by Cristian Alferaru unless noted otherwise.

## Notes

- If additional components are added (e.g., D-DIN font, JTS), this file should be updated with their authors and license terms.
