#!/usr/bin/env bash
# Author: Codex (GPT-5)
# Date: February 13, 2026

set -uo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8765}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGES_DIR="${ROOT_DIR}/docs/images"
EXAMPLES_DIR="${ROOT_DIR}/examples"

log() {
  printf '[chapter-capture] %s\n' "$*"
}

mcp_call_success() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  local out=""
  local i

  for i in $(seq 1 120); do
    if [[ "$method" == "GET" ]]; then
      out="$(curl --noproxy '*' -sS --max-time 6 "${BASE_URL}${path}" 2>/dev/null || true)"
    else
      out="$(curl --noproxy '*' -sS --max-time 10 -X POST "${BASE_URL}${path}" --data-binary "$data" 2>/dev/null || true)"
    fi
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
  mcp_call_success GET "/scene/command?id=${id}" >/dev/null
}

mcp_cmd_raw() {
  local id="$1"
  mcp_call_success GET "/scene/command?id=${id}"
}

mcp_cmd_try() {
  local id="$1"
  mcp_call_success GET "/scene/command?id=${id}" >/dev/null 2>&1 || true
}

mcp_ptr_world() {
  local action="$1"
  local x="$2"
  local y="$3"
  local z="$4"
  mcp_call_success GET "/scene/pointer?action=${action}&worldX=${x}&worldY=${y}&worldZ=${z}" >/dev/null
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

use_tool() {
  mcp_cmd "tool.builtin.$1"
}

reset_scene() {
  mcp_cmd "edit.reset_scene_for_capture"
  mcp_cmd_try "view.camera.orbit"
}

capture_to() {
  local target="$1"
  local resp src

  sleep 0.8
  resp="$(mcp_cmd_raw "export.screenshot")" || { log "failed export.screenshot for ${target}"; return 1; }
  src="$(echo "$resp" | rg -o '/home[^\" ]+mcp\\.demo_[0-9_]+\\.png' | tail -n 1 || true)"
  if [[ -z "$src" || ! -f "$src" ]]; then
    # Fallback to latest screenshot if stdout parsing is noisy.
    src="$(ls -1t "${EXAMPLES_DIR}"/mcp.demo_*.png 2>/dev/null | head -n 1 || true)"
  fi

  [[ -n "$src" && -f "$src" ]] || { log "failed screenshot for ${target}"; return 1; }
  cp "$src" "${IMAGES_DIR}/${target}"
  log "captured ${target}"
}

capture_ch01() {
  log "chapter 01"
  reset_scene
  capture_to "ch01_01_intro_clean_scene.png"
}

capture_ch02() {
  log "chapter 02"
  reset_scene
  mcp_cmd_try "view.selection"
  mcp_cmd_try "view.object_info"
  mcp_cmd_try "view.objects"
  mcp_cmd_try "view.plugin_manager"
  capture_to "ch02_01_launch_with_core_panels.png"
}

capture_ch03() {
  log "chapter 03"
  reset_scene
  use_tool rectangle
  click_world -2 0 -2
  click_world 2 0 2
  use_tool push_pull
  click_world 0 0 0
  mcp_ptr_world move 0 6 0
  click_world 0 6 0
  mcp_cmd_try "tool.builtin.select"
  click_world 0 0 0
  mcp_cmd_try "edit.group"
  capture_to "ch03_01_saved_model_example.png"
}

capture_ch04() {
  log "chapter 04"
  reset_scene
  mcp_cmd_try "view.selection"
  mcp_cmd_try "view.object_info"
  mcp_cmd_try "view.objects"
  mcp_cmd_try "view.model_settings"
  mcp_cmd_try "view.polyline_settings"
  mcp_cmd_try "view.architecture_settings"
  mcp_cmd_try "view.lighting"
  mcp_cmd_try "view.plugin_manager"
  capture_to "ch04_01_ui_overview_all_panels.png"
}

capture_ch05() {
  log "chapter 05"
  reset_scene
  use_tool rectangle
  click_world -6 0 0
  click_world -2 0 4
  use_tool rectangle
  click_world 2 0 0
  click_world 6 0 4
  use_tool select
  drag_world -9 0 -2 9 0 6
  capture_to "ch05_09_interaction_anchor.png"
}

capture_ch06() {
  log "chapter 06"
  reset_scene
  use_tool line
  click_world -6 0 -2
  click_world 6 0 -2
  use_tool rectangle
  click_world -2 0 0
  click_world 2 0 4
  mcp_cmd_try "view.camera.walkthrough"
  mcp_cmd_try "view.camera.orbit"
  capture_to "ch06_08_toolbar_anchor.png"
}

capture_ch07() {
  log "chapter 07"
  reset_scene
  use_tool line
  click_world -8 0 -2
  click_world 8 0 -2
  use_tool rectangle
  click_world -2 0 0
  click_world 2 0 4
  use_tool push_pull
  click_world 0 0 2
  mcp_ptr_world move 0 5 2
  click_world 0 5 2
  use_tool voxel
  click_world 5 0 2
  click_world 6 0 2
  click_world 7 0 2
  capture_to "ch07_01_tools_mix_example.png"
}

capture_ch08() {
  log "chapter 08"
  reset_scene
  mcp_cmd_try "view.selection"
  mcp_cmd_try "view.object_info"
  mcp_cmd_try "view.objects"
  mcp_cmd_try "view.model_settings"
  mcp_cmd_try "view.polyline_settings"
  mcp_cmd_try "view.architecture_settings"
  mcp_cmd_try "view.lighting"
  mcp_cmd_try "view.plugin_manager"
  capture_to "ch08_01_panels_reference_example.png"
}

capture_ch09() {
  log "chapter 09"
  reset_scene
  mcp_cmd_try "view.selection"
  mcp_cmd_try "view.objects"
  mcp_cmd_try "view.plugin_manager"
  use_tool rectangle
  click_world -2 0 -2
  click_world 3 0 3
  capture_to "ch09_06_commands_anchor.png"
}

capture_ch10() {
  log "chapter 10"
  reset_scene
  use_tool rectangle
  click_world -2 0 -2
  click_world 2 0 2
  capture_to "ch10_01_tutorial_rectangle.png"

  use_tool push_pull
  click_world 0 0 0
  mcp_ptr_world move 0 12 0
  click_world 0 12 0
  capture_to "ch10_02_tutorial_extruded.png"

  mcp_cmd_try "view.camera.walkthrough"
  mcp_cmd_try "view.camera.orbit"
  capture_to "ch10_03_tutorial_orbit_check.png"
}

capture_ch11() {
  log "chapter 11"
  reset_scene
  mcp_cmd_try "view.plugin_manager"
  mcp_cmd_try "view.objects"
  capture_to "ch11_01_plugin_workflow_panel.png"
}

capture_ch12() {
  log "chapter 12"
  reset_scene
  use_tool voxel
  click_world 4 0 1
  click_world 5 0 1
  click_world 6 0 1
  capture_to "ch12_01_issue_residual_artifact.png"

  reset_scene
  capture_to "ch12_02_fix_after_cleanup.png"
}

capture_ch13() {
  log "chapter 13"
  reset_scene
  use_tool line
  click_world -4 0 0
  click_world 4 0 0
  use_tool rectangle
  click_world -2 0 -2
  click_world 2 0 2
  mcp_cmd_try "view.selection"
  capture_to "ch13_01_appendix_reference_scene.png"
}

main() {
  mkdir -p "${IMAGES_DIR}"
  local failed=()

  capture_ch01 || failed+=("01")
  capture_ch02 || failed+=("02")
  capture_ch03 || failed+=("03")
  capture_ch04 || failed+=("04")
  capture_ch05 || failed+=("05")
  capture_ch06 || failed+=("06")
  capture_ch07 || failed+=("07")
  capture_ch08 || failed+=("08")
  capture_ch09 || failed+=("09")
  capture_ch10 || failed+=("10")
  capture_ch11 || failed+=("11")
  capture_ch12 || failed+=("12")
  capture_ch13 || failed+=("13")

  if [[ ${#failed[@]} -eq 0 ]]; then
    log "all chapter captures completed"
    return 0
  fi

  log "completed with failures in chapters: ${failed[*]}"
  return 1
}

main "$@"
