#!/usr/bin/env bash
# 第47章: 「機械で拾える」レビュー観点を grep で一括確認する。
# 使い方:  ./review-grep.sh <ソースのディレクトリ>   （例: ./review-grep.sh review1-transaction/src/main）
# 見つかった行は「疑う場所」です。誤検出もあります。必ず読んで判断してください。
# 読まないと分からない観点（認可の抜け、競合、冪等性など）は、grep では拾えません。

set -u
target="${1:-.}"

check() {
  local title="$1" pattern="$2"
  local hits
  hits=$(grep -rnE --include='*.java' --include='*.xml' --include='*.yaml' --include='*.yml' --include='*.properties' --include='*.sql' "$pattern" "$target" 2>/dev/null)
  if [ -n "$hits" ]; then
    echo "### $title"
    echo "$hits" | sed 's/^/  /'
    echo
  fi
}

echo "== 古い世代の書き方（付録 boot3-to-4 の grep と同じ） =="
check "javax・Jackson 2・旧テスト注釈・旧Security" 'javax\.(servlet|validation|persistence)|com\.fasterxml\.jackson\.(databind|core)|@MockBean|@SpyBean|WebSecurityConfigurerAdapter|\.and\(\)|antMatchers|authorizeRequests\(|spring-boot-starter-web<'
check "非推奨のステータス名・旧Nullable" 'UNPROCESSABLE_ENTITY|org\.springframework\.lang\.Nullable'

echo "== Spring・Java =="
check "フィールド注入（@Autowired が private フィールドに付いている）" '@Autowired'
check "Bean のフィールドに可変な状態（final でない private フィールド）" '^\s+private\s+([A-Z][A-Za-z<>, ]*|int|long|boolean|double)\s+[a-z][A-Za-z]*(\s*=.*)?;'
check "static な可変コレクション" 'static\s+(final\s+)?(List|Map|Set|ArrayList|HashMap|HashSet)<'
check "例外を握りつぶす・全部捕まえる" 'catch\s*\((Exception|Throwable)\b|printStackTrace'
check "検査例外を投げる @Transactional（rollbackFor がないとコミットされる）" 'throws\s+(IOException|Exception)'

echo "== DB・同時実行 =="
check "数えてから入れる（count / exists）" 'count[A-Za-z]*\(|SELECT\s+count\(|existsBy'
check "FOR UPDATE（ロックの有無）" 'FOR\s+UPDATE'
check "SQL の文字列連結・MyBatis の \${}" '"\s*\+\s*[a-z]+\s*\+\s*"|\$\{'
check "EAGER・全列取得" 'FetchType\.EAGER|SELECT\s+\*'

echo "== ブラウザ境界・認可 =="
check "CSRF 無効・CORS の全許可" 'csrf\(.*disable|csrf\.disable|allowedOrigins\("\*"\)|setAllowedOriginPatterns|allowedOriginPatterns\("\*"\)|setAllowCredentials\(true\)|allowCredentials\(true\)'
check "permitAll（認可を別の場所で本当にしているか）" 'permitAll\(\)'
check "URL の文字列比較で判定" 'getRequestURI\(\)\s*\.(equals|startsWith|contains)|getRequestURI\(\)\)'
check "Cookie の生成（HttpOnly・SameSite の指定なし）" 'new\s+Cookie\('

echo "== SSE =="
check "SseEmitter の生成（onCompletion/onTimeout/onError があるか）" 'new\s+SseEmitter\('
check "SseEmitter のタイムアウト 0 / 無期限" 'SseEmitter\((0L|Long\.MAX_VALUE)\)'

echo "== ログ・応答 =="
check "ログにトークン・Cookie・URL全体・本文" 'log\.(trace|debug|info|warn|error)\(.*([Tt]oken|[Cc]ookie|getRequestURL|getQueryString|password|joinCode|code=)'
check "例外メッセージ・スタックを応答に出す" 'ex\.getMessage\(\)|e\.getMessage\(\)|getStackTrace\(\)'
check "例外をそのままログに渡す（cause のメッセージも出る）" 'log\.(warn|error)\(.*,\s*(e|ex)\)'
check "Entity / 行をそのまま返す Controller（戻り値が Row / Entity）" 'public\s+(List<)?[A-Za-z]*(Row|Entity)>?\s+[a-z]+\('

echo "== 運用 =="
check "Actuator の公開範囲" 'management\.endpoints|exposure\.include|management\.server'
check "秘密らしき値の直書き" '(password|secret|api[-_]?key)\s*[:=]\s*["'\'']?[A-Za-z0-9]{6,}'
