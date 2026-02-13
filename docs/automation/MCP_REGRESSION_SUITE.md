# MCP Regression Suite

Author: Codex (GPT-5)
Date: February 13, 2026

## Goal
Provide a replayable MCP operation suite that:
- reproduces tutorial/manual operations,
- regenerates documentation screenshots,
- and serves as user-level regression testing seed data.

## Files
- Script: `docs/automation/mcp_regression_suite.sh`
- Output screenshots: `docs/images/ch05_*.png`, `docs/images/ch06_*.png`, `docs/images/ch09_*.png`
- Model target: `examples/mcp.demo.k3d`

## Run
From repository root:

```bash
bash docs/automation/mcp_regression_suite.sh
```

Optional endpoint override:

```bash
MCP_BASE_URL=http://127.0.0.1:8765 bash docs/automation/mcp_regression_suite.sh
```

## What It Executes
- Chapter 05 capture flow (`interaction fundamentals`)
- Chapter 06 capture flow (`toolbar/button operations`)
- Chapter 09 capture flow (`command palette command execution`)

Each chapter starts with a deterministic scene reset and camera normalization:
- camera mode: orbit perspective,
- camera up vector: `(0,1,0)`,
- target near origin.

## Compatibility Notes
- The suite performs a console-level hard reset each chapter (prototype stores + selections + camera), so it does not depend on `edit.reset_scene_for_capture`.
- If available, `view.capture_ui_minimal` and `view.capture_ui_restore` are used to reduce panel noise in captures.
- On older runtime builds, selection window/volume preview overlays may still differ from expected screenshot behavior.

## Regression Usage
Treat each screenshot step as a test checkpoint:
- If geometry differs, likely tool/selection/command regression.
- If camera framing differs, likely camera-controller regression.
- If overlays differ, likely UI/panel behavior regression.

For CI-style usage, run this script in a controlled environment where the editor and MCP server are already running against `examples/mcp.demo.k3d`.
