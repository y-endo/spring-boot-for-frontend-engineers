# 第15章 設定・プロファイル・秘密

<div class="goal-box" markdown>

この章でできるようになること

- `application.yaml`、環境変数、コマンドライン引数の優先順位を、実験で確かめた事実として説明できる
- `@ConfigurationProperties`（`record` ＋ `@Validated` ＋ `@DefaultValue`）で、型のある設定クラスを書ける
- 不正な設定値で、アプリを**起動時に**落とす（fail fast）設計ができる
- プロファイル（dev / prod）の役割と、やってはいけない使い方を言える
- 秘密（パスワード・鍵）を Git に入れない運用と、設定キーのタイポ・環境変数の置き忘れの罠を説明できる

</div>

## 1. 問いかけ：本番だけ違う値は、どこに書くのか

開発では `localhost` のDB、本番では別のDB。開発ではCookieのSecureをオフ、本番ではオン。テストでは時間を進めたい。こういう**環境ごとに変わる値**を、コードの中に直接書いたら、どうなるでしょうか。

- 値を変えるたびに、**ビルドし直し**て、配り直す
- 本番のパスワードが、**Git の履歴に残る**
- 「開発の値のまま本番に出した」事故が起きる

フロントエンドでも、`.env` と `process.env.XXX`（Vite なら `import.meta.env`）で同じ問題に向き合ってきたはずです。Spring Boot の設定の仕組みは、それをもう少し整理したものです。この章では、**設定を型で読み、間違いを起動時に見つけ、秘密を Git に入れない**までを扱います。

## 2. 設定の置き場所と優先順位

Spring Boot は、**複数の場所から設定を集めて**、1つの設定に重ねます（外側の「Environment」と呼ばれます）。主な場所は次のとおりです。**上にあるほど優先されます**。

| 優先度 | 場所 | 例 |
| --- | --- | --- |
| 高 | コマンドライン引数 | `--quickvote.demo.members=7` |
| ↑ | 環境変数 | `QUICKVOTE_DEMO_MEMBERS=5` |
| ↑ | プロファイル別のファイル | `application-dev.yaml` |
| 低 | 共通のファイル | `application.yaml` |
| 最下 | コードの既定値 | `@DefaultValue("3")` |

上のほうが、下を**上書き**します。実際に確かめます。`dev` プロファイルの `application-dev.yaml` には `members: 3` と書いてあります。

```bash
# yaml だけ（members=3）
java -jar target/quickvote-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
# → 見本の投票を作りました: ...（主催者は X-Visitor-Id: 1、参加者は 2〜4）

# 環境変数で上書き（members=5）
QUICKVOTE_DEMO_MEMBERS=5 java -jar target/quickvote-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
# → 参加者は 2〜6

# 環境変数とコマンドライン引数の両方（コマンドラインが勝つ。members=7）
QUICKVOTE_DEMO_MEMBERS=5 java -jar target/quickvote-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --quickvote.demo.members=7
# → 参加者は 2〜8
```

!!! tip "フロントエンドで例えると"
    `.env`、`.env.local`、`.env.production` を重ねる Vite や Next.js の仕組みに近いものです。違いは、**ビルド時ではなく起動時に読む**こと。同じ jar を、環境変数だけ変えて、開発・検証・本番に使い回せます（ビルドし直さない）。

**この優先順位があるので、本番の運用では「jar（と中の yaml）は同じまま、環境変数で値を渡す」**形を取ります。

### 設定ファイルを分ける

QuickVote の `src/main/resources/` には、設定ファイルが複数あります。

```text
application.yaml          ← すべての環境で共通
application-dev.yaml      ← dev プロファイルのとき、上に重なる
application-prod.yaml     ← prod プロファイルのとき（完成形にある）
```

```yaml title="src/main/resources/application.yaml（第2部のラボ）"
spring:
  application:
    name: quickvote
server:
  port: 18012
```

```yaml title="src/main/resources/application-dev.yaml"
# 開発用。--spring.profiles.active=dev で有効になる。
quickvote:
  demo:
    seed: true
    members: 3
```

完成形（`code/quickvote`）の `application-prod.yaml` は、こうです。

```yaml title="code/quickvote/src/main/resources/application-prod.yaml（抜粋）"
# 本番用の上書き。SPRING_PROFILES_ACTIVE=prod で有効になる。
# 接続先などの秘密は、ここに書かず環境変数で渡す:
#   SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD
quickvote:
  visitor-cookie:
    # HTTPS 前提。Cookie が平文のHTTPで送られないようにする。
    secure: true
```

**本番の差分は、最小限にする**のがコツです。共通部分は `application.yaml` に置き、`prod` には「本番だけ違う値」だけを書きます。

## 3. 型のある設定：`@ConfigurationProperties`

設定の値を Java から読む方法は2つあります。

=== "@Value（1つずつ読む）"

    ```java
    @Service
    class DemoService {
        @Value("${quickvote.demo.members:3}")     // 文字列で書く。綴りを間違えても、起動時には分からない
        private int members;
    }
    ```

=== "@ConfigurationProperties（まとめて読む・推奨）"

    ```java title="src/main/java/com/example/quickvote/common/QuickVoteProperties.java（抜粋）"
    @ConfigurationProperties(prefix = "quickvote")
    @Validated
    public record QuickVoteProperties(
            @DefaultValue @Valid StandInAuth standInAuth,
            @DefaultValue @Valid Demo demo) {

        public record StandInAuth(
                @DefaultValue("X-Visitor-Id") @NotBlank @Pattern(regexp = "[A-Za-z0-9-]+") String header) {
        }

        public record Demo(
                @DefaultValue("false") boolean seed,
                @DefaultValue("3") @Min(0) @Max(20) int members) {
        }
    }
    ```

| | `@Value` | `@ConfigurationProperties` |
| --- | --- | --- |
| 型 | 1つずつ。文字列で名前を書く | **クラス（`record`）にまとまる** |
| 検証 | 自分で書く | **Bean Validation が使える**（`@Validated`） |
| 既定値 | 文字列の中（`:3`） | `@DefaultValue` |
| IDE の補完 | 弱い | 効く |
| 使い回し | 値を使う場所ごとに書く | 設定クラスをコンストラクタで受け取る |
| 設定の一覧性 | 散らばる | 1か所に集まる |

使う側は、普通の Bean として**コンストラクタで受け取る**だけです。

```java title="src/main/java/com/example/quickvote/poll/DemoDataLoader.java（抜粋）"
public DemoDataLoader(PollService pollService, JoinService joinService, QuickVoteProperties properties) {
    ...
}
// 使う: properties.demo().members()
```

設定クラスを Bean として登録するのは、`QuickvoteApplication` の `@ConfigurationPropertiesScan` です（第10章の `@ComponentScan` と同様に、パッケージ以下を探します）。

!!! warning "テストの注意：`@WebMvcTest` は設定クラスを含めない"
    第16章の `@WebMvcTest` は、Web に関係する Bean だけを起動します。`QuickVoteProperties` は含まれないので、設定を使う Bean（`StandInWebConfig`）があると「Bean が見つからない」で落ちます。そのテストでは、`@EnableConfigurationProperties(QuickVoteProperties.class)` を足します（`PollControllerValidationTest` を参照）。

### 名前の対応（relaxed binding）

`quickvote.stand-in-auth.header` と書く yaml のキーが、Java の `standInAuth().header()` に対応するのは、**名前の規則を柔らかく読む**（relaxed binding）からです。環境変数の名前でも読めます。`ConfigurationTest` で確かめた対応です。

| 書く場所 | 書き方 | 同じ項目 |
| --- | --- | --- |
| yaml（推奨） | `quickvote.stand-in-auth.header`（kebab-case） | ✓ |
| yaml／コマンドライン | `quickvote.standInAuth.header`（camelCase） | ✓ |
| 環境変数 | `QUICKVOTE_STANDINAUTH_HEADER`（大文字、`_` 区切り、ハイフンなし） | ✓ |
| 環境変数 | `QUICKVOTE_DEMO_MEMBERS` | `quickvote.demo.members` |

**環境変数にするときは、`.` を `_`、小文字を大文字にして、ハイフンを取る**と覚えます。

### 仕様と設定を分ける

何でも設定にすればよい、ではありません。QuickVote は、**設定にしない値**を決めています。

```java title="src/main/java/com/example/quickvote/poll/PollRules.java（抜粋）"
/** QuickVote の仕様上の上限・期間。設定ではなくコードに置く（運用で気軽に変えてよい値ではないため）。 */
public static final int MAX_PARTICIPANTS = 50;
public static final int MAX_OPTIONS = 10;
```

| | 設定（`QuickVoteProperties`） | 仕様（`PollRules`） |
| --- | --- | --- |
| 例 | 開発用の見本データ、Cookie の `Secure`、レート制限の回数、SSE の間隔 | 参加者は50人、選択肢は10個、タイトルは100文字 |
| 変える人・場面 | 運用（環境ごと、障害時） | 仕様変更（コードの変更・テスト・DBの制約と**一緒に**） |
| 変えるとき | 再起動で足りる | コード、テスト、DBの制約、ドキュメントを揃えて直す |

「50人」を環境変数で気軽に変えられるようにすると、DB の制約・テスト・画面の表示と**食い違う**事故になります。**設定にするのは、環境ごとに変えてよい値だけ**です。

## 4. 起動時に落とす（fail fast）

設定が間違っていたら、**起動のときに落とす**。これが原則です。

!!! note "用語：fail fast"
    間違いを、**できるだけ早い段階で、大きな音を立てて**失敗させる設計です。起動時に分かれば、デプロイの時点で気づけます。実行時まで分からないと、本番で、最初にその設定を使ったリクエストが失敗します（深夜に）。

### 範囲外の値

```bash
java -jar target/quickvote-0.0.1-SNAPSHOT.jar --quickvote.demo.members=99
```

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target com.example.quickvote.common.QuickVoteProperties failed:

    Property: quickvote.demo.members
    Value: "99"
    Reason: 20 以下の値にしてください


Action:

Update your application's configuration
```

環境変数（`QUICKVOTE_DEMO_MEMBERS=99`）で渡しても、同じエラーで落ちます。どの**キー**に、どの**値**を入れて、なぜ駄目か、が1画面で読めます。

### 型が違う値

```bash
java -jar target/quickvote-0.0.1-SNAPSHOT.jar --quickvote.demo.members=たくさん
```

```text
Description:

Failed to bind properties under 'quickvote.demo.members' to int:

    Property: quickvote.demo.members
    Value: "たくさん"
    Origin: "quickvote.demo.members" from property source "commandLineArgs"
    Reason: failed to convert java.lang.String to ... int (caused by java.lang.NumberFormatException: For input string: ...)
```

`Origin:` に、**値がどこから来たか**（`commandLineArgs`）まで書かれています。環境変数と yaml のどちらが勝っているのか分からなくなったとき、この情報が役に立ちます。

!!! tip "AIが生成した設定クラスで見るところ"
    `@ConfigurationProperties` に `@Validated` が付いているか、必須の値に検証（`@NotBlank` など）があるか、範囲に制約（`@Min`、`@Max`）があるか。**検証がない設定クラスは、間違った値が通ってしまいます**。

## 5. 罠：設定のミスは、黙って通ることがある

起動時に落ちるのは、**型が合わない・範囲外**のような、検査される間違いです。次の2つは、**エラーにならず通ります**。実験で確かめました（`ConfigurationTest`）。

### 罠①：設定キーのタイポは、黙って無視される

```java
// 正しくは stand-in-auth.header。heder と綴りを間違えた
runner.withPropertyValues("quickvote.stand-in-auth.heder=X-Typo").run(ctx -> {
    assertThat(ctx).hasNotFailed();                                   // ← 起動に成功する
    assertThat(ctx.getBean(QuickVoteProperties.class).standInAuth().header()).isEqualTo("X-Visitor-Id");   // 既定値のまま
});
```

**知らないキーは、無視されます**。タイポは、設定が効かない形で現れます。しかも、`@DefaultValue` があるので、**エラーも出ない**。「設定したのに効かない」の原因の多くは、綴りです。

!!! warning "バージョン注意：古い記事のキーは、無視される"
    Boot 3 までの記事に出てくる設定キーが、Boot 4 では**存在しない**ことがあります。たとえば `spring.jackson.datetime.write-dates-as-timestamps` は Boot 4 のメタデータにありません（Jackson 3 では日時の既定が ISO 8601 文字列で、設定の必要がない）。**無視されるので、エラーにもならず、書いたつもりのまま効いていない**状態になります。キーが本当にあるか、IDE の補完（`application.yaml` で Ctrl+Space）か公式ドキュメントで確かめます。

### 罠②：環境変数の置き忘れ

yaml に `header: ${QV_HEADER}` と書き、環境変数 `QV_HEADER` を渡し忘れるとどうなるでしょうか。`@Value` なら起動時に「Could not resolve placeholder」で落ちます。しかし `@ConfigurationProperties` では、**展開できなかった `${...}` が、文字列のまま設定値になります**。

```java
runner.withPropertyValues("quickvote.stand-in-auth.header=${QV_HEADER_UNSET}")   // 環境変数が無い
// 検証がなければ、header は "${QV_HEADER_UNSET}" という文字列のまま束縛され、起動に成功する
```

この実験では、検証を付ける前は、環境変数がなくても**そのまま起動しました**。対策は、**値の形を検証する**ことです。QuickVote では `@Pattern(regexp = "[A-Za-z0-9-]+")` を付けたので、置き忘れると起動時に落ちます。

```text
Binding to target com.example.quickvote.common.QuickVoteProperties failed:

    Property: quickvote.standInAuth.header
    Value: "${QV_HEADER}"
    Reason: 正規表現 "[A-Za-z0-9-]+" にマッチさせてください
```

DB のパスワード（`spring.datasource.password=${DB_PASSWORD}`）のように、**形式を検証しにくい秘密**では、置き忘れた値が「そのまま」使われ、**最初の接続の失敗**として表れます。置き忘れに気づける仕組み（起動時の接続確認、CI でのチェック）が要ります。

## 6. プロファイル：環境ごとの切り替え

### プロファイルとは

!!! note "用語：プロファイル（profile）"
    アプリの**動作モードの名前**です（`dev`、`test`、`prod` など）。有効にすると、(1) `application-{名前}.yaml` が上に重なり、(2) `@Profile("名前")` を付けた Bean が有効になります。

有効にする方法は、次のとおりです（コマンドライン引数が環境変数より優先されます）。

| 方法 | 例 |
| --- | --- |
| コマンドライン引数 | `--spring.profiles.active=dev` |
| 環境変数 | `SPRING_PROFILES_ACTIVE=dev`（実験で確認） |
| Maven から起動 | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| テスト | `@ActiveProfiles("dev")`（`DevProfileTest`） |

QuickVote の `DemoDataLoader` は、プロファイルではなく**設定値が `true` のときだけ**有効になります。

```java title="src/main/java/com/example/quickvote/poll/DemoDataLoader.java（抜粋）"
@Component
@ConditionalOnProperty(name = "quickvote.demo.seed", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner { ... }
```

`@ConditionalOnProperty` は、第10章で読んだ**自動構成と同じ「条件付き」の仕組み**です。`dev` プロファイルの yaml が `seed: true` を設定するので、`dev` のときだけ見本データが作られます。2つのテストで確かめています。

| テスト | 結果 |
| --- | --- |
| `DefaultProfileTest`（プロファイルなし） | `DemoDataLoader` は Bean にならない。投票は0件 |
| `DevProfileTest`（`@ActiveProfiles("dev")`） | `DemoDataLoader` が Bean になり、起動時に「ランチどこ行く？」が作られる |

### やってはいけないプロファイルの使い方

| アンチパターン | 問題 | 代わりに |
| --- | --- | --- |
| コード内で `if (profile == "prod")` と分岐 | テストできない。環境ごとに動作が変わる | 設定値（`quickvote.xxx`）を読む |
| プロファイルの種類が増える（`dev`、`dev2`、`staging-a`…） | 組み合わせが爆発し、何が有効か分からない | 設定値の差だけで表す |
| テスト専用の挙動を本番コードに残す | 本番で意図せず有効になる | テストのコード側（`src/test`）に置く |
| `prod` で認証や検証を**緩める**分岐 | 本番だけ安全でないという最悪の逆転 | 緩めるのは `dev` 側。本番が既定で安全 |

「**プロファイルは、値を切り替えるもの**。ロジックを切り替えるものではない」と覚えます。

!!! warning "この時点の保証"
    `dev` プロファイルの見本データは、**開発専用**です。本番に `dev` を有効にして出すと、固定の訪問者ID（1〜）で作られた投票が作られます。プロファイルは `prod` の起動で必ず指定する、起動スクリプトや Dockerfile（第42章）で固定する、などの運用で守ります。

## 7. 秘密：Git に入れない

**秘密**とは、漏れたら悪用される値です（DB のパスワード、API キー、トークン署名用の鍵、暗号化キー）。

### 原則

1. **Git に入れない**：`application.yaml` に書いたら、履歴に残ります。**消しても履歴から取り出せます**。一度入れたら、**その秘密を無効にして作り直す**（パスワードの変更）しかありません
2. **コードにも書かない**：`private static final String API_KEY = "sk-..."`
3. **環境ごとに別の値**：開発の秘密が、本番にも通用してはいけません
4. **ログに出さない**：設定の値をログに出さない（第43章）。起動時に「Using generated security password」のようなパスワードを出さない設定（完成形の `UserDetailsServiceAutoConfiguration` の除外）も、同じ理由です

### 渡し方

| 方法 | 使いどころ |
| --- | --- |
| **環境変数** | 本番の基本。`SPRING_DATASOURCE_PASSWORD` のように、Boot のキーに対応した名前で渡せる（relaxed binding） |
| yaml に `${変数名}` と書いて展開 | キー名と環境変数名を分けたいとき。**置き忘れの罠に注意**（5 節） |
| Git 管理外のファイルを読む | 開発用。`.gitignore` に入れておく |
| 秘密管理サービス（クラウドの Secret Manager など） | 本番の運用が成熟したとき |

Git 管理外のファイルを読む設定は、`spring.config.import` で書けます。`optional:` を付けると、ファイルがなくても起動します。実験で確かめました。

```bash
# local.yaml（Git に入れない）に quickvote.demo.members: 6 を書いておく
java -jar app.jar --spring.config.import=optional:file:./local.yaml   # → 参加者は 2〜7（local.yaml が効く）
# ファイルを消して再実行                                                  # → 参加者は 2〜4（無くても起動し、既定値のまま）
```

```gitignore title=".gitignore（追加しておく）"
application-local.yaml
local.yaml
secrets.yaml
.env
```

!!! danger "本番で事故になるパターン：秘密を Git に入れた"
    「テスト用だから」と、本番と同じパスワードを `application.yaml` に書いて push した。履歴を消しても、**フォークやクローンに残る**。公開リポジトリなら、数分で自動スキャンに見つかります。
    すでに入れてしまったら、(1) 履歴の消去ではなく、**秘密を無効化して再発行する**ことが先 (2) その後で、履歴の掃除を検討する。AI は、サンプルとして `password: postgres` のような値を書き込むことがあります。それが**本物の値と同じ**にならないよう、レビューで確認してください。

## 8. 現場で：AIが書いた設定のレビュー

| 見るもの | 疑う理由 |
| --- | --- |
| `application.yaml` にパスワード・キー・トークンが入っている | Git に残る。環境変数に |
| `@Value("${...}")` が、あちこちに散らばっている | 型・検証・一覧性がない。`@ConfigurationProperties` に |
| `@ConfigurationProperties` に `@Validated` と制約がない | 間違った値が通る。fail fast しない |
| 上限や仕様の値が、設定になっている | 仕様は設定ではない。DB制約・テストとずれる |
| `prod` だけロジックが違う（`@Profile("prod")` のコード分岐） | 本番だけ動作が違う。テストされない |
| `spring.xxx` のキーが古い記事のもの | 無視される。キーの存在を確認 |
| 設定を `System.getenv("XXX")` で直接読む | Spring の設定の仕組みを通らない。優先順位・検証・テストの差し替えが効かない |
| 環境変数の既定値に本物の値が入っている（`${DB_PASSWORD:postgres}`） | 置き忘れても動くため、本番で弱い値のまま動く。必須の秘密に既定値を持たせない |
| `application-local.yaml` などが `.gitignore` にない | 開発者の秘密が入る |

---

## まとめ：3点セット

!!! abstract "この章の3点セット"
    **① 一言で言うと**
    設定は、コマンドライン引数 > 環境変数 > プロファイル別 yaml > 共通 yaml > コードの既定値、の優先順位で重なり、`@ConfigurationProperties`（`record` ＋ `@Validated`）で型のある設定として読み、間違いは起動時に落とす。環境ごとに変えてよい値だけを設定にし、秘密は Git に入れず環境変数などで渡す。

    **② 現場でこう聞かれる**
    「本番のDBのパスワードは、どこに置きますか？ `application-prod.yaml` に書いてもいいですか？」
    → 書かない。Git の履歴に残る。環境変数（`SPRING_DATASOURCE_PASSWORD`）か秘密管理サービスで渡し、yaml には接続の形（ホスト名など秘密でないもの）だけを置く。置き忘れても気づけるよう、起動時の検証や接続確認を用意する。

    **③ 自分のサービスならどこに当たるか**
    自分のサービスの設定値を10個挙げ、「設定にする（環境ごとに変える）」「仕様としてコードに置く」「秘密として環境変数で渡す」に分ける。

## 演習

??? question "確認問題1：予測"
    `application-dev.yaml` に `members: 3`、環境変数に `QUICKVOTE_DEMO_MEMBERS=5`、コマンドラインに `--quickvote.demo.members=7`。さらに `SPRING_PROFILES_ACTIVE=dev` で起動した場合、`members` はいくつですか。

??? success "解答"
    7 です。優先順位は、コマンドライン引数 > 環境変数 > プロファイル別 yaml です。実験でも、参加者は `2〜8`（7人）でした。

??? question "確認問題2：タイポ"
    `application.yaml` に `quickvote.demo.memebrs: 10`（綴りの間違い）と書いて起動しました。何が起きますか。気づくために、何をしますか。

??? success "解答"
    エラーなく起動し、`members` は既定値（3）のままです。知らないキーは無視されるためです。気づく方法は、(1) IDE の補完でキー名を確かめる (2) 起動後の値を確認する（ログ、テスト）(3) 重要な設定は、値が既定値のままだと困るなら必須にする（`@NotNull` にして `@DefaultValue` を付けない）、などです。`@ConfigurationProperties(ignoreUnknownFields = false)` で、未知のキーを起動時エラーにする方法もあります（その prefix の下のタイポに限ります）。

??? question "確認問題3：どこに置くか"
    次の値を、「設定」「仕様（コード）」「秘密」に分類してください。
    (a) 参加者の上限50人 (b) DB のパスワード (c) 訪問者 Cookie の `Secure` 属性 (d) SSE のハートビート間隔

??? success "解答"
    (a) 仕様（コード、`PollRules`）。DB制約・テスト・画面と一緒に変える。(b) 秘密（環境変数など。Git に入れない）。(c) 設定（開発は false、本番は true。`quickvote.visitor-cookie.secure`）。(d) 設定（運用で調整してよい。`quickvote.sse.heartbeat-interval`）。

??? question "応用：設定を足す"
    「見本の投票のタイトル」を設定にしてください（`quickvote.demo.title`、既定 `ランチどこ行く？`、空文字は不可）。(1) `QuickVoteProperties.Demo` の変更 (2) `DemoDataLoader` の変更 (3) 空文字を指定したときの挙動、を答えてください。

??? success "解答"
    (1) `Demo` に `@DefaultValue("ランチどこ行く？") @NotBlank String title` を追加。(2) `DemoDataLoader` の `new CreatePollRequest("ランチどこ行く？", ...)` を `properties.demo().title()` に変える。(3) `--quickvote.demo.title=` のように空にすると、`@NotBlank` に違反して起動時に `APPLICATION FAILED TO START`（`Property: quickvote.demo.title`）で落ちる。`ConfigurationTest` に、同じ形のテストを足せます。

??? question "説明問題：現場でこう聞かれる"
    「設定が間違っていたら、起動してから最初のリクエストで落ちるより、起動時に落ちるほうがいいのですか？」200字程度で答えてください。

??? success "解答"
    例：はい。起動時に落ちれば、デプロイの時点で気づけて、利用者に影響が出る前に直せます。実行時まで落ちないと、その設定を最初に使ったリクエストが本番で失敗し、原因の特定も遅れます。そのため、設定クラスに検証を付け、不正な値や必須の欠落を起動時に検出します。ただし、タイポや環境変数の置き忘れは自動では検出されないので、形式の検証や確認の仕組みが別に要ります。
