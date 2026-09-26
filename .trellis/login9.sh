#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
fb() {
  "$ADB" -s $D shell uiautomator dump //sdcard//f.xml >/dev/null 2>&1
  "$ADB" -s $D pull //sdcard//f.xml "$OUT/f.xml" >/dev/null 2>&1
  python -c "
import re
xml = open(r'$OUT/f.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0)
    if 'focused=\"true\"' in n:
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', n); g=[int(x) for x in b.groups()]
        d=re.search(r'content-desc=\"([^\"]*)\"', n)
        print('  y-range', g[1], g[3], 'desc', (d.group(1) if d else ''))
        break
"
}
for i in 1 2 3; do
  "$ADB" -s $D shell input keyevent 20; sleep 1
  echo "down $i:"; fb
done
