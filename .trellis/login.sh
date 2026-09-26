#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
dump() { "$ADB" -s $D shell uiautomator dump //sdcard//$1.xml >/dev/null 2>&1; "$ADB" -s $D pull //sdcard//$1.xml "$OUT/$1.xml" >/dev/null 2>&1; }
"$ADB" -s $D shell input keyevent 4; sleep 2      # hide IME
dump l1
python -c "
import re
xml = open(r'$OUT/l1.xml', encoding='utf-8', errors='ignore').read()
idx = 0
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    if 'android.widget.EditText' in n:
        b = re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', n)
        g = [int(x) for x in b.groups()]
        print('FIELD', idx, (g[0]+g[2])//2, (g[1]+g[3])//2)
        idx += 1
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0); d = re.search(r'content-desc=\"([^\"]*)\"', n)
    if d and d.group(1) == '登录':
        b = re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', n)
        g = [int(x) for x in b.groups()]
        print('LOGIN', (g[0]+g[2])//2, (g[1]+g[3])//2)
"
