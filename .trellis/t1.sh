#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
# 1) search page: verify history block + focus chain (need a recorded query first)
"$ADB" -s $D shell input tap 2350 159; sleep 4      # 我的 tab
"$ADB" -s $D shell input tap 800 400; sleep 4       # 搜索栏（我的页）
shot s0-search-empty
key 19; shot s1-up-back-to-field
