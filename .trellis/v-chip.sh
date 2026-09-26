#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
"$ADB" -s $D shell input tap 2350 159; sleep 4     # 我的
"$ADB" -s $D shell input tap 799 424; sleep 5      # 搜索栏
shot c1-page
# chip: down twice from the field (field -> chip), then OK on the chip
key 20; key 20; sleep 1; shot c2-clear-focused
key 19; sleep 1; shot c3-chip-focused
key 23; sleep 5; shot c4-after-chip-click
