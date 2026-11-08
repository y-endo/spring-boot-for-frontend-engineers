# 付録 Spring Boot 3 → 4 差分カード

<div class="goal-box" markdown>

この付録でできるようになること

- 古い記事やAIが出した Spring のコードを見て、**Spring Boot 4 で動かない箇所を数分で見つけて直せる**
- 「パッケージが変わった」「クラスが消えた」「既定値が変わった」を区別して説明できる
- 確実な情報と、公式ガイドで確認すべき情報を分けて読める

</div>

Spring Boot 3 の記事は、検索結果でもAIの出力でも、まだ大量に出てきます。この付録は、**直すための早見表**です。変更の背景を学ぶ場所ではありません。

## 0. 確認度の見方

| 印 | 意味 |
| --- | --- |
| ✅ 実測 | Spring Boot 4.1.2 で、コンパイル・実行・Maven Central の確認をした（`code/labs/appendix-boot3-to-4/`、`code/quickvote/`） |
| 📘 公式 | [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) に書かれているが、この教材では実行していない |

印のない項目は載せていません。ここに無い変更は、移行ガイドで確認してください（Spring Security 7 と Spring Framework 7 にも、それぞれ移行ガイドがあります）。

## 1. 先に見る：古さを見抜く grep

AIの出力や古い記事のコードを、プロジェクトに入れる前に次のコマンドを通すと、典型的な古い書き方が見つかります（`code/labs/appendix-boot3-to-4/grep-old.sh`）。

```bash
grep -rnE 'javax\.(servlet|validation|persistence)|com\.fasterxml\.jackson\.(databind|core)|@MockBean|@SpyBean|WebSecurityConfigurerAdapter|\.and\(\)|antMatchers|authorizeRequests\(|spring-boot-starter-web<' src pom.xml
```

古い書き方を集めたサンプル（`old/`）に対して実行すると、次のように見つかります。

```text
old/OldJavax.java:1:import javax.servlet.http.HttpServletRequest;
old/OldMockBean.java:1:import org.springframework.boot.test.mock.mockito.MockBean;
old/OldSecurityAdapter.java:3:class OldSecurityAdapter extends WebSecurityConfigurerAdapter {}
old/OldSecurityAnd.java:5:        http.formLogin(f -> {}).and().httpBasic(b -> {});
old/OldSecurityAntMatchers.java:5:        http.authorizeHttpRequests(a -> a.antMatchers("/api/**").authenticated());
```

!!! warning "grep に引っかからない古さもある"
    既定値が変わった項目（§4 Jackson）は、コンパイルが通ったまま挙動だけが変わります。**コンパイルエラーになる変更は安全で、静かに変わる変更が危険**です。

## 2. 全体の早見表

| # | 分類 | 古い（Boot 3以前） | いま（Boot 4） | 確認度 |
| --- | --- | --- | --- | --- |
| 1 | スターター | `spring-boot-starter-web` | `spring-boot-starter-webmvc` | ✅ |
| 2 | スターター | `spring-boot-starter-oauth2-client` ほか | `spring-boot-starter-security-oauth2-client` ほか | ✅ |
| 3 | スターター | `spring-boot-starter-aop` | `spring-boot-starter-aspectj` | ✅ |
| 4 | スターター | `spring-boot-starter-test` 1本 | 機能ごとの `…-test` | ✅ |
| 5 | テスト | `@MockBean` / `@SpyBean` | `@MockitoBean` / `@MockitoSpyBean` | ✅ |
| 6 | テスト | `…test.autoconfigure.web.servlet.WebMvcTest` | `…webmvc.test.autoconfigure.WebMvcTest` | ✅ |
| 7 | テスト | `TestRestTemplate` が既定で使える | 別モジュール＋注釈が必要 | ✅📘 |
| 8 | テスト | `org.testcontainers:postgresql` ほか | `testcontainers-postgresql` ほか | ✅ |
| 9 | Jackson | `com.fasterxml.jackson.databind` | `tools.jackson.databind` | ✅ |
| 10 | Jackson | `ObjectMapper`・検査例外 | `JsonMapper`・非検査例外 | ✅ |
| 11 | Jackson | 既定値 | 既定値が変わった | ✅ |
| 12 | Security | `.and()` ・`antMatchers` | ラムダDSL・`requestMatchers` | ✅ |
| 13 | Security | `WebSecurityConfigurerAdapter` | `SecurityFilterChain` Bean | ✅ |
| 14 | 型 | `org.springframework.lang.Nullable` | `org.jspecify.annotations.Nullable` | ✅ |
| 15 | 型 | `javax.*` | `jakarta.*`（Boot 3で済み） | ✅ |
| 16 | パッケージ | `…autoconfigure.jdbc.JdbcConnectionDetails` ほか | 機能別モジュールの新パッケージ | ✅ |

次の節から、カテゴリごとに詳細を見ます。

## 3. スターター（pom.xml / build.gradle.kts）

### カード1：`web` → `webmvc`

=== "古い"

    ```xml
    <artifactId>spring-boot-starter-web</artifactId>
    ```

=== "いま"

    ```xml title="code/quickvote/pom.xml"
    <artifactId>spring-boot-starter-webmvc</artifactId>
    ```

- ✅ `spring-boot-starter-web` は 4.1.2 にまだ存在しますが、**非推奨**と明記されています（Maven Central の POM の説明文）。動くので気づきにくく、新規コードでは `webmvc` を使います
- Initializr で「Spring Web」を選ぶと、`webmvc` が入ります（実際に取得して確認）
- 📘 Web の機能ごとにスターターが分かれた流れの一部です。`web-services` も `webservices` に変わっています

### カード2：OAuth2 のスターターに `security-` が付く

| 古い | いま |
| --- | --- |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |

✅ Initializr が出力する名前です。旧名は 4.1.2 にまだ存在し、説明文に非推奨と書かれています。使い分けは[ログイン型認証の概観](login-auth.md)にあります。

### カード3：`aop` が無い

✅ `spring-boot-starter-aop` は 4.1.2 では見つかりません（Maven Central で確認）。`spring-boot-starter-aspectj` があります。`@Transactional` のためにAOPスターターを足している古い記事は、そもそも不要です（Spring のコアが持っています。[第23章](../part04/ch23-transactional-proxy.md)）。

### カード4：テスト用スターターは機能ごと

QuickVote の `pom.xml` には、`spring-boot-starter-webmvc-test`・`-jdbc-test`・`-flyway-test`・`-security-test`・`-validation-test`・`-actuator-test` が並んでいます（✅）。**使う機能に合わせて `…-test` を足す**のが Boot 4 の形です。📘 これらは `spring-boot-starter-test` を連れてくるので、単体で書く必要はありません。

!!! note "classic スターター"
    ✅ `spring-boot-starter-classic` と `spring-boot-starter-test-classic` は存在します。📘 移行を段階的に進めるための**一時的な**逃げ道です。新規のプロジェクトでは使いません。

### カード5：Flyway は専用スターター

✅ QuickVote は `spring-boot-starter-flyway` ＋ `org.flywaydb:flyway-database-postgresql` です。📘 公式ガイドも、Flyway には専用スターターが必要としています。`flyway-core` だけを足す古い書き方は、そのままでは使えません。

## 4. Jackson 3

Spring Boot 4 の JSON は **Jackson 3** です。いちばん広く影響が出ます。

### カード6：パッケージとアーティファクト

| 対象 | 古い（Jackson 2） | いま（Jackson 3） |
| --- | --- | --- |
| パッケージ | `com.fasterxml.jackson.databind` | `tools.jackson.databind` |
| グループID | `com.fasterxml.jackson.core` | `tools.jackson.core` |
| **アノテーション** | `com.fasterxml.jackson.annotation` | **同じ（据え置き）**。`jackson-annotations` は 2.21 |

```java
import com.fasterxml.jackson.annotation.JsonProperty;   // 据え置き。そのまま使える
import tools.jackson.databind.json.JsonMapper;          // こちらは tools.jackson
```

✅ `@JsonProperty`・`@JsonIgnore` などは import を変えなくて構いません。変わるのは `ObjectMapper`・`JsonNode`・`TypeReference` などの本体側です。`com.fasterxml.jackson.databind.ObjectMapper` は、Boot 4 のクラスパスに存在しません（コンパイルエラーを確認）。

### カード7：`ObjectMapper` → `JsonMapper`、例外は非検査

=== "古い"

    ```java
    @Autowired ObjectMapper mapper;

    String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {      // 検査例外
            throw new IllegalStateException(e);
        }
    }
    ```

=== "いま"

    ```java title="code/labs/appendix-ts-java/spring-web/.../JacksonDemo.java（抜粋）"
    import tools.jackson.databind.json.JsonMapper;

    String json = mapper.writeValueAsString(new Dto("ランチ", 3));   // try/catch も throws も不要
    ```

- ✅ `JacksonException` は `RuntimeException` です（実測）。`throws` を書く必要はありません
- ✅ QuickVote は自動構成された `JsonMapper` をコンストラクタ注入しています
- 📘 自分で Bean を差し替えるときは、`ObjectMapper` ではなく `JsonMapper` の Bean を定義します

### カード8：既定値の変化（静かに変わる）

Jackson 3 の既定値を、素の `JsonMapper` と Spring Boot が組み立てた `JsonMapper` の両方で実測しました（Jackson 3.1.7）。

| 観察項目 | 実測結果 |
| --- | --- |
| 日時（`Instant`）の出力 | `"2026-11-08T03:04:05Z"`（ISO 8601の文字列）。`Duration` は `"PT1M30S"` |
| JSONの `null`／欠落 → `boolean`・`int` | **拒否**（`MismatchedInputException`） |
| 余計なプロパティ | 受け入れる（`FAIL_ON_UNKNOWN_PROPERTIES` は false） |
| 末尾にゴミのあるJSON | 拒否（`StreamReadException`） |
| 普通のクラスの出力順 | **アルファベット順**（`SORT_PROPERTIES_ALPHABETICALLY` が true） |
| `record` の出力順 | 宣言順（`zebra, apple, mango` のまま） |

!!! danger "Jackson 3 で動かなくなる典型"
    `record CreatePollRequest(boolean allowMemberOptions, …)` に、`allowMemberOptions` を**含まない**JSONを送ると 400 になります。Jackson 2 では `false` で通っていました。QuickVote は `Boolean`（ラッパー型）で受けて、コンパクトコンストラクタで「省略＝false」にしています（[第13章](../part02/ch13-api-contract.md)）。

✅ `spring.jackson.use-jackson2-defaults=true` を付けて同じ観察をすると、プリミティブへの `null` は受け入れ、プロパティの並び替えは無効になりました（実測）。**移行の応急処置**には使えますが、新規では付けません。

### カード9：設定キーとクラス名

| 古い | いま | 確認度 |
| --- | --- | --- |
| `@JsonComponent` | `@JacksonComponent`（`org.springframework.boot.jackson`） | ✅ |
| `Jackson2ObjectMapperBuilderCustomizer` | `JsonMapperBuilderCustomizer`（`org.springframework.boot.jackson.autoconfigure`） | ✅ |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` | `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS`（`tools.jackson.databind.cfg`） | ✅ |
| `spring.jackson.read.*` / `write.*` | `spring.jackson.json.read.*` / `json.write.*` | 📘 |
| `spring.jackson.datetime.write-dates-as-timestamps` | **存在しないキー**。日時は既定でISO文字列 | ✅ |

QuickVote が使う `spring.jackson.factory.constraints.read.max-document-length`（JSONの最大サイズ。超えると400）も、実際に動かして確認しています。

## 5. テスト

### カード10：`@MockBean` → `@MockitoBean`

=== "古い"

    ```java
    import org.springframework.boot.test.mock.mockito.MockBean;

    @WebMvcTest(PollController.class)
    class PollControllerTest {
        @MockBean PollService service;
    }
    ```

=== "いま"

    ```java title="code/labs/appendix-boot3-to-4/.../NewTestFormsTest.java（抜粋）"
    import org.springframework.test.context.bean.override.mockito.MockitoBean;
    import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

    @WebMvcTest(GreetController.class)
    class NewTestFormsTest {
        @MockitoBean Greeter greeter;
    }
    ```

- ✅ `@MockBean` と `@SpyBean` は、Boot 4.1.2 の `spring-boot-test` に**クラス自体がありません**（コンパイルエラー）
- ✅ `@MockitoBean` / `@MockitoSpyBean` は **Spring Framework のパッケージ**（`org.springframework.test.context.bean.override.mockito`）です。Bootのパッケージではありません
- 📘 テストクラスのフィールドに付けます。`@Configuration` クラスの中では使えません

### カード11：テスト用アノテーションのパッケージ移動

| 対象 | 古い | いま | 確認度 |
| --- | --- | --- | --- |
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` | ✅ |
| `@AutoConfigureMockMvc` | 同上 | `org.springframework.boot.webmvc.test.autoconfigure` | ✅ |
| `TestRestTemplate` | `org.springframework.boot.test.web.client` | `org.springframework.boot.resttestclient`（`spring-boot-resttestclient`） | ✅ |
| `@JsonTest` | `org.springframework.boot.test.autoconfigure.json` | **変わらない** | ✅ |
| `@SpringBootTest` ・`@TestConfiguration` | `org.springframework.boot.test.context` | **変わらない** | ✅ |

- 規則は「機能別モジュールに引っ越すと、パッケージも `…<機能>.test.autoconfigure` に変わる」です。すべてが動くわけではないので、`@JsonTest` のように**変わらないもの**もあります
- 📘 `@SpringBootTest` は MockMvc を自動では用意しなくなりました。`@AutoConfigureMockMvc` を付けます（QuickVote も使っています）
- 📘 `TestRestTemplate` を使うには、`@AutoConfigureTestRestTemplate` と依存の追加が要ります。QuickVote は JDK の `HttpClient` を使うので使っていません

### カード12：Testcontainers 2

| 古い（1.x） | いま（2.x） |
| --- | --- |
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` |
| `org.testcontainers:junit-jupiter` | `org.testcontainers:testcontainers-junit-jupiter` |
| `org.testcontainers.containers.PostgreSQLContainer<?>`（ジェネリック） | `org.testcontainers.postgresql.PostgreSQLContainer`（非ジェネリック） |

✅ Maven Central で、`org.testcontainers:postgresql` は 2.0.5 が存在せず（1.x まで）、`testcontainers-postgresql` が 2.0.5 にあります。旧パッケージのクラスは非推奨の警告つきでまだコンパイルできますが、使いません。`@ServiceConnection` は `org.springframework.boot.testcontainers.service.connection` のままです。

### カード13：小さな変更

| 古い | いま | 確認度 |
| --- | --- | --- |
| `HttpStatus.UNPROCESSABLE_ENTITY` | `HttpStatus.UNPROCESSABLE_CONTENT`（旧名は非推奨） | ✅ |
| JUnit 5（`org.junit.jupiter.*`） | JUnit 6。パッケージは同じ | ✅ |
| Mockito の self-attaching 警告 | JDK 25 で出る（動作には影響なし） | ✅ |

## 6. Spring Security 7

### カード14：`and()` が無い／ラムダDSL

=== "古い"

    ```java
    http.csrf().disable()
        .authorizeRequests()
            .antMatchers("/api/**").authenticated()
        .and()
        .formLogin();
    ```

=== "いま"

    ```java title="code/labs/appendix-boot3-to-4/.../SecurityConfig.java（抜粋）"
    http
        .csrf(csrf -> csrf.spa())
        .authorizeHttpRequests(a -> a
            .requestMatchers("/public/**").permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().denyAll())
        .formLogin(Customizer.withDefaults())
        .httpBasic(Customizer.withDefaults());
    ```

古い書き方を1つずつコンパイルして、**すべて失敗する**ことを確認しました（✅）。

| 古い書き方 | Boot 4（Security 7）の結果 |
| --- | --- |
| `http.csrf()`（引数なし） | `Customizer` を取る形しかない |
| `http.authorizeHttpRequests()`（引数なし） | 同上 |
| `http.authorizeRequests()` | メソッドが存在しない |
| `.antMatchers(…)` / `.mvcMatchers(…)` / `http.antMatcher(…)` | メソッドが存在しない（`requestMatchers`） |
| `.and()` | メソッドが存在しない |
| `extends WebSecurityConfigurerAdapter` | クラスが存在しない |
| `@EnableGlobalMethodSecurity` | 非推奨（警告）。`@EnableMethodSecurity` を使う |

`WebSecurityConfigurerAdapter` は Boot 3 の時点ですでに無く、Boot 2 時代の記事の特徴です。**`SecurityFilterChain` を返す `@Bean`** が現在の形です（[第31章](../part06/ch31-spring-security.md)）。

### カード15：Security まわりの小さな実測

- ✅ `csrf.spa()` でSPA向けのCSRF設定になる。`CookieCsrfTokenRepository` は `final` クラスなので、属性は `setCookieCustomizer` で変える（[第34章](../part06/ch34-browser-boundary.md)）
- ✅ ユーザーを定義していなくても「Using generated security password」がログに出る。`UserDetailsServiceAutoConfiguration` は `org.springframework.boot.security.autoconfigure` に移動している
- 📘 `@WithMockUser` を使うには `spring-boot-starter-security-test` が必要

## 7. 型・パッケージ

### カード16：JSpecify と `javax` → `jakarta`

| 古い | いま | 確認度 |
| --- | --- | --- |
| `org.springframework.lang.Nullable` | `org.jspecify.annotations.Nullable` | ✅（旧は非推奨警告） |
| `javax.servlet.*` / `javax.validation.*` | `jakarta.servlet.*` / `jakarta.validation.*` | ✅ |

- `javax` → `jakarta` は **Boot 3 で済んだ変更**で、Boot 4 で新たに起きるものではありません。Boot 2 の記事を見たときの目印です（Boot 4 のクラスパスには `javax.servlet` が存在しない）
- `@Nullable` は実行時の防御ではなく、**IDEや検査ツールへの目印**です（[第5章](../part01/ch05-null-exceptions.md)）

### カード17：自動構成クラスの引っ越し

自動構成の内部クラスを直接 import している古いコードは、パッケージが変わっています（✅）。

| 古い | いま |
| --- | --- |
| `org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails` | `org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails` |
| `org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest` | `org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest` |
| `org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration` | `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration` |

変わらないもの：`@SpringBootApplication`（`org.springframework.boot.autoconfigure`）、`@ConfigurationProperties`、`@DefaultValue`、`@Transactional`。通常のアプリコードで import するものの多くは、そのままです。

## 8. 動作・運用の変更（📘 公式ガイド）

実行して確かめていない項目です。該当しそうなときは移行ガイドを読んでください。

| 変更 | メモ |
| --- | --- |
| Undertow の削除 | Tomcat か Jetty を使う |
| 実行可能jarの起動スクリプトの削除 | `java -jar` で起動する |
| `spring.dao.exceptiontranslation.enabled` | `spring.persistence.exceptiontranslation.enabled` に改名 |
| liveness／readiness プローブ | 既定で有効（QuickVoteは明示設定） |
| `HttpMessageConverters` | 非推奨 |
| WAR配備の `spring-boot-starter-tomcat` | `spring-boot-starter-tomcat-runtime` |
| `spring-boot-starter-batch` | 既定がメモリ上のメタデータ。DB保存は `…-batch-jdbc` |

## 9. 直し方の手順

1. **grep** で既知の古い書き方を拾う
2. **コンパイル**する。消えたクラスとメソッドはここで落ちる
3. **テストを流す**。Jackson の既定値のように、静かに変わる挙動はここでしか見つからない
4. 見つからない変更は[公式の移行ガイド](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)で確認する

AIに頼むときは、「Spring Boot 4.1・Jackson 3（`tools.jackson`）・Security はラムダDSL・テストは `@MockitoBean`」と**先に条件を伝える**と混入が減ります。それでも混ざるので、出力は必ずこの手順でふるいにかけ、レビュー観点は[第47章](../part10/ch47-ai-review.md)で確認します。

## 10. 確認問題

??? question "確認問題1：コンパイルは通るが挙動が変わる"
    Boot 3 では動いていたAPIが、Boot 4 に上げただけで一部のリクエストが400になりました。コンパイルエラーはありません。疑う場所は？

??? success "解答"
    Jackson 3 の既定値です。`record` や DTO のプリミティブ項目（`boolean`、`int`）に、JSONの項目が無い（または `null`）と、Jackson 3 は拒否します。`Boolean` などのラッパー型で受けるか、移行の応急処置として `spring.jackson.use-jackson2-defaults=true` を使います。

??? question "確認問題2：`@MockBean` の置き換え"
    `@MockBean` を `@MockitoBean` に置き換えるとき、import 以外に気をつけることは？

??? success "解答"
    `@MockitoBean` は Spring Framework のパッケージ（`org.springframework.test.context.bean.override.mockito`）にあります。テストクラスのフィールドに付けるもので、`@Configuration` クラスの中では使えません（公式ガイド）。

??? question "応用：次の1行は、Boot 4 でそのまま動くか"
    `http.csrf().disable().authorizeRequests().antMatchers("/api/**").authenticated();`

??? success "解答"
    動きません。`csrf()` は引数なしでは呼べず、`authorizeRequests()` と `antMatchers` は存在しません。`http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.requestMatchers("/api/**").authenticated());` が現在の形です。ただし `csrf` を無効にするかどうかは設計の判断です（[第34章](../part06/ch34-browser-boundary.md)）。
