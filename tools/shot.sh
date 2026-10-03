#!/usr/bin/env bash
# Takes a screenshot of the connected phone and lists the visible text with bounds.
# Usage: tools/shot.sh <name> [output directory]
adb="${LOCALAPPDATA//\\//}/Android/Sdk/platform-tools/adb.exe"
dir="${2:-${TEMP:-/tmp}}"
export MSYS_NO_PATHCONV=1
"$adb" shell screencap -p /sdcard/tnm_shot.png
"$adb" pull /sdcard/tnm_shot.png "$dir/$1.png" > /dev/null
"$adb" shell uiautomator dump /sdcard/tnm_ui.xml > /dev/null
"$adb" shell cat /sdcard/tnm_ui.xml | tr '>' '>\n' \
  | sed -n 's/.* text="\([^"]*\)".*content-desc="\([^"]*\)".*bounds="\([^"]*\)".*/\3 \1|\2/p' \
  | grep -v ' |$'
