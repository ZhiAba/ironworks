#!/bin/bash
# 回归测试运行器（分级 + 参数化 + 单实例锁 + 脚本快照）
#
# 用法：
#   bash tools/run-tests.sh                 # 全量回归（封版/部署前）
#   bash tools/run-tests.sh 95 96           # 冒烟：只跑指定编号
#   bash tools/run-tests.sh group liquid    # 集成：按分组跑
#   bash tools/run-tests.sh list            # 列出全部测试
#
# 分组：liquid / power / turret / factory / multicrafter / content / ui
set -u
DIR=/sdcard/dsh/mindustry-mod
LOCK=/tmp/ironworks-chain-test.lock
MODE="${1:-full}"

if [ "$MODE" = "list" ]; then
  node "$DIR/tools/select-tests.js" list
  exit 0
fi

if [ -f "$LOCK" ]; then
  PID=$(cat "$LOCK" 2>/dev/null || true)
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    echo "已有回归在跑（pid $PID），请等它结束或先 job_kill。" >&2
    exit 1
  fi
  echo "清理过期锁（pid ${PID:-空}）"
  rm -f "$LOCK"
fi

SNAP=/tmp/chain-test-snap-$$.sh
if [ "$MODE" = "full" ]; then
  cp "$DIR/tools/chain-test.sh" "$SNAP" || exit 1
  echo "[tier] full regression（T1–T100）"
else
  if [ "$MODE" = "group" ]; then
    node "$DIR/tools/select-tests.js" group "$2" > "$SNAP" || exit 1
  else
    node "$DIR/tools/select-tests.js" "$@" > "$SNAP" || exit 1
  fi
  echo "[tier] subset: $*"
fi

echo $$ > "$LOCK"
START=$(date +%s)
trap 'rm -f "$LOCK" "$SNAP"' EXIT

LOG=/tmp/chain-test-run-$.log
bash "$SNAP" 2>&1 | tee "$LOG"
CODE=${PIPESTATUS[0]}
END=$(date +%s)

node "$DIR/tools/check-results.js" "$LOG"
ACHK=$?

LINES=$(wc -l < "$SNAP")
echo "RESULT exit=$CODE assert=$ACHK selected_script_lines=$LINES elapsed=$((END-START))s"
rm -f "$LOG"
[ $CODE -eq 0 ] && [ $ACHK -eq 0 ]
