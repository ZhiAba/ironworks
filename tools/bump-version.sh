#!/bin/bash
# 版本号递增（铁流工业专用规则）
#
#   a.b.c：c 满 10 进 b，b 满 100 进 a
#   0.20.9 → 0.21.0      0.99.9 → 1.0.0      0.65.0 → 0.65.1
#
# 用法：
#   bash tools/bump-version.sh          # 原地递增 mod.hjson 的 version 并打印
#   bash tools/bump-version.sh --next   # 只打印下一个版本号，不改文件
#   VERSION=0.99.9 bash tools/bump-version.sh --next   # 用指定版本试算（测试用）
set -euo pipefail

MOD=/sdcard/dsh/mindustry-mod
FILE="$MOD/mod.hjson"

cur="${VERSION:-$(grep -E '^version:' "$FILE" | head -1 | awk '{print $2}')}"
if ! [[ "$cur" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "版本号格式不对：$cur（应该是 a.b.c）" >&2
    exit 1
fi

a="${BASH_REMATCH[1]}"; b="${BASH_REMATCH[2]}"; c="${BASH_REMATCH[3]}"

# 去掉前导 0，避免被当成八进制（033 之类）
a=$((10#$a)); b=$((10#$b)); c=$((10#$c))

c=$((c + 1))
if [ "$c" -ge 10 ]; then
    c=0
    b=$((b + 1))
fi
if [ "$b" -ge 100 ]; then
    b=0
    a=$((a + 1))
fi

new="$a.$b.$c"

if [ "${1:-}" = "--next" ]; then
    echo "$new"
    exit 0
fi

sed -i "s/^version: .*/version: $new/" "$FILE"
echo "$cur -> $new"
