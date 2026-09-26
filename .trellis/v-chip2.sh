#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
"$ADB" -s $D shell input tap 2350 159; sleep 4
"$ADB" -s $D shell input tap 799 424; sleep 5
key 20; key 20; key 19; sleep 1        # to the chip
key 23; sleep 6; shot d1-chip-search
