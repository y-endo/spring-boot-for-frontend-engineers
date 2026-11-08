# 第33章 招待・参加コード・総当たり対策

<div class="goal-box" markdown>

この章でできるようになること

- 参加コードの探索空間を計算し、「長さだけでは足りない」ことを数字で説明できる
- 総当たりのスクリプトを実際に動かし、試行回数制限で止めて確かめられる
- 一度限りの招待を、**条件付き UPDATE の更新件数**で実装でき、同時実行でも1人しか通らないことを確かめられる
- 招待を GET で消費してはいけない理由と、締切後に未消費のまま拒否する設計を説明できる
- URL エンコードで回数制限をすり抜けられた事故の原因と、直し方を説明できる

</div>

## 1. いきなり事故：参加コードが、10秒で破られる

QuickVote の参加コードは8文字です。ここでは、**わざと3文字に縮めた**ラボで、総当たりのスクリプトを動かします。

```bash
cd code/labs/part06-session-lab
./mvnw -q -DskipTests package
java -jar target/quickvote-0.0.1-SNAPSHOT.jar --server.port=18033 \
     --lab.join-code-length=3 --lab.rate-limit=none          # 3文字・回数制限なし
```

別の端末で、攻撃のスクリプト（`scripts/BruteForce.java`）を動かします。被害者が投票を作り、攻撃者は参加コードを**知らないまま**、全部の組み合わせを `POST /api/join` に送ります。

```bash
java scripts/BruteForce.java http://localhost:18033
```

!!! question "予測カード"
    3文字の参加コード（使える文字は31種）は、全部で31の3乗＝29,791通りです。1回の試行は数ミリ秒です。何秒で当たるでしょうか。

    - A: 1分以上　　B: 10秒前後　　C: 1秒未満

```text
被害者の参加コードは 3 文字（答えは伏せて総当たりを始めます）
当たり: CAY  試行 1944 回 / 全 29791 通り  9124 ms
答え合わせ: 一致
```

答えは **B** です。この回は1,944回目（全体の6.5%）で当たりましたが、運が悪くても全部で約2分です。攻撃者は、**ログインも何もなしで**、投票の参加者になりました。参加者になれば、選択肢と得票が見え、投票もできます。

## 2. 探索空間を計算する

実際の QuickVote は8文字です。計算します。

| 条件 | 通り数 | 毎秒 1,000 回で全部試すと |
| --- | --- | --- |
| 3文字 | 31³ = 29,791 | 約30秒 |
| 5文字 | 31⁵ = 28,629,151 | 約8時間 |
| **8文字（QuickVote）** | **31⁸ = 852,891,037,441** | **約27年** |

文字種が31なのは、見間違えやすい `0 O 1 I L` を除いた英大文字と数字だからです（人間が口頭で伝えたり、入力したりするため）。**平均で当たるのは半分の地点**なので、毎秒1,000回なら約13.5年、毎秒10万回（分散した攻撃）なら約49日です。

ここで、もう一段考えます。**攻撃者は特定の投票を狙うとは限りません**。「どれでもいいから、どれかの投票に入れればよい」なら、同時に開いている投票の数で割れます。

| 同時に有効な投票 | 毎秒 1,000 回で、どれか1つに当たるまで（平均） |
| --- | --- |
| 1 | 約13.5年 |
| 1,000 | 約5日 |
| 10,000 | 約12時間 |

**コードの長さだけでは足りません。** 人間が打てる短さにする以上、**試行回数を止める仕組み**が必須です。これが、次の節の主役です。

## 3. 試行回数制限で止める

回数制限を有効にして（既定）、同じスクリプトを動かします。

```bash
java -jar target/quickvote-0.0.1-SNAPSHOT.jar --server.port=18033 --lab.join-code-length=3
java scripts/BruteForce.java http://localhost:18033
```

```text
429 で止められた: 試行 12 回目  Retry-After=6 秒  208 ms
```

12回目で止まりました。13回目以降も、`Retry-After`（待つべき秒数）だけ待たないと通りません。仕組みは、**訪問者ごとのトークンバケット**（容量10、6秒に1個補充）です。最初の1回は Cookie がまだないので数えず、2〜11回目で10個を使い切り、12回目が拒否されます。仕組みの詳細は次の章（第35章）に回します。

攻撃者は、すぐに気づきます。「Cookie を捨てて、毎回新しい訪問者になれば、訪問者ごとの制限は効かない」。そこで、`rotate` モードです。

```bash
java scripts/BruteForce.java http://localhost:18033 rotate      # 試すたびに Cookie を捨てる
```

```text
429 で止められた: 試行 91 回目  Retry-After=1 秒  1767 ms
```

**91回目で止まりました。** もう1つの軸、**接続元IPごとの制限**（容量100、毎分60回補充）が効いたからです。訪問者単位とIP単位の**2軸**で数える理由が、ここで分かります。

| 攻撃者のやり方 | 止める軸 |
| --- | --- |
| 同じ Cookie で試し続ける | 訪問者単位（容量10・毎分10） |
| Cookie を捨てて試し続ける | IP単位（容量100・毎分60） |
| 複数のIPから試す | どちらも突破できる。**探索空間（8文字）と有効期間（最長7日）で守る** |

!!! warning "完璧な防御はない"
    回数制限は、**突破に必要な時間を、現実的でない長さに引き延ばす**ものです。1訪問者あたり毎分10回なら、8文字を全部試すのに平均約8万年かかります。IPを数万個持つ攻撃者には、探索空間の広さ（8文字）が最後の防御です。**どれか1つではなく、重ねて**守ります。

数え方の方針も、完成形のテストで固定されています。

- **成功も失敗も「試行」として数える**。失敗だけ数えると、「当たりを引くまでは数えない」ことになる
- **制限中は、正しいコードでも通らない**（`RateLimitIT`）。通すと、「通ったら当たり」という手がかりになる
- 形式が違うコード（8文字でない、使えない文字）は DB を引かずに 404 にし、**応答は存在しないコードと同じ**（`JOIN_CODE_NOT_FOUND`）

## 4. 事故：URL をエンコードすると、回数制限をすり抜けた

完成形の回数制限は、レビューで見つかった穴（指摘1）を修正した結果です。元の実装は、こうでした。

```java
// 元の実装（悪い例）：リクエストの URI を文字列で比べて、対象かどうかを判定していた
boolean isAttempt = "POST".equals(request.getMethod()) && "/api/join".equals(request.getRequestURI());
```

一見、正しく見えます。ラボで、この元の実装を再現できます（`--lab.rate-limit=raw-uri`）。通常の URL と、**`j` を `%6a` にパーセントエンコードした URL** で、14回ずつ送ります。

```bash
for p in /api/join /api/%6aoin; do
  for i in $(seq 1 14); do
    curl -s -o /dev/null -w '%{http_code} ' -c k.txt -b k.txt -X POST "localhost:18033$p" \
         -H "X-XSRF-TOKEN: ..." -H 'Content-Type: application/json' -d '{"code":"AAA"}'
  done; echo
done
```

```text
/api/join     404 404 404 404 404 404 404 404 404 404 404 429 429 429
/api/%6aoin   404 404 404 404 404 404 404 404 404 404 404 404 404 404     ← 止まらない
```

**同じ API なのに、エンコードしただけで回数制限をすり抜けました。** 原因は、2つの「見ているもの」のずれです。

| | `getRequestURI()` | Spring MVC |
| --- | --- | --- |
| `/api/%6aoin` を | **生の文字列のまま**（`/api/%6aoin`）返す | 各セグメントを**デコードして** `/api/join` として処理する |

フィルタは「`/api/join` ではない」と判断して素通りさせ、そのあとの MVC は「`/api/join` だ」と判断して処理します。**判定する側と処理する側で、同じ URL を違う文字列として見ていました。** 同じ型の事故は、「大文字小文字」「末尾スラッシュ」「`..`」など、あらゆる場所で起きます。

直し方は、**フレームワークが解釈するのと同じ方法で判定する**ことです。Spring Security の `PathPatternRequestMatcher` は、MVC と同じ `PathPattern` で、デコード後のパスを、コンテキストパスを除いて判定します。

```java title="src/main/java/com/example/quickvote/security/EntryMatchers.java（抜粋）"
private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

/** 参加コードで参加する。 */
static final RequestMatcher JOIN = PATHS.matcher(HttpMethod.POST, "/api/join");
/** 招待を受ける。 */
static final RequestMatcher ACCEPT_INVITE = PATHS.matcher(HttpMethod.POST, "/api/invites/{token}/accept");

static boolean isAttempt(HttpServletRequest request) {
    return JOIN.matches(request) || ACCEPT_INVITE.matches(request);
}
```

```java title="ラボの EntryMatchersTest"
MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/%6aoin");   // %6a = j
assertThat(request.getRequestURI()).isEqualTo("/api/%6aoin");                          // 生の文字列
assertThat("/api/join".equals(request.getRequestURI())).isFalse();                     // 元の実装は見逃す
assertThat(EntryMatchers.JOIN.matches(request)).isTrue();                              // 直した実装は見逃さない
```

完成形で、実際に確かめます（13回送る）。

```text
/api/%6aoin ×13 → 404 404 404 404 404 404 404 404 404 404 404 429 429
通常の /api/join も 429（同じバケットを共有している）
```

エンコードを変えても、回数は増やせません。なお `%2e`（`.`）や `%2f`（`/`）のような**特殊な文字のエンコード**は、そもそも Spring Security の `StrictHttpFirewall` が拒否します（400 になることを確かめました）。通る `%6a` のような**普通の文字のエンコード**が、この事故の入口でした。

!!! tip "プロのレビューで実際に見つかった穴"
    この教材のリファレンス実装には、シニアのレビューで18件の指摘が入りました。この章に関わるのは次の穴です。AIが書いたコードをレビューするときの観点にしてください。

    | 指摘 | 穴 | 観点 |
    | --- | --- | --- |
    | 1 | URL を文字列比較で判定 → エンコードですり抜け | 「判定に使っている文字列は、処理側が見ているものと同じか」 |
    | 3 | 不正なリクエストでも訪問者の行が増える | 「拒否される前に、資源（行・メモリ）を作っていないか」（第35章） |
    | 17 | 締切後の招待の扱いが不明 | 「仕様の隙間（締切後・期限切れ・満員）を決めているか」 |

## 5. 一度限りの招待

主催者は、共同主催者を招待できます。リンクは**一度だけ**使えて、24時間で失効します。

| 項目 | 設計 |
| --- | --- |
| トークン | 32バイト乱数（第32章と同じ。`Tokens.newToken()`）。DBにはハッシュだけ |
| 有効期間 | 24時間（`expires_at`） |
| 一度限り | 受諾で `used_at` を埋める。**原子的に** |
| 受けた人 | HOST になる（すでに MEMBER なら昇格） |
| 失敗の応答 | 無効・期限切れ・使用済み・存在しない、すべて同じ 404 `INVITE_INVALID`（理由を区別しない） |

### 原子的に消費する：更新件数で判定する

素朴な実装は、「未使用かを SELECT で確かめてから、UPDATE で使用済みにする」です。第4部の「残り1枠に2人」と**同じ事故**になります。2人が同時に SELECT を通り抜けるからです。psql の2つのセッションで再現します（`pg_sleep` で、2人が同時に通る状況を作ります）。

=== "悪い書き方：SELECT してから UPDATE"

    ```sql
    BEGIN;
    SELECT 'used_at is ' || coalesce(used_at::text,'NULL') FROM invites WHERE id=2;   -- 確認する
    SELECT pg_sleep(1);                                                                -- 間に、もう1人が割り込む
    UPDATE invites SET used_at = now() WHERE id=2;                                     -- 使用済みにする
    COMMIT;
    ```

    ```text
    [A] used_at is NULL      [A] UPDATE 1
    [B] used_at is NULL      [B] UPDATE 1        ← 2人とも「未使用だった」ので成功してしまう
    ```

=== "完成形：条件付き UPDATE"

    ```sql
    BEGIN;
    UPDATE invites SET used_at = now() WHERE id=2 AND used_at IS NULL;   -- 確認と更新を1文で
    SELECT pg_sleep(1);
    COMMIT;
    ```

    ```text
    [A] UPDATE 1
    [B] UPDATE 0        ← 後続は、先行のコミットを待ってから条件を再評価し、0件になる
    ```

完成形は、**確認と更新を1つの UPDATE 文**にして、**更新件数**で成否を判定します。

```java title="src/main/java/com/example/quickvote/invite/InviteRepository.java（抜粋）"
public int consume(String tokenHash, long visitorId, Instant now) {
    return jdbc.sql("""
            UPDATE invites SET used_at = :now, used_by = :visitorId
            WHERE token_hash = :tokenHash AND used_at IS NULL AND expires_at > :now
            """)
            // ... param ...
            .update();        // 更新件数。0 なら「使用済み・期限切れ・存在しない」のどれか
}
```

PostgreSQL は、1つの UPDATE 文の中で「条件の確認」と「書き込み」を、行ロックの下で行います。後続は、先行が終わるまで待ち、**更新後の値**で条件を評価し直すので、0件になります。`expires_at > :now` も同じ文に入っているので、期限切れの判定まで原子的です。

完成形に、実際のHTTPで、**10人が同じ招待を同時に受諾**させます。

```bash
for i in $(seq 1 10); do ( ... curl -X POST /api/invites/$TOK/accept ... ) & done; wait
```

```text
      1 200
      9 404
```

```text
 id | used | used_by
----+------+---------
  1 | t    |       8
```

**成功はちょうど1人**です（完成形のテスト `ConcurrencyIT` も、同じことを確かめています）。残り9人は 404 `INVITE_INVALID` で、理由は教えません。

### GET で消費しない

招待リンクを、チャットに貼ったとします。チャットアプリは、リンクの**プレビューを作るために、サーバー側から URL を GET で開きます**。メールのウイルス検査や、ブラウザの先読みも同じです。もし受諾が GET だったら、**招待を受ける前に、プレビューのロボットが消費**してしまいます。

だから受諾は `POST /api/invites/{token}/accept` です。GET で叩くと、確かに消費されません。

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:18130/api/invites/$TOK2/accept      # GET
```

```text
405
```

静的クライアントは、さらに一工夫しています。招待リンクは `https://…/#invite=<トークン>` の形（`#` 以降のフラグメント）で、**フラグメントはブラウザからサーバーに送られません**。アクセスログにもリファラにも残らず、リンクを開いても何も消費されません。ページに出る「招待を受ける」ボタンを押したときだけ、POST が飛びます。

!!! note "GET は安全であるべき"
    HTTP の GET は、**サーバーの状態を変えない**ことが前提の約束（安全なメソッド）です。この前提で、プレビュー・先読み・キャッシュ・クローラーが動いています。状態を変える操作（消費・削除・投票）を GET にすると、これらが勝手に実行してしまいます。

### 締切後は拒否し、招待は未消費のまま残す

仕様の隙間を1つ決めておきます。「締切後に、有効な招待を受けたら？」。答えは、**409 `POLL_CLOSED` で拒否し、招待は使用済みにしない**です。

```bash
# 招待を発行したあとで、主催者が締め切る → 別の訪問者が受諾する
```

```text
{"detail":"この投票は締め切られています。", ... "code":"POLL_CLOSED"}    409
```

```text
 id | used
----+------
  1 | t        ← さっきの10人の競争で使われたもの
  2 | f        ← 締切後に拒否された招待は、未消費のまま
```

順番にも意味があります（`InviteService.accept`）。

1. 投票を特定（無効な招待なら、ここで 404）
2. 投票の行をロック
3. 招待を**消費**（0件なら 404 `INVITE_INVALID`）
4. **締切後なら 409**（例外でトランザクションごとロールバックされ、3の消費も取り消される）
5. 参加者を作る / 昇格

「無効な招待」の判定が、締切の判定より**先**です。逆にすると、使用済み・期限切れの招待を持つ人に、「この投票が締め切られているか」を教えてしまいます（第30章：認可は状態より先）。

## 6. 定数時間比較

秘密の値を**文字列比較**すると、先頭が違えば早く、末尾だけ違えば遅く返ります。この**時間の差**から、秘密を1文字ずつ当てる攻撃（タイミング攻撃）が、理屈の上では成り立ちます。実際に、比較の速さを測ります（`scripts/TimingDemo.java`）。

```bash
java scripts/TimingDemo.java
```

```text
--- 1 回目（1回の比較あたりのナノ秒）
Arrays.equals          先頭が違う: 3 ns   / 末尾だけ違う: 22 ns
MessageDigest.isEqual  先頭が違う: 964 ns / 末尾だけ違う: 945 ns
```

`Arrays.equals` は、**先頭だけ違うものが速い**（3 ns と 22 ns）。`MessageDigest.isEqual` は、どちらも約950 ns で、位置によらず一定です（定数時間）。

QuickVote の訪問者トークンと招待トークンは、この問題を**構造で避けています**。トークンを自分で比べず、**ハッシュを DB のインデックスで引く**だけだからです。攻撃者が送った値を SHA-256 にした結果で検索するので、比較の速さから「送った値のどこまで合っているか」は読み取れません（ハッシュ値は、入力が1文字違うだけで、まったく別の値になる）。

自分で秘密同士を比べる場面（Webhook の署名、API キーの照合など）では、**`MessageDigest.isEqual` を使う**と決めておきます。差は数十ナノ秒で、ネットワークの揺らぎ（ミリ秒）に埋もれて普通は測れません。それでも、**使い分けを悩まなくて済む**ように、秘密には常に定数時間比較を使います。

## 7. QR は「URL を絵にしただけ」

「QR コードにすれば安全」という誤解があります。QR コードは、**URL（や文字列）を、カメラで読める絵にしただけ**です。

| 誤解 | 事実 |
| --- | --- |
| QR は暗号化されている | 暗号化されていない。読み取れば、中の文字列がそのまま分かる |
| 画像だから共有しにくい | スクリーンショットを撮れば、**参加コードそのもの**を渡したのと同じ |
| 一度使えば消える | QR に有効期限や回数はない。**期限や回数は、サーバーが URL に対して決める** |

つまり、QR は「入力の手間を減らす」ための**運び方**で、守りではありません。参加コード入りの QR を会議室のスクリーンに映せば、見える人は全員参加できます。守りは、これまでの節（探索空間、回数制限、人数上限、締切）で作ります。QR を使うときも、**QR に入れる文字列が、そのまま秘密である**と考えます（招待を QR にするなら、`#invite=` のフラグメント形式のまま、一度限りに頼ります）。

---

## まとめ：3点セット

!!! abstract "この章の3点セット"
    **① 一言で言うと**
    短い参加コード（31⁸）は、探索空間の計算と、訪問者単位・IP単位の試行回数制限（429＋Retry-After）を**重ねて**守る。一度限りの招待は、`UPDATE … WHERE used_at IS NULL AND expires_at > ?` の更新件数で原子的に消費し、GET では消費せず、締切後は未消費のまま 409 で拒否する。URL の判定は、フレームワークが解釈するのと同じ方法（`PathPatternRequestMatcher`）で行う。

    **② 現場でこう聞かれる**
    「参加コードは8文字で十分ですか？」
    → 文字種31で8文字なら 8.5×10¹¹ 通りだが、同時に有効な投票の数で割られるので、長さだけでは不十分。訪問者・IP の二軸の試行回数制限（成功・失敗とも数える）と、有効期間・人数上限・締切を重ねて守る。

    **③ 自分のサービスならどこに当たるか**
    「短い秘密を人間が打つ入口」（コード、PIN、招待）はどこか。1つあたりの試行回数に上限があるか。一度限りの操作が、SELECT→UPDATE になっていないか。

## 演習

??? question "確認問題1：探索空間"
    参加コードを、使える文字31種のまま10文字にします。毎秒1,000回の総当たりで、1つの投票に当たるまでの平均時間は、8文字と比べて何倍になりますか。

??? success "解答"
    31²＝961倍です。8文字なら約13.5年のところ、約13,000年になります。ただし人間が打つ負担が増えるので、長さを増やすより、試行回数制限などの重ね掛けを優先する設計になります。

??? question "確認問題2：すり抜けの原因"
    `request.getRequestURI().startsWith("/api/join")` で判定するフィルタは、`/api/%6aoin` を捕まえられますか。それはなぜですか。

??? success "解答"
    捕まえられません。`getRequestURI()` はデコード前の生の文字列（`/api/%6aoin`）を返し、MVC は `/api/join` として処理するからです。`PathPatternRequestMatcher` のように、MVC と同じデコード後のパスで判定します。

??? question "応用：条件付き UPDATE を書く"
    「クーポンを1人1回だけ使える」ようにします。`coupons(id, used_at, used_by)` 表で、使えたかどうかを更新件数で判定する SQL と、Java のコード（JdbcClient）の骨格を書いてください。

??? success "解答"
    ```sql
    UPDATE coupons SET used_at = :now, used_by = :userId
    WHERE id = :id AND used_at IS NULL
    ```
    ```java
    int updated = jdbc.sql("UPDATE coupons SET used_at = :now, used_by = :userId WHERE id = :id AND used_at IS NULL")
            .param("now", now).param("userId", userId).param("id", id).update();
    if (updated == 0) {
        throw new ApiException(ErrorCode.COUPON_INVALID);   // 使用済み・存在しないを区別しない
    }
    ```
    「先に SELECT で未使用か確かめる」は書かない。

??? question "説明問題：現場でこう聞かれる"
    「招待リンクを GET で開いたら受諾されるほうが、ボタンを押す手間がなくて便利では？」200字程度で答えてください。

??? success "解答"
    例：チャットのリンクプレビュー、メールの検査、ブラウザの先読みは、人が押す前にサーバーが GET で URL を開く。GET で消費すると、本人が受ける前にロボットが消費して、招待が使えなくなる。GET は状態を変えない約束なので、受諾は POST にし、リンクを開いただけでは何も消費されないようにする。
