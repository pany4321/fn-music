#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
"$ADB" -s $D shell uiautomator dump //sdcard//l5.xml >/dev/null 2>&1; "$ADB" -s $D pull //sdcard//l5.xml "$OUT/l5.xml" >/dev/null 2>&1
COORD=$(python -c "
import re
xml = open(r'$OUT/l5.xml', encoding='utf-8', errors='ignore').read()
idx=0
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0)
    if 'android.widget.EditText' in n:
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n); g=[int(x) for x in b.groups()]
        if idx==1: print((g[0]+g[2])//2,(g[1]+g[3])//2)
        idx+=1
")
echo "account field: $COORD"
"$ADB" -s $D shell input tap $COORD; sleep 2; "$ADB" -s $D shell input text "pan"; sleep 2
"$ADB" -s $D shell input keyevent 111; sleep 1
"$ADB" -s $D shell input keyevent 20; sleep 1; "$ADB" -s $D shell input keyevent 20; sleep 1
"$ADB" -s $D shell input keyevent 20; sleep 1; "$ADB" -s $D shell input keyevent 20; sleep 1
"$ADB" -s $D exec-out screencap -p > "$OUT/l6-pre.png" 2>/dev/null
"$ADB" -s $D shell input keyevent 23; sleep 12
"$ADB" -s $D exec-out screencap -p > "$OUT/l7-after.png" 2>/dev/null
