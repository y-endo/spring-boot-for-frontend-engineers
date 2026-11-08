# 付録 TypeScript ↔ Java 対応表

<div class="goal-box" markdown>

この付録の使い方

- 「TypeScriptで書くならこうだが、Javaではどう書くか」を引く辞書です。通して読む必要はありません
- 各節は **表（早見）→ 左右対訳のコード → 詳しい章へのリンク** の順です
- Javaのコードは、`code/labs/appendix-ts-java/` で JDK 25 により実際にコンパイル・実行して確かめたものです。SpringのコードはSpring Boot 4.1.2のテストまで通してあります

</div>

記号の読み方：✅ ほぼ同じ　⚠ 似ているが違う（注意）　❌ 無い（別の書き方で代用）

## 1. 型

| TypeScript | Java | 印 | メモ |
| --- | --- | --- | --- |
| `number` | `int` / `long` / `double` / `BigDecimal` | ⚠ | 用途で選ぶ。金額・率は `BigDecimal`（[第7章](../part01/ch07-value-pitfalls.md)） |
| `bigint` | `BigInteger` / `long` | ✅ | |
| `string` / `boolean` | `String` / `boolean` | ✅ | `String` は参照型。比較は `equals` |
| `any` | `Object` | ⚠ | 使うたびにキャストが要る。多用は型の保護を捨てる |
| `unknown` | `Object` ＋ `instanceof` で絞り込み | ✅ | |
| `void` | `void` | ✅ | |
| `never` | （無い） | ❌ | 例外を投げるメソッドで代用 |
| `undefined` / `null` | `null`（のみ） | ⚠ | `undefined` は無い（[第5章](../part01/ch05-null-exceptions.md)） |
| <code>"OPEN" &#124; "CLOSED"</code> | `enum PollStatus { OPEN, CLOSED }` | ⚠ | 文字列リテラル型は無い。enumを作る |
| 判別可能ユニオン | `sealed interface` ＋ `record` | ✅ | 網羅漏れがコンパイルエラー（[第4章](../part01/ch04-data-shapes.md)） |
| `type`／`interface`（データの形） | `record` | ⚠ | 形が合うだけでは代入できない（[第3章](../part01/ch03-types.md)） |
| `A & B` | `class X implements A, B` | ⚠ | 交差型そのものは無い |
| `[string, number]`（タプル） | `record Pair<A,B>(A first, B second)` | ⚠ | 名前付きのrecordを作る |
| `Array<T>` / `T[]` | `List<T>` | ✅ | `T[]` もあるが固定長 |
| `Record<string, T>` | `Map<String, T>` | ✅ | |
| `readonly` / `as const` | `final` / `record` / `List.of` | ⚠ | `final` は再代入禁止のみ |
| `T extends U` | `T extends U` | ✅ | 実行時に型が消える（型消去） |
| `Partial<T>` / `Pick<T>` / `Omit<T>` | （無い） | ❌ | 別の `record` を手で書く |
| `typeof x === "string"` | `x instanceof String s` | ✅ | 絞り込みも効く |

=== "TypeScript"

    ```ts
    type Shape =
      | { kind: "circle"; r: number }
      | { kind: "rect"; w: number; h: number }

    function area(s: Shape): number {
      switch (s.kind) {
        case "circle": return Math.PI * s.r ** 2
        case "rect":   return s.w * s.h
      }
    }
    ```

=== "Java"

    ```java title="code/labs/appendix-ts-java/TypesDemo.java（抜粋）"
    sealed interface Shape permits Circle, Rect {}
    record Circle(double r) implements Shape {}
    record Rect(double w, double h) implements Shape {}

    double area(Shape s) {
        return switch (s) {
            case Circle c -> Math.PI * c.r() * c.r();
            case Rect(double w, double h) -> w * h;   // 分割代入のように取り出せる
        };
    }
    ```

実行結果は `area(new Circle(1.0))` が `3.141592653589793`、`area(new Rect(2, 3))` が `6.0` です。`Circle` と `Rect` のどちらかを `permits` に足し忘れたり、`case` を1つ抜いたりすると、実行前にコンパイルエラーになります。

!!! warning "`number` を1つの型で済ませない"
    `0.1 + 0.2` は `double` で `0.30000000000000004`、`BigDecimal` で `0.3` でした（実行して確認済み）。TypeScriptの `number` に当たる型が4つあり、**選ぶことが設計**です。

## 2. null・undefined

| TypeScript | Java | メモ |
| --- | --- | --- |
| `x?.y` | `Optional.ofNullable(x).map(X::y)` | 戻り値で「無いかも」を表す |
| `x ?? "既定"` | `Objects.requireNonNullElse(x, "既定")` / `opt.orElse("既定")` | |
| `x!`（非null表明） | （無い） | nullなら実行時に `NullPointerException` |
| <code>x: string &#124; undefined</code> | `@Nullable String x`（JSpecify） | 注釈は目印。実行時に守ってはくれない |
| `if (x == null) throw …` | `Objects.requireNonNull(x, "メッセージ")` | |
| `strictNullChecks` | （既定では無い） | Javaの型は全部 `null` を許す |

```java title="code/labs/appendix-ts-java/NullDemo.java（抜粋）"
String nick = find(db, "u1")                // Optional<User>
        .map(User::nickname)                // nicknameがnullなら空のまま進む
        .orElse("(なし)");                   // x?.nickname ?? "(なし)" に相当
```

```text
(なし)
NPE: Cannot invoke "String.length()" because "raw" is null
```

NPEのメッセージに変数名が出るのは、デバッグ情報つきでコンパイルしたとき（`javac -g`、Mavenの既定）です。`java NullDemo.java` で直接実行すると `"<local3>"` と表示されました。

!!! tip "`Optional` は戻り値専用"
    フィールドや引数に `Optional` を使うのは避けます。「結果が無いかも」を呼び出し側に伝える場所でだけ使います（[第5章](../part01/ch05-null-exceptions.md)）。

## 3. 関数

| TypeScript | Java | 印 |
| --- | --- | --- |
| `(a, b) => a + b` | `(a, b) -> a + b` | ✅ |
| `function f() {}`（トップレベル） | クラスの中のメソッド | ❌ |
| `x => x.length` | `String::length`（メソッド参照） | ✅ |
| `(x: number) => string` | `Function<Integer, String>` | ⚠ |
| `() => T` / `(x) => void` | `Supplier<T>` / `Consumer<T>` | ⚠ |
| `(x) => boolean` | `Predicate<T>` | ⚠ |
| `function f(a, b = 1)` | オーバーロード（同名で引数違い） | ❌ |
| `function f(...n: number[])` | `int... n`（可変長引数） | ✅ |
| `const { from, to } = range` | `if (o instanceof Range(int from, int to))` | ⚠ |
| クロージャ | ラムダ（捕まえる変数は実質 `final`） | ⚠ |
| `function*`（ジェネレータ） | （無い）`Stream` / `Iterator` で代用 | ❌ |

```java title="code/labs/appendix-ts-java/FuncDemo.java（抜粋）"
String greet(String name) { return greet(name, "Hello"); }              // 既定値の代わりに
String greet(String name, String greeting) { return greeting + ", " + name; }

int base = 10;
Function<Integer, Integer> plusBase = x -> x + base;   // OK
// base++;   // ← あとで変えるとコンパイルエラー（ラムダが捕まえる変数は変更不可）
```

出力は `Hello, QuickVote / Hi, QuickVote`、`plusBase.apply(5)` は `15` です。TypeScriptのクロージャは変数を後から書き換えられますが、Javaのラムダでは不可です。詳しくは[第6章](../part01/ch06-collections-streams.md)。

## 4. クラス

| TypeScript | Java | 印 |
| --- | --- | --- |
| `class A implements I` | `class A implements I` | ✅ |
| `constructor(readonly x: T)` | フィールド宣言 ＋ コンストラクタ（recordなら1行） | ⚠ |
| `#x` / `private x` | `private`（実行時にも強制） | ✅ |
| 修飾子なし＝`public` | 修飾子なし＝**パッケージ内だけ** | ⚠ |
| `static readonly X = 1` | `static final int X = 1` | ✅ |
| `abstract class` | `abstract class` | ✅ |
| `get count()` | `count()`（慣習。構文は無い） | ⚠ |
| `{ ...config, port: 1 }` | `config.withPort(1)`（自作） | ❌ |
| 複数 `extends`／mixin | （無い）`interface` を複数 `implements` | ❌ |
| デコレータ `@Foo` | アノテーション `@Foo` | ⚠ |
| オブジェクトリテラル `{ a: 1 }` | `record` を `new` する | ⚠ |

```java title="code/labs/appendix-ts-java/ClassDemo.java（抜粋）"
record Config(String host, int port) {
    Config {                                              // 検証つきコンストラクタ
        if (port < 1) throw new IllegalArgumentException("port");
    }
    Config withPort(int p) { return new Config(host, p); }   // { ...config, port: p }
}
```

実行すると `Config[host=localhost, port=80] -> Config[host=localhost, port=8080]` と表示されます。recordは不変なので、「変えたコピー」を返すメソッドを自分で用意します。詳細は[第3章](../part01/ch03-types.md)・[第4章](../part01/ch04-data-shapes.md)。

## 5. モジュール・パッケージ管理

| TypeScript / Node | Java | 印 |
| --- | --- | --- |
| `import { X } from "./a/b"` | `import com.example.a.X;` | ⚠ |
| ファイルパスでimport | **完全修飾名（パッケージ名）** でimport | ⚠ |
| `export` | `public` | ⚠ |
| `export default` | （無い） | ❌ |
| barrel（`index.ts`） | （無い） | ❌ |
| 1ファイル複数export | 1ファイルに `public` クラス1つ | ⚠ |
| `package.json` | `pom.xml`（Gradleなら `build.gradle.kts`） | ⚠ |
| `dependencies` / `devDependencies` | `compile`（既定）／ `test` スコープ | ✅ |
| npm registry | Maven Central | ✅ |
| `node_modules`（プロジェクトごと） | `~/.m2`（マシンで共有） | ⚠ |
| `^1.2.3` の範囲指定 | 厳密な版。**BOM（親POM）が版を決める** | ⚠ |
| `package-lock.json` | （無い）BOMが担う | ⚠ |
| `workspaces` | マルチモジュール | ✅ |

QuickVoteの `pom.xml` が依存の版を1つも書いていないのは、Spring Bootの親POMが決めているからです（[第10章](../part02/ch10-spring-boot.md)）。Gradleとの対訳は[Gradle 対訳](gradle.md)にあります。

## 6. 非同期

| TypeScript | Java | 印 |
| --- | --- | --- |
| `await f()` | `f()`（そのまま呼ぶ。スレッドがその場で待つ） | ⚠ |
| `Promise<T>` | `CompletableFuture<T>` / `Future<T>` | ⚠ |
| `.then(fn)` | `.thenApply(fn)` | ✅ |
| `.catch(fn)` | `.exceptionally(fn)` | ✅ |
| `Promise.all([…])` | `CompletableFuture.allOf(…)` / 仮想スレッド＋`Future` | ✅ |
| `Promise.race([…])` | `CompletableFuture.anyOf(…)` | ✅ |
| `setTimeout` | `ScheduledExecutorService.schedule(…)` | ✅ |
| イベントループ（1スレッド） | 1リクエスト1スレッド | ⚠ |

Javaのサーバーでは、**`async/await` を書かず、待つ処理をそのまま書く**のが基本です。待っている間も、そのリクエスト専用のスレッドが居座るので、並行性はスレッドの数で決まります（[第9章](../part01/ch09-threads.md)・[第24章](../part04/ch24-connection-pool.md)）。

=== "TypeScript"

    ```ts
    const [a, b, c] = await Promise.all([
      fetchUser(1), fetchUser(2), fetchUser(3),
    ])
    ```

=== "Java"

    ```java title="code/labs/appendix-ts-java/AsyncDemo.java（抜粋）"
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<String>> fs = List.of(
                ex.submit(() -> fetchUser(1)),
                ex.submit(() -> fetchUser(2)),
                ex.submit(() -> fetchUser(3)));
        for (Future<String> f : fs) IO.print(f.get() + " ");   // get() が await に近い
    }
    ```

1件200msの処理を3件並べて、全体が約200ms（直列なら約600ms）で終わりました。`CompletableFuture` の失敗は `CompletionException` に包まれ、元の例外は `getCause()` で取り出します（`cause = java.lang.IllegalStateException: boom` を確認）。

## 7. コレクション操作

| TypeScript | Java |
| --- | --- |
| `arr.map(f)` | `list.stream().map(f).toList()` |
| `arr.filter(f)` | `list.stream().filter(f).toList()` |
| `arr.reduce((s, v) => s + v, 0)` | `list.stream().mapToInt(…).sum()` |
| `arr.find(f)` | `list.stream().filter(f).findFirst()`（`Optional`） |
| `arr.some(f)` / `arr.every(f)` | `anyMatch(f)` / `allMatch(f)` |
| `arr.flatMap(f)` | `flatMap(f)` |
| `[...new Set(arr)]` | `stream().distinct().toList()` |
| `Object.groupBy(arr, f)` | `Collectors.groupingBy(f)` |
| `arr.sort((a, b) => a - b)` | `list.sort(Comparator.naturalOrder())`（元を変更） |
| `arr[0]` / `arr.at(-1)` | `list.getFirst()` / `list.getLast()` |
| `arr.slice(1, 3)` | `list.subList(1, 3)`（元の「窓」） |
| `arr.join(", ")` | `String.join(", ", list)` |
| `[...a, ...b]` | `new ArrayList<>(a)` ＋ `addAll(b)` |
| `map.get(k) ?? 0` | `map.getOrDefault(k, 0)` |
| `map.set(k, (map.get(k) ?? 0) + 1)` | `map.merge(k, 1, Integer::sum)` |
| `Object.freeze(arr)` | `List.of(…)`（不変。`add` は例外） |

```java title="code/labs/appendix-ts-java/CollDemo.java（抜粋）"
Map<String, Integer> tally = votes.stream()
        .collect(Collectors.groupingBy(Vote::option, TreeMap::new, Collectors.summingInt(Vote::weight)));
// 結果: {そば=1, カレー=1, ラーメン=3}
```

`stream()` を挟む点が最大の違いです。`filter` や `map` は、`List` ではなく `Stream` のメソッドです。末尾の `.toList()` で `List` に戻します（[第6章](../part01/ch06-collections-streams.md)）。

## 8. 文字列・日時・JSON

| TypeScript | Java |
| --- | --- |
| `` `Hello ${name}` `` | `"Hello %s".formatted(name)` |
| 複数行テンプレート | テキストブロック `"""…"""` |
| `a === b`（文字列） | `a.equals(b)`（`==` は同一オブジェクトか） |
| `Date.now()` | `Instant.now(clock)`（`Clock` を注入） |
| `new Date().toISOString()` | `Instant#toString()`（例 `2026-11-08T03:04:05Z`） |
| `Intl.DateTimeFormat` | `DateTimeFormatter.ofPattern(…)` |
| `JSON.stringify(x)` | `mapper.writeValueAsString(x)` |
| `JSON.parse(s)`（型は信用） | `mapper.readValue(s, Dto.class)`（失敗は例外） |
| `JSON.parse(s)`（形を決めない） | `mapper.readTree(s)`（`JsonNode`） |

```java title="code/labs/appendix-ts-java/spring-web/.../JacksonDemo.java（抜粋）"
import tools.jackson.databind.json.JsonMapper;     // Jackson 3：パッケージは tools.jackson

String json = mapper.writeValueAsString(new Dto("ランチ", 3));
Dto back = mapper.readValue(json, Dto.class);
```

時刻の落とし穴は[第7章](../part01/ch07-value-pitfalls.md)、JSONの型は[第13章](../part02/ch13-api-contract.md)です。Jackson 2の記事は `com.fasterxml.jackson.databind` で書かれています。見分け方は[Spring Boot 3→4 差分カード](boot3-to-4.md)にあります。

## 9. エラー処理

| TypeScript | Java | 印 |
| --- | --- | --- |
| `throw new Error("x")` | `throw new IllegalStateException("x")` | ✅ |
| `class E extends Error` | `class E extends RuntimeException` | ✅ |
| `new Error("x", { cause })` | `new X("x", cause)` ／ `getCause()` | ✅ |
| `try / catch / finally` | `try / catch / finally` | ✅ |
| `catch (e)`（`unknown`） | `catch (IOException e)`（型で受ける） | ⚠ |
| <code>e instanceof A &#124;&#124; e instanceof B</code> | <code>catch (A &#124; B e)</code> | ✅ |
| 何でも `throw` できる | `Throwable` のみ。**checked例外は `throws` の宣言が必須** | ⚠ |
| `await using` / `finally` で閉じる | `try (var r = …) { }`（try-with-resources） | ✅ |
| `arr[9]` → `undefined` | `arr[9]` → `ArrayIndexOutOfBoundsException` | ⚠ |

```java title="code/labs/appendix-ts-java/ErrDemo.java（抜粋）"
try (var a = new Res("A"); var b = new Res("B")) {
    IO.println("body");
    throw new RuntimeException("in body");
}
// 出力: open A / open B / body / close B / close A（宣言と逆順に必ず閉じる）
```

「例外を握りつぶさない」「checkedとuncheckedの使い分け」は[第5章](../part01/ch05-null-exceptions.md)、HTTPエラーへの変換は[第14章](../part02/ch14-validation-errors.md)です。

## 10. テスト

| Vitest / Jest | JUnit 6 ＋ AssertJ | 印 |
| --- | --- | --- |
| `it("…", () => {})` | `@Test void …() {}` | ✅ |
| `describe` | クラス／`@Nested` | ✅ |
| `beforeEach` | `@BeforeEach` | ✅ |
| `it.each([…])` | `@ParameterizedTest` ＋ `@CsvSource` | ✅ |
| `expect(x).toEqual(y)` | `assertThat(x).isEqualTo(y)` | ✅ |
| `expect(() => f()).toThrow()` | `assertThatThrownBy(() -> f())` | ✅ |
| `vi.fn()` / `vi.mock` | Mockito（Springでは `@MockitoBean`） | ⚠ |
| supertest | `MockMvcTester` | ✅ |
| fake timers | `Clock` を注入して固定 | ⚠ |
| スナップショット | （標準では無い） | ❌ |

=== "Vitest"

    ```ts
    describe("tally", () => {
      let votes: number[]
      beforeEach(() => { votes = [] })
      it.each([[1, 2, 3], [10, 20, 30]])("%i + %i = %i", (a, b, e) => {
        expect(a + b).toBe(e)
      })
    })
    ```

=== "JUnit"

    ```java title="code/labs/appendix-ts-java/spring-web/.../BasicsTest.java（抜粋）"
    @BeforeEach
    void setUp() { votes = new ArrayList<>(); }

    @ParameterizedTest
    @CsvSource({"1,2,3", "10,20,30", "-1,1,0"})
    void 足し算(int a, int b, int expected) {
        assertThat(a + b).isEqualTo(expected);
    }
    ```

メソッド名に日本語を使えます（`void 追加すると増える()`）。ただし数字で始めることはできません。入門は[第8章](../part01/ch08-junit.md)、APIのテストは[第16章](../part02/ch16-api-testing.md)です。

## 11. ビルド・実行・開発ツール

| Node / npm | Java / Maven | Gradle |
| --- | --- | --- |
| `npm install` | `./mvnw dependency:resolve`（ビルド時に自動） | `./gradlew build` |
| `npm run dev` | `./mvnw spring-boot:run` | `./gradlew bootRun` |
| `npm test` | `./mvnw test`（速いテスト）／ `./mvnw verify`（全部） | `./gradlew test` |
| `npm run build` | `./mvnw package` → `target/*.jar` | `./gradlew bootJar` |
| `npm ls` | `./mvnw dependency:tree` | `./gradlew dependencies` |
| `tsc` | `javac`（Mavenが呼ぶ） | 同左 |
| `node app.js` | `java -jar app.jar` | 同左 |
| `.env` | `application.yaml`／環境変数（[第15章](../part02/ch15-configuration.md)） | 同左 |
| `nvm` / `.nvmrc` | `JAVA_HOME`／toolchain／SDKMAN | `java.toolchain` |
| ESLint / Prettier | Checkstyle・SpotBugs／Spotless（教材では扱わない） | 同左 |

Windowsでは `./mvnw` を `.\mvnw.cmd` に読み替えます。`.jar` はJavaの実行可能ファイルで、`node_modules` ごと1つに固めた成果物に近いものです（[第2章](../part01/ch02-how-java-runs.md)）。

## 12. Webフレームワーク：Express／Next.js ↔ Spring MVC

| Express / Next.js | Spring MVC |
| --- | --- |
| `app.get("/x", handler)` ／ `route.ts` の `GET` | `@GetMapping("/x")` |
| `req.params.id` | `@PathVariable long id` |
| `req.query.verbose` | `@RequestParam boolean verbose` |
| `req.body`（`zod` で検証） | `@Valid @RequestBody Dto`（Bean Validation） |
| `req.get("X-Request-Id")` | `@RequestHeader("X-Request-Id")` |
| `req.cookies.QV_VISITOR` | `@CookieValue("QV_VISITOR")` |
| `res.status(201).json(x)` | `ResponseEntity.created(uri).body(x)` |
| `app.use(mw)` ／ `middleware.ts` | `Filter`（`OncePerRequestFilter`） |
| エラー処理ミドルウェア | `@RestControllerAdvice` ＋ `@ExceptionHandler` |
| `fetch()`（サーバー側） | `RestClient` |
| `process.env.X` | `@ConfigurationProperties` |

=== "Express"

    ```ts
    router.get("/polls/:id", (req, res) => {
      const poll = store.get(Number(req.params.id))
      if (!poll) return res.status(404).json({ code: "POLL_NOT_FOUND" })
      res.json(poll)
    })
    router.post("/polls", (req, res) => {
      const body = CreatePoll.parse(req.body)          // zod：失敗は例外 → 400
      res.status(201).location(`/api/polls/${id}`).json(created)
    })
    ```

=== "Spring MVC"

    ```java title="code/labs/appendix-ts-java/spring-web/.../PollController.java（抜粋）"
    @GetMapping("/polls/{id}")
    PollView get(@PathVariable long id) {
        PollView p = store.get(id);
        if (p == null) throw new PollNotFoundException(id);   // → @RestControllerAdvice が404に変換
        return p;
    }

    @PostMapping("/polls")
    ResponseEntity<PollView> create(@Valid @RequestBody CreatePoll body) {
        // 検証に失敗すると、このメソッドに入る前に400で返る
        return ResponseEntity.created(URI.create("/api/polls/" + id)).body(created);
    }
    ```

差が出るのは「入口の検証」と「エラーの出口」です。Expressではハンドラの中で `parse` し、失敗を自分で変換します。Springは**メソッドに入る前に検証が走り**、例外は**1か所（`@RestControllerAdvice`）で一括変換**します。

```java title="code/labs/appendix-ts-java/spring-web/.../ApiErrors.java（抜粋）"
@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(PollNotFoundException.class)
    ProblemDetail notFound(PollNotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setProperty("code", "POLL_NOT_FOUND");
        return pd;
    }
}
```

テストは supertest と同じ感覚で書けます。次の4件（作成201、空タイトル400、存在しない404、フィルターのヘッダー）が、Spring Boot 4.1.2 で通ることを確認しました。

```java
var result = mvc.post().uri("/api/polls")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"\"}")
        .exchange();
assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
```

詳しい流れは[第12章](../part02/ch12-request-journey.md)（リクエストの旅）、検証とエラーは[第14章](../part02/ch14-validation-errors.md)です。

## 13. 引っかかりやすい違い 10

1. `==` はオブジェクトの同一性です。文字列・値の比較は `equals`
2. `int` には `null` が入りません。`Integer` は入ります
3. `List<int>` は書けません。`List<Integer>`（ボクシング）です
4. 修飾子なしは `public` ではなく「同じパッケージだけ」です
5. メソッドの引数は常に値渡し（参照型は参照の値渡し）です
6. `final` は不変ではありません
7. ジェネリクスの型は実行時に消えます（`new T()` は書けません）
8. checked例外は `throws` に書かないとコンパイルエラーです
9. 配列・リストの範囲外は `undefined` ではなく例外です
10. 依存の版は自分で選ばず、BOMが決めます
