#!/bin/bash
# 版本号递增（铁流工业专用规则，a.b.c.d —— 无进制，按语义进位）
#
#   a (Major) 大时代/架构级：架构重构、剧情线完结、新星球发布   → a+1，b/c/d 归零
#   b (Minor) 机制/内容级：新方块体系、核电站、算力集群、MCT     → b+1，c/d 归零
#   c (Patch) 小调整/平衡：改伤害、改配方、调 UI 颜色            → c+1，d 归零
#   d (Hotfix) 纯修 Bug：NPE 闪退、物理覆盖错位                  → d+1
#
# 例（假设 0.64.0.8）：
#   修 NPE          → 0.64.0.9
#   调天罚耗电      → 0.64.1.0
#   实装 RBMK       → 0.65.0.0
#   剧情完结        → 1.0.0.0
#
# 用法：
#   bash tools/bump-version.sh hotfix      # 也可以写 fix / 修复
#   bash tools/bump-version.sh patch       #            balance / 调整
#   bash tools/bump-version.sh minor       #            feat / 机制 / 内容
#   bash tools/bump-version.sh major       #            时代 / 架构
#   bash tools/bump-version.sh --next patch        # 只试算，不改文件
#   VERSION=0.64.0.8 bash tools/bump-version.sh --next minor    # 指定起点试算
set -euo pipefail

MOD=/sdcard/dsh/mindustry-mod
FILE="$MOD/mod.hjson"

only_next=0
if [ "${1:-}" = "--next" ]; then
    only_next=1
    shift
fi

kind="${1:-}"
case "$kind" in
    hotfix|fix|bug|d|修复)  kind=hotfix ;;
    patch|balance|c|调整)   kind=patch ;;
    minor|feat|content|b|机制|内容) kind=minor ;;
    major|a|时代|架构)      kind=major ;;
    *)
        echo "用法: bash tools/bump-version.sh [--next] hotfix|patch|minor|major" >&2
        exit 1
        ;;
esac

cur="${VERSION:-$(grep -E '^version:' "$FILE" | head -1 | awk '{print $2}')}"
# 兼容旧的三段号：0.65.0 当成 0.65.0.0
if [[ "$cur" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    cur="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}.0"
fi
if ! [[ "$cur" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "版本号格式不对：$cur（应该是 a.b.c.d）" >&2
    exit 1
fi

a=$((10#${BASH_REMATCH[1]}))
b=$((10#${BASH_REMATCH[2]}))
c=$((10#${BASH_REMATCH[3]}))
d=$((10#${BASH_REMATCH[4]}))

case "$kind" in
    hotfix) d=$((d + 1)) ;;
    patch)  c=$((c + 1)); d=0 ;;
    minor)  b=$((b + 1)); c=0; d=0 ;;
    major)  a=$((a + 1)); b=0; c=0; d=0 ;;
esac

new="$a.$b.$c.$d"

if [ "$only_next" -eq 1 ]; then
    echo "$new"
    exit 0
fi

sed -i "s/^version: .*/version: $new/" "$FILE"
echo "$cur -> $new  ($kind)"
