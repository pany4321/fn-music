#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
"$ADB" -s $D shell input keyevent 4; sleep 2
"$ADB" -s $D shell input keyevent 4; sleep 3
"$ADB" -s $D shell input tap 2060 164; sleep 4       # 首页
"$ADB" -s $D shell input tap 2020 700; sleep 5       # 最近播放卡片
"$ADB" -s $D shell input tap 1300 988; sleep 14      # 播放第一条
"$ADB" -s $D exec-out screencap -p > "$OUT/ly3.png" 2>/dev/null
sleep 8
"$ADB" -s $D exec-out screencap -p > "$OUT/ly4.png" 2>/dev/null
