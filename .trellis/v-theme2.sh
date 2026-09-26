#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
key 19; key 19; key 19          # to the tab
key 20; key 20                  # search bar -> settings
key 23; sleep 4                 # open settings
shot th1-settings
for i in $(seq 1 8); do key 20; done
shot th2-theme-row
key 22; key 22; sleep 2         # move right along the theme pills
shot th3-theme-switched
