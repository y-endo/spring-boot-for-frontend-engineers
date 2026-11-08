# 第43〜46章の実験で使う小さな関数。BASE=http://localhost:18042 . ./lib.sh のように読み込む。
BASE=${BASE:-http://localhost:8080}
# 訪問者として初回アクセスし、Cookie と XSRF を用意する: new_visitor <jarファイル>
new_visitor() { curl -sS -c "$1" -b "$1" "$BASE/" > /dev/null; }
xsrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$1" | tr -d '\r'; }
# api <jar> <METHOD> <path> [JSONファイル] [追加ヘッダー...]
api() {
  jar=$1; method=$2; path=$3; body=$4; shift 4 2>/dev/null || shift 3
  if [ -n "$body" ]; then
    curl -sS -c "$jar" -b "$jar" -X "$method" "$BASE$path" -H 'Content-Type: application/json; charset=utf-8' \
      -H "X-XSRF-TOKEN: $(xsrf "$jar")" "$@" --data-binary @"$body"
  else
    curl -sS -c "$jar" -b "$jar" -X "$method" "$BASE$path" -H "X-XSRF-TOKEN: $(xsrf "$jar")" "$@"
  fi
}
