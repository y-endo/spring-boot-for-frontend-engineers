# 第13章 API契約：DTOとJSON

<div class="goal-box" markdown>

この章でできるようになること

- QuickVote の API を「リクエストの形・レスポンスの形・ステータス」の契約として読み書きできる
- `record` の DTO を設計し、内部のデータ（Entity）をそのまま返してはいけない理由を、実例で説明できる
- Jackson 3（`tools.jackson`）が、未知のフィールド・型違い・欠落・`null`・日時をどう扱うかを、実験で確かめた事実として言える
- 「時刻はサーバーが決める」という設計（`closesInMinutes`）の理由を説明できる
- JSON の境界でデータが守られないことを前提に、AI が書いた DTO をレビューできる

</div>

## 1. 問いかけ：TypeScript の型も、Java の型も、JSON の上では何も守ってくれない

フロントエンドでは、API の応答に型を付けます。

```ts
interface CreatePollRequest {
  title: string
  closesInMinutes: number
  allowMemberOptions?: boolean
}
```

サーバーの Java 側も、同じ形の `record` があります。型が両側にあるので、安心できそうです。

では、次のリクエストを送ると、サーバーはどうするでしょうか。

```json
{ "title": 123, "closesInMinutes": "30", "allowMemberOptions": 1, "extra": "hello" }
```

`title` は数値、`closesInMinutes` は文字列、`allowMemberOptions` は真偽値ではなく `1`、知らない項目 `extra` まである。TypeScript の型からは、全部エラーです。

答えを先に言うと、**このリクエストは成功します**（201）。この章では、**型が両側にあるのに、JSON の境界では守られない**ことを、実験で確かめます。そして、守るために何をするかを決めます。

## 2. API契約：エンドポイントと「形」の約束

**API契約**とは、クライアントとサーバーの間の約束です。QuickVote の第2部時点の契約を、表にします。

| メソッド・パス | リクエストの形 | 成功時 | レスポンスの形 |
| --- | --- | --- | --- |
| `POST /api/polls` | `CreatePollRequest` | **201** ＋ `Location` | `PollDetail` |
| `POST /api/join` | `JoinRequest` | 200 | `JoinResponse` |
| `GET /api/polls/{pollId}` | なし | 200 | `PollDetail` |
| `POST /api/polls/{pollId}/options` | `AddOptionRequest` | **201** | `OptionResponse` |
| `PUT /api/polls/{pollId}/my-vote` | `VoteRequest` | 200 | `VoteResponse` |

エラーは、すべて ProblemDetail（`application/problem+json`、第14章）です。

!!! note "用語：DTO（Data Transfer Object）"
    HTTP でやり取りする**データの入れ物**です。リクエストを受け取る `CreatePollRequest`、応答を返す `PollDetail` のように、**API の境界専用の型**を作ります。業務ロジックは持たず、`record`（第4章）がぴったりです。

TypeScript の `interface` と、Java の `record` を並べます。

=== "TypeScript"

    ```ts
    interface VoteRequest { optionId: number }
    interface VoteResponse { optionId: number }
    ```

=== "Java（QuickVote）"

    ```java title="src/main/java/com/example/quickvote/vote/VoteRequest.java"
    public record VoteRequest(@NotNull Long optionId) {
    }
    ```

    ```java title="src/main/java/com/example/quickvote/vote/VoteResponse.java"
    public record VoteResponse(long optionId) {
    }
    ```

違いは2つです。(1) TypeScript の `interface` は**コンパイル時に消える**ので、実行時の JSON は何も検査されません。Java の `record` は**実行時にも存在する型**で、Jackson が `record` のコンストラクタを呼んで作ります。(2) Java では、`@NotNull` のような**検証の注釈**を付けられます（第14章）。

### リクエストとレスポンスで、DTO を分ける

QuickVote には、リクエスト用（`...Request`）とレスポンス用（`...Response`、`PollDetail`）の DTO があり、**分けてあります**。理由は、(1) 入ってくる項目と出ていく項目が違うこと（`id` や `role` は、サーバーが決める値で、クライアントは送れない）(2) 入力には検証の注釈を、出力には付けないこと、です。

## 3. 内部のデータをそのまま返さない

投票には、内部のデータ表現があります。`Poll` という `record` で、DB の1行（いまはメモリ上の1件）に当たります。これを、そのまま返したらどうなるでしょうか。

```java title="src/main/java/com/example/quickvote/poll/Poll.java（内部の表現）"
public record Poll(
        long id,                // 内部の連番ID
        UUID publicId,          // 外に見せるID
        String title,
        PollStatus status,
        String joinCode,        // 参加コード（主催者にだけ見せる）
        boolean allowMemberOptions,
        Instant closesAt,
        Instant deleteAfter,    // 物理削除の時刻（内部事情）
        Instant createdAt,
        long lastEventSeq) { ... }
```

`JsonContractTest` で、これをそのまま JSON にしてみます。

```java title="src/test/java/com/example/quickvote/json/JsonContractTest.java（抜粋）"
String json = boot.writeValueAsString(row);
assertThat(json).contains("\"id\":1", "\"joinCode\":\"ABCDEFGH\"", "\"deleteAfter\"");
```

テストは通ります。つまり、**内部の連番ID、参加コード、物理削除の時刻まで、参加者全員に見えてしまう**ということです。参加コードは「主催者にだけ見せる」仕様なので、これは**仕様違反**（情報の漏洩）です。

API として返す形は、`PollDetail` として**別に設計**します。

```java title="src/main/java/com/example/quickvote/poll/PollDetail.java（抜粋）"
public record PollDetail(
        UUID id,                     // 内部の連番IDではなく、公開用のID
        String title,
        String status,               // OPEN / CLOSED（締切時刻を過ぎていれば CLOSED）
        Instant closesAt,
        boolean allowMemberOptions,
        String role,                 // 呼び出した人の役割
        int participantCount,
        String joinCode,             // 主催者にだけ値を入れ、参加者には null
        long lastEventSeq,
        int totalVotes,
        Long myVoteOptionId,
        List<OptionView> options) {
```

| | 内部の `Poll` | 公開する `PollDetail` |
| --- | --- | --- |
| ID | 連番 `long`（推測しやすい） | `UUID`（推測しにくい） |
| 状態 | 保存された `status` | **計算した結果**（今の時刻で締切後なら `CLOSED`） |
| 参加コード | 常に持つ | **HOST にだけ**値を入れる |
| 物理削除の時刻 | 持つ | **出さない** |
| 得票・得票率・参加人数 | 持たない（別の表から集計） | **まとめて返す** |

!!! danger "Entityをそのまま返すと起きること"
    AI は「`return pollRepository.findById(id)`」のように、**内部の型を `@RestController` から直接返す**コードを、よく出します。短くて動くからです。しかし (1) 見せたくない項目が漏れる (2) 内部の変更（列の追加・名前の変更）が、そのまま API の変更になり、クライアントが壊れる (3) 必要でない項目まで返し、通信量が増える、の問題があります。第8部の JPA の Entity では、さらに遅延ロードの問題も加わります。**レビューでは、Controller の戻り値の型が DTO かどうかを、最初に見てください。**

「`@JsonIgnore` で隠せばよい」という案もありますが、**項目が増えるたびに隠し忘れる**危険があります。出す項目を**明示的に選ぶ**（DTO を作る）ほうが、安全側に倒れます。

## 4. Jackson 3：JSON と Java を行き来する部品

JSON と Java のオブジェクトの変換は、**Jackson** が行います（第12章の「Jackson 読む」「Jackson 書く」）。Spring Boot 4 は **Jackson 3** を使います。

!!! warning "バージョン注意：Jackson 2 の記事は、パッケージ名が違う"
    古い記事・AI の出力では、`ObjectMapper` と `com.fasterxml.jackson.databind.*` が出てきます。Jackson 3 では次のように変わりました。

    | | Jackson 2（古い記事） | Jackson 3（Boot 4、本教材） |
    | --- | --- | --- |
    | 変換の本体のパッケージ | `com.fasterxml.jackson.databind.*` | **`tools.jackson.databind.*`** |
    | 使う型 | `ObjectMapper` | **`tools.jackson.databind.json.JsonMapper`** |
    | アノテーション（`@JsonProperty` など） | `com.fasterxml.jackson.annotation.*` | **同じ**（`com.fasterxml.jackson.annotation.*` のまま） |
    | 例外 | `IOException` の仲間（検査例外。`throws` や try/catch が必要） | **`tools.jackson.core.JacksonException`（非検査例外）**。書かなくてよい |
    | `java.time` の対応 | 別モジュール（`jackson-datatype-jsr310`）を足す | 標準で対応（実験で確認済み） |
    | 設定の変更 | 作ったあとに `mapper.configure(...)` | `JsonMapper.builder()...build()` で作る（作った後は変えない） |
    Boot が自動構成した `JsonMapper` は、そのままコンストラクタで受け取れます（`JsonContractTest` も、フィールドで受け取っています）。`import com.fasterxml.jackson.databind.ObjectMapper` が出てきたら、Jackson 2 の書き方です。

## 5. 実験：JSON の境界で何が起きるか

`JsonContractTest` は、Boot が構成した `JsonMapper`（`boot`）と、素の `JsonMapper`（`bare`）で、挙動を確かめるテストです。結果を、**予測してから**見ます。

!!! question "予測カード"
    `CreatePollRequest`（`title` は `String`、`closesInMinutes` は `Integer`、`allowMemberOptions` は `Boolean`）に、次のJSONを読ませます。それぞれ、成功か、例外か。
    1. `{"title":"t","closesInMinutes":30,"extra":1}`（知らない項目 `extra`）
    2. `{"title":123,"closesInMinutes":30}`（`title` が数値）
    3. `{"title":"t","closesInMinutes":"30"}`（`closesInMinutes` が文字列）
    4. `{"title":"t","closesInMinutes":30.7}`（小数）
    5. `{"title":"t","closesInMinutes":"abc"}`（数字でない文字列）
    6. `{"title":"a","title":"b","closesInMinutes":30}`（同じキーが2回）

| # | 結果（実際に確かめた） |
| --- | --- |
| 1 | **成功。`extra` は黙って無視される**（素の `JsonMapper` でも同じ） |
| 2 | **成功。`title` は `"123"`（文字列）になる** |
| 3 | **成功。`closesInMinutes` は `30` になる** |
| 4 | **成功。`30` に切り捨てられる**（`30.7` の小数部が黙って消える） |
| 5 | 例外（`InvalidFormatException`）。HTTP では **400** |
| 6 | **成功。後のものが勝つ**（`title` は `"b"`） |

読み替えの「ゆるさ」に、驚いたかもしれません。**Jackson は、JSON の値を目的の型に変換できるなら、できるだけ読み替えます**。同じく `allowMemberOptions` に `1` や `"true"` を渡すと、どちらも `true` になります。

HTTP で送った結果も、同じです（実際のレスポンス）。

| 送ったもの | ステータス | 応答の様子 |
| --- | --- | --- |
| 知らない項目つき | 201 | 作成される |
| `"title": null` | 400 | `VALIDATION_FAILED`、`errors[0].field = "title"` |
| `"closesInMinutes": "abc"` | 400 | `BAD_REQUEST`、`detail: "Failed to read request"` |
| 本文が配列 `[1]` | 400 | `BAD_REQUEST` |
| 本文が空 | 400 | `BAD_REQUEST` |
| `Content-Type: text/plain` | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| `"options": [null]` | 400 | `VALIDATION_FAILED`、`errors[0].field = "options[0]"` |

!!! danger "本番で事故になるパターン：型の読み替えを信用する"
    `"closesInMinutes": 30.7` が `30` になる、`"allowMemberOptions": 1` が `true` になる、というのは、**バグではなく既定の仕様**です。クライアントのバグ（文字列と数値の取り違え）が、サーバーでは**エラーにならず通ってしまう**ので、気づけません。数値が金額や数量なら、桁や小数を黙って失うことになります。
    守る方法は、(1) 値の**範囲・形式を Bean Validation で検査する**（第14章。`closesInMinutes` は 5〜10080）(2) 読み替えを許さない設定を、調べて入れる、の2つです。Jackson には読み替えを厳しくする設定がありますが、**既定の挙動を知らないまま使わない**ことが先です。この教材では、(1) を基本とします。

### `null` と欠落：プリミティブは受け取れない

`allowMemberOptions` は、`boolean` ではなく `Boolean`（ラッパー型）で受けています。

```java title="src/main/java/com/example/quickvote/poll/CreatePollRequest.java（抜粋）"
public record CreatePollRequest(
        @NotBlank @BoundedText(maxChars = PollRules.MAX_TEXT_CHARS) String title,
        @NotNull @Min(PollRules.MIN_CLOSES_IN_MINUTES) @Max(PollRules.MAX_CLOSES_IN_MINUTES) Integer closesInMinutes,
        Boolean allowMemberOptions,
        @Size(max = PollRules.MAX_OPTIONS) List<@NotBlank @BoundedText(...) String> options) {

    public CreatePollRequest {                                  // (1)!
        title = title == null ? null : title.strip();
        allowMemberOptions = allowMemberOptions != null && allowMemberOptions;   // (2)!
        options = options == null ? List.of() : options.stream().map(s -> s == null ? null : s.strip()).toList();
    }
}
```

1. `record` の**コンパクトコンストラクタ**（第4章）。Jackson が `record` を作るたびに通るので、整形をここに集められる
2. 「省略 = `false`」と、**サーバーが決める**

`boolean`（プリミティブ）にするとどうなるでしょうか。

```text
tools.jackson.databind.exc.MismatchedInputException: Cannot map `null` into type `boolean`
(set `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` to 'false' to allow)
```

!!! warning "バージョン注意：Jackson 3 は、プリミティブの欠落・`null` を許さない"
    JSON に項目がない（欠落）、または `null` のとき、プリミティブ型（`boolean`、`int`）の項目は**エラー**になります（`FAIL_ON_NULL_FOR_PRIMITIVES` が既定で有効。欠落も `null` として扱われます）。Jackson 2 の時代は黙って `false` / `0` になった場面です。古い記事の `record` が、そのまま動かないことがあります。
    QuickVote は `Boolean` で受けて、**欠落と `null` のどちらも「省略」と見なし、コンパクトコンストラクタで既定値を決める**方針にしています。実験でも、`{"title":"t","closesInMinutes":30}` と `{... "allowMemberOptions":null, "options":null}` は、**同じ `CreatePollRequest`** になります（欠落と `null` は区別できない）。

欠落と `null` を区別したい場面（`PATCH` で「この項目は変えない」と「空にする」を分ける）では、別の工夫が要ります。QuickVote には該当する API がないので、ここでは扱いません。

## 6. 時刻のJSON表現と「時刻はサーバーが決める」

QuickVote の締切は、クライアントが「絶対時刻」を送る設計ではなく、**「今から何分後か」（`closesInMinutes`）を送り、締切の時刻はサーバーが計算する**設計です。

```java title="src/main/java/com/example/quickvote/poll/PollService.java（抜粋）"
Instant now = clock.instant();
Instant closesAt = now.plus(request.closesInMinutes(), ChronoUnit.MINUTES); // 締切の時刻はサーバーが決める
```

!!! example "設計判断：なぜ絶対時刻ではないのか"
    クライアントが `closesAt: "2026-11-08T12:00:00Z"` を送る設計には、問題がいくつかあります。
    1. **通信の遅れ**：送ってから届くまでの数秒で、「5分後以降」という条件が、境界で食い違う
    2. **端末の時計**：スマホの時計が数分ずれていると、締切がずれる。他人の端末の時計は信用できない
    3. **タイムゾーンの解釈**：`2026-11-08T21:00:00`（オフセットなし）のように、地域の時刻が混ざる
    4. **テスト**：「今」が実時間に依存し、テストで再現しにくい（`Clock` を差し替えれば、サーバーの時刻は固定できる）

    「何分後」なら、これらが減ります。**時刻の基準を、サーバーの `Clock` 1つに集められる**のが利点です。
    代わりに、**同じリクエストを再送すると、締切が少しずれる**欠点があります。再送に強くする方法は、第27章（冪等性）で扱います。

応答に出る時刻は、**UTC の ISO 8601 文字列**です。

```text
"closesAt": "2026-11-07T17:29:57.833537Z"
```

`JsonContractTest` で確かめた事実を、表にします。

| 操作 | 結果 |
| --- | --- |
| `Instant` を JSON にする | `"2026-11-08T01:02:03.123456789Z"`（UTC、**ナノ秒まで**出ることがある） |
| `"2030-01-01T09:00:00+09:00"` を `Instant` で読む | `2030-01-01T00:00:00Z` に**UTC へ換算されて**読める |
| 数値 `1893456000` を `Instant` で読む | **エポック秒として読めてしまう**（`2030-01-01T00:00:00Z`）。意図しない入力が通る |
| `"2030-01-01"`（日付だけ）を読む | 例外（`InvalidFormatException`）。400 |

!!! tip "フロントエンドで例えると"
    JavaScript の `Date` は、ミリ秒（3桁）までしか持てません。サーバーの `Instant` はナノ秒を持てるので、**往復すると値が変わる**可能性があります。比較や並び替えのキーに、文字列の時刻をそのまま使わないでください。
    サーバー側では、DB の `timestamptz`（マイクロ秒）に合わせて、現在時刻を丸めます（完成形の `Db.now`）。第7章の `Instant`/`LocalDateTime` の話の続きです。

**JSON の時刻は、UTC の ISO 8601**。これを守れば、JavaScript の `Date` でも、`Intl.DateTimeFormat` でも、タイムゾーンを気にせず扱えます。サーバー側で `LocalDateTime`（タイムゾーンなし）を JSON に出すことは、しません（第7章の事故の元です）。

## 7. 数値：JSON の `number` は、JavaScript で丸められる

もう1つ、JSON の境界で守られないものがあります。**数値の精度**です。

```java
// JsonContractTest より
assertThat(boot.writeValueAsString(Map.of("id", 9_007_199_254_740_993L)))
        .isEqualTo("{\"id\":9007199254740993}");              // サーバーは正確に書く
assertThat(boot.writeValueAsString(new BigDecimal("33.40"))).isEqualTo("33.40");
assertThat(boot.writeValueAsString(0.1 + 0.2)).isEqualTo("0.30000000000000004");
```

サーバーは `9007199254740993` を正確に書きます。しかし JavaScript の `number` は倍精度浮動小数点で、**2の53乗 − 1（`Number.MAX_SAFE_INTEGER`）より大きい整数は、正確に表せません**。ブラウザの `JSON.parse` は、これを近い値（`9007199254740992`）に丸めます。サーバーのテストはどれだけ通っても、ブラウザで値が変わるのです。

QuickVote の対策は、**外に出す ID を `UUID`（文字列）にする**ことです。選択肢の `id`（`long`）は、小さい連番なので問題になりません。「外に出す ID は、`long` の連番が巨大になりうるなら文字列で出す」を、設計の基準にします。

| 型 | JSON での注意 |
| --- | --- |
| `long`（大きい ID） | JavaScript で 2^53 を超えると丸められる。文字列（UUID など）で出す |
| `BigDecimal`（得票率 `33.4`） | 小数1桁に整えて出す（第7章）。JavaScript 側は `number` で受けるので、さらに計算するなら注意 |
| `double` | `0.1 + 0.2` が `0.30000000000000004`。お金や率には使わない |
| `Instant` | UTC の ISO 8601 文字列（6 節） |
| `enum` | 名前の文字列（`"HOST"`）。**名前の変更が API の変更になる** |

## 8. HTTPステータスの決め方

契約の一部は、**成功時のステータス**です。`PollController` を読みます。

```java title="src/main/java/com/example/quickvote/poll/PollController.java（抜粋）"
@PostMapping
public ResponseEntity<PollDetail> create(VisitorPrincipal visitor, @Valid @RequestBody CreatePollRequest request) {
    PollDetail detail = pollService.create(visitor.visitorId(), request);
    return ResponseEntity.created(URI.create("/api/polls/" + detail.id())).body(detail);   // (1)!
}

@GetMapping("/{pollId}")
public PollDetail get(VisitorPrincipal visitor, @PathVariable UUID pollId) {              // (2)!
    return pollService.get(visitor.visitorId(), pollId);
}
```

1. `ResponseEntity.created(...)` は **201 Created** と、`Location` ヘッダー（作ったものの URL）を付ける
2. 戻り値の型をそのまま返すと、**200 OK** になる

| ステータス | 使いどころ | QuickVote |
| --- | --- | --- |
| 200 OK | 取得・更新の成功 | `GET` の詳細、`PUT` の投票、`POST /api/join` |
| 201 Created | **新しいものを作った** | 投票の作成、選択肢の追加 |
| 204 No Content | 成功したが返す本文がない | 締切・削除（完成形） |
| 400 | リクエストの形がおかしい | 検証エラー、JSON が壊れている |
| 401 / 403 / 404 | 識別できない／許されない／ない | 第6部で整理 |
| 409 | 今の状態と衝突 | 満員、上限、締切後 |
| 500 | サーバーの想定外 | `INTERNAL_ERROR` |

`POST /api/join` は「参加」を作る操作ですが、**同じ人の再参加は成功として扱う**ので（再送に強くする）、200 にしています。**ステータスは、仕様として決める**ものです。AI に任せると、すべて 200 で返す実装も出てくるので、レビューで確かめます（400/404/409/422 の使い分けは、第14章）。

## 9. 手を動かす：全体を通して呼ぶ

ラボを起動して、契約どおりに動くことを確かめます。

```bash
cd code/labs/part02-quickvote-inmemory
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # 見本の投票つき
```

`dev` プロファイルは、見本の投票を1つ作ります（訪問者1が主催者）。起動ログに ID が出ます。

```text
見本の投票を作りました: id=19b1e477-bf3a-475a-bb33-b52d8e464574（主催者は X-Visitor-Id: 1、参加者は 2〜4）
```

```bash
curl -s localhost:18012/api/polls/<id> -H 'X-Visitor-Id: 1'
```

```json
{"id":"19b1e477-...","title":"ランチどこ行く？","status":"OPEN","closesAt":"2026-11-07T17:29:57.833537Z",
 "allowMemberOptions":true,"role":"HOST","participantCount":4,"joinCode":"BZFPG6R6","lastEventSeq":0,
 "totalVotes":0,"myVoteOptionId":null,"options":[{"id":1,"label":"カレー","votes":0,"percent":0.0},...]}
```

訪問者2（参加者）で同じものを見ると、`"role":"MEMBER"`、`"joinCode":null` になります。**同じ `PollDetail` が、見る人で変わる**。契約として、これを決めてあるのです。

!!! warning "Windows の注意：curl で日本語の JSON を直接渡すと 400 になる"
    Git Bash などのコードページが 932 の環境で、`curl -d '{"title":"ランチ"}'` のように日本語を直接書くと、**UTF-8 でないバイト列が送られて**、サーバーが `400 Failed to read request` を返すことがあります（この教材の環境で再現しました）。次のように、**UTF-8 のファイルを送ります**。
    ```bash
    printf '%s' '{"title":"ランチどこ行く？","closesInMinutes":30}' > body.json
    curl -X POST localhost:18012/api/polls -H 'X-Visitor-Id: 1' \
      -H 'Content-Type: application/json' --data-binary @body.json
    ```
    JSON の文字コードは UTF-8 と決まっています。送る側のツールの文字コード設定を疑ってください。

## 10. 現場で：AIが書いた DTO と Controller のレビュー

| 見るもの | 疑う理由 |
| --- | --- |
| Controller が Entity／内部の型を直接返している | 項目の漏洩、API の固定化（3 節） |
| `Map<String, Object>` で受け取る・返す | 型が守られない。`record` に |
| リクエスト DTO に `boolean` / `int` などのプリミティブ | Jackson 3 で欠落が 400 に。`Boolean` / `Integer` ＋ 既定値の決め方を明示 |
| `@NotNull`、`@Size` などの検証が付いていない | JSON の境界は何も守らない（第14章） |
| `LocalDateTime` / `java.util.Date` を JSON に出す | タイムゾーンが不明。`Instant`（UTC）に |
| 金額・率を `double` / `float` で | 誤差が出る。`BigDecimal` か整数（第7章） |
| 大きな `long` の ID をそのまま出す | JavaScript で丸められる |
| `import com.fasterxml.jackson.databind...` | Jackson 2 の書き方。Boot 4 では `tools.jackson` |
| `@JsonIgnore` で隠す設計 | 隠し忘れる。出す項目を DTO で明示 |
| クライアントが時刻を送り、サーバーがそのまま信用 | 6 節。サーバーが決められないか |
| すべて 200 で返す | ステータスは仕様。クライアントが判定できない |

---

## まとめ：3点セット

!!! abstract "この章の3点セット"
    **① 一言で言うと**
    API 契約は、エンドポイント・リクエストとレスポンスの DTO・ステータスの約束であり、JSON の境界では型が守られない（Jackson は型を読み替え、未知の項目を無視し、数値は JavaScript で丸められる）ので、DTO で外に出す形を明示し、範囲と形式は検証で守り、時刻と ID はサーバーが決める。

    **② 現場でこう聞かれる**
    「`return pollRepository.findById(id)` で、DB の行をそのまま返せば、DTO を作らなくて済みませんか？」
    → 済むが、内部の項目（参加コード、内部ID、削除時刻）が漏れ、内部の変更がそのまま API の変更になる。出す項目は DTO で明示する。

    **③ 自分のサービスならどこに当たるか**
    自分のサービスの API を1つ選び、リクエストとレスポンスの DTO を `record` で書く。内部のデータにあって、**外に出さない項目**を3つ挙げる。

## 演習

??? question "確認問題1：予測と実行"
    `{"title":"t","closesInMinutes":"30"}` を `POST /api/polls` に送ると、何が起きますか。予測してから、`JsonContractTest` の該当テストを読んで確かめてください。

??? success "解答"
    成功します（201）。`"30"`（文字列）は、`Integer` の `30` に読み替えられます。`{"closesInMinutes":"abc"}` のように数字として読めない文字列だけが、400（`BAD_REQUEST`）になります。「型が違うのでエラー」にはならないことが、この章の要点です。

??? question "確認問題2：プリミティブ"
    `AddOptionRequest` を `record AddOptionRequest(boolean required, String label)` と書き換えました。`{"label":"カレー"}` を送ると、何が起きますか。

??? success "解答"
    `required` が欠落しているため、Jackson 3 は `Cannot map null into type boolean` で失敗し、HTTP では 400 になります。Jackson 2 の時代は黙って `false` になりました。`Boolean`（ラッパー）で受けて、コンパクトコンストラクタで既定値を決めるか、そもそも不要な項目なら足さないことです。

??? question "確認問題3：漏洩を見つける"
    次の Controller のメソッドの問題を、2つ以上指摘してください。
    ```java
    @GetMapping("/{pollId}")
    public Poll get(@PathVariable UUID pollId) {
        return pollRepository.findByPublicId(pollId).orElseThrow();
    }
    ```

??? success "解答"
    (1) 戻り値が内部の型 `Poll` で、参加コード（`joinCode`）、内部ID、`deleteAfter` が**誰にでも**見える。(2) 訪問者の特定も、参加者かどうかの確認（認可）もない。投票の ID を知っていれば誰でも読める。存在しない場合も `NoSuchElementException`（500）で、404 の契約を守れない。(3) 得票・得票率・自分の票が含まれない。`PollDetail` を返し、`pollService.get(visitorId, pollId)` を通します。

??? question "応用：契約を1つ足す"
    投票の「タイトルだけを返す軽量な API」`GET /api/polls/{pollId}/summary` を足す設計をしてください。(1) レスポンスの `record` (2) HOST にだけ見せる項目 (3) 返すステータスを、書き出してください（コードは書かなくてよい）。

??? success "解答"
    例：(1) `record PollSummary(UUID id, String title, String status, Instant closesAt, int participantCount)` (2) 参加コード（`joinCode`）は含めない。HOST にだけ見せたいなら、`PollDetail` と同じ扱いで `joinCode` を持たせ、HOST 以外は `null` (3) 成功は 200。参加していない人・存在しない投票は、どちらも 404 `POLL_NOT_FOUND`（存在を漏らさない）。認可は `ParticipantService.require` を通す。

??? question "説明問題：現場でこう聞かれる"
    「サーバーが時刻を決めるのはなぜですか？ クライアントが `closesAt` を送るほうが自由度が高いのでは？」200字程度で答えてください。

??? success "解答"
    例：クライアントの時計は端末ごとにずれていて、信用できないからです。通信の遅れで、送ってから届くまでに条件（5分後以降など）が崩れることもあります。「何分後」だけを受け取り、サーバーが `Clock` から締切を計算すれば、基準の時計が1つになり、テストでも時間を固定できます。その代わり、再送で締切がずれるので、冪等性の設計で補います。
