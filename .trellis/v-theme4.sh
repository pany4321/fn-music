#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
"$ADB" -s $D shell input swipe 1300 900 1300 400 400; sleep 2
"$ADB" -s $D shell input swipe 1300 900 1300 500 400; sleep 2
"$ADB" -s $D exec-out screencap -p > "$OUT/w4-bottom.png" 2>/dev/null
