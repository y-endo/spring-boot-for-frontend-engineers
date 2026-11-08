# トラブルシューティング

<div class="goal-box" markdown>

この付録でできるようになること

- 画面やログに出たエラーメッセージ（の一部）から、**原因・対処・詳しい章**を引ける
- 症状が「エラーなしで効かない」ものも、似た症状から原因の候補を絞れる
- Windows で起きやすいつまずきを、まとめて確認できる

</div>

## 使い方

1. 出たメッセージの**特徴的な一部**（例：`checksum mismatch`、`Connection is not available`）を、ページ内検索（`Ctrl+F` / `⌘F`）か、画面上部のサイト内検索（`/` キー）に入れます。
2. 分類の表で、**症状 → 原因 → 対処 → 詳しい章**の順に読みます。コードで書いた部分は、本文に実際に載っているメッセージや設定そのままです。コードでない行は、症状の説明です。
3. 「詳しい章」で、なぜそうなるかと再現の手順を読みます。

!!! note "メッセージについて"
    この表のメッセージは、本編の実験（Spring Boot 4.1.2、PostgreSQL 18、JDK 25）で実際に出たものです。バージョンや OS の言語によって、文言が少し違うことがあります。日本語の環境では、同じ内容の日本語のメッセージが出ることもあります（第19章）。`...` は、本文で省略されている部分です。

## 分類から探す

<div class="grid cards" markdown>

-   :material-wrench-outline: **[環境構築（JDK・Maven・Docker）](#env)**

    ---

    12 項目

-   :fontawesome-brands-windows: **[Windows 固有のつまずき](#windows)**

    ---

    10 項目

-   :material-language-java: **[Java の基本（コンパイル・実行時エラー）](#java)**

    ---

    16 項目

-   :material-hammer-wrench: **[Maven・ビルド・テスト、古い記事のコード](#maven)**

    ---

    13 項目

-   :material-power: **[起動時のエラー（Bean・設定）](#startup)**

    ---

    15 項目

-   :material-swap-horizontal: **[HTTP・JSON・検証](#http)**

    ---

    14 項目

-   :material-database-outline: **[DB・Flyway・JdbcClient](#db)**

    ---

    20 項目

-   :material-lock-outline: **[トランザクション・ロック・接続プール](#tx)**

    ---

    22 項目

-   :material-clock-outline: **[再送・時刻・定期処理](#schedule)**

    ---

    8 項目

-   :material-shield-lock-outline: **[Security・Cookie・CSRF・CORS](#security)**

    ---

    17 項目

-   :material-broadcast: **[SSE・複数台構成](#sse)**

    ---

    12 項目

-   :material-table-link: **[JPA](#jpa)**

    ---

    11 項目

-   :material-docker: **[Docker・運用（ログ・メトリクス・停止）](#ops)**

    ---

    14 項目

</div>

!!! tip "原因が見当たらないとき"
    [第12章のリクエストの旅の地図](../part02/ch12-request-journey.md)で「どこで起きたか」を先に決めると、調べる場所が絞れます。`401`/`403` は Filter 列、`400` は引数の組み立て、`404` は宛先表、`500` は自分のコードの中、一部のリクエストだけ遅いなら Service の先（DB・外部API）です。

## 環境構築（JDK・Maven・Docker） { #env }

最初の関門です。多くは、第1章の「Windowsでつまずく所」の表に載っています。Windows 固有のものは[次の節](#windows)にもまとめています。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `java` は動くのに `mvnw` が「JAVA_HOME が正しくない」と言う | `JAVA_HOME` が未設定、または古いJDKやJREを指している | PowerShell なら `echo $env:JAVA_HOME` で確認し、JDK 25 のフォルダに設定してターミナルを開き直す。macOS は `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` | [第1章](../part00/ch01-setup.md) |
| `javac` が見つからない（`java -version` は通る） | PATH に `bin` が入っていない、またはJREだけの環境（古い JRE が先に PATH に入っている） | JDK を入れ直す。`where.exe javac` で場所を確認し、`javac -version` も必ず確認する | [第1章](../part00/ch01-setup.md) |
| `docker version` で Server が出ない | Docker Desktop が起動していない | Docker Desktop を起動し、緑になるまで待つ | [第1章](../part00/ch01-setup.md) |
| Docker Desktop が起動しない、WSL の警告が出る | WSL 2 が無効、または仮想化がBIOSで無効 | `wsl --install` を実行して再起動。BIOS/UEFI で仮想化（VT-x/SVM）を有効にする | [第1章](../part00/ch01-setup.md) |
| `bind: address already in use`（5432） | PCに既にPostgreSQL等が5432番を使っている | `-p 5433:5432` に変える（以後、接続先ポートも5433にする）。`netstat -ano \| findstr 5432` で使用中のプロセスを確認 | [第1章](../part00/ch01-setup.md)、[第17章](../part03/ch17-postgres-flyway.md) |
| 日本語を含むパスでビルドが失敗する | ユーザー名やフォルダ名に日本語・全角文字がある | `C:\dev\java-study` のような半角英数字だけのパスに置く | [第1章](../part00/ch01-setup.md) |
| パスに空白があって失敗する（`Program Files`、`My Documents` など） | パスの空白 | 可能なら空白のないフォルダに置く。コマンドではパスを `"..."` で囲む | [第1章](../part00/ch01-setup.md) |
| `mvnw` が `Permission denied`（macOS/Linux） | 実行権限がない | `chmod +x mvnw` | [第1章](../part00/ch01-setup.md) |
| 初回の `mvnw` が進まない | 会社のプロキシやVPNでダウンロードが止まる | `~/.m2/settings.xml` にプロキシ設定を入れる、またはVPNを切る | [第1章](../part00/ch01-setup.md) |
| 古い記事のとおり `-v ...:/var/lib/postgresql/data` を付けた（PostgreSQL 18） | 18以降のイメージは、データの標準の保存先が `/var/lib/postgresql/18/docker` になり、ボリュームは `/var/lib/postgresql` に付けるのが正しい形 | ボリュームの付け先を `/var/lib/postgresql` にする | [第1章](../part00/ch01-setup.md) |
| IntelliJ で「JDK が見つからない」と言われる | プロジェクトの SDK が JDK 25 になっていない | `File > Project Structure > Project` で SDK に JDK 25 を指定する。VS Code は `Java: Configure Java Runtime` で JDK 25 が選ばれているか確認 | [第1章](../part00/ch01-setup.md) |
| `pom.xml` を変えたのにIDEが反映しない | IDE が依存関係を読み直していない | Maven ツールウィンドウの **Reload** を押す | [第1章](../part00/ch01-setup.md) |

## Windows 固有のつまずき { #windows }

Git Bash・PowerShell・コンソールの文字コードなど、Windows で起きるものを本文から集めました。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| 日本語が `?` や記号になる／文字化けする。`stdout.encoding= MS932` と出る | Windows のコンソールの文字コードが MS932（Shift_JIS 系）。Java 25 はコンソールの文字コードに合わせて出力する | 実行時に `-Dstdout.encoding=UTF-8` を付けるか、Windows Terminal で `chcp 65001`。ソースファイルは必ず UTF-8 で保存する | [第1章](../part00/ch01-setup.md)、[第2章](../part01/ch02-how-java-runs.md) |
| `このシステムではスクリプトの実行が無効` | PowerShell の実行ポリシー | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`（ユーザー範囲のみ） | [第1章](../part00/ch01-setup.md) |
| PowerShell で `-Dtest='PercentagesTest#oneVoteEach'` のような引数が動かない | PowerShell は `-D` で始まる引数や `#` を含む引数を、そのまま渡さないことがある | 引数全体を `'...'` で囲む（`.\mvnw.cmd test '-Dtest=PercentagesTest#oneVoteEach'`）。または `--%` を使う | [第1章](../part00/ch01-setup.md) |
| PowerShell で `./mvnw` が使えない | Windows のラッパーは `mvnw.cmd` | `.\mvnw.cmd test` のように書く（本文のコマンド例は `./mvnw` と併記） | [第1章](../part00/ch01-setup.md)、[第8章](../part01/ch08-junit.md) |
| Git Bash から `curl -d '{"title":"ランチ"}'` を送ると `400 Failed to read request` | コードページが 932 の環境で、日本語を直接書くと UTF-8 でないバイト列が送られる（この教材の環境で再現） | UTF-8 のファイルを作り、`--data-binary @body.json`（`Content-Type: application/json; charset=utf-8`）で送る。JSON の文字コードは UTF-8 と決まっている | [第13章](../part02/ch13-api-contract.md)、[第19章](../part03/ch19-jdbcclient.md) |
| Git Bash の `docker run ... ls /opt/java/openjdk/bin` で、パスが `D:/Application/Git/opt/...` に書き換わる | Git Bash が `/` で始まる引数を Windows のパスに書き換える | `MSYS_NO_PATHCONV=1 docker run ...` と付ける。PowerShell では起きない | [第42章](../part09/ch42-docker.md) |
| `JAVA_HOME` を設定したのに反映されない | 設定前に開いたターミナルのまま。インストーラーが自動で設定済みの場合もある | 設定後はターミナルを開き直す。まず `echo $env:JAVA_HOME` で確認 | [第1章](../part00/ch01-setup.md) |
| `mvnw`（シェルスクリプト）が macOS/Linux やコンテナの中で動かない | 改行コードが `CRLF` になっている（Windows は CRLF、macOS/Linux は LF） | リポジトリに `.gitattributes` を置き `* text=auto eol=lf` と指定、または Git の `core.autocrlf` を確認。IDE の改行コードも LF にする | [第1章](../part00/ch01-setup.md) |
| Windows で作ったファイルを Git 経由で受け取ると `mvnw` が実行できない（CI など） | 実行権限が落ちている | `chmod +x mvnw`（CI の手順に入れる） | [第46章](../part09/ch46-release.md) |
| 日本語・空白を含むパスで失敗する | ユーザー名・フォルダ名の全角文字、`Program Files` などの空白 | 半角英数字だけ、空白なしのパスに置く | [第1章](../part00/ch01-setup.md) |

## Java の基本（コンパイル・実行時エラー） { #java }

第1部の実験で出てくるエラーです。メッセージの形で、コンパイル時・起動時・実行時のどの段階かを見分けます。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `TypeError.java:2: エラー: 不適合な型: Stringをintに変換できません:` | 型の不一致（コンパイルエラー。実行する前に止まる） | 型を直す。`ファイル名:行番号: エラー:` と `^` で位置が示される | [第2章](../part01/ch02-how-java-runs.md) |
| `エラー: メイン・クラスMainを検出およびロードできませんでした`／`java.lang.ClassNotFoundException: Main` | クラスパスの間違い（`-cp nothing` のように、`Main.class` のない場所を探した） | `-cp` に `.class` のあるフォルダを指定する | [第2章](../part01/ch02-how-java-runs.md) |
| `Exception in thread "main" java.lang.NoClassDefFoundError: Tally`／`Caused by: java.lang.ClassNotFoundException: Tally` | 起動後に使う場面で、クラス（`Tally.class`）が見つからない。クラスは必要になった時点で初めて探される | クラスパスに `.class` が揃っているか確認する | [第2章](../part01/ch02-how-java-runs.md) |
| `UnsupportedClassVersionError` | 新しいJavaでコンパイルした `.class` を古いJVMで動かした | 実行する JVM の版を、コンパイルした JDK 以上にそろえる | [第2章](../part01/ch02-how-java-runs.md) |
| `toMap → Duplicate key alice (attempted merging values カレー and ラーメン)` | `Collectors.toMap` はキーが重複すると例外を投げる | 第3引数に結合関数（`(a, b) -> b` ＝後勝ち）を渡す。キーが本当に一意か確認する | [第6章](../part01/ch06-collections-streams.md) |
| `List.of(...).add → UnsupportedOperationException`／`Arrays.asList(...).add → UnsupportedOperationException` | `List.of` は変更できない。`Arrays.asList` は置換だけでき、追加はできない | 変更するなら `new ArrayList<>(List.of(...))` で作る | [第6章](../part01/ch06-collections-streams.md) |
| `for-each 中の remove → ConcurrentModificationException` | 拡張 for のループ中に、そのリストから要素を削除した | `nums.removeIf(...)` と書く | [第6章](../part01/ch06-collections-streams.md) |
| `ClassCastException: class java.lang.Integer cannot be cast to class java.lang.String (java.lang.Integer and java.lang.String are in module java.base of loader 'bootstrap')` | 型消去により、生の型（`List` だけ）や `@SuppressWarnings` で抑えた警告をすり抜けて、取り出し時に初めて失敗する | 生の型を書かない。`@SuppressWarnings("unchecked")` を見たらなぜ必要か確認する | [第6章](../part01/ch06-collections-streams.md) |
| `1 / 3 → ArithmeticException: Non-terminating decimal expansion; no exact representable decimal result.` | `BigDecimal` の割り算は、割り切れないと丸め方の指定が必要 | `divide(..., 1, RoundingMode.HALF_UP)` のように桁と丸めを指定する。丸め方は業務の仕様 | [第7章](../part01/ch07-value-pitfalls.md) |
| `Optional.of(null)` で `NullPointerException` | `Optional.of` は `null` を即座に拒否する | `null` かもしれない値は `Optional.ofNullable(...)` を使う | [第5章](../part01/ch05-null-exceptions.md) |
| `Expecting value to be false but was true`（`AssertionFailedError`、境界のテストだけ落ちる） | 判定を `<` から `<=` に変えるなど、境界の挙動が変わった | 境界の値（締切ちょうど）のテストを書いて守る | [第8章](../part01/ch08-junit.md) |
| 参加コードを入力したのに「見つかりません」と言われる（テストでは通る） | 文字列を `==` で比べている。リクエストから来た文字列は実行時に作られた別のオブジェクト | 文字列やID・コードの比較は必ず `equals`（または `Objects.equals`） | [第7章](../part01/ch07-value-pitfalls.md) |
| 3人が1票ずつなのに得票率が 33% + 33% + 33% = 99% | 各自を切り捨て・四捨五入すると合計が100にならない | 最大剰余法で配る。どの丸め方を採用するかは人間が決める | [第7章](../part01/ch07-value-pitfalls.md) |
| 締切を「23:30」にしたのにサーバーでは9時間ずれる | タイムゾーンを明示しない `LocalDateTime.now()`、`ZoneId.systemDefault()`、`new Date()` を使っている | サーバー内は `Instant`（UTC）で持ち、現在時刻は引数か注入された部品で受け取る | [第7章](../part01/ch07-value-pitfalls.md)、[第28章](../part05/ch28-time-expiry.md) |
| 絵文字入りの選択肢が `@Size(max = 100)` の数え方とずれる | `@Size` は `String.length()`（UTF-16 の単位数）で数える。絵文字は1つが2になる | どの単位で数える仕様か決めて、それに合った検証を自分で用意する | [第7章](../part01/ch07-value-pitfalls.md) |
| テストは通るのに、まれに失敗する／100回通ったのに本番で落ちる | 競合状態やタイミングに依存したコード。通った回数は根拠にならない | 狙った条件を意図的に作るテストを書く | [第8章](../part01/ch08-junit.md)、[第26章](../part04/ch26-concurrency-testing.md) |

## Maven・ビルド・テスト、古い記事のコード { #maven }

Boot 3 の記事や AI の出力をそのまま写して動かないものは、[Spring Boot 3→4 差分カード](boot3-to-4.md)でまとめて確認できます。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `@MockBean`／`@SpyBean` が使えない（import できない） | Boot 4.1.2 には `@MockBean`／`@SpyBean` のクラス自体が存在しない | `@MockitoBean`（`org.springframework.test.context.bean.override.mockito.MockitoBean`）に置き換える | [第16章](../part02/ch16-api-testing.md)、[Boot 3→4 差分カード](boot3-to-4.md) |
| `@WebMvcTest` の import が見つからない（`org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`） | Boot 4 でパッケージが変わった | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` に直す。古い import が出たら Boot 3 以前の記事 | [第16章](../part02/ch16-api-testing.md)、[Boot 3→4 差分カード](boot3-to-4.md) |
| `import javax.persistence.*` や `javax.validation.*` が出てくる | 古い世代（Spring Boot 2 以前）の書き方 | `jakarta.persistence.*`／`jakarta.validation.*` に読み替える | [第14章](../part02/ch14-validation-errors.md)、[第39章](../part08/ch39-jpa-basics.md) |
| `spring-boot-starter-web` を使っている | Boot 4 では Web は `spring-boot-starter-webmvc`。旧名は 4.1.2 でも取得できるが非推奨 | `spring-boot-starter-webmvc` を使う。テスト用も `spring-boot-starter-webmvc-test` など機能ごと | [第10章](../part02/ch10-spring-boot.md)、[Boot 3→4 差分カード](boot3-to-4.md) |
| `com.fasterxml.jackson.databind.ObjectMapper` が出てくる | Jackson 2 の書き方 | Jackson 3 は `tools.jackson.databind.json.JsonMapper`。アノテーション（`@JsonProperty` など）は `com.fasterxml.jackson.annotation.*` のまま | [第13章](../part02/ch13-api-contract.md)、[Boot 3→4 差分カード](boot3-to-4.md) |
| `HttpStatus.UNPROCESSABLE_ENTITY` が非推奨と言われる | Spring Framework 7 で RFC の名称変更に合わせた | `HttpStatus.UNPROCESSABLE_CONTENT` | [第14章](../part02/ch14-validation-errors.md)、[Boot 3→4 差分カード](boot3-to-4.md) |
| 古い `mockMvc.perform(...).andExpect(...)` の書き方 | 動くが、AssertJ に統一した新しい書き方が推奨 | `MockMvcTester`（`assertThat(...)`）を使う | [第16章](../part02/ch16-api-testing.md) |
| `spring.jackson.datetime.write-dates-as-timestamps` を書いたが効かない（エラーも出ない） | Boot 4 のメタデータに存在しない。知らないキーは無視される | IDE の補完（`application.yaml` で Ctrl+Space）か公式ドキュメントでキーを確かめる | [第15章](../part02/ch15-configuration.md) |
| `V3__add_note.sql` を消したのに、同じマイグレーションのエラーが出続ける | `spring-boot:run` は `src/main/resources` を `target/classes` にコピーするだけで、削除をコピー先に反映しない | `./mvnw clean spring-boot:run`（`clean` を付ける） | [第17章](../part03/ch17-postgres-flyway.md) |
| テストが遅い（数十本で数分） | すべてに `@SpringBootTest` を付けている。注釈の組み合わせが違うたびにコンテキストが作り直される | 入力検証・エラーの形は `@WebMvcTest`、業務ロジックは Spring なしの単体テスト。共通の土台（`AbstractIntegrationTest`）で設定をそろえる | [第16章](../part02/ch16-api-testing.md)、[第21章](../part03/ch21-testcontainers.md) |
| テストを単体では流せるのに、まとめて流すと落ちる | テスト同士が DB を通じて影響し合っている | 各テストの前に表を空にする（`TRUNCATE ... RESTART IDENTITY CASCADE`） | [第21章](../part03/ch21-testcontainers.md)、[第26章](../part04/ch26-concurrency-testing.md) |
| 20スレッドで待ち合わせる並行性テストが固まる／終わらない | `@Transactional` のメソッドでは、1スレッドが接続を1本握ったまま待つので、プール（既定10本）を超えるスレッドが待ち合う | 全スレッドにタイムアウトを付ける。プールを超えない本数にする | [第26章](../part04/ch26-concurrency-testing.md) |
| H2 で通ったテストが、本番の PostgreSQL で落ちる | H2 の PostgreSQL 互換モードは、本線の SQL を実行できない（`timestamptz`、`ON CONFLICT` など） | 本物の PostgreSQL を Testcontainers で起動してテストする | [第21章](../part03/ch21-testcontainers.md) |

## 起動時のエラー（Bean・設定） { #startup }

DIコンテナの良い点は、配線の間違いが**起動時に**分かることです。`APPLICATION FAILED TO START` の `Description:` と `Action:` を読みます。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `APPLICATION FAILED TO START`／`Parameter 0 of constructor in com.example.quickvote.poll.OptionRepository required a bean of type 'com.example.quickvote.vote.VoteRepository' that could not be found.` | 必要な Bean がない。`@Service` などの目印の付け忘れ、`@SpringBootApplication` のパッケージの外、`@Bean` メソッドの抜け | 「何番目の引数が、どの型を要求して、何が足りないか」を読む。`Action:` に直し方が書いてある | [第11章](../part02/ch11-di-container.md) |
| `No qualifying bean of type '...ShareFormatter' available: expected single matching bean but found 2: plainFormatter,fancyFormatter` | 同じ型の Bean が2つあり、どちらを渡すか決められない | `@Primary` で既定を決めるか、`@Qualifier("名前")` で指名する | [第11章](../part02/ch11-di-container.md) |
| `The dependencies of some of the beans in the application context form a cycle:` | Bean の循環依存（A が B を、B が A を要求する） | 共通部分を第3のクラスに切り出す、依存の向きを一方向にそろえる。`spring.main.allow-circular-references` を `true` にするのは問題を隠すだけ | [第11章](../part02/ch11-di-container.md) |
| `Cannot invoke "...Repo.find()" because "this.repo" is null` | 自分で `new` したオブジェクトにはコンテナが何も注入しない（フィールド注入のクラス） | コンテナに作らせる。コンストラクタ注入にすれば `new` の時点でコンパイルエラーになる | [第11章](../part02/ch11-di-container.md) |
| 起動は成功するのに `HTTP/1.1 404`／`{"timestamp":"2026-11-07T16:44:38.717Z","status":404,"error":"Not Found","path":"/api/hello"}` | `@SpringBootApplication` のパッケージの外にある Controller は、コンポーネントスキャンの範囲外で、エラーも警告も出ない | アプリのクラスは、`@SpringBootApplication` を付けたクラスと同じパッケージ、またはその配下に置く | [第10章](../part02/ch10-spring-boot.md) |
| `Binding to target com.example.quickvote.common.QuickVoteProperties failed:`／`Property: quickvote.demo.members`／`Reason: 20 以下の値にしてください` | 設定の値が検証（`@Max` など）に違反している | `Property:` のキーと `Value:` を直す。環境変数で渡しても同じエラーになる | [第15章](../part02/ch15-configuration.md) |
| `Failed to bind properties under 'quickvote.demo.members' to int:`／`Reason: failed to convert java.lang.String to ... int` | 設定の値の型が合わない。`Origin:` に値の出どころ（例：`commandLineArgs`）が出る | 値を直す。環境変数と yaml のどちらが勝っているかは `Origin:` で分かる | [第15章](../part02/ch15-configuration.md) |
| 設定したのに効かない（エラーも出ない） | 設定キーのタイポ。知らないキーは無視され、`@DefaultValue` の既定値のままになる | キーの綴りを疑う。IDE の補完で確かめる | [第15章](../part02/ch15-configuration.md) |
| `Value: "${QV_HEADER}"`（`${...}` が展開されないまま束縛される） | 環境変数の置き忘れ。`@ConfigurationProperties` は展開できなかった `${...}` を文字列のまま束縛し、起動に成功してしまう | 値の形を検証する（`@Pattern` など）。DB パスワードのように検証しにくい秘密は、最初の接続の失敗として表れる | [第15章](../part02/ch15-configuration.md) |
| `UnsatisfiedDependencyException: Error creating bean with name 'pollController' ...`／`No qualifying bean of type 'com.example.quickvote.poll.PollService' available`（`@WebMvcTest`） | `@WebMvcTest` は `@Service` を起動しないので、Controller の依存を満たせない | `@MockitoBean` で偽物を置く。テストでも DI コンテナの規則は同じ | [第16章](../part02/ch16-api-testing.md) |
| `@WebMvcTest` で「Bean が見つからない」（設定クラスを使う Bean がある） | `@WebMvcTest` は `@ConfigurationProperties` のクラスを含めない | `@EnableConfigurationProperties(QuickVoteProperties.class)` を足す | [第15章](../part02/ch15-configuration.md)、[第16章](../part02/ch16-api-testing.md) |
| 自作の `JsonMapper` の `@Bean` を足したら、`spring.jackson.*` の設定が効かなくなった | 丸ごと定義すると、Boot が施していた既定の設定がすべて使われなくなる（`@ConditionalOnMissingBean` で自動構成が引っ込む） | 設定の変更は `application.yaml` や Boot のカスタマイズの口で行い、Bean を丸ごと差し替えない | [第10章](../part02/ch10-spring-boot.md) |
| `--debug` のレポートに `Jackson2HttpMessageConvertersConfiguration` が「Did not match」と出る | エラーではない。Jackson 2 用の旧設定で、Boot 4 は Jackson 3 を使う | 古い記事の `ObjectMapper` は `JsonMapper` と読み替える | [第10章](../part02/ch10-spring-boot.md) |
| Service の中で `private Long currentUserId;` のようなフィールドを持っている | Bean は singleton なので全リクエストで共有される。普段は再現せず、本番の同時アクセスで他人の権限・データが見える | ローカル変数・引数で渡す。リクエスト固有の情報は Spring Security の認証情報などで取る | [第11章](../part02/ch11-di-container.md)、[第9章](../part01/ch09-threads.md) |
| 秘密（DB パスワードなど）を `application.yaml` に書いて Git に push した | 履歴を消してもフォークやクローンに残る。公開リポジトリなら数分で自動スキャンに見つかる | まず秘密を無効化して再発行する。その後で履歴の掃除を検討する | [第15章](../part02/ch15-configuration.md) |

## HTTP・JSON・検証 { #http }

クライアントから見える応答（ステータスと `code`）から、地図のどこで起きたかを当てます。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `400`／`Failed to read request` | 引数の組み立てで失敗。JSON が壊れている、型が違う（`"closesInMinutes": "abc"`）、本文が空、本文が配列 | `BAD_REQUEST` の応答。送った JSON と型を確認する。Windows の Git Bash なら[文字コード](#windows)も疑う | [第12章](../part02/ch12-request-journey.md)、[第13章](../part02/ch13-api-contract.md) |
| ``tools.jackson.databind.exc.MismatchedInputException: Cannot map `null` into type `boolean` `` （`FAIL_ON_NULL_FOR_PRIMITIVES`） | Jackson 3 は、プリミティブ（`boolean`、`int`）の項目が欠落・`null` だとエラーにする。Jackson 2 では黙って `false` / `0` だった | `Boolean` で受け、コンパクトコンストラクタで既定値を決める | [第13章](../part02/ch13-api-contract.md)、[第47章](../part10/ch47-ai-review.md) |
| `405`／`Method 'GET' is not supported.`（`METHOD_NOT_ALLOWED`） | POST 専用の URL に GET を送った | `Allow: POST` ヘッダーで許されるメソッドが分かる | [第14章](../part02/ch14-validation-errors.md) |
| `400`／`Failed to convert 'pollId' with value: 'abc'` | パスの値（`/api/polls/abc`）の型が合わない | パスの値を直す | [第14章](../part02/ch14-validation-errors.md) |
| `415`（`UNSUPPORTED_MEDIA_TYPE`） | `Content-Type: text/plain` で JSON を送った | `Content-Type: application/json` を付ける | [第13章](../part02/ch13-api-contract.md) |
| `400`／`VALIDATION_FAILED`（`入力値が正しくありません。`、`errors[0].field = "title"`） | `@Valid` の検問で入力検証に失敗した（`"title": null`、`"options": [null]` など） | `errors` の `field` と `message` を読んで入力を直す | [第14章](../part02/ch14-validation-errors.md) |
| `404` だが Controller はあるはず | DispatcherServlet の宛先表の問題。パスの綴り、パッケージの外、メソッド違い | `RequestMapping` の綴りと、[パッケージの位置](#startup)を確認する | [第12章](../part02/ch12-request-journey.md) |
| `500`（Controller の中） | 自分のコードの中で想定外の例外 | ログのスタックトレースを読む（応答の本文には例外のメッセージを載せない） | [第12章](../part02/ch12-request-journey.md)、[第14章](../part02/ch14-validation-errors.md) |
| `401`／`403` が返る（Controller に届かない） | Filter 列（Security）、または引数の組み立ての段階 | [Security の節](#security)を参照 | [第12章](../part02/ch12-request-journey.md)、[第31章](../part06/ch31-spring-security.md) |
| エラー応答に `org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint ...`、制約名、内部ID が出ている | 想定外の例外のメッセージをそのまま応答に載せている（内部の事情が外に出る）。ProblemDetail でもない | 想定外は `INTERNAL_ERROR` と決まった文言だけにする。`ProblemDetailAdvice` に集める | [第14章](../part02/ch14-validation-errors.md) |
| `"closesInMinutes": 30.7` が `30` に、`"allowMemberOptions": 1` が `true` になる（エラーにならない） | Jackson は、変換できるなら読み替える（既定の仕様） | 範囲・形式を Bean Validation で検査する（`closesInMinutes` は 5〜10080） | [第13章](../part02/ch13-api-contract.md) |
| 17,000文字の JSON が `Failed to read request`、9,000バイトのヘッダーが `400` | JSON の本文は `spring.jackson.factory.constraints.read.max-document-length: 16384`、ヘッダーは Tomcat の既定（8KB）が上限 | 上限は層ごとに設定する（`server.max-http-request-header-size` など） | [第35章](../part06/ch35-rate-limiting.md) |
| ページ送りで同じ投票が2回出る／見えない投票がある | 値が重なる列だけで並べている（`ORDER BY created_at DESC` のみ） | 最後に一意な列を足す（`ORDER BY created_at DESC, id DESC`） | [第20章](../part03/ch20-query-index.md) |
| Controller が Entity を直接返している（内部の項目が漏れる、列の変更が API の変更になる） | 内部の型をそのまま返すと、見せたくない項目の漏洩・クライアントの破損・遅延ロードの問題が起きる | DTO（`record`）を返す。レビューでは Controller の戻り値の型を最初に見る | [第13章](../part02/ch13-api-contract.md)、[第40章](../part08/ch40-n-plus-one.md) |

## DB・Flyway・JdbcClient { #db }

PostgreSQL のエラーは `SQLSTATE` と制約名が手がかりです。制約に名前を付けておくと、メッセージから原因がすぐ分かります。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `Migration checksum mismatch for migration version 1`／`Validate failed: Migrations have failed validation`（`FlywayValidateException`） | 適用済みのマイグレーションを書き換えた。DB の記録（checksum）と手元のファイルが違う | 書き換えを戻す。間違いは新しい V で直す。`repair` は記録を合わせるだけで DB の形は変わらないので、安易に使わない | [第17章](../part03/ch17-postgres-flyway.md) |
| `Found more than one migration with version 3` | 別ブランチで同じ版番号のファイルを作った | まだ誰も適用していないファイルなら、番号を付け替える | [第17章](../part03/ch17-postgres-flyway.md) |
| `Detected resolved migration not applied to database: 2.1.` | 適用済みの版より小さい番号が、後から合流した | 番号は常に今ある最大より大きくする。`outOfOrder=true` は環境ごとに適用順が変わるので通常は使わない | [第17章](../part03/ch17-postgres-flyway.md) |
| `Migration of schema "public" to version "3 - add memo" failed! Changes successfully rolled back.`／`ERROR: relation "nosuch_table" does not exist`（`SQL State : 42P01`） | マイグレーションの途中の SQL が失敗した | PostgreSQL は構造を変える文もトランザクションの中で実行し、失敗すると全部なかったことになる。直して再実行すればよい | [第17章](../part03/ch17-postgres-flyway.md) |
| `spring.jpa.hibernate.ddl-auto=update` が AI の出力に入っている | アプリが起動時にテーブルを自動で変更する。履歴が残らず、環境ごとに形がずれる | 本線では採用しない。スキーマは Flyway で作る | [第17章](../part03/ch17-postgres-flyway.md) |
| `cannot insert a non-DEFAULT value into column "id"` | `GENERATED ALWAYS AS IDENTITY` の列に、アプリが値を指定した | 列を INSERT の対象から外す（DB が採番する） | [第18章](../part03/ch18-data-modeling.md) |
| `ERROR:  duplicate key value violates unique constraint "participants_poll_visitor_key"` | UNIQUE 制約の違反（同じ投票に同じ訪問者を二重登録） | 制約の名前から原因が分かる。アプリでは `DuplicateKeyException` として扱う | [第18章](../part03/ch18-data-modeling.md)、[第19章](../part03/ch19-jdbcclient.md) |
| `ERROR:  new row for relation "participants" violates check constraint "participants_role_check"` | CHECK 制約の違反（許していない値） | 値を直す | [第18章](../part03/ch18-data-modeling.md) |
| `ERROR:  null value in column "title" of relation "polls" violates not-null constraint` | NOT NULL の列に NULL | 入力検証で先に弾く | [第18章](../part03/ch18-data-modeling.md) |
| `ERROR:  insert or update on table "participants" violates foreign key constraint "participants_poll_id_fkey"` | 外部キーの違反（存在しない親を指している） | 親の行を先に作る、IDを確認する | [第18章](../part03/ch18-data-modeling.md)、[第22章](../part04/ch22-transactions.md) |
| `ERROR:  value too long for type character varying(100)` | `varchar(100)` の列に長すぎる値 | 入力検証で文字数を制限する | [第18章](../part03/ch18-data-modeling.md) |
| `ERROR:  cannot use subquery in check constraint` | CHECK 制約では他の行を数えられない | DB の制約が守れるのは1行で判定できる条件だけ。人数上限のような複数行の条件は[ロック](#tx)で守る | [第18章](../part03/ch18-data-modeling.md) |
| `Failing row contains (...)` が例外メッセージに出る | 違反した行の全部の値（タイトルや参加コード）が DETAIL に入る | そのままログや応答に出さない（`SafeThrowable` で防ぐ） | [第18章](../part03/ch18-data-modeling.md)、[第43章](../part09/ch43-logging.md) |
| `EmptyResultDataAccessException`／`IncorrectResultSizeDataAccessException` | `single()` で 0 行（Empty）または 2 行以上（IncorrectResultSize）だった | 0行がありうるなら `optional()`。2行以上は設計か UNIQUE を疑う | [第19章](../part03/ch19-jdbcclient.md) |
| `BadSqlGrammarException ... Can't infer the SQL type to use for an instance of java.time.Instant. Use setObject() with an explicit Types value to specify the type to use.` | PostgreSQL の JDBC ドライバは `Instant` を `timestamptz` と推測しない | `OffsetDateTime`（UTC）に直して渡す。変換は1か所に集める | [第19章](../part03/ch19-jdbcclient.md) |
| SQL を文字列連結で作っている（`"WHERE title LIKE '%" + keyword + "%'"`、`String.format`） | SQL インジェクションが成立する。「検証しているから大丈夫」ではない | 値は必ずバインドする（`:name`）。Bean Validation は前線であり、規則を守る道具ではない | [第19章](../part03/ch19-jdbcclient.md) |
| `Failed to execute script V1__create_polls_and_options.sql`／`不明なデータ型: "TIMESTAMPTZ"`（`Unknown data type: "TIMESTAMPTZ"`） | H2 は `timestamptz` を知らない | テストは本物の PostgreSQL（Testcontainers）で行う | [第21章](../part03/ch21-testcontainers.md) |
| `Syntax error in SQL statement "INSERT INTO votes (...) VALUES (?, ?, ?, ?) [*]ON CONFLICT (poll_id, participant_id) DO UPDATE SET ...` | `ON CONFLICT` は PostgreSQL の書き方で、H2 では実行できない | H2 に合わせて SQL を変えない。本物の PostgreSQL でテストする | [第21章](../part03/ch21-testcontainers.md) |
| `ERROR: cached plan must not change result type`（SQLSTATE 0A000） | 稼働中に列を足した直後、`SELECT *` の結果の形が変わり、ドライバが覚えていた文のひな形が合わなくなる（1回失敗して作り直す） | 列を明示する（`SELECT id, public_id, title, ...`）。デプロイ手順に「失敗は1〜数回ありうる」と織り込む | [第46章](../part09/ch46-release.md) |
| `ERROR:  column "name" of relation "polls" contains null values` | contract（`NOT NULL`）を早まった。古いアプリが `name` を書かないまま行を作り続けていた | 全アプリが新しい列を使うようになってから contract する。間に再バックフィルが要る | [第46章](../part09/ch46-release.md) |

## トランザクション・ロック・接続プール { #tx }

「付けたのに効かない」と「足りなくて待たされる」と「同時に来て破られる」の3系統です。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `@Transactional` を付けたのに、トランザクションが始まらない（エラー・警告なし）。`tx=false` | 同じクラスの中から `this` 経由で呼んでいる（自己呼び出し）。プロキシ（秘書）を通らない | 別のクラスに切り出す、`TransactionTemplate` で境界をコードに書く。ログに `Creating new transaction` が出るか確認する | [第23章](../part04/ch23-transactional-proxy.md)、[第29章](../part05/ch29-scheduling.md) |
| `private`／`final`／`static` なメソッドに `@Transactional` を付けた | プロキシが介入できないので、付けても効かない | 公開メソッドに付ける | [第23章](../part04/ch23-transactional-proxy.md) |
| `IOException` を投げたのに、`polls` に1行残った（コミットされた） | checked 例外は既定でコミットされる。ロールバックされるのは `RuntimeException` と `Error` | 業務エラーは `RuntimeException` のサブクラスで表す。checked なら `rollbackFor = Exception.class` を意識して決める | [第23章](../part04/ch23-transactional-proxy.md) |
| `catch (Exception e) { log.warn(...); }` のあと、半端なデータが残る／何も保存されないのに成功を返す | トランザクション内の例外を握りつぶした。(a) 半端なデータがコミットされる、(b) 保存されないのに成功 | 立ち直れると言える例外だけ catch する。立ち直れないなら再スローする | [第23章](../part04/ch23-transactional-proxy.md) |
| `@Transactional` の中で `CompletableFuture`・`@Async`・`parallelStream()` を使ったら、ロールバックしても INSERT が残った | 別スレッドの DB 操作は、元のトランザクションの外で、別の接続で動く（トランザクションはスレッドに紐づく） | トランザクションの中で別スレッドに DB 操作を任せない | [第24章](../part04/ch24-connection-pool.md)、[第29章](../part05/ch29-scheduling.md) |
| `ERROR:  current transaction is aborted, commands ignored until end of transaction block` | トランザクションの途中で SQL がエラーになると、そのトランザクションは中断状態になり、以降の SQL がすべて拒否される。`COMMIT` と打っても `ROLLBACK` になる | 例外を握りつぶして続行しない。UNIQUE 違反を取り直すなら、トランザクションの外（`TransactionTemplate` で1回ごと）で行う | [第22章](../part04/ch22-transactions.md)、[第19章](../part03/ch19-jdbcclient.md) |
| `org.springframework.transaction.UnexpectedRollbackException`／`Transaction rolled back because it has been marked as rollback-only`／`Global transaction is marked as rollback-only but transactional code requested commit` | 内側（`REQUIRED`）が失敗し、外側が catch して続行した。同じトランザクションに参加しているので、内側の失敗は外側の失敗にもなる | 内側の失敗を握りつぶさない。別のトランザクションが必要なら `REQUIRES_NEW`（ただし気軽に使わない） | [第23章](../part04/ch23-transactional-proxy.md) |
| `ERROR: cannot execute INSERT in a read-only transaction`（`UncategorizedSQLException`） | `@Transactional(readOnly = true)` のメソッドで書いた。`BEGIN READ ONLY` が流れ、PostgreSQL が書き込みを拒否する | 書くメソッドには `readOnly` を付けない | [第23章](../part04/ch23-transactional-proxy.md) |
| `HikariPool-1 - Connection is not available, request timed out after 30000ms`／`(total=10, active=10, idle=0, waiting=37)` | コネクションプールの枯渇。`active` が `total` と同じで `idle=0`、`waiting` が増えている。CPU も DB も暇なのに遅いときに見る | トランザクションを短くする。トランザクションの中で外部通信（メール・決済 API・Slack）をしない。プールを闇雲に増やさない | [第24章](../part04/ch24-connection-pool.md) |
| `HikariPool-1 - Connection is not available, request timed out after 2001ms (total=2, active=2, idle=0, waiting=1)`（`REQUIRES_NEW` を使う処理を2本同時に呼ぶ） | 外側が接続を握ったまま、内側が2本目を要求する。プールが小さいと互いに待ち合う（自己デッドロック） | `REQUIRES_NEW` を気軽に使わない。業務更新と同じトランザクションにする | [第24章](../part04/ch24-connection-pool.md)、[第23章](../part04/ch23-transactional-proxy.md) |
| `CannotGetJdbcConnectionException: Failed to obtain JDBC Connection` | プール枯渇で接続を取れなかった。Spring が上の Hikari のメッセージを包んで投げる | 括弧の中（`total`・`active`・`idle`・`waiting`）で診断する | [第24章](../part04/ch24-connection-pool.md) |
| `Unable to obtain connection from database: HikariPool-1 - Connection is not available ... (total=1, active=1,`（アプリが起動しない） | プールを1本にした。Flyway が起動時に DB 接続を2本使う（プールには起動時に必要な本数という下限がある。プール2本でも起動できなかった） | プールは3本以上にする | [第24章](../part04/ch24-connection-pool.md)、[第45章](../part09/ch45-performance.md) |
| 仮想スレッドにしたのに DB が詰まる | 仮想スレッドで増えるのはスレッドで、DB 接続の数は増えない | プールを闇雲に大きくせず、DB が同時に処理できる量から決める | [第24章](../part04/ch24-connection-pool.md) |
| 参加者が上限の50人を超えた（51人、69人など） | 「数える → 足す」の間に別のトランザクションが割り込んだ（check-then-act）。トランザクションを付けるだけでは防げない | 数える前に親の行を `SELECT ... FOR UPDATE` でロックし、鍵を取ってから数え直す | [第25章](../part04/ch25-locking.md)、[第9章](../part01/ch09-threads.md) |
| 新しい入口（QRコード参加など）を足したら、上限を破られる | 新しい入口がロックの規則を知らない。テストは1人ずつなので気づけない | 上限判定を1つのメソッドに集約する。入口ごとに並行性テストを書く | [第25章](../part04/ch25-locking.md) |
| `ERROR:  could not serialize access due to concurrent update`（SQLSTATE `40001`） | REPEATABLE READ で、自分が読んだあとに他が更新してコミットした行を更新しようとした | トランザクション全体をやり直す（有限回） | [第26章](../part04/ch26-concurrency-testing.md) |
| `ERROR:  could not serialize access due to read/write dependencies among transactions` | SERIALIZABLE で DB が競合を検出し、コミット時に片方を失敗させた | 失敗した側をトランザクションごと最初からやり直す。利用者に 500 を返さない | [第26章](../part04/ch26-concurrency-testing.md) |
| `ERROR:  deadlock detected`（SQLSTATE `40P01`、Spring では `PessimisticLockingFailureException`） | 2つのトランザクションが逆の順序で鍵を取り、互いを待った | 複数の鍵を取る処理は、取る順序を固定する（QuickVote は冪等性の行 → `polls` の行） | [第26章](../part04/ch26-concurrency-testing.md)、[第48章](../part10/ch48-capstone.md) |
| `ERROR:  duplicate key value violates unique constraint "votes_poll_participant_key"`（同じ人の投票が同時に来た） | 2回目の INSERT が1回目のコミットを待ち、UNIQUE 違反になる（二重に入ることはない） | 衝突した2回目を失敗ではなく更新にしたいなら `ON CONFLICT DO UPDATE` | [第26章](../part04/ch26-concurrency-testing.md) |
| `ERROR:  canceling statement due to statement timeout`（SQLSTATE 57014） | `statement_timeout` を超えた SQL が DB に打ち切られた | タイムアウトを全部決める（接続・文・ロック）。待ちきれなかったエラーを 500 と区別するかは設計の判断 | [第45章](../part09/ch45-performance.md) |
| `FATAL:  terminating connection due to idle-in-transaction timeout` | `idle_in_transaction_session_timeout` を超えて、トランザクションを開いたまま何もしない接続が DB に切られた | 外部通信の待ちでトランザクションを握りっぱなしにしない | [第45章](../part09/ch45-performance.md) |
| ロックの待ちが `HTTP 500`（`INTERNAL_ERROR`）で返る（SQLSTATE 57014、55P03） | ロックのタイムアウトが、想定外の例外として扱われている | 「混んでいる、あとで試して」は 503 や 409 で返す方が親切。設計の判断 | [第45章](../part09/ch45-performance.md)、[第41章](../part08/ch41-sql-vs-jpa.md) |

## 再送・時刻・定期処理 { #schedule }

応答が消える、時刻に依存する、定期的に動く。どれも「普段の開発では再現しない」ものです。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| リトライしたら選択肢が2つできた（保存は成功し、応答だけが消えた） | クライアントが再送のたびに `Idempotency-Key` を新しく作った。サーバーは別の操作として受ける | 1回の操作に1つのキーを割り当て、再送では同じキーを使い回す | [第27章](../part05/ch27-idempotency.md) |
| `422`／`IDEMPOTENCY_KEY_REUSED`（`同じ Idempotency-Key が別の内容のリクエストで使われています。`） | 同じキーで中身が違う。キーの使い方の間違い（クライアントのバグ） | キーは操作ごとに作る。別の投票に同じキーを使い回していないか確認する | [第27章](../part05/ch27-idempotency.md) |
| サービスの中で `Instant.now()`／`new Date()` を直接呼んでいて、締切のテストが書けない | 現在時刻をテストから動かせない | 現在時刻は引数か注入された部品（`Clock`）で受け取る | [第28章](../part05/ch28-time-expiry.md) |
| `ERROR ... TaskUtils$LoggingErrorHandler : Unexpected error occurred in scheduled task` | `@Scheduled` の中で例外が出た。例外は握られてログに出るだけで、スケジュールは止まらない | ログを見て原因を直す。例外メッセージに入力値（`token=SECRET-1`）が入らないようにする | [第29章](../part05/ch29-scheduling.md)、[第43章](../part09/ch43-logging.md) |
| `ERROR ... SimpleAsyncUncaughtExceptionHandler : Unexpected exception occurred invoking async method: void ...ErrorDemo$AsyncWorker.failing()` | `@Async` の中の例外は呼び出し元に届かず、このログに出るだけ | 失敗の通知方法を設計する。自己呼び出しでは `@Async` が効かない点にも注意 | [第29章](../part05/ch29-scheduling.md) |
| `期限切れデータの削除に失敗しました。次回の実行で続きから削除します`（`UncategorizedSQLException`）。他のテーブルの掃除も止まる | 削除が1件の `poison` で失敗し、直列に並べた後ろの掃除に進まない。失敗した回は消せた件数もログに出ない | テーブルごとに try/catch で独立させる、失敗が続いたらアラート、期限切れなのに残っている件数を指標にする | [第29章](../part05/ch29-scheduling.md) |
| 定期処理が2台で二重に動く／アドバイザリロックが効かない | PgBouncer などの外部プーラーを「トランザクション単位」で使うと、接続が文ごとに別のセッションに割り当たり、セッション単位のロックの前提が崩れる | 外部プーラーを入れるときはこの前提を確認する | [第29章](../part05/ch29-scheduling.md) |
| `AFTER_COMMIT` のリスナーが呼ばれず、更新だけが残った | コミットとリスナーの呼び出しの間にサーバーが落ちた。後続の処理だけが失われる | 業務更新と「やるべき仕事」の記録を同じトランザクションで保存し、別の仕組みが拾う設計にする（QuickVote の SSE） | [第29章](../part05/ch29-scheduling.md)、[第37章](../part07/ch37-event-history.md) |

## Security・Cookie・CSRF・CORS { #security }

「誰か分からない」は 401、「権限がない」は 403、CSRF トークンがないのも 403。どの層が拒否したかで見分けます。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| 依存に `spring-boot-starter-security` を足しただけで全 URL が `HTTP/1.1 401`。起動ログに `Using generated security password: ...` ／`WWW-Authenticate: Basic realm="Realm", charset="UTF-8"` | Security を入れると、既定ですべての URL を守る（Basic 認証・生成パスワード）。ブラウザ（`Accept: text/html`）は 302 でログイン画面へ | `SecurityFilterChain` で何を許可するかを書く。Security を入れた＝守られている、ではない | [第31章](../part06/ch31-spring-security.md) |
| `IllegalStateException: Can't configure requestMatchers after anyRequest` | `authorizeHttpRequests` で `anyRequest()` を先に書いた。上から順に判定し、最初に合ったものが勝つ | 広い条件（`anyRequest`）は必ず最後に置く | [第31章](../part06/ch31-spring-security.md) |
| `401` と `403` の使い分けが分からない | 識別できていない人（匿名）→401。識別できている人が権限のない URL に来た→403。CSRF トークンがない・違う→403（`CsrfFilter` が直接拒否） | `ExceptionTranslationFilter` が `AuthenticationEntryPoint`／`accessDeniedHandler` に振り分ける | [第31章](../part06/ch31-spring-security.md) |
| `HTTP 403   {"detail":"CSRFトークンが正しくありません。", ... "code":"CSRF_REJECTED"}` | CSRF トークンがない、または値が違う。Cookie は付いているのにトークンがない | `X-XSRF-TOKEN` ヘッダーにトークンを付けて送る（Cookie `XSRF-TOKEN` の値） | [第34章](../part06/ch34-browser-boundary.md)、[第35章](../part06/ch35-rate-limiting.md) |
| `Access to fetch at '...' from origin '...' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.`／`TypeError: Failed to fetch` | 別オリジンの JavaScript に応答を読ませないブラウザの仕組み（CORS）。サーバーには届いていることがある。単純でない要求は先にプリフライト（`OPTIONS`）で止まり、本体は送られない | 許可するオリジンを必要最小限に指定する。サーバーのログで届いたかを確認する | [第34章](../part06/ch34-browser-boundary.md) |
| CORS を許可したら攻撃側のページが応答を読めた | CORS の設定ミスは、そのまま情報漏洩になる。POST が 403 のままなのは CSRF トークンが別の層で働いているため | 許可するオリジンを絞る。守りは重ねる | [第34章](../part06/ch34-browser-boundary.md) |
| `ERROR ... Servlet.service() for servlet [dispatcherServlet] threw exception`／`org.springframework.security.authorization.AuthorizationDeniedException: Access Denied`／`Unable to handle the Spring Security Exception because the response is already committed.`／`Cannot render error page for request [null] as the response has already been committed.` | SSE が終わるとき、サーブレットコンテナがフィルタチェーンをもう一度通す（非同期ディスパッチ）。自作の認証フィルタが認証情報を保存していないと匿名扱いになる | 認証情報をリポジトリへ明示的に保存する（`saveContext`／`RequestAttributeSecurityContextRepository`）。利用者の画面は無事でも、ログの ERROR は事実 | [第32章](../part06/ch32-anonymous-session.md)、[第36章](../part07/ch36-sse.md) |
| 自作の認証フィルタが2回動く（DB を2回引く、Cookie を2回発行する） | フィルタを `@Component` にすると、Spring Boot が Servlet フィルタとしても自動登録する | 自作の Security フィルタは `@Component` にせず、`SecurityConfig` の中で `new` して `addFilterBefore/After` で足す | [第31章](../part06/ch31-spring-security.md) |
| Cookie を消したら権限を失った | 匿名セッションは Cookie が唯一の手がかり。仕様どおり | 「失くしたらどうなるか」を設計書に書き、画面にも書く | [第32章](../part06/ch32-anonymous-session.md) |
| `X-Visitor-Id: 1` と送ると誰でも主催者になりすませる | 第2部の仮の仕組み。誰でも書き換えられる自己申告で、認証ではない | 第6部で本物の識別（Cookie の訪問者トークン）に置き換える | [第12章](../part02/ch12-request-journey.md)、[第30章](../part06/ch30-authz-design.md)、[第32章](../part06/ch32-anonymous-session.md) |
| 部外者に `403` を返している／「見つかりません」と「権限がありません」でメッセージが違う | 403 は「存在するが許されていない」と聞こえ、存在が漏れる。認可の前に状態（「締切後です」）を見ても漏れる | 部外者には存在しない場合と同じ 404 を返す。認可（404・403）は状態（409）より先 | [第30章](../part06/ch30-authz-design.md) |
| 連番 ID を書き換えるだけで他人の投票が取れる（IDOR）。他人の投票を削除できる | 「その人に見せてよいか」を確かめていない | 公開 ID（UUID）で受け、参加者であることを確かめてから処理する。認可を省かない | [第30章](../part06/ch30-authz-design.md) |
| `429`／`Retry-After: 5`／`RATE_LIMITED`（`試行回数が多すぎます。しばらく待ってからやり直してください。`） | 回数制限（トークンバケット）を超えた。専用のステータス | `Retry-After`（秒）だけ待って再試行する。他のエラーと同じ ProblemDetail の形 | [第33章](../part06/ch33-invites-brute-force.md)、[第35章](../part06/ch35-rate-limiting.md) |
| `/api/%6aoin   404 404 404 404 404 404 404 404 404 404 404 404 404 404`（止まらない） | 回数制限が `request.getRequestURI()` を文字列で比べていて、`%6a` にエンコードした URL をすり抜けた | ルーティングと同じ基準（デコード後・パターン一致）で対象かを判定する | [第33章](../part06/ch33-invites-brute-force.md) |
| `X-Forwarded-For` を偽装すると IP 単位の回数制限をすり抜ける | アプリがインターネットに直接さらされていると、誰でも偽装できる | 信頼できるプロキシが付けた値だけを信用し、プロキシ側で外から来た `X-Forwarded-*` を上書き・削除する | [第34章](../part06/ch34-browser-boundary.md)、[第35章](../part06/ch35-rate-limiting.md) |
| プロキシの後ろで HSTS ヘッダー（`Strict-Transport-Security`）が付かない | TLS 終端で、アプリは HTTP で受けるため HTTPS だと知らない | `server.forward-headers-strategy: framework` にして `X-Forwarded-Proto` を信用する（信頼できるプロキシに限る） | [第34章](../part06/ch34-browser-boundary.md) |
| nginx の後ろで IP 単位の制限が全員で共有される | `getRemoteAddr()` は接続してきた相手の IP。nginx を挟むと全員が nginx の IP に見える | `server.forward-headers-strategy` と信頼するプロキシの設定を入れる | [第38章](../part07/ch38-multi-instance.md)、[第35章](../part06/ch35-rate-limiting.md) |

## SSE・複数台構成 { #sse }

切れる・止まる・届かない。SSE は「ふつうの HTTP の応答が終わらないだけ」なので、プロキシやタイムアウトの知識がそのまま効きます。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `curl: (18) transfer closed with outstanding read data remaining`（60秒ちょうどで切れる） | nginx の `proxy_read_timeout`（既定60秒）より、アプリのハートビート（実験では90秒）が長い。無通信が上限を超えると切られる | ハートビートを60秒より短く（QuickVote の既定は15秒）。または `proxy_read_timeout 1h`。`proxy_buffering off` も入れておく | [第38章](../part07/ch38-multi-instance.md) |
| `heartbeat 送信失敗: AsyncRequestNotUsableException`／`onError AsyncRequestNotUsableException` | クライアントが切れたあとの書き込みの失敗。サーバーが切断に気づくのは次の書き込み（1回目は成功してしまうこともある） | エラーではなく切断の検知。`onCompletion`／`onError` で購読を解除する | [第36章](../part07/ch36-sse.md) |
| 切断された `SseEmitter` がメモリに残り続ける（ログにも出ない） | `Map` や `List` に入れた `SseEmitter` を `onCompletion`／`onError` で外していない | 登録した場所と同じ数だけ解除の場所を作る | [第36章](../part07/ch36-sse.md) |
| 遅い読み手1人のせいで、全員の配信が止まる（`配信 #56 に 27686 ms`） | `SseEmitter.send()` は同期。相手が読まないと OS の送信バッファが埋まり、戻ってこなくなる | 書き込みを専用の配信プールに分け、`sendTimeout` で打ち切る | [第36章](../part07/ch36-sse.md) |
| 250本つなぐと 200 本で頭打ち、残りはヘッダーすら返らない（`/sse/blocking`） | 1接続ごとにリクエストのスレッドを握る作りは、Tomcat の処理スレッドの上限（200）で詰まる | `SseEmitter` はスレッドを手放す作りで、この問題を避けている。仮想スレッドでも書き込みの詰まりは防げない | [第36章](../part07/ch36-sse.md) |
| 停止（`docker stop`）が30秒止まる | SSE は終わらないリクエストなので、graceful shutdown が処理中の完了を待ち続ける | SSE の接続を停止時に閉じる設計にする | [第36章](../part07/ch36-sse.md)、[第45章](../part09/ch45-performance.md) |
| 再接続すると履歴が1件だけ欠ける（連番の1番が消える） | シーケンスで振った連番は、コミット順に見えるとは限らない。「最後に見た番号より大きいもの」という読み方が、遅いトランザクションと速いトランザクションが重なった日に欠ける | 親の行を更新して採番する（QuickVote の直し方）。カーソルにシーケンスの値をそのまま使わない | [第37章](../part07/ch37-event-history.md) |
| 履歴が500件を超えると、再送が止まる | ページサイズ（500）の境界で止まるバグ。ページサイズより少ない履歴では絶対に起きないので、ふだんのテストに現れない | ちょうどページサイズ・ページサイズ＋1 のデータでテストする | [第37章](../part07/ch37-event-history.md) |
| 画面が静かに止まる（接続は開いたままで、通知もハートビートも届かない） | 購読の登録（`computeIfAbsent(...).add`）と解除が同時に走り、名簿から外れた集合に追加された（取得・作成と追加の2操作の間は `ConcurrentHashMap` も守らない） | 取得・作成・追加を `compute` の中で1つの操作にする | [第37章](../part07/ch37-event-history.md) |
| 2台構成で、隣のサーバーにつないだ人に届かない | SSE の購読はメモリ上にある。他のインスタンスの更新は見えない | PostgreSQL の `LISTEN`／`NOTIFY` で橋渡しし、履歴は DB に持つ | [第38章](../part07/ch38-multi-instance.md) |
| `LISTEN` の接続が切れていた間の通知が届かない | `NOTIFY` は、その瞬間に LISTEN している接続にだけ届き、保存されない | 再接続時に DB を読み直して追いつく（「通知は合図、正は DB」）。通知が届くことを前提にしない | [第38章](../part07/ch38-multi-instance.md) |
| PgBouncer 経由だと `LISTEN` が成立しない | トランザクション単位のプーリングを経由している | `LISTEN` 専用の接続は、プーラーを通さず直接つなぐ | [第38章](../part07/ch38-multi-instance.md) |

## JPA { #jpa }

JPA のエラーは、SQL の本数と永続コンテキストの寿命で読みます。ログは Hibernate の統計（SQLの本数）を数えます。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| `LazyInitializationException`／`Cannot lazily initialize collection of role 'com.example.jpalab.poll.Poll.options' with key '1' (no session)` | 遅延ロードは永続コンテキスト（Session）が開いている間だけ SQL を出せる。トランザクションが終わったあとで触った | Service の中で DTO まで作る（最も安全）。fetch join／`@EntityGraph` も使える。`open-in-view` を true のままにして隠さない | [第40章](../part08/ch40-n-plus-one.md) |
| `WARN ... JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning` | open-in-view が既定で有効。リクエストの終わりまで DB 接続を握り続け、N+1 も隠れる | `spring.jpa.open-in-view: false` にする。落ちるのは正しい姿 | [第40章](../part08/ch40-n-plus-one.md) |
| 開発中は速いのに本番だけ遅い（SQL が 101 本出る） | N+1 問題。親を取る 1 本のあと、子を取る SQL が親の件数だけ流れる | fetch join、`@EntityGraph`、DTO 射影、バッチフェッチ。SQL の本数をテストで確かめる | [第40章](../part08/ch40-n-plus-one.md) |
| 一覧の件数が合わない（選択肢が0件の投票が消える） | fetch join は `join`（内部結合）、`@EntityGraph` は `left join`（外部結合） | `left join fetch` と書くか、`@EntityGraph` を使う | [第40章](../part08/ch40-n-plus-one.md) |
| コレクションの fetch join に `Pageable` を付けた結果が読めない | ライブラリの版や書き方で挙動が変わる。古い版では全件を読んでからメモリ上で切る | 先に親の ID だけをページングで取り、その ID で fetch join する2段階にする。SQL の本数と件数をテストで確かめる | [第40章](../part08/ch40-n-plus-one.md) |
| `ObjectOptimisticLockingFailureException: Unexpected row count (expected row count 1 but was 0) [update polls set closes_at=?,status=?,title=?,version=? where id=? and version=?]` | 楽観ロックの衝突。`UPDATE` の `WHERE version=?` が、読んだ時点の値と合わず 0 件になった | 読み直して、やり直すか、利用者に知らせる。version を画面に持たせて往復させる | [第40章](../part08/ch40-n-plus-one.md) |
| `ObjectOptimisticLockingFailureException: Row was already updated or deleted by another transaction for entity [...]`（detached を `save()`） | 編集画面を開いた時点の Entity を保存した場合も、`merge` で version が比較されて同じように検出される | 同上 | [第40章](../part08/ch40-n-plus-one.md) |
| `DataIntegrityViolationException ← ConstraintViolationException`（確認してから挿入した） | 「あるか確認 → なければ入れる」の間に別の挿入が割り込み、UNIQUE が止めた。票が2行になることはないが、利用者に 500 が見える | `ON CONFLICT` でエラーではなく更新にする | [第41章](../part08/ch41-sql-vs-jpa.md) |
| `poll.toString()` が `StackOverflowError`。ログに `[FAILED toString()]` | 双方向関連の `toString` が互いを呼び合う。Lombok の `@Data`／`@ToString` も同じ | `toString` に関連（コレクション・親への参照）を含めない | [第41章](../part08/ch41-sql-vs-jpa.md) |
| Entity を `Set` に入れると1件に潰れる／保存すると `HashSet` から見つからなくなる | `id` だけで `equals`／`hashCode` を実装した。保存前は `id` が `null` で互いに等しく、保存で `hashCode` が変わる | `id` が `null` の間は等しくしない。`hashCode` は定数か不変の業務キー。または実装しない | [第41章](../part08/ch41-sql-vs-jpa.md) |
| `save()` を呼んでいないのに `UPDATE` が出る／Controller で書き換えたのに保存されない | 管理中（managed）の Entity は変更が検出される。トランザクションの外の Entity（detached）は精算されない | 更新するメソッドに `@Transactional` を付け、その中で「読む → 変える」まで完結させる | [第39章](../part08/ch39-jpa-basics.md) |

## Docker・運用（ログ・メトリクス・停止） { #ops }

アプリのログに何も出ないまま消える、ヘルスチェックが遅い、止めたときに処理が落ちる、という運用の事故です。

| 症状（実際のメッセージ） | 原因 | 対処 | 詳しい章 |
| --- | --- | --- | --- |
| アプリのログに何も出ないまま消えた（`OOMKilled=true ExitCode=137`） | コンテナのメモリ制限を超え、OS のカーネルがプロセスを強制終了した（137 = 128 + SIGKILL の 9）。Java の `OutOfMemoryError` とは別物 | アプリのログではなく `docker inspect -f 'OOMKilled={{.State.OOMKilled}} ExitCode={{.State.ExitCode}}' <コンテナ>` を見る | [第42章](../part09/ch42-docker.md) |
| 起動時に `Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=75` と出る | エラーではない。JVM が環境変数から追加オプションを読んだときの正常な表示 | `Dockerfile` の `ENV` で設定したものが読まれている | [第42章](../part09/ch42-docker.md) |
| `COPY . .` を最初に書いたら、どのファイルを直しても毎回全部ビルドし直し | 下の層を変えると、その上の層は全部作り直しになる | 変わりにくい順に下から積む。マルチステージビルドでコンパイラやソースを最終イメージに持ち込まない | [第42章](../part09/ch42-docker.md) |
| コンテナが `unhealthy` なのに再起動しない | Docker（と Compose）は `unhealthy` を表示と依存関係の判定に使うだけ。再起動はオーケストレーター（Kubernetes の liveness）の仕事 | 何を liveness にし何を readiness にするかを慎重に決める | [第42章](../part09/ch42-docker.md)、[第44章](../part09/ch44-metrics.md) |
| DB を止めても `docker ps` が約100秒 `healthy` のまま。readiness が 503 を返すまで 30 秒かかる | 健康チェックが接続を取ろうとして HikariCP の `connection-timeout`（既定30秒）まで待つ。compose の `timeout: 3s` に毎回失敗し、`retries: 6` で `unhealthy` になる | `spring.datasource.hikari.connection-timeout` を短く（たとえば 3 秒）する。ヘルスチェックの速さも設計の対象 | [第44章](../part09/ch44-metrics.md)、[第45章](../part09/ch45-performance.md) |
| `management.server.port` を分けたら、本体のポートで `/actuator/**` が 404 になった | 管理ポートを分けると `/actuator/**` は管理ポートだけになる。公開するエンドポイントの指定は1つ | ロードバランサーやコンテナのヘルスチェックも管理ポート（9090）に向ける | [第44章](../part09/ch44-metrics.md) |
| `stop_grace_period` を短くしたら、停止時に処理中のリクエストが落ちた | 実行基盤の猶予が、アプリの待ち上限（`timeout-per-shutdown-phase`）より短いと、待っている最中に強制終了される | タイムアウトを「処理は最長でも数秒」と決め、猶予をそれより長くする | [第45章](../part09/ch45-performance.md) |
| `WARN HikariPool-1 - Failed to validate connection`／`DataSource health check failed`（DB 停止中） | DB が止まっていて、接続の検証とヘルスチェックが失敗している。DB が戻れば約10秒で自動復旧し、アプリの再起動は不要 | DB を戻す。liveness は 200 のまま、readiness が 503 になるのは設計の意図どおり | [第43章](../part09/ch43-logging.md)、[第44章](../part09/ch44-metrics.md) |
| `ERROR [dispatcherServlet] Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception`（`requestId` がない、応答が ProblemDetail でない） | Controller より手前（Security のフィルタが Cookie から訪問者を DB で引く段階）の例外。`SafeThrowable` を通らず、生のまま出る | 例外がログに届く道は全部で何本あるか、とレビューで問う。最外のフィルタで捕まえるか、ログ出力側で許可リストにする | [第43章](../part09/ch43-logging.md) |
| `ERROR TransactionInterceptor  Application exception overridden by rollback exception` | Spring のトランザクション管理がロールバック時に出す ERROR。メッセージに何が入るかはフレームワークとドライバ次第 | 別の DB エラー（制約違反など）では生のメッセージが出うる。ログへの経路を全部確認する | [第43章](../part09/ch43-logging.md) |
| ログに別のリクエストの ID が混ざる | `MDC`／`ThreadLocal` の消し忘れ（Filter の `finally`）。スレッドはプールで使い回される | `chain.doFilter` が例外を投げても消えるよう、`finally` で `MDC.remove` する | [第12章](../part02/ch12-request-journey.md)、[第43章](../part09/ch43-logging.md) |
| `@Scheduled`／SSE 配信のスレッドのログに `requestId` がない | MDC はスレッド専用で、リクエストを処理するスレッドを離れた処理には相関 ID が付かない | 非同期処理に引き継ぎたいなら、自分で引き継ぐ仕組みを足す | [第43章](../part09/ch43-logging.md) |
| ログや例外メッセージに入力値（選択肢の本文など）が残る | 原因側の例外（DB ドライバ、JSON パーサ）が作るメッセージは自分では制御できない | 自分のメッセージに入れないだけでは足りない。許可リスト方式（`SafeThrowable`）で出力する | [第14章](../part02/ch14-validation-errors.md)、[第43章](../part09/ch43-logging.md) |
| バックアップを取っているのに、復元できない | 権限が足りない、バージョンが合わない、ダンプが空など。取る側は成功して復元側で初めて分かる | 復元を一度も試していないバックアップは証明にならない。実際に復元して RTO を見積もる | [第46章](../part09/ch46-release.md) |

---

## 関連する付録

- 古い記事やAIのコードが動かないときは、[Spring Boot 3→4 差分カード](boot3-to-4.md)
- 知らない用語が出てきたときは、[用語集](glossary.md)
