#!/bin/bash
# 回归测试运行器：单实例锁 + 脚本快照。
# 为什么需要快照：bash 是边读边执行脚本的，跑到一半改 chain-test.sh 会读坏脚本（已踩过）。
set -u
SRC=/sdcard/dsh/mindustry-mod/tools/chain-test.sh
SNAP=/tmp/chain-test-snapshot-$$.sh
LOCK=/tmp/ironworks-chain-test.lock

if [ -f "$LOCK" ]; then
  PID=$(cat "$LOCK" 2>/dev/null || true)
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    echo "已有回归在跑（pid $PID），请等它结束或先 job_kill。" >&2
    exit 1
  fi
  echo "清理过期锁（pid ${PID:-空}）"
  rm -f "$LOCK"
fi

cp "$SRC" "$SNAP" || exit 1
echo $$ > "$LOCK"
trap 'rm -f "$LOCK" "$SNAP"' EXIT

echo "运行快照：$SNAP"
bash "$SNAP"
