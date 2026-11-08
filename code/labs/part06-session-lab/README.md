# 第6部のラボ：匿名セッションと Spring Security

第2部のメモリ版 QuickVote（`../part02-quickvote-inmemory/`）から、暫定の `X-Visitor-Id`（`StandIn*`）を外し、
**本物の匿名セッション**（Cookie の訪問者トークン ＋ Spring Security 7）を足したものです。第32〜35章で使います。
visitors 表だけメモリ上の `Map`（完成形は PostgreSQL）で、フィルタ・設定・回数制限のコードは完成形（`code/quickvote/`）と同じです。

```bash
./mvnw test                                 # 7件（Docker 不要）
./mvnw -q -DskipTests package
java -jar target/quickvote-0.0.1-SNAPSHOT.jar          # ポート 18032。Windows は .\mvnw.cmd
```

## 「壊してみる」スイッチ（`lab.*`）

既定値はすべて正しい動作（完成形と同じ）です。本番のコードにこういうスイッチは置きません。

| スイッチ | 既定 | 実験 |
| --- | --- | --- |
| `--lab.save-context=false` | `true` | 認証フィルタが `SecurityContext` を保存しない（第32章） |
| `--lab.session-management=false` | `true` | `SessionManagementFilter` を外す。上と組み合わせると SSE の終了で `AuthorizationDeniedException`（第32章） |
| `--lab.rate-limit=raw-uri\|none` | `path-pattern` | URL の判定を文字列比較に戻す／回数制限なし（第33章） |
| `--lab.join-code-length=3` | `8` | 参加コードを短くして総当たりで破る（第33章） |
| `--lab.csrf=false` | `true` | CSRF 対策なしで、別オリジンからの攻撃を再現（第34章） |
| `--lab.cors-allow-origin=http://localhost:18134` | なし | CORS を許可する設定ミス（第34章） |
| `--lab.bucket-on-ip-reject=true` | `false` | IP で拒否した後も訪問者単位のバケツを作る元のバグ（第35章） |

## スクリプト

```bash
java scripts/BruteForce.java http://localhost:18033            # 参加コードの総当たり（rotate を付けると Cookie を捨てる）
java scripts/TimingDemo.java                                   # 比較の速さ：Arrays.equals と MessageDigest.isEqual
```

## 補助

- `GET /lab/stats`：訪問者の数・バケットの数（第35章。本番に置いてはいけない）
- `GET /victim.html`：被害者のページ（第34章。`../ch34-evil/` の攻撃ページと組み合わせる）
- `GET /api/polls/{id}/events`：最小の SSE（接続時に認可 → 1件送る → 0.3秒後に閉じる）。`-Dlab.sse-close-after-millis=4000` で閉じるまでの時間を変えられる
- `POST /api/polls/{id}/close`：締切（HOST のみ。本文なしの POST。第34章）

## テスト

| テスト | 証明していること |
| --- | --- |
| `SessionHttpTest` | 入口の POST だけが訪問者 Cookie を発行し、属性が付く。SSE が完了しても ERROR が出ない（`-Dlab.save-context=false -Dlab.session-management=false` を付けると赤） |
| `EntryMatchersTest` | `/api/%6aoin` を、文字列比較は見逃し、`PathPatternRequestMatcher` は見逃さない |
| `BucketRaceTest` | 取得と更新を分けた元の実装は制限がリセットされ、`compute` の中で行う完成形はされない |
| `RateLimiterBucketCountTest` | 偽 Cookie 10,000 個で、元のバグは 10,000 個・完成形は 5 個のバケツ |
