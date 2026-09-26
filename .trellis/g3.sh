#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
"$ADB" -s $D shell am force-stop com.fnmusic.tv.debug
"$ADB" -s $D shell am start -n com.fnmusic.tv.debug/com.fnmusic.tv.MainActivity >/dev/null 2>&1
sleep 9
"$ADB" -s $D exec-out screencap -p > "$OUT/g3-home.png" 2>/dev/null
