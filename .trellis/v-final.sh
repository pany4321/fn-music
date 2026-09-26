#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
# search page: full chain field -> chip -> clear -> results
"$ADB" -s $D shell input tap 2350 159; sleep 4
"$ADB" -s $D shell input tap 799 424; sleep 5
"$ADB" -s $D shell input text "Hotel"; sleep 3
key 4; sleep 1
key 20; shot f1-chip
key 20; shot f2-clear
key 20; shot f3-results
key 19; key 19; shot f4-back-up
