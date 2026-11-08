#!/usr/bin/env bash
# 第36〜38章の実験用に、QuickVote から4種類の jar を作る。code/quickvote/ は変更しない。
#   reference.jar   リファレンス実装そのもの
#   naive.jar       メモリ配信のみの版（pg_notify を呼ばず、コミット後に同じJVMの購読者だけへ知らせる。ハートビートはDBの読み直しを頼まない）
#   nocloseall.jar  停止時に hub.closeAll() を呼ばない版（graceful shutdown が SSE に止められる）
#   onepage.jar     指摘5の修正前（履歴の再送が最初の500件で止まる）
# 使い方: ./build.sh            （4つとも作る）
#         ./build.sh naive      （指定したものだけ）
set -euo pipefail
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot}"
export PATH="$JAVA_HOME/bin:$PATH"

HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../../quickvote"
WORK="${WORK_DIR:-$HERE/.work}"
EVENT=src/main/java/com/example/quickvote/event
VARIANTS=("${@:-reference naive nocloseall onepage}")
[ "$#" -eq 0 ] && VARIANTS=(reference naive nocloseall onepage)

rm -rf "$WORK" && mkdir -p "$WORK" "$HERE/jars"
# target/ と IDE の生成物を除いてコピーする
(cd "$SRC" && tar --exclude=./target --exclude=./.idea -cf - .) | (cd "$WORK" && tar -xf -)

for v in "${VARIANTS[@]}"; do
  echo "== build: $v"
  # まず元のイベント関連ファイルに戻してから、変種のファイルだけ上書きする
  cp "$SRC/$EVENT"/*.java "$WORK/$EVENT/"
  if [ -d "$HERE/variants/$v" ]; then cp "$HERE/variants/$v"/*.java "$WORK/$EVENT/"; fi
  (cd "$WORK" && ./mvnw -B -q -DskipTests package)
  cp "$WORK/target/quickvote.jar" "$HERE/jars/$v.jar"
done
echo "== done"; ls -la "$HERE/jars"
