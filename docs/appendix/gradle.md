# 付録 Gradle（build.gradle.kts）対訳

<div class="goal-box" markdown>

この付録でできるようになること

- QuickVote の `pom.xml` を、Gradle の `build.gradle.kts` に**要素ごとに**読み替えられる
- `./mvnw` のコマンドを `./gradlew` のコマンドに置き換えられる
- 現場で Gradle のプロジェクトに出会っても、依存・テスト・jar 作成の場所が分かる

</div>

この教材は Maven で進めます。Gradle は、Android 開発や、新しめの Spring プロジェクトで出会う確率が高いビルドツールです。**書けなくてよいので、読めるようになる**のが目標です。

!!! note "確認したこと"
    - Spring Initializr（Spring Boot 4.1.2、Java 25、Gradle - Kotlin）で、QuickVote と同じ依存のプロジェクトを取得し、`./gradlew build -x test` が成功しました（Gradle 9.8.2）
    - QuickVote の `pom.xml` を `build.gradle.kts` に移したものに QuickVote の `src` を載せて、`bootJar`・速いテスト42件・統合テスト1クラス（7件）が成功しました
    - 実験は `code/labs/appendix-gradle/` にあります

## 1. 全体像：同じ仕事を、XMLかKotlinで書く

| 役割 | Maven | Gradle（Kotlin DSL） |
| --- | --- | --- |
| 設定ファイル | `pom.xml`（XML・宣言的） | `build.gradle.kts`（Kotlinのコード） |
| プロジェクト名 | `pom.xml` の `<artifactId>` | `settings.gradle.kts` の `rootProject.name` |
| ラッパー | `mvnw` / `mvnw.cmd` | `gradlew` / `gradlew.bat` ＋ `gradle/wrapper/` |
| 生成物の置き場 | `target/` | `build/` |
| ローカルキャッシュ | `~/.m2` | `~/.gradle` |
| 考え方 | **フェーズ**（`test` → `package` → `verify`）を順に進む | **タスク**の依存グラフ（`build` は `check` と `assemble` に依存） |

TypeScriptの読者向けに言い換えると、`pom.xml` は `package.json` に近い「データ」で、`build.gradle.kts` は `vite.config.ts` に近い「コードで書く設定」です。どちらも `npm` に当たる依存解決を担います。

## 2. QuickVote の pom.xml を移す

### 2.1 親・プラグイン・プロジェクト情報

=== "pom.xml"

    ```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.2</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>quickvote</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <description>QuickVote: …</description>
    <properties>
        <java.version>25</java.version>
    </properties>
    ```

=== "build.gradle.kts"

    ```kotlin title="code/labs/appendix-gradle/build.gradle.kts"
    plugins {
        java
        id("org.springframework.boot") version "4.1.2"
        id("io.spring.dependency-management") version "1.1.7"
    }

    group = "com.example"
    version = "0.0.1-SNAPSHOT"
    description = "QuickVote: …"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    repositories {
        mavenCentral()
    }
    ```

| Maven | Gradle | 解説 |
| --- | --- | --- |
| `<parent>` の `spring-boot-starter-parent` | `id("org.springframework.boot")` ＋ `io.spring.dependency-management` | 依存の**版を決める役**（BOM）。Gradleでは2つのプラグインが担う |
| `<groupId>` `<version>` | `group` `version` | |
| `<artifactId>` | `settings.gradle.kts` の `rootProject.name = "quickvote"` | |
| `<java.version>25</java.version>` | `java.toolchain.languageVersion = 25` | Gradle は**ツールチェーン**でJDKを指定する。手元のJDKを自動検出 |
| （暗黙の Maven Central） | `repositories { mavenCentral() }` | 取得元を**明示する** |

### 2.2 依存

=== "pom.xml"

    ```xml
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-docker-compose</artifactId>
        <scope>runtime</scope>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>
    ```

=== "build.gradle.kts"

    ```kotlin
    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-webmvc")
        developmentOnly("org.springframework.boot:spring-boot-docker-compose")
        testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
    ```

1依存が**1行**に収まります。`groupId:artifactId` をコロンでつなぎ、版は書きません（BOM が決める点はMavenと同じです）。

| Maven の `<scope>` | Gradle の構成 | 意味 |
| --- | --- | --- |
| `compile`（既定） | `implementation` | コンパイルにも実行にも使う |
| `runtime` | `runtimeOnly` | 実行時だけ。コードから直接参照しない（例：JDBCドライバ） |
| `test` | `testImplementation` | テストだけ |
| `provided` | `compileOnly` | コンパイル時だけ |
| `runtime` ＋ `<optional>true</optional>` | `developmentOnly` | 開発時の実行だけ。jar には入らない |

!!! warning "QuickVote の PostgreSQL ドライバ"
    Initializr は PostgreSQL ドライバを `runtimeOnly` で出力します。QuickVote はコードから `PGConnection` を直接使う（LISTEN/NOTIFY）ので、`pom.xml` で compile スコープに変えています。Gradle でも **`implementation("org.postgresql:postgresql")`** に変えます。こうした「Initializr の既定から手で変えた箇所」は、移すとき必ず確認します。

`testRuntimeOnly("org.junit.platform:junit-platform-launcher")` は、Gradle で JUnit 6 を動かすために Initializr が足す行です。Mavenには対応する行がありません。

### 2.3 テストとjar

QuickVote の `pom.xml` は、`*Test` を surefire で、`*IT` を failsafe で動かします。Gradle には failsafe 相当が無いので、**テスト用のタスクをもう1つ作ります**。

=== "pom.xml"

    ```xml
    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>…spring-boot-maven-plugin…</plugin>
            <plugin>
                …maven-failsafe-plugin…
                <goals>
                    <goal>integration-test</goal>
                    <goal>verify</goal>
                </goals>
            </plugin>
        </plugins>
    </build>
    ```

=== "build.gradle.kts"

    ```kotlin
    tasks.withType<Test> { useJUnitPlatform() }

    tasks.test { exclude("**/*IT.class") }      // 速いテストだけ

    val integrationTest = tasks.register<Test>("integrationTest") {
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        include("**/*IT.class")                 // Testcontainers を使う *IT
        shouldRunAfter(tasks.test)
    }
    tasks.check { dependsOn(integrationTest) }

    tasks.bootJar { archiveFileName = "quickvote.jar" }   // <finalName> に当たる
    ```

| Maven | Gradle | 解説 |
| --- | --- | --- |
| `spring-boot-maven-plugin` | `org.springframework.boot` プラグイン（§2.1 に含まれる） | jar に依存を同梱する |
| `<finalName>` | `tasks.bootJar { archiveFileName = … }` | Dockerfile から jar 名で参照するため |
| surefire（`*Test`） | `tasks.test` | |
| failsafe（`*IT`） | `integrationTest` タスクを自作 | `verify` ではなく `check` に組み込む |

!!! note "Kotlin DSL の書き方の注意"
    `val x by tasks.registering(Test::class) { … }` と書いた古い形は、Gradle 9.8.2 では非推奨の警告が出ました（`tasks.register<Test>("名前") { … }` に置き換えると消えます）。AIが出す Gradle のコードは、この種の古さを含みやすいです。

## 3. コマンドの対訳

| やりたいこと | Maven | Gradle |
| --- | --- | --- |
| 起動 | `./mvnw spring-boot:run` | `./gradlew bootRun` |
| 起動（引数つき） | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` | `./gradlew bootRun --args='--server.port=8081'` |
| 速いテスト | `./mvnw test` | `./gradlew test` |
| 全テスト | `./mvnw verify` | `./gradlew check` |
| 統合テスト1クラス | `./mvnw verify -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ConcurrencyIT` | `./gradlew integrationTest --tests '*ConcurrencyIT'` |
| jar を作る | `./mvnw package` | `./gradlew bootJar` |
| テストを飛ばしてビルド | `./mvnw package -DskipTests` | `./gradlew build -x test` |
| 掃除 | `./mvnw clean` | `./gradlew clean` |
| 依存の木 | `./mvnw dependency:tree` | `./gradlew dependencies --configuration runtimeClasspath` |
| 特定の依存の経路 | `./mvnw dependency:tree -Dincludes=…` | `./gradlew dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` |
| 設定・タスクを確認する | `./mvnw help:effective-pom`（親POMを展開した設定） | `./gradlew tasks` |

Windows では `./gradlew` を `.\gradlew.bat` に読み替えます。**`-x test` の意味は「`test` タスクを実行しない」**です。`build -x test` と書くと、QuickVote のように `integrationTest` を `check` に組み込んでいる場合は、`integrationTest` が動きます。Dockerなしで確かめるときは `-x test -x integrationTest` と書きます。

```text
$ ./gradlew build -x test -x integrationTest
BUILD SUCCESSFUL in 6s
$ ls build/libs
quickvote-0.0.1-SNAPSHOT-plain.jar   ← 依存を含まない普通のjar
quickvote.jar                        ← 実行できるjar（bootJar）
```

`-plain.jar` は、Maven では見ない成果物です。実行は `java -jar build/libs/quickvote.jar` です（Dockerfile が参照するのはこちら）。

## 4. ラッパーとファイル構成

```text
quickvote/
├── build.gradle.kts            ← pom.xml
├── settings.gradle.kts         ← rootProject.name だけ
├── gradlew / gradlew.bat       ← mvnw / mvnw.cmd
├── gradle/wrapper/
│   ├── gradle-wrapper.jar      ← Maven の wrapper(only-script) には無い jar
│   └── gradle-wrapper.properties   ← 使う Gradle の版（9.8.2）
└── src/…                       ← Mavenと同じ構成
```

- ✅ `src/main/java`・`src/test/java`・`src/main/resources` の配置は、Mavenと**同じ**です。ソースは移さなくて構いません
- `gradle-wrapper.properties` の `distributionUrl` が、プロジェクトが使う Gradle の版を固定します。初回の `./gradlew` はその版をダウンロードします
- `.gradle/` と `build/` は `.gitignore` に入れます（Initializr が生成する `.gitignore` に入っています）

## 5. Maven と違って戸惑う点

| 点 | 内容 |
| --- | --- |
| **デーモン** | 2回目以降が速いのは、バックグラウンドの Gradle デーモンが残るため。止めるには `./gradlew --stop` |
| **設定がコード** | `build.gradle.kts` の間違いは、Kotlinのコンパイルエラーとして出る。XMLの閉じ忘れとは別種のエラー |
| **Groovy版もある** | `build.gradle`（Groovy DSL）は `implementation 'a:b'` とクォートで書く。Kotlin版は `implementation("a:b")`。**AIの出力はどちらも出る**ので、拡張子で見分ける |
| **依存の解決順** | Maven は「近い宣言が勝つ」、Gradle は「最も高い版が勝つ」が基本。ただし Spring Boot のプラグインが BOM で揃える |
| **増分ビルド** | 変更のないタスクは `UP-TO-DATE` と表示されて飛ばされる |

## 6. どちらを使うか

- **本教材は Maven**です。`pom.xml` が「データ」として読めること、BOM（親POM）が版を決める仕組みが素直に見えることが理由です
- Gradle が優れる場面：ビルドが複雑で、タスクの依存を細かく書きたいとき。Android や Kotlin のプロジェクト
- どちらも**同じ jar ができ、同じ Spring Boot が動きます**。ビルドツールの差は、アプリの挙動に影響しません

## 7. 演習

??? question "確認問題1：スコープの読み替え"
    次の Maven の依存を、Gradle の1行に書いてください。
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    ```

??? success "解答"
    `testImplementation("org.testcontainers:testcontainers-postgresql")`。版は Spring Boot の BOM が決めるので書きません。

??? question "確認問題2：jar 名"
    Gradle で `./gradlew bootJar` を実行したら、`quickvote-0.0.1-SNAPSHOT.jar` ができました。Dockerfile が期待する `quickvote.jar` にするには？

??? success "解答"
    `tasks.bootJar { archiveFileName = "quickvote.jar" }` を足します。Maven の `<finalName>` と同じ役割です。

??? question "応用：実行して確かめる"
    `code/labs/appendix-gradle/` で `./gradlew tasks --group verification` を実行し、`test` と `integrationTest` の2つが表示されることを確認してください。`./gradlew test` と `./gradlew integrationTest` の違いを、Maven の surefire／failsafe に対応づけて説明してください。

??? success "解答"
    `test` は `*IT` を除いた速いテスト（surefire の `test` フェーズ）、`integrationTest` は Testcontainers を使う `*IT`（failsafe の `verify` フェーズ）です。`./gradlew check` は両方を実行します。

??? question "説明問題：現場でこう聞かれる"
    「このプロジェクトは Gradle ですが、教材は Maven でした。何から読めばいいですか？」200字程度で答えてください。

??? success "解答"
    例：まず `build.gradle.kts` の `dependencies` ブロックを読みます。`implementation` が Maven の compile スコープ、`testImplementation` が test スコープに当たり、版は書かれていません（BOMが決めています）。次に、`tasks` ブロックでテストと jar の設定を確認します。`src/` の構成は Maven と同じなので、アプリのコードの読み方は変わりません。
