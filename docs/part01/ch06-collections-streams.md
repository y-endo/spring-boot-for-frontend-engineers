# 第6章 コレクション・ジェネリクス・ラムダ・Stream

<div class="goal-box" markdown>

この章でできるようになること

- `List` / `Set` / `Map` を、TypeScriptの `Array` / `Set` / `Map` と対応づけて使い分けられる
- `List.of`、`Arrays.asList`、`unmodifiableList`、`copyOf` の違いを説明できる
- ジェネリクスの型消去と、`List<Object>` が `List<String>` の親にならない理由を説明できる
- ラムダ式とメソッド参照を読み書きでき、外側の変数を更新できない理由を言える
- 投票の集計を、`for` と `Stream` の両方で書き、使い分けの理由を説明できる

</div>

## 1. 問いかけ：票を数える

QuickVoteの中心的な仕事の1つが集計です。投票のリストから、選択肢ごとの票数を出します。

TypeScriptなら、次のように書きます。

```ts
type Vote = { voter: string; option: string }

const tally = votes.reduce<Record<string, number>>((acc, v) => {
  acc[v.option] = (acc[v.option] ?? 0) + 1
  return acc
}, {})
```

同じことを、Javaでは次のように書けます。

```java
Map<String, Long> tally = votes.stream()
    .collect(Collectors.groupingBy(Vote::option, Collectors.counting()));
```

見た目は違いますが、考え方は同じです。この章の目標は、このコードを読めて、書けるようになることです。そのために、道具箱（コレクション）、型の安全（ジェネリクス）、関数の渡し方（ラムダ）、処理の流れ（Stream）の順に進めます。

## 2. コレクション：3つの基本

| | TypeScript | Java | 特徴 |
| --- | --- | --- | --- |
| 順序のある並び | `Array` | `List` | 重複できる。位置で取り出す |
| 重複しない集まり | `Set` | `Set` | 重複を入れても1つ |
| キーと値 | `Map` / `Record` | `Map` | キーで取り出す |

Javaでは、`List` や `Map` は interface（約束）で、実体は別の実装クラスです。

| interface | 実装 | 選ぶ理由 |
| --- | --- | --- |
| `List` | `ArrayList` | 標準。迷ったらこれ |
| `Set` | `HashSet` | 標準。順序は保証されない |
| `Map` | `HashMap` | 標準。順序は保証されない |
| `Map` | `LinkedHashMap` | 入れた順を保つ |
| `Map` | `TreeMap` | キーの順に並べて保つ |

```java
List<String> names = new ArrayList<>(List.of("alice", "bob", "alice"));    // (1)!
Set<String> unique = new HashSet<>(names);
Map<String, Integer> votes = new HashMap<>();
for (String n : names) {
    votes.merge(n, 1, Integer::sum);                 // (2)!
}
```

1. 変数の型は interface（`List`）で、右辺は実装（`ArrayList`）。第3章の「変数の型と実体は違ってよい」の実践になっている。`<>` は、型引数を左辺から推論させる書き方
2. `merge(キー, 初期値, 結合関数)`。キーがなければ初期値を入れ、あれば結合関数で合算する。JSの `m[k] = (m[k] ?? 0) + 1` に当たる

```text
List = [alice, bob, alice] size=3
Set size = 2
Map = {alice=2, bob=1}
```

### 動かしてみる：Map の順序

同じ4つのキーを3種類の `Map` に入れて、順番を見ます。

```java
for (String k : List.of("zebra", "apple", "mango", "banana")) {
    hash.put(k, k.length());
    linked.put(k, k.length());
    tree.put(k, k.length());
}
```

```text
HashMap       = {zebra=5, banana=6, apple=5, mango=5}
LinkedHashMap = {zebra=5, apple=5, mango=5, banana=6}
TreeMap       = {apple=5, banana=6, mango=5, zebra=5}
```

| 実装 | 順序 |
| --- | --- |
| `HashMap` | 入れた順ではない。どんな順になるかは実装次第で、保証はない |
| `LinkedHashMap` | 入れた順 |
| `TreeMap` | キーの昇順 |

!!! warning "よくある間違い：HashMap の順序に頼る"
    JavaScriptの `Map` と `Object` は、キーを入れた順に取り出せます。そのつもりで `HashMap` を使うと、環境やJavaの版が変わったときだけ順番が変わり、テストが壊れます。
    画面に出す順番が大事なら、`LinkedHashMap`（入れた順）か `TreeMap`（キー順）を使うか、リストに入れて明示的に並べ替えます。

### `computeIfAbsent`：Map の中に List を作る

```java
Map<Integer, List<String>> byLength = new TreeMap<>();
for (String k : linked.keySet()) {
    byLength.computeIfAbsent(k.length(), _ -> new ArrayList<>()).add(k);
}
```

```text
文字数ごと = {5=[zebra, apple, mango], 6=[banana]}
```

キーがなければ新しいリストを作って入れ、そのリストに追加する、という定番の書き方です。`groupBy` のような処理を `for` で書くときによく出てきます。

## 3. 不変コレクションと、「窓」の罠

第4章の `record` で見たとおり、`final` と不変は別物でした。コレクションを変更できなくする方法は複数あり、違いは次の表のとおりです。

| 書き方 | 追加・削除 | 元のリストを変えたとき | 備考 |
| --- | --- | --- | --- |
| `List.of(...)` | 不可（`UnsupportedOperationException`） | （元がない） | 不変。`null` も入れられない |
| `Arrays.asList(...)` | 不可 | （配列に連動） | 置換（`set`）はできる。固定長 |
| `Collections.unmodifiableList(x)` | 不可 | 変わった結果が見える | 元のリストを包んだ、読み取り専用の窓 |
| `List.copyOf(x)` | 不可 | 影響しない | 独立した不変のコピー |

実際に確かめます。

```java
List<String> fixed = List.of("a", "b");
try {
    fixed.add("c");
} catch (UnsupportedOperationException e) {
    IO.println("List.of(...).add → UnsupportedOperationException");
}

List<String> original = new ArrayList<>(List.of("a", "b"));
List<String> view = Collections.unmodifiableList(original);   // 「読み取り専用の窓」
List<String> copy = List.copyOf(original);                     // 独立した不変コピー
original.add("c");
IO.println("view = " + view + " / copy = " + copy);

List<String> fixedSize = Arrays.asList("x", "y");
fixedSize.set(0, "X");                                          // 置換はOK
try {
    fixedSize.add("z");                                         // 追加はNG
} catch (UnsupportedOperationException e) {
    IO.println("Arrays.asList(...).add → UnsupportedOperationException / 内容 = " + fixedSize);
}
```

```text
List.of(...).add → UnsupportedOperationException
view = [a, b, c] / copy = [a, b]
Arrays.asList(...).add → UnsupportedOperationException / 内容 = [X, y]
```

`view`（窓）には、あとから追加された `c` が見えています。`unmodifiableList` を返しているから安全だと思っても、元のリストが別の場所で変更されれば、中身は変わります。外に渡すときは `List.copyOf` を使うのが安全です。

### ループ中の削除

```java
List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4));
try {
    for (Integer n : nums) {
        if (n == 2) nums.remove(n);
    }
} catch (ConcurrentModificationException e) {
    IO.println("for-each 中の remove → ConcurrentModificationException");
}
nums.removeIf(n -> n % 2 == 0);                                 // こう書く
IO.println("removeIf 後 = " + nums);
```

```text
for-each 中の remove → ConcurrentModificationException
removeIf 後 = [1, 3]
```

`for-each` で回している最中にそのリストを変更すると、例外になります。条件で消すなら `removeIf` を使います。

!!! note "例外にならない場合もある"
    「最後から2番目の要素を消すと、例外にならない」という紛らわしい挙動があります。運よく通っても、正しいコードではありません。変更する必要があるなら、`removeIf` を使うか、新しいリストを作ります。

!!! note "「ConcurrentModificationException」は並行処理の話ではない"
    名前に `Concurrent` と付いていますが、1つのスレッドでも起きます。複数スレッドでの共有（第9章）とは別の話です。

## 4. ジェネリクス：型を引数にする

`List<String>` の `<String>` がジェネリクス（型引数）で、TypeScriptの `Array<string>` と同じ役割をします。

```java title="ch06/Generics.java（抜粋）"
record Box<T>(T value) {                                // ジェネリクスなレコード
    <R> Box<R> map(java.util.function.Function<T, R> f) {
        return new Box<>(f.apply(value));
    }
}

<T> T firstOrThrow(List<T> list) {                      // ジェネリックメソッド
    if (list.isEmpty()) throw new NoSuchElementException("空です");
    return list.get(0);
}
```

```text
Box[value=QuickVote] → Box[value=9]
first = a
```

`firstOrThrow(List.of("a", "b"))` は `String` を返し、キャストは要りません。コンパイラが、型引数から戻り値の型を決めてくれるからです。

| | TypeScript | Java |
| --- | --- | --- |
| クラス | `class Box<T> {}` | `class Box<T> {}` |
| 関数 | `function f<T>(x: T): T` | `<T> T f(T x)`（`<T>` は戻り値の型の前に置く） |
| 制約 | `T extends Foo` | `T extends Foo`（同じ） |

### 型消去：実行時には `<String>` は消えている

!!! note "用語：型消去（type erasure）"
    Javaのジェネリクスが働くのは、コンパイルのときだけです。実行時の `.class` からは、`<String>` のような型引数が消えています。
    ジェネリクスが導入される前に作られた既存のコードとの互換性を保つための設計です。

TypeScriptでは型がすべて消えますが、Javaで消えるのはジェネリクスの型引数だけです（クラスの型は残ります）。実験してみます。

```java
List<String> strings = new ArrayList<>();
List<Integer> ints = new ArrayList<>();
IO.println("同じクラス? " + (strings.getClass() == ints.getClass()));
```

```text
同じクラス? true
```

実行時には、どちらもただの `ArrayList` です。このため、コンパイラの保護をすり抜ける抜け道ができます。

```java
@SuppressWarnings({"unchecked", "rawtypes"})
List<String> sneaky = (List) ints;      // 型引数を無視した生の型（raw type）に通す
ints.add(42);
try {
    String oops = sneaky.get(0);        // 取り出し時に初めて ClassCastException
    IO.println(oops);
} catch (ClassCastException e) {
    IO.println("ClassCastException: " + e.getMessage());
}
```

```text
ClassCastException: class java.lang.Integer cannot be cast to class java.lang.String (java.lang.Integer and java.lang.String are in module java.base of loader 'bootstrap')
```

`String` として取り出した瞬間に、実際の型が `Integer` だと分かり、実行時に例外になりました。この抜け道の入口は、コンパイラの警告（`unchecked`、`rawtypes`）を抑制している部分です。

!!! tip "現場での目安"
    - 生の型（`List` だけ、`<>` なし）を書かない
    - `@SuppressWarnings("unchecked")` を見たら、なぜ必要か確認する
    - JSONの読み込み（`Map<String, Object>` へのキャスト）で、この問題に実際に出会う（第13章）

### `List<Object>` は `List<String>` の親ではない

```java
// List<Object> objs = new ArrayList<String>();     // ← コンパイルエラー
```

`String` は `Object` の子ですが、`List<String>` は `List<Object>` の子ではありません。代入できてしまうと、`objs.add(42)` で `String` のリストに `Integer` を入れられるからです。

読むだけなら安全なので、`Number` の仲間のリストなら何でも受け取る、という書き方があります。

```java
double sum(List<? extends Number> numbers) {            // 境界付き（読み取り専用の窓）
    double s = 0;
    for (Number n : numbers) s += n.doubleValue();
    return s;
}

// sum(List.of(1, 2, 3)) → 6.0 、 sum(List.of(1.5, 2.5)) → 4.0
```

`? extends Number` は「`Number` またはその子の何か」を表します。取り出すことはできても追加はできない、読み取り専用の窓と考えてください。

!!! tip "フロントエンドで例えると"
    TypeScriptの配列は、`Dog[]` を `Animal[]` に代入できます（共変）。便利ですが、`animals.push(new Cat())` で壊れる抜け穴があります。
    Javaのジェネリクスは、この抜け穴をあえて塞いでいて、`? extends` は読むだけなら許すための手段です。

## 5. ラムダ式：関数を渡す

TypeScriptの `(x) => x * 2` に当たるものが、Javaにもあります。

=== "TypeScript"

    ```ts
    const length = (s: string) => s.length
    const isPositive = (n: number) => n > 0
    ```

=== "Java"

    ```java
    Function<String, Integer> length = s -> s.length();
    Function<String, Integer> length2 = String::length;   // メソッド参照
    Predicate<Integer> positive = n -> n > 0;
    ```

Javaでは、関数の型を「関数型インターフェース」で決めます。TypeScriptの `(s: string) => number` に当たる型を、Javaは `Function<String, Integer>` のような既存の interface で表します。

| 関数型インターフェース | 形 | TypeScriptでの見た目 |
| --- | --- | --- |
| `Function<T, R>` | `T → R` | `(t: T) => R` |
| `Predicate<T>` | `T → boolean` | `(t: T) => boolean` |
| `Supplier<T>` | `() → T` | `() => T` |
| `Consumer<T>` | `T → ()` | `(t: T) => void` |
| `Comparator<T>` | `(T, T) → int` | `(a: T, b: T) => number` |
| `Runnable` | `() → ()` | `() => void` |

!!! note "用語：メソッド参照"
    `String::length` のように、既存のメソッドをラムダの代わりに名前で渡す書き方です。`s -> s.length()` と同じ意味になります。`::` の左がクラス（または変数）、右がメソッド名です。

### 壊してみる：外側の変数を更新する

JavaScriptでは普通に書ける、次のコードを試します。

```java title="ch06/LambdaCapture.java"
List<Integer> nums = List.of(1, 2, 3);
int sum = 0;
nums.forEach(n -> sum += n);        // JavaScript なら普通に書ける
IO.println(sum);
```

```text
LambdaCapture.java:6: エラー: ラムダ式から参照されるローカル変数は、finalまたは事実上のfinalである必要があります
    nums.forEach(n -> sum += n);        // JavaScript なら普通に書ける
                      ^
エラー1個
```

ラムダの中から、外側の変数を書き換えることはできません。参照できるのは、再代入されない変数（事実上のfinal）だけです。

理由は、ラムダが別のスレッドで、あとから実行されるかもしれないことにあります。外側のローカル変数は、呼び出しが終わったあとには存在しないので、そこへの書き換えを許すと整合性が取れなくなります。そこでJavaは、読むだけの変数のコピーをラムダに持たせて、安全にしています。

書き方としては、次のようにします。

```java
IO.println("sum = " + nums.stream().mapToInt(Integer::intValue).sum());   // 合計は stream で書く

var box = new AtomicInteger();               // どうしても必要なら、箱を使う
nums.forEach(box::addAndGet);
```

```text
sum = 6
box = 6
```

ただし、`AtomicInteger` などの箱を使う書き方は最終手段です。多くの場合、`Stream` の `sum()` や `reduce` を使えば、変数を更新せずに書けます。

## 6. Stream：処理の流れを書く

### 対応表

`Stream` は、TypeScriptの `array.map().filter().reduce()` に当たるものです。

| TypeScript | Java Stream |
| --- | --- |
| `arr.filter(f)` | `list.stream().filter(f)` |
| `arr.map(f)` | `.map(f)` |
| `arr.reduce(f, init)` | `.reduce(init, f)` |
| `arr.sort(cmp)` | `.sorted(cmp)` |
| `arr.slice(0, n)` | `.limit(n)` |
| `arr.some(f)` | `.anyMatch(f)` |
| `arr.every(f)` | `.allMatch(f)` |
| `arr.find(f)` | `.filter(f).findFirst()`（`Optional` を返す） |
| （配列に戻る） | `.toList()` |

大きな違いが2つあります。

1. `.stream()` で始めて、`.toList()` などで終えます。途中の `map` や `filter` はすぐには実行されず、最後の操作（終端操作）で初めて実行されます（遅延評価）
2. Stream は1回しか使えません

### 動かしてみる

```java title="ch06/Streams.java（抜粋）"
record Vote(String voter, String option) {}

List<Vote> votes = List.of(
    new Vote("alice", "カレー"), new Vote("bob", "ラーメン"), new Vote("carol", "カレー"),
    new Vote("dave", "カレー"), new Vote("erin", "そば"), new Vote("frank", "ラーメン"));

List<String> curryVoters = votes.stream()
    .filter(v -> v.option().equals("カレー"))
    .map(Vote::voter)
    .toList();                                       // 不変リスト
IO.println("カレー派 = " + curryVoters);

Map<String, Long> tally = votes.stream()
    .collect(Collectors.groupingBy(Vote::option, Collectors.counting()));
IO.println("集計 = " + new TreeMap<>(tally));

tally.entrySet().stream()
    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
            .thenComparing(Map.Entry.comparingByKey()))
    .forEach(e -> IO.println("  " + e.getKey() + " : " + e.getValue()));
```

```text
カレー派 = [alice, carol, dave]
集計 = {そば=1, カレー=3, ラーメン=2}
  カレー : 3
  ラーメン : 2
  そば : 1
```

3つ目の `sorted(...)` は、票数の多い順に並べ、同じ票数ならキー順にします。`Comparator` の組み立ては少し読みにくいのですが、`reversed()`（逆順）と `thenComparing(...)`（同じときの次の基準）で、ルールを重ねています。

### 遅延評価と1回きり

```java
Stream<Integer> s = Stream.of(1, 2, 3).peek(n -> IO.println("  peek " + n));
IO.println("Stream を作っただけ（まだ何も実行されていない）");
s.toList();
try {
    s.toList();
} catch (IllegalStateException e) {
    IO.println("2回目 → " + e.getMessage());
}
```

```text
Stream を作っただけ（まだ何も実行されていない）
  peek 1
  peek 2
  peek 3
2回目 → stream has already been operated upon or closed
```

`peek` は、作った時点では動かず、`toList()`（終端操作）で初めて動きました。2回目の `toList()` は例外になります。`Stream` は再利用できないので、使い回したいなら結果を `List` に入れておきます。

### 同じ処理を `for` で書く

```java
Map<String, Integer> byFor = new TreeMap<>();
for (Vote v : votes) {
    byFor.merge(v.option(), 1, Integer::sum);
}
IO.println("for 版 = " + byFor);
```

```text
for 版 = {そば=1, カレー=3, ラーメン=2}
```

結果は同じです。では、どちらを使えばよいでしょうか。

| | `for` | `Stream` |
| --- | --- | --- |
| 向いている処理 | 途中で `break`・`return`、複数の変数の更新、例外処理 | 「変換→絞り込み→集計」の一本道 |
| デバッグ | ステップ実行が追いやすい | 1行が長くなり、追いにくい |
| 読みやすさ | 手順が上から下に並ぶ | 意図を宣言的に書ける |
| 向かない処理 | 複雑な変換の連鎖 | 副作用（外側の変数の更新、DBの書き込み） |

使い分けの目安は、次のとおりです。

- Stream を使うのは、リストを別のリストや集計に変換するだけで、3〜4段で収まるとき
- for を使うのは、途中で抜ける、複数の結果を同時に作る、チェック例外を扱う、ステップ実行で追いたい、といったとき
- Stream の中で、外側の状態を変更しない（`forEach` で外のリストに追加するなど）。ラムダのルールに反し、並列にすると壊れる

!!! warning "Stream の落とし穴"
    ```java
    votes.stream().collect(Collectors.toMap(Vote::voter, Vote::option));
    ```
    `toMap` は、キーが重複すると例外を投げます。
    ```text
    toMap → Duplicate key alice (attempted merging values カレー and ラーメン)
    ```
    重複を許すなら、第3引数に結合関数（`(a, b) -> b`＝後勝ち）を渡します。AIが書いた `toMap` は、キーが本当に一意かを必ず確認します。

!!! warning "`parallelStream()` は使わない"
    `parallelStream()` は処理を並列に実行しますが、共有のスレッドプールを使います。サーバーでは他のリクエストと競合して、かえって遅くなったり、共有状態が壊れたりします。第9章の知識を得てから、慎重に判断します。

!!! tip "フロントエンドで例えると"
    JavaScriptの配列メソッドはすぐに実行され、新しい配列を作ります。Javaの Stream は、最後まで繋いでから1回だけ実行するパイプラインです。
    メソッドチェーンの見た目は同じでも、`.toList()` を忘れると何も起きません。最も戸惑うのはこの点です。

### コレクションの選び方：迷ったときの早見表

| やりたいこと | 選ぶもの |
| --- | --- |
| 順番どおりに並べたい、位置で取り出したい | `ArrayList`（`List`） |
| 重複を除きたい、「含まれるか」を調べたい | `HashSet`（`Set`） |
| キーで素早く取り出したい | `HashMap`（`Map`） |
| 入れた順序で取り出したい | `LinkedHashMap` / `LinkedHashSet` |
| キーの順に並べて保ちたい | `TreeMap` / `TreeSet` |
| 変更させたくない定数 | `List.of(...)` / `Map.of(...)` / `Set.of(...)` |
| 外に渡す（呼び出し側に変更させたくない） | `List.copyOf(...)` |
| 複数スレッドで共有して書き込む | `ConcurrentHashMap`（第9章） |

## 7. 現場で：AIが書いたコードを読むとき

| 見るもの | 疑う理由 |
| --- | --- |
| `HashMap` を、順序が大事な場面で使っている | 順序は保証されない。`LinkedHashMap` / `TreeMap` にする |
| 外に `List` を返している | 呼び出し側から書き換えられる。`List.copyOf` と `Collections.unmodifiableList`（窓）のどちらかを確認 |
| 生の型（`List list`）、`@SuppressWarnings("unchecked")` | 型の保護がない |
| `.collect(Collectors.toList())` | 古い書き方。`.toList()` のほうが不変で簡潔 |
| Stream が5段以上、ネスト | 読めない。`for` か、名前を付けた補助メソッドに分ける |
| Stream の中でDBアクセス | 1件ずつ問い合わせる「N+1」のもと（第40章） |
| `toMap` | キー重複の例外 |
| 外側の `List` / カウンタをラムダ内で更新 | コンパイルできない。無理に回避していたら、設計を疑う |
| `HashMap` / `ArrayList` を複数スレッドで共有 | 壊れる（第9章） |

---

## まとめ：3点セット

!!! abstract "この章の3点セット"
    **① 一言で言うと**
    `List` / `Set` / `Map` は interface で、`ArrayList` / `HashSet` / `HashMap` が標準の実装。不変にするには `List.of` / `List.copyOf` を使い、`unmodifiableList` は元が変わると見える「窓」。ジェネリクスの型引数は実行時に消える（型消去）。ラムダは関数型インターフェースに代入され、外側の変数を更新できない。Stream は終端操作で1度だけ実行される遅延パイプライン。

    **② 現場でこう聞かれる**
    「`Collections.unmodifiableList` を返しているのに、呼び出し側のリストが変わっています。なぜですか？」
    → 元のリストを包んだ読み取り専用の窓なので、元のリストを他で変更すると、窓から見える中身も変わる。独立した不変コピーが欲しいなら `List.copyOf` を使う。

    **③ 自分のサービスならどこに当たるか**
    自分のサービスで「集計」が必要な場面を1つ挙げ、`for` と `Stream` のどちらで書くか、理由つきで決める。

## 演習

??? question "確認問題1：どの List.of か"
    次の3つの呼び出しで、例外になるものはどれですか。予測してから実行してください。

    ```java
    var a = List.of("x", "y");
    a.set(0, "z");              // (1)

    var b = Arrays.asList("x", "y");
    b.set(0, "z");              // (2)
    b.add("w");                 // (3)
    ```

??? success "解答"
    (1) と (3) が `UnsupportedOperationException` です。(2) は成功します。
    `List.of` は完全に不変で、`set` もできません。`Arrays.asList` は固定長で、置換はできても、追加・削除はできません。

??? question "確認問題2：集計を書く"
    `List<Vote> votes` から、投票者ごとの投票数（`Map<String, Long>`）を、Stream で作ってください。さらに、`for` と `merge` でも書いてください。

??? success "解答"
    ```java
    Map<String, Long> byVoter = votes.stream()
        .collect(Collectors.groupingBy(Vote::voter, Collectors.counting()));

    Map<String, Integer> byFor = new HashMap<>();
    for (Vote v : votes) {
        byFor.merge(v.voter(), 1, Integer::sum);
    }
    ```
    `counting()` が返すのは `Long` ですが、`merge` の `Integer::sum` は `Integer` になります。型が違うので、そのまま比較できない点に注意してください。

??? question "確認問題3：ラムダの制約"
    「ラムダの中から外側のローカル変数を更新できない」理由を、自分の言葉で説明してください。

??? success "解答"
    ラムダは、定義した場所とは別のタイミング・別のスレッドで実行されうるからです。ローカル変数はメソッドの終了とともになくなるため、あとから実行されるラムダが書き換えることはできません。そこでJavaは、ラムダに値のコピーを持たせ、コピー元が変わらないこと（final または事実上のfinal）を要求します。

??? question "応用：QuickVoteの小さな変更"
    票数の降順（同数なら選択肢名の昇順）で並べた `List<String>`（`"カレー:3"` の形式）を作ってください。

??? success "解答"
    ```java
    List<String> ranking = tally.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .map(e -> e.getKey() + ":" + e.getValue())
        .toList();
    ```

??? question "説明問題：現場でこう聞かれる"
    「Streamと`for`は、どう使い分けていますか？」200字程度で答えてください。

??? success "解答"
    例：リストを変換・絞り込み・集計するだけの一本道の処理は、意図が読み取りやすいStreamで書きます。途中で抜ける、複数の状態を更新する、チェック例外を扱う、ステップ実行で追いたい、といった場合は`for`を使います。Streamの中で外側の状態を変更したり、DBアクセスを呼んだりはしません。段数が増えて読みにくくなったら、`for`や名前付きのメソッドに分けます。
