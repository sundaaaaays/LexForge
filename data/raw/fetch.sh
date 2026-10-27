#!/usr/bin/env bash
# 断点续传下载（应对代理在 ~1.5MB 处中断）
set -u
URL="https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
OUT="ecdict.csv"
touch "$OUT"
stall=0
for i in $(seq 1 400); do
  before=$(stat -c %s "$OUT")
  curl -sSL --http1.1 -C - --max-time 25 -o "$OUT" "$URL" 2>/dev/null
  after=$(stat -c %s "$OUT")
  echo "attempt $i: $before -> $after"
  if [ "$after" -eq "$before" ]; then
    stall=$((stall+1))
    if [ "$stall" -ge 4 ]; then echo "stalled at $after bytes"; break; fi
  else
    stall=0
  fi
  # 完整性判断：文件末尾应能读到换行且 parse 出 13 列（粗判）
  if [ "$after" -gt 5000000 ] && tail -c 200 "$OUT" | grep -q '^[^,]*,'; then
    echo "looks complete at $after"
  fi
done
ls -lh "$OUT"
