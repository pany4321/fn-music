#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
key 4; sleep 2                     # exit editing
"$ADB" -s $D shell input tap 436 903; sleep 8   # open first album result (records the query)
shot sr1-after-open
key 4; sleep 4                     # back to the search page
shot sr2-history
key 20; sleep 1; shot sr3-chip-focused   # field -> chips row
key 20; sleep 1; shot sr4-down-results   # chips -> results
