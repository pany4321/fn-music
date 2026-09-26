#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
"$ADB" -s $D shell input tap 2350 159; sleep 4     # 我的
"$ADB" -s $D shell input tap 799 424; sleep 5      # 搜索栏
"$ADB" -s $D shell input text "Hotel"; sleep 4
key 4; sleep 1                                      # 收起输入法
key 20; sleep 1; shot n1-down-to-chip
key 20; sleep 1; shot n2-second-row
key 19; sleep 1; shot n3-back-up
