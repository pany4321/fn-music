#!/bin/bash
ADB="D:/research/android/Sdk/platform-tools/adb.exe"
D=3B1F5VEA9BBV3NFR
OUT="D:/vscode/fn-music-tv/.trellis"
key() { "$ADB" -s $D shell input keyevent "$1"; sleep 1; }
shot() { "$ADB" -s $D exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
key 4; sleep 2                       # 退出编辑态（输入法收起）
shot sk0-after-back
key 20; sleep 1; shot sk1-down1      # 字段 -> 历史首格
key 20; sleep 1; shot sk2-down2      # -> 第二行（清空 所在行）
key 19; sleep 1; shot sk3-up-back    # 回到第一行
