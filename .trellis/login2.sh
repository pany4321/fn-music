#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
"$ADB" -s $D shell input tap 1276 487; sleep 2; "$ADB" -s $D shell input text "192.168.3.97:5666"; sleep 2
"$ADB" -s $D shell input tap 839 789; sleep 2; "$ADB" -s $D shell input text "pan"; sleep 2
"$ADB" -s $D shell input tap 1276 1084; sleep 2; "$ADB" -s $D shell input text "Pany730923"; sleep 2
"$ADB" -s $D shell input keyevent 111; sleep 1
"$ADB" -s $D shell uiautomator dump //sdcard//l2.xml >/dev/null 2>&1; "$ADB" -s $D pull //sdcard//l2.xml "$OUT/l2.xml" >/dev/null 2>&1
python -c "
import re
xml = open(r'$OUT/l2.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0); d = re.search(r'content-desc=\"([^\"]*)\"', n)
    if d and d.group(1) == '登录':
        b = re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', n)
        g=[int(x) for x in b.groups()]; print('LOGIN', (g[0]+g[2])//2, (g[1]+g[3])//2)
    if d and d.group(1) == '密码':
        b = re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', n)
        g=[int(x) for x in b.groups()]; print('PWD', (g[0]+g[2])//2, (g[1]+g[3])//2)
"
