#!/bin/sh
set -eu

if command -v zenity >/dev/null 2>&1; then
  exec zenity --password --title="Authentication required" --text="${1:-Enter your password}"
fi

exit 1
