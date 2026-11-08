# 第30章のラボ：IDOR（認可を忘れた API）

第2部のラボ（`../part02-quickvote-inmemory/`）に、`idor` プロファイルのときだけ有効な `NaivePollController`（`/api/v0/polls/{id}`＝認可を忘れた悪い例、`/api/v1/polls/{id}`＝直した例）と、権限表のテスト `PermissionMatrixTest` を足したものです。

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=idor     # ポート 18030
./mvnw test -Dtest=PermissionMatrixTest                    # 11件
```

以下は、元の第2部のラボの README です。

# 第2部のラボ：メモリ上の Map で動く QuickVote

第2部（第10〜16章）で育てるアプリです。DB はまだ使わず、`ConcurrentHashMap` に投票・選択肢・参加者・票を持ちます。
クラス名・DTO 名・エラーコード・メソッド名は、完成形（`code/quickvote/`）と揃えてあります。第3部では Repository の中身（Map → SQL）を入れ替えます。

```bash
./mvnw test                                            # 全テスト（Docker 不要）
./mvnw spring-boot:run                                 # 起動（ポート 18030）
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # 見本の投票つきで起動
```

Windows は `.\mvnw.cmd`。JDK 25 が必要です。

## 使い方（curl）

訪問者の特定は暫定です。リクエストヘッダー `X-Visitor-Id` を訪問者IDとして信用します（誰でも偽装できます。第6部で Cookie ＋ Spring Security に置き換えます）。

```bash
curl -s -X POST localhost:18030/api/polls -H 'X-Visitor-Id: 1' -H 'Content-Type: application/json' \
  -d '{"title":"ランチどこ行く？","closesInMinutes":30,"allowMemberOptions":true,"options":["カレー","そば"]}'
curl -s -X POST localhost:18030/api/join -H 'X-Visitor-Id: 2' -H 'Content-Type: application/json' -d '{"code":"<参加コード>"}'
curl -s -X PUT  localhost:18030/api/polls/<pollId>/my-vote -H 'X-Visitor-Id: 2' -H 'Content-Type: application/json' -d '{"optionId":1}'
curl -s localhost:18030/api/polls/<pollId> -H 'X-Visitor-Id: 1'
```

## 完成形との差（この時点の保証）

| 項目 | 第2部のラボ | いつ直るか |
| --- | --- | --- |
| 保存先 | メモリ。再起動で全部消える | 第3部 |
| 人数・選択肢の上限 | 「数える → 足す」の間は守られていない | 第4部 |
| 選択肢追加の再送 | `Idempotency-Key` なし。再送で二重登録 | 第5部 |
| 訪問者の特定 | `X-Visitor-Id` ヘッダー（偽装できる） | 第6部 |
| 締切の手動変更・削除・招待・SSE | なし | 第5〜7部 |
| ログ | 既定の書式（完成形は構造化ログ） | 第43章 |

## 章との対応

| テスト・コード | 章 |
| --- | --- |
| `di/DiFailureTest`, `di/SingletonStateAccidentTest` | 第11章（DIの失敗、Beanの状態の事故） |
| `journey/*`（`journey` プロファイル）、`JourneyLogTest` | 第12章（Filter 自作、通過ログ） |
| `json/JsonContractTest` | 第13章（JSONの境界） |
| `PollControllerValidationTest`, `common/BoundedTextValidatorTest` | 第14章（入力検証、エラー） |
| `config/*`, `QuickVoteProperties`, `application*.yaml` | 第15章（設定・プロファイル） |
| `PollControllerValidationTest`（`@WebMvcTest`）、`QuickVoteFlowTest`（`@SpringBootTest`）、`poll/PollServiceTest` | 第16章（テスト） |

`journey` プロファイルを有効にする（`--spring.profiles.active=journey`）と、リクエストが通る地点ごとに `[journey]` ログが出ます。
