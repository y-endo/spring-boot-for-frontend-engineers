#!/usr/bin/env bash
# old/ の「古い書き方」が Spring Boot 4.1.2 のクラスパスでどう失敗するかを確かめる。
# 事前に: ./mvnw -q dependency:build-classpath -Dmdep.outputFile=cp.txt -Dmdep.includeScope=test
CP="$(tr -d '\r\n' < cp.txt)"
mkdir -p target/old-out
for f in old/*.java; do
  echo "=== $f"
  javac -J-Duser.language=en -J-Duser.country=US -Xlint:deprecation -encoding UTF-8 -proc:none -d target/old-out -cp "$CP" "$f" 2>&1 | grep -vE "^\s+\^|^Note:" | head -${LINES_PER_FILE:-6}
done
