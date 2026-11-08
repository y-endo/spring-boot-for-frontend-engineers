# 付録

本編の途中で「これはどこで引けばよいか」となったときの、**辞書と早見表**の置き場です。通して読む必要はありません。必要になったときに開いてください。

## 付録一覧

<div class="grid cards" markdown>

-   :material-swap-horizontal: **[TypeScript ↔ Java 対応表](ts-java.md)**

    ---

    型・null・関数・クラス・モジュール・非同期・コレクション・エラー・テスト・ビルド・Express/Next.js と Spring MVC。「TSならこう、Javaならこう」を表と左右対訳で引きます。

-   :material-update: **[Spring Boot 3→4 差分カード](boot3-to-4.md)**

    ---

    古い記事やAIの出力を直すための早見表。スターター名、Jackson 3、`@MockitoBean`、Security のラムダDSL、Testcontainers 2。実測と公式ガイドを区別して載せています。

-   :material-elephant: **[Gradle 対訳](gradle.md)**

    ---

    QuickVote の `pom.xml` を `build.gradle.kts` に要素ごとに移します。`./mvnw` と `./gradlew` のコマンド対応も。

-   :material-account-key-outline: **[ログイン型認証の概観](login-auth.md)**

    ---

    作らないが読めるように。パスワード保存、フォームログインとセッション、セッションとJWT、OAuth2/OIDC、リフレッシュトークン、MFA。

-   :material-wrench-outline: **[トラブルシューティング](troubleshooting.md)**

    ---

    Windows・Docker・IntelliJ・Maven でつまずいたときの症状別の引き方。

-   :material-book-alphabet: **[用語集](glossary.md)**

    ---

    本編で使う Java・Spring・サーバーまわりの用語を、五十音・アルファベット順に引けます。

</div>

## こんなときはここ

| 状況 | 開くページ |
| --- | --- |
| TypeScriptで書けることを、Javaでどう書くか分からない | [TypeScript ↔ Java 対応表](ts-java.md) |
| AIが出したコードが、コンパイルで落ちる／警告だらけ | [Spring Boot 3→4 差分カード](boot3-to-4.md) |
| 古い記事のコードが `javax` や `@MockBean` を使っている | [Spring Boot 3→4 差分カード](boot3-to-4.md) |
| 職場のプロジェクトが Gradle だった | [Gradle 対訳](gradle.md) |
| 仕様に「ログイン」「Googleでログイン」が出てきた | [ログイン型認証の概観](login-auth.md) |
| 用語が分からない | [用語集](glossary.md) |
| 環境構築・ビルド・Dockerで動かない | [トラブルシューティング](troubleshooting.md) |

## 付録に載せたコードの確かめ方

付録のコードは、本編と同じく**実際に動かして確かめたものだけ**を載せています。

| 付録 | 確かめた場所 | 内容 |
| --- | --- | --- |
| TypeScript ↔ Java | `code/labs/appendix-ts-java/` | 8本の単体Javaファイル（JDK 25で実行）＋ Spring Boot 4.1.2 の小さなプロジェクト（テスト12件） |
| Spring Boot 3→4 | `code/labs/appendix-boot3-to-4/` | 古い書き方21本が失敗／警告になることと、新しい書き方の通過、Jackson 3 の既定値の実測 |
| Gradle | `code/labs/appendix-gradle/` | Initializr から取得して `./gradlew build -x test`、QuickVote の `src` を載せた確認 |
| ログイン型認証 | `code/labs/appendix-login-auth/` | BCrypt・Argon2・JWT・フォームログイン・リソースサーバー・`oauth2Login` の実行（テスト10件） |

!!! note "付録の位置づけ"
    本編を置き換えるものではありません。**「なぜそうなるのか」は本編の章で、「どう書き換えるか・どれを引くか」は付録**、という分担です。
