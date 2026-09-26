#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
# go to My page -> artist band -> walk right to 全部歌手 -> open grid
"$ADB" -s $D shell input tap 2350 159; sleep 4
key 20; key 20; key 20
for i in $(seq 1 9); do key 22; done
key 23; sleep 5
shot g1-grid
key 19; shot g2-up-to-back
key 23; sleep 4; shot g3-after-ok
