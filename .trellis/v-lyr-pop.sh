#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
key 20; key 20; sleep 1; shot p1-results     # chip -> clear -> results
key 4; key 4; sleep 3                        # leave search
"$ADB" -s $D shell input tap 2060 164; sleep 4          # 首页
"$ADB" -s $D shell swipe 1300 1000 1300 500 400; sleep 2
"$ADB" -s $D shell swipe 1300 1000 1300 500 400; sleep 2
shot p2-home-scrolled
