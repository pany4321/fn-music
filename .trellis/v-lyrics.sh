#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
"$ADB" -s $D shell input keyevent 4; sleep 3          # leave search
"$ADB" -s $D shell input tap 2060 164; sleep 4        # 首页 tab
"$ADB" -s $D shell input tap 2020 700; sleep 5        # 最近播放 卡片
"$ADB" -s $D shell input tap 1300 988; sleep 12       # 第一条歌曲
"$ADB" -s $D exec-out screencap -p > "$OUT/ly1.png" 2>/dev/null
sleep 6
"$ADB" -s $D exec-out screencap -p > "$OUT/ly2.png" 2>/dev/null
