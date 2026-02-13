#!/usr/bin/env bash
# Author: Codex (GPT-5)
# Date: February 13, 2026

set -euo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8765}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMG_DIR="${ROOT_DIR}/docs/images"

log() { printf '[mcp-docs-v2] %s\n' "$*"; }

retry_cmd() {
  local attempts="$1"
  local delay="$2"
  shift 2
  local i
  for i in $(seq 1 "$attempts"); do
    if "$@"; then
      return 0
    fi
    sleep "$delay"
  done
  return 1
}

mcp_get() {
  local path="$1"
  local out
  out="$(curl --noproxy '*' -sS --max-time 8 "${BASE_URL}${path}" 2>/dev/null || true)"
  [[ "$out" == *'"success":true'* ]] || return 1
  printf '%s' "$out"
}

mcp_post() {
  local path="$1"
  local payload="$2"
  local out
  out="$(curl --noproxy '*' -sS --max-time 10 -X POST "${BASE_URL}${path}" --data-binary "$payload" 2>/dev/null || true)"
  [[ "$out" == *'"success":true'* ]] || return 1
  printf '%s' "$out"
}

mcp_get_retry() {
  local path="$1"
  retry_cmd 40 0.4 mcp_get "$path"
}

mcp_post_retry() {
  local path="$1"
  local payload="$2"
  retry_cmd 40 0.4 mcp_post "$path" "$payload"
}

cmd() {
  local id="$1"
  mcp_get_retry "/scene/command?id=${id}" >/dev/null
}

cmd_try() {
  local id="$1"
  mcp_get_retry "/scene/command?id=${id}" >/dev/null 2>&1 || true
}

console() {
  local script="$1"
  mcp_post_retry "/scene/console" "$script" >/dev/null
}

ptr() {
  local action="$1"; local x="$2"; local y="$3"; local z="$4"
  mcp_get_retry "/scene/pointer?action=${action}&worldX=${x}&worldY=${y}&worldZ=${z}" >/dev/null
}

click() {
  local x="$1"; local y="$2"; local z="$3"
  ptr down "$x" "$y" "$z"
  ptr up "$x" "$y" "$z"
}

reset_scene() {
  console 'app.run {
    while (scene.exitGroup()) {}
    scene.resetScene()
    def rp = scene.rootPrototype()
    rp.lineStore.clearAll()
    rp.faceStore.clearAll()
    rp.dimensionStore.clearAll()
    rp.textStore.clearAll()
    scene.root.children.clear()
    scene.clearAllSelections()
    save.set("examples/mcp.demo.k3d")
    cameraCtl.setPosition(18f, 14f, 18f)
    cameraCtl.setTarget(0f, 0f, 0f)
    camera.up.set(0f, 1f, 0f)
    camera.lookAt(0f, 0f, 0f)
    camera.update()
    status.message = "Docs reset: perspective, Y-up"
  }'
  cmd_try "view.camera.orbit"
}

shot() {
  local target="$1"
  local resp path
  resp="$(mcp_get_retry '/scene/command?id=export.screenshot')"
  path="$(echo "$resp" | jq -r '.stdout' | sed -E 's/.*Screenshot saved: ([^ ]+).*/\1/')"
  [[ -f "$path" ]] || { log "screenshot path missing for ${target}: ${path}"; return 1; }
  cp "$path" "${IMG_DIR}/${target}"
  log "captured ${target}"
}

ch01() { reset_scene; shot ch01_01_intro_clean_scene.png; }
ch02() { reset_scene; cmd_try view.selection; cmd_try view.object_info; cmd_try view.objects; cmd_try view.plugin_manager; shot ch02_01_launch_with_core_panels.png; }
ch03() { reset_scene; cmd tool.builtin.rectangle; click -2 0 -2; click 2 0 2; cmd tool.builtin.push_pull; click 0 0 0; ptr move 0 6 0; click 0 6 0; cmd_try tool.builtin.select; click 0 0 0; cmd_try edit.group; shot ch03_01_saved_model_example.png; }
ch04() { reset_scene; cmd_try view.selection; cmd_try view.object_info; cmd_try view.objects; cmd_try view.model_settings; cmd_try view.polyline_settings; cmd_try view.architecture_settings; cmd_try view.lighting; cmd_try view.plugin_manager; shot ch04_01_ui_overview_all_panels.png; }
ch07() { reset_scene; cmd tool.builtin.line; click -8 0 -2; click 8 0 -2; cmd tool.builtin.rectangle; click -2 0 0; click 2 0 4; cmd tool.builtin.push_pull; click 0 0 2; ptr move 0 5 2; click 0 5 2; cmd tool.builtin.voxel; click 5 0 2; click 6 0 2; click 7 0 2; shot ch07_01_tools_mix_example.png; }
ch08() { reset_scene; cmd_try view.selection; cmd_try view.object_info; cmd_try view.objects; cmd_try view.model_settings; cmd_try view.polyline_settings; cmd_try view.architecture_settings; cmd_try view.lighting; cmd_try view.plugin_manager; shot ch08_01_panels_reference_example.png; }
ch10() { reset_scene; cmd tool.builtin.rectangle; click -2 0 -2; click 2 0 2; shot ch10_01_tutorial_rectangle.png; cmd tool.builtin.push_pull; click 0 0 0; ptr move 0 12 0; click 0 12 0; shot ch10_02_tutorial_extruded.png; cmd_try view.camera.walkthrough; cmd_try view.camera.orbit; shot ch10_03_tutorial_orbit_check.png; }
ch11() { reset_scene; cmd_try view.plugin_manager; cmd_try view.objects; shot ch11_01_plugin_workflow_panel.png; }
ch12() { reset_scene; cmd tool.builtin.voxel; click 4 0 1; click 5 0 1; click 6 0 1; shot ch12_01_issue_residual_artifact.png; reset_scene; shot ch12_02_fix_after_cleanup.png; }
ch13() { reset_scene; cmd tool.builtin.line; click -4 0 0; click 4 0 0; cmd tool.builtin.rectangle; click -2 0 -2; click 2 0 2; cmd_try view.selection; shot ch13_01_appendix_reference_scene.png; }

main() {
  mkdir -p "$IMG_DIR"
  mcp_get_retry '/mcp/status' >/dev/null
  ch01
  ch02
  ch03
  ch04
  ch07
  ch08
  ch10
  ch11
  ch12
  ch13
  log 'done'
}

main "$@"
