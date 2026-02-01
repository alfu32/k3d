# Specification

## Embedded Groovy Interactive Console (TUI) for K3D LibGDX Editor

---

## 1. Purpose

Implement a **persistent, interactive Groovy console** for a LibGDX-based 3D mesh editor that:

* Runs **inside the same JVM** as the editor
* Remains interactive while the LibGDX GUI is active
* Provides a **JS-console–like user experience**
* Supports **multiline editing**, **function definitions**, and **history**
* Allows **safe mutation of LibGDX state** via explicit dispatch
* Reuses **existing Groovy APIs only** (no custom language tooling)

This console is a **developer / power-user tool**, not a sandboxed scripting environment.

---

## 2. Non-Goals

The implementation must **not**:

* Embed `groovysh` or its terminal UI
* Implement a custom scripting language or DSL
* Modify or reuse the existing plugin Groovy runtime
* Perform automatic mutation detection or AST rewriting
* Allow unsafe cross-thread LibGDX access

---

## 3. High-Level Architecture

### 3.1 Separate Groovy Contexts (Mandatory)

There must be **two completely separate Groovy runtimes**:

| Context        | Purpose  | Access                   |
| -------------- | -------- | ------------------------ |
| Plugin Groovy  | Existing | Sandboxed, no app access |
| Console Groovy | New      | Direct app access        |

They **must not share**:

* `Binding`
* `GroovyShell`
* `GroovyClassLoader`

---

## 4. Console Lifecycle

### 4.1 Startup

When the application is launched with:

```
--edit <file>
```

The application must:

1. Parse CLI arguments
2. Launch LibGDX normally
3. Spawn a **dedicated console thread**
4. Initialize the Groovy console runtime
5. Display a prompt in the original terminal
6. Keep GUI and console running concurrently

The console terminates when:

* The application exits
* Or stdin reaches EOF

---

## 5. Threading Model (Hard Requirement)

### Threads

* **LibGDX Render Thread**

    * Owns all rendering and mutable engine state
* **Console Thread**

    * Owns stdin, TUI, Groovy evaluation
* **Main Thread**

    * Startup only

### Rule

> **No LibGDX state may be mutated outside the render thread.**

All mutations must be marshalled using:

```kotlin
Gdx.app.postRunnable { ... }
```

---

## 6. Application Dispatch API

Expose exactly one mutation bridge to Groovy:

```kotlin
fun run(block: () -> Unit)
```

Implementation:

```kotlin
fun run(block: () -> Unit) {
    Gdx.app.postRunnable(block)
}
```

This function is the **official boundary** between console and engine.

---

## 7. Groovy Console Runtime

### 7.1 Persistent State

Create **one** Groovy runtime for the entire editor session:

* `Binding`
* `GroovyClassLoader`
* `GroovyShell`

They must **never be recreated**.

---

### 7.2 Bound Variables

The following variables must be available in Groovy:

| Name        | Description                           |
| ----------- | ------------------------------------- |
| `app`       | Application façade (exposes `run {}`) |
| `scene`     | Scene façade or proxy                 |
| `selection` | Current selection                     |
| `console`   | Console helper utilities              |

---

## 8. TUI Layout (Final Decision)

### 8.1 Layout Structure

Use a **two-pane horizontal split**:

```
┌──────────────────────────────────────────┐
│ Output / Listing Pane (scrollable)       │
│                                          │
│  - results                               │
│  - errors                                │
│  - console.log output                    │
│                                          │
├──────────────────────────────────────────┤
│ Command Editor Pane (multiline)           │
│ >                                        │
└──────────────────────────────────────────┘
```

### Rationale

* Matches browser JS consoles
* Maximizes horizontal space for code
* Simplifies focus and keyboard handling

A history sidebar is **explicitly out of scope** for the initial implementation.

---

## 9. Command Editor Behavior

### 9.1 Editing Model

* The editor is a **multiline code editor**
* Cursor movement, mouse selection, scrolling are supported
* The editor buffer is **never destroyed implicitly**

---

### 9.2 Submission Rules

* Pressing **Enter** submits the buffer **only if Groovy parsing is complete**
* If input is incomplete (e.g. open `{`), remain in editor mode
* Execution occurs only after a syntactically complete submission

---

## 10. Multiline Evaluation

### Required Behavior

This must work:

```groovy
app.run {
    scene.meshes[0].translate(1, 0, 0)
    selection.rotateZ(45)
}
```

### Implementation Rule

* Accumulate editor buffer as a single string
* Attempt `GroovyShell.evaluate(...)`
* If Groovy reports **unexpected EOF / incomplete input**:

    * Do not execute
    * Continue editing
* On success:

    * Execute
    * Clear editor buffer
    * Push submission to history

---

## 11. History Model

### 11.1 Immutable History (Mandatory)

* Every submission creates a **new history entry**
* History entries are **never modified**
* Editing a recalled entry creates a **new submission**

---

### 11.2 History Navigation

#### Default behavior

* If editor buffer is **empty**:

    * ↑ / ↓ navigates history
* If editor buffer is **non-empty**:

    * ↑ / ↓ moves cursor within buffer

#### Explicit history recall

Use one of:

* `Ctrl+↑ / Ctrl+↓`
* `Alt+↑ / Alt+↓`

---

## 12. History Persistence

### Storage

* Persist history to disk
* One file in application config directory
* Plain text or JSON lines

Example:

```
~/.appname/console-history.groovy
```

### Retention Policy

* Default maximum: **500 entries**
* Oldest entries are pruned
* Pruning occurs on startup or shutdown

---

## 13. Output Pane Behavior

The output pane must:

* Be independently scrollable
* Preserve scrollback
* Not steal focus from the editor
* Avoid auto-scrolling if user scrolled up
* Display:

    * Evaluation results
    * Exceptions
    * Explicit console output

---

## 14. Console Utility Helpers

Expose a `console` object with at least:

```groovy
console.log(obj)
console.dir(obj)
console.type(obj)
```

Suggested behavior:

* `log` → print values
* `dir` → print properties / fields
* `type` → print JVM class name

---

## 15. Function Definitions & Larger Logic

The console must support:

```groovy
def move(dx, dy, dz) {
    app.run {
        selection.translate(dx, dy, dz)
    }
}
```

Later:

```groovy
move(1, 0, 0)
```

No special handling required beyond persistent Groovy state.

---

## 16. Syntax Highlighting

* Syntax highlighting is handled **entirely by the TUI editor**
* Groovy provides no coloring
* Editor must highlight:

    * Current buffer
    * History recall
    * Printed source code (if applicable)

---

## 17. Source Recall (Optional but Recommended)

* Store submitted source snippets
* Allow helper like:

```groovy
console.source("move")
```

* Render stored source using editor renderer with highlighting

---

## 18. Security Model

This console is:

* Trusted
* Developer-facing
* Not sandboxed

No restrictions are placed on:

* File system access
* Reflection
* JVM APIs

---

## 19. Dependencies

### Allowed

* Core Groovy (`org.apache.groovy:groovy`)
* LibGDX (existing)
* Terminal/UI libraries (optional)

### Forbidden

* `groovysh`
* Custom AST transforms
* Custom scripting engines

---

## 20. Acceptance Criteria

The implementation is complete when:

* GUI and console run concurrently
* Console remains responsive during rendering
* Multiline Groovy input works correctly
* History is persistent and immutable
* LibGDX mutations occur safely on render thread
* No changes are made to plugin Groovy execution
* No duplicate Groovy runtimes are introduced

---

## 21. Design Principles (For the Agent)

* Reuse Groovy as designed
* Prefer explicit boundaries over magic
* Treat the console as **live code execution**
* Do not reimplement a language
* Favor correctness and predictability over cleverness

---

If you want next, I can:

* Convert this into a **Codex task checklist**
* Produce a **minimal class/module skeleton**
* Or define a **formal state machine** for the TUI interaction model
