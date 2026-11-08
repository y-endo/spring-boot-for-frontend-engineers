#!/bin/sh
# ログレベルと出力形式を「環境変数だけで」変えられることを確かめる。再ビルドは不要。
# 前提: ../ch42-docker の compose（qv42）が起動済み。
run() {  # run <名前> <環境変数...>
  name=$1; shift
  docker rm -f qv43-lv >/dev/null 2>&1
  docker run -d --name qv43-lv --network qv42_default \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/quickvote \
    -e SPRING_DATASOURCE_USERNAME=quickvote -e SPRING_DATASOURCE_PASSWORD=quickvote \
    -e QUICKVOTE_VISITORCOOKIE_SECURE=false -e QUICKVOTE_CLEANUP_INTERVAL=10s "$@" qv42-app >/dev/null
  # 起動完了の目印（INFO の Started ...）は WARN だと出ないので、一定時間だけ待つ
  sleep 30   # 起動に10秒ほど、そのあと期限切れ削除（10秒ごと）が2回ほど走る
  echo "=== $name : $(docker logs qv43-lv 2>&1 | wc -l) 行"
  docker logs qv43-lv 2>&1 | tail -2 | cut -c1-230
}
run "既定（INFO・ECS JSON）"
run "ルートを WARN に" -e LOGGING_LEVEL_ROOT=WARN
run "自分のパッケージだけ DEBUG" -e LOGGING_LEVEL_COM_EXAMPLE_QUICKVOTE=DEBUG
run "JSON をやめて人間向けの形式に" -e LOGGING_STRUCTURED_FORMAT_CONSOLE=
docker rm -f qv43-lv >/dev/null 2>&1
