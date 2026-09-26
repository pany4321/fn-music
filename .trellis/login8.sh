#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
foc() {
  "$ADB" -s $D shell uiautomator dump //sdcard//f.xml >/dev/null 2>&1
  "$ADB" -s $D pull //sdcard//f.xml "$OUT/f.xml" >/dev/null 2>&1
  python -c "
import re
xml = open(r'$OUT/f.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0)
    if 'focused=\"true\"' in n:
        d=re.search(r'content-desc=\"([^\"]*)\"', n); t=re.search(r'text=\"([^\"]*)\"', n)
        print('  FOCUS:', repr((d.group(1) if d else '') or (t.group(1) if t else '')))
        break
"
}
"$ADB" -s $D shell input keyevent 4; sleep 2   # 退出编辑态
for i in 1 2 3 4; do
  "$ADB" -s $D shell input keyevent 20; sleep 1
  echo "after DOWN $i:"; foc
done
