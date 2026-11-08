#!/bin/sh
# コンテナの制限が JVM のヒープ上限にどう反映されるかを、PrintFlagsFinal で確かめる。
IMG=eclipse-temurin:25-jre
show() { grep -E ' MaxHeapSize| MaxRAMPercentage| UseG1GC | UseSerialGC | ActiveProcessorCount'; }
echo "### 制限なし（Docker Desktop の VM 全体 約15.6GiB が見える）"
docker run --rm $IMG java -XX:+PrintFlagsFinal -version 2>&1 | show
echo "### -m 512m（既定の MaxRAMPercentage=25）"
docker run --rm -m 512m $IMG java -XX:+PrintFlagsFinal -version 2>&1 | show
echo "### -m 512m -XX:MaxRAMPercentage=75"
docker run --rm -m 512m -e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 $IMG java -XX:+PrintFlagsFinal -version 2>&1 | show
echo "### -m 512m --cpus 1（CPU 1個だと G1 ではなく Serial が選ばれる）"
docker run --rm -m 512m --cpus 1 -e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 $IMG java -XX:+PrintFlagsFinal -version 2>&1 | show
echo "### -m 2g --cpus 2"
docker run --rm -m 2g --cpus 2 -e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 $IMG java -XX:+PrintFlagsFinal -version 2>&1 | show
echo "### JVM が見ているコンテナ情報"
docker run --rm -m 512m --cpus 1.5 $IMG java -XshowSettings:system -version 2>&1 | head -12
