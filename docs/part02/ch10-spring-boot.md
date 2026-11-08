# 第10章 Spring Bootとは：最初のAPIと起動の裏側

<div class="goal-box" markdown>

この章でできるようになること

- Java・Spring Framework・Spring Boot・Tomcat・JVM の関係を、層の図で説明できる
- Spring Initializr で雛形を作り、`pom.xml` を読んで「なぜバージョンを書かなくてよいのか」を説明できる
- 最初のAPIを書いて起動し、起動ログを1行ずつ読める
- 自動構成が「条件付きの設定（if文）」であることを、`--debug` のレポートで確かめられる
- AIが出した `pom.xml` や起動エラーを見て、古い書き方や置き場所の誤りを指摘できる

</div>

## 1. 問いかけ：`java -jar` の1行でサーバーが立つのはなぜか

Node で API を作るなら、`npm i express` で入れて、`app.get(...)` でルートを書き、`app.listen(3000)` で待ち受けます。JSON の扱い、ログ、ポートの設定も、必要に応じて自分で足していきます。

Spring Boot では、次の1行で HTTP サーバーが立ちます。

```bash
java -jar target/hello-0.0.1-SNAPSHOT.jar
```

Tomcat（HTTP サーバー）のインストールも、JSON 変換の設定も、ポートの指定もしていないのに、`GET /api/hello` に JSON が返ります。この章ではまずアプリを動かし、そのあとで、何をいつ誰が決めたのかをログとレポートで確かめます。第2部の最初の章なので、ここで「Spring Boot とは何か」の全体像をつかんでおいてください。

## 2. 層で見る：Spring Boot は何の上に載っているか

Spring に初めて触れると、Java、Spring、Spring Framework、Spring Boot、Tomcat、Servlet と名前がたくさん出てきて混乱します。層で整理すると、1枚に収まります。

![Javaサーバーの層構造](../assets/diagrams/ch10-layers.svg)

!!! note "用語：Spring Framework と Spring Boot"
    Spring Framework は、サーバーアプリを作るための部品集です。DI コンテナ（第11章）、Web（Spring MVC）、検証、テスト支援などを持っています。Spring Boot は、その Spring Framework を設定なしで動く状態にまとめる仕組みで、起動、自動構成、スターター、設定ファイル、依存のバージョン表（BOM）を提供します。
    本教材で「Spring」と書くときは、この2つを合わせたものを指します。

!!! note "用語：Servlet / Tomcat"
    Servlet は、HTTP リクエストを受け取って応答を返す、Java 標準の仕組みです（`jakarta.servlet.*`）。Tomcat は、Servlet を動かす HTTP サーバーで、Servlet コンテナとも呼ばれます。昔は Tomcat を別にインストールして、そこにアプリを配置していました。Spring Boot は Tomcat をアプリの中に埋め込むので、`java -jar` だけで起動できます。

### 例え：レストランと電源タップ

例えを2つ使います。どちらもこの章の中だけで使います。

![レストラン：ホール係・厨房・食材庫・注文票](../assets/illustrations/restaurant.webp)

レストランは、Web アプリの層にあたります。注文票（リクエスト）が入口から来て、ホール係（Controller）が受け、厨房（Service）が調理し、食材庫（Repository / DB）から材料を取り出します。次章以降も、各層の役割をこの例えで話します。

!!! warning "この例えが外れる点"
    実際のサーバーでは、1人の店員（スレッド）が、受付から配膳まで1つの注文を最後まで担当します。ホール係と厨房係が別人で、注文票を手渡しているわけではありません（第12章で動画で見ます）。また、層の名前は慣習であって、Spring が強制するものではありません。

![配線済みの電源タップ](../assets/illustrations/power-strip.webp)

電源タップは、Spring Boot にあたります。Boot は、雛形を生成して終わりのツールではなく、あらかじめ配線済みのタップです。差し込み口に何かを挿すと、配線が自動でつながります。Web 用のスターターを足せば Tomcat と JSON 変換がつながり、DB 用のスターターを足せばコネクションプールがつながります。

!!! warning "この例えが外れる点"
    タップは何もしなくても電気が来るだけですが、Boot は既定の判断を持っています（ポートは 8080、日時の JSON は ISO 8601、など）。その判断は、読者が何かを定義すると引っ込みます（本章の 7 節）。また、雛形生成ツールとは違って、Boot が配線した結果はソースコードとして生成されず、実行時に決まります。何が配線されたかは、見に行って確かめる必要があります。

## 3. 手を動かす①：Initializr で雛形を作る

Spring Initializr は、プロジェクトの雛形を作る Web サービスです（`https://start.spring.io`）。ブラウザでも `curl` でも使えて、IntelliJ IDEA の「新規プロジェクト」からも同じものが呼ばれます。

```bash
curl https://start.spring.io/starter.zip \
  -d bootVersion=4.1.2 -d javaVersion=25 -d type=maven-project \
  -d groupId=com.example -d artifactId=hello -d name=hello \
  -d packageName=com.example.hello -d dependencies=web \
  -o hello.zip
unzip hello.zip -d hello
```

展開すると、次のファイルができます（教材では `code/labs/ch10-hello-boot/` に置いてあります）。

```text
hello/
├── pom.xml                                   ← 依存とビルドの設定（package.json に相当）
├── mvnw / mvnw.cmd                           ← Maven Wrapper（./mvnw で Maven を実行）
├── .mvn/wrapper/maven-wrapper.properties     ← Maven のバージョン指定
└── src/
    ├── main/java/com/example/hello/HelloApplication.java   ← main() を持つクラス
    ├── main/resources/application.properties                ← 設定ファイル（本教材では yaml にする）
    └── test/java/com/example/hello/HelloApplicationTests.java
```

### `pom.xml` を読む：package.json との違い

`pom.xml` は XML ですが、データとして読めば怖くありません。重要な部分だけを抜き出します。

```xml title="pom.xml（抜粋）"
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.2</version>                       <!-- (1)! -->
</parent>
<properties>
    <java.version>25</java.version>                <!-- (2)! -->
</properties>
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>   <!-- (3)! -->
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>                        <!-- (4)! -->
    </dependency>
</dependencies>
```

1. 親 POM です。この1行で、数百の依存ライブラリのバージョン表（BOM）を取り込みます。ここに書くのは Spring Boot のバージョンだけです
2. コンパイルに使う Java のバージョンです
3. スターターです。「Web を作るのに必要なもの一式」にあたる依存で、中身は下の `dependency:tree` で見ます
4. `scope` が `test` の依存は、テストのときだけ使います（`package.json` の `devDependencies` に近い）

!!! note "用語：BOM（Bill of Materials）"
    どのライブラリのどのバージョンを組み合わせれば動くかを、動作確認済みの組として1つの表にしたものです。Spring Boot の親 POM が持っています。
    表に載っているライブラリは `<version>` を書かなくてよく、Spring Boot 4.1.2 を指定するだけで、Tomcat 11.0.27、Jackson 3.1.7、Spring Framework 7.0.10 などの組み合わせが決まります。

npm と比べると、次のようになります。

| | npm（package.json） | Maven（pom.xml） |
| --- | --- | --- |
| バージョンの書き方 | `"express": "^4.19.0"`（範囲） | `<version>4.1.2</version>`（ぴったり1つ） |
| 実際のバージョンを固定するもの | lockfile（package-lock.json） | BOM（親 POM のバージョン表）。lockfile は標準ではない |
| 推移的な依存 | node_modules に入る。バージョンが重なることがある | 1つに決まる（同じライブラリは1バージョン） |
| 更新の単位 | パッケージごとに上げる | Spring Boot のバージョンを上げると全部が一緒に変わる |

Maven は、範囲を指定せず、動作確認済みの組を丸ごと選ぶ考え方です。個別のバージョンを自分で書き始めたら、BOM を外れて組み合わせが保証されなくなっているサインです。

### スターターの中身を見る

```bash
./mvnw dependency:tree -Dscope=compile
```

```text
com.example:hello:jar:0.0.1-SNAPSHOT
\- org.springframework.boot:spring-boot-starter-webmvc:jar:4.1.2:compile
   +- org.springframework.boot:spring-boot-starter:jar:4.1.2:compile
   |  +- org.springframework.boot:spring-boot-starter-logging:jar:4.1.2:compile
   |  |  +- ch.qos.logback:logback-classic:jar:1.5.38:compile
   |  +- org.springframework.boot:spring-boot-autoconfigure:jar:4.1.2:compile
   |  \- org.yaml:snakeyaml:jar:2.6:compile
   +- org.springframework.boot:spring-boot-starter-jackson:jar:4.1.2:compile
   |  \- org.springframework.boot:spring-boot-jackson:jar:4.1.2:compile
   |     \- tools.jackson.core:jackson-databind:jar:3.1.7:compile
   +- org.springframework.boot:spring-boot-starter-tomcat:jar:4.1.2:compile
   |  \- org.apache.tomcat.embed:tomcat-embed-core:jar:11.0.27:compile
   \- org.springframework.boot:spring-boot-webmvc:jar:4.1.2:compile
      \- org.springframework:spring-webmvc:jar:7.0.10:compile
```

（長いので抜粋しています。実際は約35行です。）1つの `spring-boot-starter-webmvc` が、ログ（Logback）、JSON（Jackson）、HTTP サーバー（Tomcat）、Spring MVC をまとめて取り込んでいます。これがスターターです。

!!! warning "バージョン注意：Boot 4 でスターター名が変わった"
    古い記事や AI が出した `pom.xml` には、次のように書かれていることがあります。
    ```xml
    <artifactId>spring-boot-starter-web</artifactId>   <!-- Boot 3 までの名前 -->
    ```
    Spring Boot 4 ではスターターが機能ごとに分かれ、Web（Spring MVC）は `spring-boot-starter-webmvc` になりました。テスト用のスターターも機能ごとにあります（`spring-boot-starter-webmvc-test` など）。旧名の `spring-boot-starter-web` は 4.1.2 でも取得できますが非推奨（deprecated）で、`webmvc` への置き換えが案内されています。新しく書くときは `webmvc` を使います。

    | 用途 | Boot 4 のスターター |
    | --- | --- |
    | Web（Spring MVC） | `spring-boot-starter-webmvc` |
    | 入力検証 | `spring-boot-starter-validation` |
    | JDBC | `spring-boot-starter-jdbc` |
    | Web のテスト | `spring-boot-starter-webmvc-test` |
    詳しい差分は、付録の「Spring Boot 3→4 差分カード」にまとめます。

## 4. 手を動かす②：最初のAPIを書く

`HelloApplication.java` は、Initializr が作ったままです。

```java title="src/main/java/com/example/hello/HelloApplication.java"
@SpringBootApplication                                  // (1)!
public class HelloApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloApplication.class, args);   // (2)!
    }
}
```

1. この1つのアノテーションに、3つの意味が入っています（5 節で分解します）
2. ここが入口です。`SpringApplication.run` が Spring を起動し、Tomcat を立ち上げます

API を1つ足します。

```java title="src/main/java/com/example/hello/HelloController.java"
@RestController
public class HelloController {

    @GetMapping("/api/hello")                            // (1)!
    public Hello hello() {
        return new Hello("こんにちは、QuickVote", Instant.now());   // (2)!
    }

    public record Hello(String message, Instant at) {    // (3)!
    }
}
```

1. 「`GET /api/hello` はこのメソッドが担当」という宣言です。Express の `app.get("/api/hello", handler)` に当たります
2. Java のオブジェクト（record）を返すだけです
3. 第4章で学んだ `record` が、そのまま応答の形になります

!!! note "用語：@RestController"
    HTTP リクエストを受けるクラスの目印です。メソッドの戻り値は JSON に変換され、応答の本文になります。同じ形で `@Controller` もありますが、こちらは HTML 画面（テンプレート）用で、本教材では使いません。

起動して、呼びます。

```bash
./mvnw spring-boot:run          # Windows は .\mvnw.cmd spring-boot:run
```

```bash
curl -i localhost:18010/api/hello
```

```text
HTTP/1.1 200
Content-Type: application/json
Content-Length: 79

{"message":"こんにちは、QuickVote","at":"2026-11-07T16:22:18.280562900Z"}
```

`Instant` が UTC の ISO 8601 文字列で出ています。JSON の変換設定は何も書いていないので、これも Boot が決めた既定の判断です（第13章で詳しく見ます）。

## 5. 起動ログを1行ずつ読む

AI が書いたコードを動かして起動に失敗したとき、最初に読むのが起動ログです。正常に起動したときのログを、一度は1行ずつ読んでおきましょう。

```text
 :: Spring Boot ::                (v4.1.2)

01:22:11.739  INFO 53384 --- [hello] [main] HelloApplication      : Starting HelloApplication v0.0.1-SNAPSHOT using Java 25.0.5 with PID 53384 (...)
01:22:11.745  INFO 53384 --- [hello] [main] HelloApplication      : No active profile set, falling back to 1 default profile: "default"
01:22:13.432  INFO 53384 --- [hello] [main] o.s.boot.tomcat.TomcatWebServer  : Tomcat initialized with port 18010 (http)
01:22:13.461  INFO 53384 --- [hello] [main] o.apache.catalina.core.StandardService : Starting service [Tomcat]
01:22:13.462  INFO 53384 --- [hello] [main] o.apache.catalina.core.StandardEngine  : Starting Servlet engine: [Apache Tomcat/11.0.27]
01:22:13.564  INFO 53384 --- [hello] [main] b.w.c.s.WebApplicationContextInitializer : Root WebApplicationContext: initialization completed in 1705 ms
01:22:14.146  INFO 53384 --- [hello] [main] o.s.boot.tomcat.TomcatWebServer  : Tomcat started on port 18010 (http) with context path '/'
01:22:14.171  INFO 53384 --- [hello] [main] HelloApplication      : Started HelloApplication in 3.344 seconds (process running for 4.108)
```

（時刻は日付を省略し、パッケージ名は短縮して表示しています。）

| 行 | 読み取れること |
| --- | --- |
| `(v4.1.2)` | 実際に動いている Spring Boot のバージョン。`pom.xml` と一致するか確認する |
| `Starting ... using Java 25.0.5 with PID` | 動いている JVM のバージョンとプロセスID。「Java 17 で動いていた」といったことはここで分かる |
| `No active profile set` | プロファイル（開発用・本番用の設定の切替）が未指定であること。第15章 |
| `Tomcat initialized with port 18010` | 埋め込み Tomcat が作られた。ポートは設定（`server.port`）から。未指定なら 8080 |
| `Starting Servlet engine: [Apache Tomcat/11.0.27]` | Tomcat のバージョン。BOM が決めた版（`pom.xml` には書いていない） |
| `Root WebApplicationContext: initialization completed in 1705 ms` | DI コンテナ（Spring が部品を作って管理する場所）の準備が終わった。第11章 |
| `Tomcat started on port 18010` | ここからリクエストを受けられる |
| `Started HelloApplication in 3.344 seconds` | 起動完了。起動の所要時間 |
| `[main]`（スレッド名の欄） | 起動処理は main スレッドで行っている |

もう1つ、最初のリクエストが来たときに出る行にも注目してください。

```text
01:22:18.232  INFO ... [io-18010-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]  : Initializing Spring DispatcherServlet 'dispatcherServlet'
01:22:18.234  INFO ... [io-18010-exec-1] o.s.web.servlet.DispatcherServlet   : Completed initialization in 1 ms
```

スレッド名が `[main]` から `[io-18010-exec-1]` に変わっています。これが、第9章で見たリクエストを処理するスレッドです（Tomcat のスレッドプールの1本で、名前は `http-nio-18010-exec-1` を省略したもの）。第12章の主役である `DispatcherServlet` は、この最初のリクエストで初期化されました。

### `@SpringBootApplication` の3つの意味

`@SpringBootApplication` は、3つのアノテーションをまとめたものです。

```mermaid
flowchart LR
    A["@SpringBootApplication"] --> B["@SpringBootConfiguration<br/>このクラスは設定クラス"]
    A --> C["@ComponentScan<br/>このパッケージ以下から<br/>部品を探す"]
    A --> D["@EnableAutoConfiguration<br/>クラスパスを見て<br/>自動で配線する"]
```

| 意味 | 何をするか | つまずきどころ |
| --- | --- | --- |
| `@ComponentScan` | `@RestController`、`@Service` などの目印が付いたクラスを探して、部品（Bean）として登録する | 探す範囲は、このクラスのパッケージ以下だけ |
| `@EnableAutoConfiguration` | 依存ライブラリを見て、必要な設定を自動で足す（7 節） | 何が足されたか、コードには現れない |
| `@SpringBootConfiguration` | このクラス自身も設定クラスとして扱う | ほぼ意識しない |

### 壊してみる：パッケージの外に置く

`HelloController` を、`com.example.hello` の外（`com.elsewhere`）に移動して、起動します。

```text
Started HelloApplication in 3.088 seconds ...      ← 起動は成功する。エラーも警告も出ない
```

```bash
curl -i localhost:18014/api/hello
```

```text
HTTP/1.1 404
Content-Type: application/json

{"timestamp":"2026-11-07T16:44:38.717Z","status":404,"error":"Not Found","path":"/api/hello"}
```

**起動は成功し、404 が返ります。** `@ComponentScan` の範囲外にあるクラスは、そもそも存在を知られていないので、エラーも出ません。

!!! danger "AIが生成したコードで起きやすい"
    AI は、ファイルを新しいパッケージ（`com.example.api` のように、アプリのルートの外）に作ることがあります。「起動はするが 404」は、この種のミスの典型です。**アプリのクラスは、`@SpringBootApplication` を付けたクラスと同じパッケージか、その配下に置きます。** QuickVote が `com.example.quickvote` の下に `poll`、`vote` などを切っているのは、この規則に従っているためです。

## 6. Spring Boot が起動時にやること

起動ログの裏側を、時間の流れで整理します。

```mermaid
flowchart LR
    S1["main が<br/>SpringApplication.run"] --> S2["設定を集める<br/>application.yaml<br/>環境変数・引数"]
    S2 --> S3["部品を探す<br/>@ComponentScan"]
    S3 --> S4["自動構成を判定<br/>条件に合うものだけ"]
    S4 --> S5["Bean を作る<br/>依存の順に"]
    S5 --> S6["Tomcat を起動<br/>ポートを開く"]
    S6 --> S7["Started ..."]
```

S4 の「条件に合うものだけ」が、次の節の話題です。

## 7. 自動構成は「条件付きの設定」：if 文として読む

Boot の自動構成は、「もし〇〇ならこの設定を足す」という if 文の集まりです。TypeScript で書くなら、次のような疑似コードになります。

```ts
// 疑似コード：自動構成が考えていること
if (classpath.has("tools.jackson.databind.json.JsonMapper")) {   // Jackson が依存にあるなら
  if (!container.has(JsonMapper)) {                              // 自分でまだ用意していないなら
    container.register("jacksonJsonMapper", defaultJsonMapper()) // 既定の JsonMapper を用意する
  }
}
```

実際の Java の定義もほぼ同じ形で、条件をアノテーションで表します。

| アノテーション | 意味 |
| --- | --- |
| `@ConditionalOnClass` | そのクラスがクラスパスにあれば（依存が入っていれば） |
| `@ConditionalOnMissingBean` | まだその Bean がなければ（自分で用意していなければ） |
| `@ConditionalOnProperty` | 設定ファイルにそのプロパティがあれば（`true` なら、など） |

この中で押さえておきたいのは `@ConditionalOnMissingBean` です。自分が先に定義したら、自動構成は引っ込みます（back off）。電源タップの例えなら、自分の延長コードを挿したときに、タップの既定の配線が使われなくなるということです。

### 見る：`--debug` で条件評価レポートを出す

```bash
java -jar target/hello-0.0.1-SNAPSHOT.jar --debug
```

起動ログのあとに `CONDITIONS EVALUATION REPORT` が出ます。条件に合って適用された設定が Positive matches、条件に合わず適用されなかった設定が Negative matches です。実際の出力から抜粋します。

```text
Positive matches:
-----------------

   JacksonAutoConfiguration matched:
      - @ConditionalOnClass found required class 'tools.jackson.databind.json.JsonMapper' (OnClassCondition)

   JacksonAutoConfiguration#jacksonJsonMapper matched:
      - @ConditionalOnMissingBean (types: tools.jackson.databind.json.JsonMapper; SearchStrategy: all) did not find any beans (OnBeanCondition)

   TomcatServletWebServerAutoConfiguration matched:
      - @ConditionalOnClass found required classes 'jakarta.servlet.ServletRequest', 'org.apache.catalina.startup.Tomcat', ... (OnClassCondition)

Negative matches:
-----------------

   GsonHttpMessageConvertersConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'com.google.gson.Gson' (OnClassCondition)

   WebMvcAutoConfiguration.ProblemDetailsErrorHandlingConfiguration:
      Did not match:
         - @ConditionalOnBooleanProperty (spring.mvc.problemdetails.enabled=true) did not find property 'spring.mvc.problemdetails.enabled' (OnPropertyCondition)
```

読み方は単純です。`Tomcat...matched` は、Tomcat のクラスが依存にあったので、Tomcat を起動する設定が有効になったという意味です。`Gson...Did not match` は、Gson（別の JSON ライブラリ）が依存にないので使わないという意味です。この例の Positive matches は全部で約50件ありました。自分では1行も書いていない約50件の判断が、起動のたびに行われています。

### 壊してみる：自分で `JsonMapper` を用意する

`@ConditionalOnMissingBean` が本当に引っ込むのかを確かめるために、わざと `JsonMapper` を自分で定義します。

```java title="CustomMapperConfig.java（実験用。profile 'custom-mapper' のときだけ有効）"
@Configuration
@Profile("custom-mapper")
public class CustomMapperConfig {

    @Bean
    public JsonMapper myMapper() {
        return JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    }
}
```

```bash
java -jar target/hello-0.0.1-SNAPSHOT.jar --spring.profiles.active=custom-mapper --debug
curl localhost:18010/api/hello
```

```text
{
  "message" : "こんにちは、QuickVote",
  "at" : "2026-11-07T16:23:43.461143700Z"
}
```

応答が整形されました。レポートでも、自動構成の `jacksonJsonMapper` が引っ込んでいます。

```text
   JacksonAutoConfiguration#jacksonJsonMapper:
      Did not match:
         - @ConditionalOnMissingBean (types: tools.jackson.databind.json.JsonMapper; SearchStrategy: all) found beans of type 'tools.jackson.databind.json.JsonMapper' myMapper (OnBeanCondition)
```

!!! danger "本番で事故になるパターン：JsonMapper を丸ごと自作すると、Boot の既定が全部消える"
    上の実験は、整形したかっただけです。しかし `JsonMapper` を丸ごと定義した結果、Boot が `JsonMapper` に施していた設定（`application.yaml` の `spring.jackson.*` を含む）がすべて使われなくなります。JSON の挙動を少し変えたいときは、Bean を丸ごと差し替えず、`application.yaml` の設定や、Boot が用意しているカスタマイズの口を使います。
    AI は「`@Bean` で `JsonMapper` を作る」コードを気軽に出します。なぜ自作が必要なのかを、レビューで必ず確かめてください。

!!! warning "バージョン注意：Boot 4 では、自動構成クラスの名前とパッケージが変わった"
    Boot 3 までの記事では、自動構成クラスは `org.springframework.boot.autoconfigure.web.servlet.*` のように、`spring-boot-autoconfigure` という大きな1つの jar の中にありました。Boot 4 では機能ごとの jar に分割され、パッケージも `org.springframework.boot.webmvc.autoconfigure.*`、`org.springframework.boot.jdbc.autoconfigure.*` などに移りました。上のレポートに `org.springframework.boot.webmvc.error.ErrorController` のような名前が出るのは、そのためです。
    JSON も、Jackson 2（`com.fasterxml.jackson.databind.ObjectMapper`）から Jackson 3（`tools.jackson.databind.json.JsonMapper`）に変わりました。レポートに `Jackson2HttpMessageConvertersConfiguration` が「Did not match」で出ているのは、Jackson 2 用の旧設定です。古い記事の `ObjectMapper` は、Boot 4 では `JsonMapper` と読み替えます。

## 8. 自動構成の「地図」を持つ：何を見れば分かるか

自動構成はソースコードに現れないので、調べ方を知っておく必要があります。

| 知りたいこと | 見る場所 |
| --- | --- |
| どの設定が有効になったか／ならなかったか、なぜか | `--debug` の `CONDITIONS EVALUATION REPORT`（またはテストでの `ConditionEvaluationReport`） |
| 依存の中身 | `./mvnw dependency:tree` |
| 設定できるキーと既定値 | Spring Boot 公式ドキュメントの「Application Properties」。IDE では `application.yaml` で補完が効く |
| 実行中の Bean の一覧 | 起動ログを `DEBUG` にする。またはテストで `ApplicationContext#getBeanDefinitionNames()` |
| 使われている Java・Tomcat のバージョン | 起動ログの先頭 |

IntelliJ IDEA では、自動構成クラス（`JacksonAutoConfiguration` など）を Ctrl+クリックでたどって、`@ConditionalOn...` を読めます。AI が生成した設定がなぜ反映されないのかを調べるとき、最終的な答えはこの自動構成のソースにあります。

## 9. 現場で：AIが出した `pom.xml` と起動エラーのレビュー

| 見るもの | 疑う理由 |
| --- | --- |
| `<version>` が個別に書かれている（Spring 管理のライブラリに） | BOM を外れる。組み合わせの保証がなくなる。本当に必要か聞く |
| `spring-boot-starter-web`、`javax.*` の import | Boot 3 以前の書き方。Boot 4 は `webmvc`、`jakarta.*` |
| 親 POM のバージョンが古い（2.x、3.x） | 教材と違う世代の書き方になっている。バージョンを確認 |
| クラスが `@SpringBootApplication` のパッケージの外にある | 起動はするが 404（5 節） |
| 「ビルドが通らないので `<scope>` を消した」 | テスト用の依存を本番に入れている。理由を聞く |
| `@Bean` で `JsonMapper` などを自作 | Boot の既定が消える（7 節）。設定で足りないか |
| 起動ログの Java バージョンが想定と違う | `JAVA_HOME` や IDE の設定の問題で、起動ログを見れば分かる |

---

## まとめ：3点セット

!!! abstract "この章の3点セット"
    **① 一言で言うと**
    Spring Boot は、Spring Framework・Tomcat・Jackson などを、BOM で決めた動作確認済みのバージョンで束ねる仕組みです。クラスパスと設定を見て「条件付きの設定（自動構成）」を適用し、`java -jar` だけで起動できる状態にします。自分で定義した Bean があれば、自動構成は引っ込みます。

    **② 現場でこう聞かれる**
    「`pom.xml` に Tomcat や Jackson のバージョンが書いてないのに、どうして動くの？」
    → 親 POM（`spring-boot-starter-parent`）の BOM がバージョンを決めています。スターターが関連ライブラリをまとめて取り込み、実際の組み合わせは `dependency:tree` と起動ログで確認できます。

    **③ 自分のサービスならどこに当たるか**
    自分のサービスに必要な機能（Web、DB、検証など）を挙げ、それぞれに対応するスターターの名前を調べます。Boot 4 で名前が変わったものがないかも確認します。

## 演習

??? question "確認問題1：層の整理"
    次の4つを、「アプリに近い順」に並べてください。
    Tomcat ／ Spring Boot ／ JVM ／ Spring Framework

??? success "解答"
    Spring Boot → Spring Framework → Tomcat → JVM。
    Boot が Spring Framework を使いやすく束ね、Spring Framework が Tomcat（Servlet の仕組み）の上で動き、Tomcat は JVM の上で動きます。アプリのコードは、いちばん上にあります。

??? question "確認問題2：予測"
    `HelloController` を `com.example.hello.web` パッケージに移しました（`HelloApplication` は `com.example.hello` のまま）。起動すると、どうなりますか。(a) 起動に失敗する (b) 起動して 404 (c) 起動して 200

??? success "解答"
    (c) 起動して 200 です。`@ComponentScan` は `HelloApplication` のパッケージ以下を探します。`com.example.hello.web` は配下なので見つかります。(b) になるのは、`com.example.hello` の外（`com.elsewhere` など）に置いたときです。配下か外かは、パッケージ名の前方一致で判断します。

??? question "確認問題3：読解"
    `--debug` のレポートに、次の行がありました。何を意味しますか。
    ```text
    TaskExecutorConfigurations.TaskExecutorConfiguration#applicationTaskExecutorVirtualThreads:
       Did not match:
          - @ConditionalOnThreading did not find VIRTUAL (OnThreadingCondition)
    ```

??? success "解答"
    仮想スレッド用のタスク実行設定が適用されなかった、という意味です。`spring.threads.virtual.enabled=true` を指定していないためです（既定は無効）。本教材では第36章（SSE）まで仮想スレッドを使いません。条件に合わなかった理由が1行で書かれていることが分かります。

??? question "応用：QuickVote の起動を確かめる"
    `code/labs/part02-quickvote-inmemory/` を `./mvnw spring-boot:run` で起動し、起動ログから (1) Spring Boot のバージョン (2) Tomcat のバージョン (3) ポート番号 (4) 起動にかかった秒数 を読み取ってください。さらに `--debug` で起動し、Negative matches の中から、自分で理由を説明できるものを3つ選んでください。

??? success "解答"
    例：(1) v4.1.2 (2) Apache Tomcat/11.0.27 (3) 18012 (4) 約4秒（環境による）。
    Negative matches の例：`GsonHttpMessageConvertersConfiguration`（Gson が依存にない）、`JmxAutoConfiguration`（JMX を有効にしていない）、`TomcatMetricsAutoConfiguration`（メトリクスの依存がない。第44章で追加する）。

??? question "説明問題：現場でこう聞かれる"
    「Spring と Spring Boot って、何が違うんですか？」200字程度で答えてください。

??? success "解答"
    例：Spring Framework は、DIコンテナや Spring MVC など、サーバーアプリの部品集です。Spring Boot は、それを動作確認済みのバージョンの組み合わせで束ね、クラスパスや設定を見て必要な設定を自動で適用し、Tomcat を内蔵して `java -jar` だけで起動できるようにする仕組みです。自分で Bean を定義すれば自動構成は引っ込むので、既定を使いながら必要な所だけ上書きできます。
