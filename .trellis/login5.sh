#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
dump() { "$ADB" -s $D shell uiautomator dump //sdcard//$1.xml >/dev/null 2>&1; "$ADB" -s $D pull //sdcard//$1.xml "$OUT/$1.xml" >/dev/null 2>&1; }
# uncheck HTTPS if selected (focus is on it per last screenshot -> CENTER toggles)
"$ADB" -s $D shell input keyevent 23; sleep 2      # toggle HTTPS off
dump m1
ACCOUNT=$(python -c "
import re
xml = open(r'$OUT/m1.xml', encoding='utf-8', errors='ignore').read()
idx=0
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0)
    if 'android.widget.EditText' in n:
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n); g=[int(x) for x in b.groups()]
        if idx==1: print((g[0]+g[2])//2,(g[1]+g[3])//2)
        idx+=1
")
echo "account: $ACCOUNT"
"$ADB" -s $D shell input tap $ACCOUNT; sleep 3
dump m2
python -c "
import re
xml = open(r'$OUT/m2.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0)
    if 'focused=\"true\"' in n and 'EditText' in n:
        print('FOCUSED EDITTEXT ok')
"
"$ADB" -s $D shell input text "pan"; sleep 2
dump m3
python -c "
import re
xml = open(r'$OUT/m3.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*class=\"android.widget.EditText\"[^>]*>', xml):
    t=re.search(r'text=\"([^\"]*)\"', m.group(0)); print('FIELD:', repr(t.group(1) if t else None))
"
