#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
"$ADB" -s $D shell input swipe 1300 1000 1300 650 300; sleep 2
"$ADB" -s $D shell uiautomator dump //sdcard//l3.xml >/dev/null 2>&1; "$ADB" -s $D pull //sdcard//l3.xml "$OUT/l3.xml" >/dev/null 2>&1
COORD=$(python -c "
import re
xml = open(r'$OUT/l3.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0); d = re.search(r'content-desc=\"([^\"]*)\"', n)
    if d and d.group(1) == '登录':
        b = re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', n)
        g=[int(x) for x in b.groups()]; print((g[0]+g[2])//2, (g[1]+g[3])//2); break
")
echo "login button: $COORD"
if [ -n "$COORD" ]; then "$ADB" -s $D shell input tap $COORD; sleep 10; "$ADB" -s $D exec-out screencap -p > "$OUT/l4-home.png" 2>/dev/null; fi
