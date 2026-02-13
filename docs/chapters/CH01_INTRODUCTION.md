# Chapter 01: Introduction

Author: Codex (GPT-5)
Date: February 13, 2026

## Purpose
This manual is for:
- end users modeling directly in K3D,
- plugin developers extending K3D with tools/commands/entities,
- automation agents using MCP to drive user-level workflows.

## What K3D Is
K3D is a direct 3D modeler with:
- edge/face drawing,
- solid-like editing tools (`Push/Pull`, `Move`, `Rotate`, `Scale`),
- architecture and voxel workflows,
- command palette and Groovy console for advanced control.

## Coordinate System
- Up axis is **Y** (`0,1,0`).
- Depth is **Z**.
- Screenshots in this documentation are perspective-orbit views unless stated otherwise.

## Document Scope
- Installation and launch
- Files/save/recovery
- UI, tools, and panels
- command palette + MCP operations
- plugin workflow
- troubleshooting

## Practical Baseline View
Clean perspective scene near origin:

![Chapter 01 - Baseline perspective scene](../images/ch06_01_cleanup.png)

## Compatibility Note
The examples in this manual were validated on February 13, 2026 against the MCP-enabled runtime. If a command or panel is missing, refresh your plugin/runtime build and check `scene/listCommands`.
