#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
key 20; shot s-down-into-grid        # down from back button -> first grid item
key 4; sleep 3                        # back to My page
"$ADB" -s $D shell input tap 800 424; sleep 5   # search bar on My page
shot s-search-page
"$ADB" -s $D shell input text "Hotel"; sleep 4
shot s-results
