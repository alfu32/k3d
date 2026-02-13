#!/usr/bin/env bash
# Author: Codex (GPT-5)
# Date: February 13, 2026

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MCP_BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8765}"
IMAGES_DIR="${ROOT_DIR}/docs/images"
EXAMPLES_DIR="${ROOT_DIR}/examples"

log() {
    printf '[mcp-suite] %s\n' "$*"
}

fail() {
    printf '[mcp-suite] ERROR: %s\n' "$*" >&2
    exit 1
}

mcp_get() {
    curl --noproxy '*' -sS "${MCP_BASE_URL}$1"
}

mcp_post() {
    local path="$1"
    local payload="$2"
    curl --noproxy '*' -sS -X POST "${MCP_BASE_URL}${path}" --data-binary "${payload}"
}

mcp_cmd() {
    mcp_get "/scene/command?id=$1"
}

mcp_try_cmd() {
    local command_id="$1"
    local response
    response="$(mcp_cmd "${command_id}" 2>/dev/null || true)"
    [[ "${response}" == *'"success":true'* ]]
}

mcp_console() {
    mcp_post "/scene/console" "$1"
}

mcp_ptr_world() {
    local action="$1"
    local x="$2"
    local y="$3"
    local z="$4"
    mcp_get "/scene/pointer?action=${action}&worldX=${x}&worldY=${y}&worldZ=${z}"
}

click_world() {
    mcp_ptr_world down "$1" "$2" "$3" >/dev/null
    mcp_ptr_world up "$1" "$2" "$3" >/dev/null
}

drag_world() {
    local x0="$1"
    local y0="$2"
    local z0="$3"
    local x1="$4"
    local y1="$5"
    local z1="$6"
    mcp_ptr_world down "${x0}" "${y0}" "${z0}" >/dev/null
    mcp_ptr_world move "${x1}" "${y1}" "${z1}" >/dev/null
    mcp_ptr_world up "${x1}" "${y1}" "${z1}" >/dev/null
}

set_orbit_pose() {
    local x="$1"
    local y="$2"
    local z="$3"
    local tx="$4"
    local ty="$5"
    local tz="$6"
    mcp_console "app.run { cameraCtl.setPosition(${x}f, ${y}f, ${z}f); cameraCtl.setTarget(${tx}f, ${ty}f, ${tz}f); camera.up.set(0f, 1f, 0f); camera.lookAt(${tx}f, ${ty}f, ${tz}f); camera.update() }" >/dev/null
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
      status.message = "Scene reset for capture (perspective, Y-up)."
    }' >/dev/null
    mcp_cmd "tool.builtin.select" >/dev/null || true
    mcp_try_cmd "view.capture_ui_minimal" || true
    mcp_try_cmd "view.camera.orbit" || true
    set_orbit_pose 18 14 18 0 0 0
}

capture_to() {
    local target_name="$1"
    local response
    local source_path
    local before_path
    local before_mtime
    local current_mtime
    local attempts

    # export.screenshot names files with second-level timestamp precision.
    # Delay between captures prevents path reuse and stale-file races.
    before_path="$(ls -1t "${EXAMPLES_DIR}"/mcp.demo_*.png 2>/dev/null | head -n 1 || true)"
    before_mtime=0
    if [[ -n "${before_path}" && -f "${before_path}" ]]; then
        before_mtime="$(stat -c %Y "${before_path}" 2>/dev/null || echo 0)"
    fi
    sleep 1.2

    response="$(mcp_cmd export.screenshot)"
    [[ "${response}" == *'"success":true'* ]] || fail "screenshot command failed for ${target_name}"
    source_path=""
    attempts=0
    while [[ ${attempts} -lt 40 ]]; do
        source_path="$(ls -1t "${EXAMPLES_DIR}"/mcp.demo_*.png 2>/dev/null | head -n 1 || true)"
        if [[ -n "${source_path}" && -f "${source_path}" ]]; then
            current_mtime="$(stat -c %Y "${source_path}" 2>/dev/null || echo 0)"
            if [[ "${source_path}" != "${before_path}" || "${current_mtime}" -gt "${before_mtime}" ]]; then
                break
            fi
        fi
        sleep 0.1
        attempts=$((attempts + 1))
    done

    [[ -n "${source_path}" && -f "${source_path}" ]] || fail "could not locate screenshot file for ${target_name}"

    cp "${source_path}" "${IMAGES_DIR}/${target_name}"
    log "captured ${target_name}"
}

use_tool() {
    mcp_cmd "tool.builtin.$1" >/dev/null
}

capture_ch05() {
    log "capturing Chapter 05"
    reset_scene_for_capture

    use_tool rectangle
    click_world -6 0 0
    click_world -2 0 4
    use_tool rectangle
    click_world 2 0 0
    click_world 6 0 4
    capture_to "ch05_01_cleanup_and_primitives.png"

    set_orbit_pose 16 11 16 0 0 0
    capture_to "ch05_02_orbit_view.png"

    set_orbit_pose 20 11 12 4 0 0
    capture_to "ch05_03_pan_effect.png"

    set_orbit_pose 10 7 8 2 0 2
    capture_to "ch05_04_zoom_effect.png"

    use_tool select
    click_world -4 0 2
    capture_to "ch05_05_single_selection.png"

    click_world 4 0 2
    capture_to "ch05_06_second_selection.png"

    drag_world -9 0 -2 9 0 6
    capture_to "ch05_07_window_selection.png"

    use_tool select
    click_world -10 0 -6
    mcp_ptr_world move 10 6 6 >/dev/null
    capture_to "ch05_08_volume_cube_preview.png"
    click_world 10 6 6
}

capture_ch06() {
    log "capturing Chapter 06"
    reset_scene_for_capture
    capture_to "ch06_01_cleanup.png"

    use_tool line
    click_world -6 0 -2
    click_world 6 0 -2
    capture_to "ch06_02_line_tool.png"

    use_tool rectangle
    click_world -2 0 0
    click_world 2 0 4
    capture_to "ch06_03_rectangle_tool.png"

    use_tool voxel
    click_world 4 0 2
    click_world 5 0 2
    click_world 6 0 2
    capture_to "ch06_04_voxel_tool.png"

    mcp_cmd "view.camera.walkthrough" >/dev/null
    mcp_console 'app.run { camera.position.set(10f, 6f, 10f); camera.up.set(0f, 1f, 0f); camera.lookAt(0f, 0f, 0f); camera.update() }' >/dev/null
    capture_to "ch06_05_camera_walk.png"

    mcp_cmd "view.camera.orthographic" >/dev/null
    mcp_cmd "view.ortho.top" >/dev/null
    mcp_cmd "view.camera.orbit" >/dev/null
    set_orbit_pose 14 11 14 0 0 0
    capture_to "ch06_06_camera_ortho_top.png"

    mcp_cmd "view.selection" >/dev/null
    mcp_cmd "view.model_settings" >/dev/null
    mcp_cmd "view.lighting" >/dev/null
    mcp_cmd "view.plugin_manager" >/dev/null
    capture_to "ch06_07_actions_and_panels.png"
}

capture_ch09() {
    log "capturing Chapter 09"
    reset_scene_for_capture
    capture_to "ch09_01_cleanup.png"

    mcp_cmd "view.objects" >/dev/null
    mcp_cmd "view.selection" >/dev/null
    mcp_cmd "view.object_info" >/dev/null
    mcp_cmd "view.model_settings" >/dev/null
    capture_to "ch09_02_view_commands_open_panels.png"

    use_tool rectangle
    click_world -2 0 -2
    click_world 3 0 3
    capture_to "ch09_03_tool_command_rectangle.png"

    use_tool select
    click_world 0 0 0
    mcp_cmd "edit.group" >/dev/null
    capture_to "ch09_04_edit_group_command.png"

    mcp_cmd "view.camera.orthographic" >/dev/null
    mcp_cmd "view.ortho.front" >/dev/null
    mcp_cmd "view.camera.orbit" >/dev/null
    set_orbit_pose 12 9 12 0 0 0
    capture_to "ch09_05_camera_commands.png"
}

main() {
    mkdir -p "${IMAGES_DIR}"
    local status
    status="$(mcp_get "/mcp/status" || true)"
    [[ "${status}" == *'"success":true'* ]] || fail "MCP server is not reachable at ${MCP_BASE_URL}"
    [[ "${status}" == *'"running":true'* ]] || fail "MCP server is reachable but not running"

    capture_ch05
    capture_ch06
    capture_ch09

    mcp_try_cmd "view.capture_ui_restore" || true
    log "done"
}

main "$@"
