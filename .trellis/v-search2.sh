#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
"$ADB" -s $D shell input tap 2350 159; sleep 4
"$ADB" -s $D shell uiautomator dump //sdcard//s1.xml >/dev/null 2>&1; "$ADB" -s $D pull //sdcard//s1.xml "$OUT/s1.xml" >/dev/null 2>&1
SB=$(python -c "
import re
xml = open(r'$OUT/s1.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0); t=re.search(r'text=\"([^\"]*)\"', n)
    if t and '搜索歌手' in t.group(1):
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n); g=[int(x) for x in b.groups()]
        print((g[0]+g[2])//2,(g[1]+g[3])//2); break
")
echo "search bar: $SB"
[ -n "$SB" ] && "$ADB" -s $D shell input tap $SB
sleep 5; shot sc1-page
"$ADB" -s $D shell input text "Hotel"; sleep 4; shot sc2-results
