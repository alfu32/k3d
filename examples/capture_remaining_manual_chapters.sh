#!/usr/bin/env bash
# Author: Codex (GPT-5)
# Date: February 13, 2026

set -euo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8765}"
EXAMPLES_DIR="$(cd "$(dirname "$0")" && pwd)"

log() {
  printf '[capture-docs] %s\n' "$*"
}

retry() {
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

mcp_status_ok() {
  local out
  out="$(curl --noproxy '*' -sS --max-time 3 "${BASE_URL}/mcp/status" 2>/dev/null || true)"
  [[ "$out" == *'"success":true'* && "$out" == *'"running":true'* ]]
}

mcp_get_raw() {
  local path="$1"
  curl --noproxy '*' -sS --max-time 6 "${BASE_URL}${path}"
}

mcp_post_raw() {
  local path="$1"
  local payload="$2"
  curl --noproxy '*' -sS --max-time 10 -X POST "${BASE_URL}${path}" --data-binary "$payload"
}

mcp_get() {
  local path="$1"
  local out=""
  local i
  for i in $(seq 1 80); do
    out="$(mcp_get_raw "$path" 2>/dev/null || true)"
    if [[ "$out" == *'"success":true'* ]]; then
      printf '%s' "$out"
      return 0
    fi
    sleep 0.25
  done
  printf '%s' "$out"
  return 1
}

mcp_post() {
  local path="$1"
  local payload="$2"
  local out=""
  local i
  for i in $(seq 1 80); do
    out="$(mcp_post_raw "$path" "$payload" 2>/dev/null || true)"
    if [[ "$out" == *'"success":true'* ]]; then
      printf '%s' "$out"
      return 0
    fi
    sleep 0.25
  done
  printf '%s' "$out"
  return 1
}

mcp_cmd() {
  local id="$1"
  mcp_get "/scene/command?id=${id}" >/dev/null
}

mcp_cmd_try() {
  local id="$1"
  mcp_get "/scene/command?id=${id}" >/dev/null 2>&1 || true
}

mcp_console() {
  local script="$1"
  mcp_post "/scene/console" "$script" >/dev/null
}

mcp_ptr_world() {
  local action="$1"
  local x="$2"
  local y="$3"
  local z="$4"
  mcp_get "/scene/pointer?action=${action}&worldX=${x}&worldY=${y}&worldZ=${z}" >/dev/null
}

click_world() {
  local x="$1"
  local y="$2"
  local z="$3"
  mcp_ptr_world down "$x" "$y" "$z"
  mcp_ptr_world up "$x" "$y" "$z"
}

drag_world() {
  local x0="$1"
  local y0="$2"
  local z0="$3"
  local x1="$4"
  local y1="$5"
  local z1="$6"
  mcp_ptr_world down "$x0" "$y0" "$z0"
  mcp_ptr_world move "$x1" "$y1" "$z1"
  mcp_ptr_world up "$x1" "$y1" "$z1"
}

set_orbit_pose() {
  local x="$1"
  local y="$2"
  local z="$3"
  local tx="$4"
  local ty="$5"
  local tz="$6"
  mcp_console "app.run { cameraCtl.setPosition(${x}f, ${y}f, ${z}f); cameraCtl.setTarget(${tx}f, ${ty}f, ${tz}f); camera.up.set(0f, 1f, 0f); camera.lookAt(${tx}f, ${ty}f, ${tz}f); camera.update(); status.message = \"Doc capture pose\" }"
}

reset_scene_for_capture() {
  mcp_console 'app.run {
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
    status.message = "Scene reset for chapter capture (perspective, Y-up)."
  }'
  mcp_cmd_try "view.camera.orbit"
  mcp_cmd_try "view.capture_ui_minimal"
  set_orbit_pose 18 14 18 0 0 0
}

capture_to() {
  local name="$1"
  local before latest src attempts

  before="$(ls -1t "${EXAMPLES_DIR}"/mcp.demo_*.png 2>/dev/null | head -n 1 || true)"
  sleep 1.2
  mcp_cmd export.screenshot

  src=""
  for attempts in $(seq 1 80); do
    latest="$(ls -1t "${EXAMPLES_DIR}"/mcp.demo_*.png 2>/dev/null | head -n 1 || true)"
    if [[ -n "$latest" && "$latest" != "$before" ]]; then
      src="$latest"
      break
    fi
    sleep 0.2
  done

  if [[ -z "$src" ]]; then
    log "ERROR: could not locate new screenshot for ${name}"
    return 1
  fi

  cp "$src" "${EXAMPLES_DIR}/${name}"
  log "captured ${name}"
}

use_tool() {
  mcp_cmd "tool.builtin.$1"
}

chapter_01_04() {
  log "capture chapter 01/04 assets"
  reset_scene_for_capture
  capture_to "manual_ch01_intro_clean_scene.png"

  mcp_cmd_try "view.selection"
  mcp_cmd_try "view.object_info"
  mcp_cmd_try "view.objects"
  mcp_cmd_try "view.model_settings"
  mcp_cmd_try "view.polyline_settings"
  mcp_cmd_try "view.architecture_settings"
  mcp_cmd_try "view.lighting"
  mcp_cmd_try "view.plugin_manager"
  set_orbit_pose 17 13 17 0 0 0
  capture_to "manual_ch04_ui_overview_panels.png"
}

chapter_03() {
  log "capture chapter 03 assets"
  reset_scene_for_capture
  use_tool rectangle
  click_world -2 0 -2
  click_world 2 0 2
  use_tool push_pull
  click_world 0 0 0
  mcp_ptr_world move 0 6 0
  click_world 0 6 0
  set_orbit_pose 12 10 12 0 2 0
  capture_to "manual_ch03_files_save_example.png"
}

chapter_07() {
  log "capture chapter 07 assets"
  reset_scene_for_capture

  use_tool line
  click_world -8 0 -2
  click_world 8 0 -2
  capture_to "manual_ch07_tool_line.png"

  use_tool rectangle
  click_world -2 0 0
  click_world 2 0 4
  capture_to "manual_ch07_tool_rectangle.png"

  use_tool push_pull
  click_world 0 0 2
  mcp_ptr_world move 0 5 2
  click_world 0 5 2
  capture_to "manual_ch07_tool_pushpull.png"

  use_tool arch_wall
  click_world -10 0 -10
  click_world 10 0 -10
  click_world 10 0 10
  click_world -10 0 10
  click_world -10 0 -10
  capture_to "manual_ch07_tool_arch_wall.png"

  use_tool voxel
  click_world 5 0 5
  click_world 6 0 5
  click_world 7 0 5
  capture_to "manual_ch07_tool_voxel.png"
}

chapter_08() {
  log "capture chapter 08 assets"
  reset_scene_for_capture

  mcp_cmd_try "view.selection"
  mcp_cmd_try "view.object_info"
  capture_to "manual_ch08_panel_selection_object.png"

  use_tool rectangle
  click_world -2 0 -2
  click_world 2 0 2
  use_tool select
  click_world 0 0 0
  mcp_cmd_try "edit.group"
  mcp_cmd_try "view.objects"
  capture_to "manual_ch08_panel_objects.png"

  mcp_console 'app.run { unit.set("cm", 30f); status.message = "Unit set to cm size 30" }'
  mcp_cmd_try "view.model_settings"
  capture_to "manual_ch08_panel_model_settings.png"

  mcp_cmd_try "view.architecture_settings"
  capture_to "manual_ch08_panel_architecture_settings.png"

  mcp_cmd_try "view.lighting"
  capture_to "manual_ch08_panel_lighting.png"

  mcp_cmd_try "view.plugin_manager"
  capture_to "manual_ch08_panel_plugin_manager.png"
}

chapter_10() {
  log "capture chapter 10 assets"
  reset_scene_for_capture

  use_tool rectangle
  click_world -2 0 -2
  click_world 2 0 2
  capture_to "manual_ch10_tutorial_step1_rectangle.png"

  use_tool push_pull
  click_world 0 0 0
  mcp_ptr_world move 0 12 0
  click_world 0 12 0
  capture_to "manual_ch10_tutorial_step2_extrude12.png"

  set_orbit_pose 14 12 10 0 5 0
  capture_to "manual_ch10_tutorial_step3_orbit_view_a.png"

  set_orbit_pose -12 10 14 0 5 0
  capture_to "manual_ch10_tutorial_step4_orbit_view_b.png"
}

chapter_11_12() {
  log "capture chapter 11/12 assets"
  reset_scene_for_capture

  mcp_cmd_try "view.plugin_manager"
  capture_to "manual_ch11_plugin_manager.png"

  mcp_cmd_try "view.camera.orthographic"
  mcp_cmd_try "view.ortho.top"
  capture_to "manual_ch12_issue_ortho_flat.png"

  mcp_cmd_try "view.camera.orbit"
  set_orbit_pose 15 12 15 0 0 0
  capture_to "manual_ch12_fix_orbit_perspective.png"
}

main() {
  retry 30 0.4 mcp_status_ok || { log "ERROR: MCP status not running"; exit 1; }
  chapter_01_04
  chapter_03
  chapter_07
  chapter_08
  chapter_10
  chapter_11_12
  log "all requested chapter assets captured"
}

main "$@"
