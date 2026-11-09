#!/usr/bin/env bash
# tools/ui.sh - drive the app on the emulator/phone through uiautomator.
#
#   tools/ui.sh texts            list visible texts and content descriptions
#   tools/ui.sh tap "Label"      tap the first node whose text or description is Label
#   tools/ui.sh shot out.png     screenshot
#   tools/ui.sh key N            send a keyevent (4 = back)
#   tools/ui.sh swipe x1 y1 x2 y2 [ms]
#
# Dump the tree after each step and check it, rather than scripting blind
# taps (CLAUDE.md, "Capture verified stills").
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
A="$ROOT/.tools/android-sdk/platform-tools/adb"
dump() { "$A" shell uiautomator dump /sdcard/ui.xml >/dev/null; "$A" shell cat /sdcard/ui.xml; }
case "${1:-}" in
    texts) dump | grep -o 'text="[^"]\+"\|content-desc="[^"]\+"' ;;
    tap)
        xy="$(dump | python3 -I -c '
import re, sys
t = sys.argv[1]
for n in re.findall(r"<node [^>]*>", sys.stdin.read()):
    if f"text=\"{t}\"" in n or f"content-desc=\"{t}\"" in n:
        a, b, c, d = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n).groups())
        print((a + c) // 2, (b + d) // 2)
        break
' "$2")"
        [ -n "$xy" ] || { echo "not found: $2" >&2; exit 1; }
        "$A" shell input tap $xy ;;
    shot) "$A" exec-out screencap -p > "$2" ;;
    key) "$A" shell input keyevent "$2" ;;
    swipe) "$A" shell input swipe "$2" "$3" "$4" "$5" "${6:-300}" ;;
    *) sed -n '2,11p' "$0" ;;
esac
