#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
dump() { "$ADB" -s $D shell uiautomator dump //sdcard//$1.xml >/dev/null 2>&1; "$ADB" -s $D pull //sdcard//$1.xml "$OUT/$1.xml" >/dev/null 2>&1; }
"$ADB" -s $D shell input tap 2350 159; sleep 4      # 我的
dump t1
SET=$(python -c "
import re
xml = open(r'$OUT/t1.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0); d=re.search(r'content-desc=\"([^\"]*)\"', n)
    if d and d.group(1)=='设置':
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n); g=[int(x) for x in b.groups()]
        print((g[0]+g[2])//2,(g[1]+g[3])//2); break
")
echo "settings: $SET"
"$ADB" -s $D shell input tap $SET; sleep 5
dump t2
python -c "
import re
xml = open(r'$OUT/t2.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0); t=re.search(r'text=\"([^\"]*)\"', n)
    if t and t.group(1) in ('主题','珊瑚夜','森野绿','青碧','紫罗兰','樱粉','石墨蓝','关于','播放与歌词'):
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n); g=[int(x) for x in b.groups()]
        print(t.group(1), (g[0]+g[2])//2, (g[1]+g[3])//2)
"
"$ADB" -s $D exec-out screencap -p > "$OUT/t-settings.png" 2>/dev/null
