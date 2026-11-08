# 付録

本編を読んでいて「これはどこで調べればよいのか」となったときに引く、辞書と早見表の置き場です。通して読む必要はなく、必要になったときに開けば足ります。

## 付録一覧

<div class="grid cards" markdown>

-   :material-swap-horizontal: **[TypeScript ↔ Java 対応表](ts-java.md)**

    ---

    型・null・関数・クラス・モジュール・非同期・コレクション・エラー・テスト・ビルド・Express/Next.js と Spring MVC。「TS ならこう、Java ならこう」を、表と左右の対訳で引けます。

-   :material-update: **[Spring Boot 3→4 差分カード](boot3-to-4.md)**

    ---

    古い記事や AI の出力を直すための早見表です。スターター名、Jackson 3、`@MockitoBean`、Security のラムダ DSL、Testcontainers 2 を扱い、実測した内容と公式ガイドの内容を区別して載せています。

-   :material-elephant: **[Gradle 対訳](gradle.md)**

    ---

    QuickVote の `pom.xml` を `build.gradle.kts` に要素ごとに置き換えます。`./mvnw` と `./gradlew` のコマンドの対応も載せています。

-   :material-account-key-outline: **[ログイン型認証の概観](login-auth.md)**

    ---

    自分では作らないが、読めるようになるための章です。パスワード保存、フォームログインとセッション、セッションと JWT、OAuth2/OIDC、リフレッシュトークン、MFA を扱います。

-   :material-wrench-outline: **[トラブルシューティング](troubleshooting.md)**

    ---

    Windows、Docker、IntelliJ、Maven でつまずいたときに、症状から原因を探せます。

-   :material-book-alphabet: **[用語集](glossary.md)**

    ---

    本編に出てくる Java、Spring、サーバー周辺の用語を、五十音順とアルファベット順で引けます。

</div>

## こんなときはここ

| 状況 | 開くページ |
| --- | --- |
| TypeScript で書けることを、Java でどう書くか分からない | [TypeScript ↔ Java 対応表](ts-java.md) |
| AI が出したコードが、コンパイルで落ちる／警告だらけ | [Spring Boot 3→4 差分カード](boot3-to-4.md) |
| 古い記事のコードが `javax` や `@MockBean` を使っている | [Spring Boot 3→4 差分カード](boot3-to-4.md) |
| 職場のプロジェクトが Gradle だった | [Gradle 対訳](gradle.md) |
| 仕様に「ログイン」「Google でログイン」が出てきた | [ログイン型認証の概観](login-auth.md) |
| 用語が分からない | [用語集](glossary.md) |
| 環境構築、ビルド、Docker がうまく動かない | [トラブルシューティング](troubleshooting.md) |

## 付録に載せたコードの確かめ方

付録のコードも本編と同じく、実際に動かして確かめたものだけを載せています。

| 付録 | 確かめた場所 | 内容 |
| --- | --- | --- |
| TypeScript ↔ Java | `code/labs/appendix-ts-java/` | 8本の単体 Java ファイル（JDK 25 で実行）と、Spring Boot 4.1.2 の小さなプロジェクト（テスト12件） |
| Spring Boot 3→4 | `code/labs/appendix-boot3-to-4/` | 古い書き方21本が失敗または警告になること、新しい書き方が通ること、Jackson 3 の既定値の実測 |
| Gradle | `code/labs/appendix-gradle/` | Initializr から取得して `./gradlew build -x test` を実行し、QuickVote の `src` を載せた状態でも確認 |
| ログイン型認証 | `code/labs/appendix-login-auth/` | BCrypt・Argon2・JWT・フォームログイン・リソースサーバー・`oauth2Login` の実行（テスト10件） |

!!! note "付録の位置づけ"
    付録は本編の代わりではありません。「なぜそうなるのか」は本編の章で説明し、「どう書き換えるか」「どれを引くか」は付録で調べる、という分担です。
