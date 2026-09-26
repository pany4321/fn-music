#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
dump() { "$ADB" -s $D shell uiautomator dump //sdcard//$1.xml >/dev/null 2>&1; "$ADB" -s $D pull //sdcard//$1.xml "$OUT/$1.xml" >/dev/null 2>&1; }
"$ADB" -s $D shell input tap 1276 1069; sleep 2       # password field
"$ADB" -s $D shell input keyevent 111; sleep 1        # hide IME
"$ADB" -s $D shell input keyevent 20; sleep 1
"$ADB" -s $D shell input keyevent 20; sleep 1
"$ADB" -s $D shell input keyevent 20; sleep 1
dump n1
python -c "
import re
xml = open(r'$OUT/n1.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0)
    if 'focused=\"true\"' in n:
        d=re.search(r'content-desc=\"([^\"]*)\"', n); t=re.search(r'text=\"([^\"]*)\"', n)
        print('FOCUSED:', repr((d.group(1) if d else '') or (t.group(1) if t else '')))
"
