#!/bin/bash
# Ironworks 构建脚本：javac -> jar -> d8 dex -> 合并 dex -> out/*.jar
# 之所以不用 Gradle：环境无 Gradle、外网受限，且产物只依赖一个 classpath jar，直接调用 JDK 工具更可控。
set -euo pipefail

MOD=/sdcard/dsh/mindustry-mod
JDK=$(cat /root/jdk.path)
JAVAC="$JDK/bin/javac"; JAR="$JDK/bin/jar"; JAVA="$JDK/bin/java"
R8_JAR=/root/api/r8-9.5.26.jar

# 编译基准版本：160.1（MindustryX 改端引擎版本，较旧，向下兼容面最大）
API="${API_JAR:-/root/api/Mindustry-160.1.jar}"
MOD_ID=ironworks
JAVA_RELEASE=16      # 参照实机可运行模组 Saturation-Firepower 4.0.6 的字节码级别(major 60)
MIN_API=21           # 与游戏 APK 的 minSdk 一致

for f in "$API" "$R8_JAR" "$JAVAC"; do
  [ -e "$f" ] || { echo "缺少必需文件: $f" >&2; exit 1; }
done

BUILD="$MOD/build"; CLASSES="$BUILD/classes"; DEXOUT="$BUILD/dex"
rm -rf "$BUILD"; mkdir -p "$CLASSES" "$DEXOUT" "$MOD/out"

echo "== 1/5 收集源码 =="
find "$MOD/src" -name '*.java' | sort > "$BUILD/sources.txt"
n=$(wc -l < "$BUILD/sources.txt")
[ "$n" -gt 0 ] || { echo "src/ 下没有 .java" >&2; exit 1; }
echo "   $n 个源文件"

echo "== 2/5 编译 (release $JAVA_RELEASE, classpath=$API) =="
"$JAVAC" --release "$JAVA_RELEASE" -encoding UTF-8 -Xlint:-options \
  -cp "$API" -d "$CLASSES" @"$BUILD/sources.txt"

echo "== 3/5 装配资源 =="
# mod.hjson / bundles / sprites / icon 全部平铺进 jar 根
cp -a "$MOD/assets/." "$CLASSES/"
cp -a "$MOD/mod.hjson" "$CLASSES/mod.hjson"
# GPL 分发要求：许可证与第三方声明随 jar 一起走
cp -a "$MOD/LICENSE" "$MOD/NOTICE.md" "$MOD/THIRD-PARTY.md" "$CLASSES/"

echo "== 4/5 打 jar =="
"$JAR" --create --file "$BUILD/$MOD_ID-classes.jar" -C "$CLASSES" .

echo "== 5/5 dex (min-api $MIN_API) =="
"$JAVA" -cp "$R8_JAR" com.android.tools.r8.D8 \
  --release --min-api "$MIN_API" \
  --output "$DEXOUT" "$BUILD/$MOD_ID-classes.jar"
[ -f "$DEXOUT/classes.dex" ] || { echo "d8 没有产出 classes.dex" >&2; exit 1; }

cp "$BUILD/$MOD_ID-classes.jar" "$MOD/out/$MOD_ID.jar"
"$JAR" --update --file "$MOD/out/$MOD_ID.jar" -C "$DEXOUT" classes.dex

echo
echo "== 产物 =="
ls -l "$MOD/out/$MOD_ID.jar" | awk '{print "  "$9"  "$5" 字节"}'
echo "  内含:"
"$JAR" --list --file "$MOD/out/$MOD_ID.jar" | awk '!/\/$/' | head -20
