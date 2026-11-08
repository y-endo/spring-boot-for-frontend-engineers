# 第1部 TypeScript脳で読むJava

<!-- part-intro:start -->
<div class="video-card">
  <video controls preload="metadata" poster="../assets/videos/part01-intro.jpg">
    <source src="../assets/videos/part01-intro.mp4" type="video/mp4">
  </video>
  <p class="video-caption">▶ 第1部でやること（約1分）</p>
  <p class="video-credit">音声：VOICEVOX:冥鳴ひまり</p>
</div>

??? note "字幕全文"
    第1部はTypeScript脳で読むJavaです。
    TypeScriptの知識に違いだけを足します。
    TypeScriptなら読めても、Javaは知らない書き方だらけです。
    型の考え方も例外の扱いも、少しずつ違います。
    第2章はJavaが動くまで。
    javacで.classを作り、JVMで動かします。
    第3章は型の世界。
    Javaの型は形ではなく、名前で決まります。
    第4章はデータの形。
    record、enum、sealedで投票の状態を型にします。
    switchの書き漏れはコンパイルエラーになります。
    第5章はnullと例外。
    NPEを起こして読み方を覚えます。
    第6章はコレクションとStream。
    mapやfilterとの対応で読めるようにします。
    第7章は値の落とし穴。
    得票率の合計が100%にならない事故を直します。
    第8章はJUnit。Springなしで最初のテストを書きます。
    第9章はスレッド。
    共有した値を、同時に書き換えると壊れることがあります。
    この部が終わると、投票の状態と得票率を素のJavaで書けます。
    テストで確かめることもできます。
    JavaはTypeScriptとの違いで読む。
<!-- part-intro:end -->

Springを学ぶ前に、**Javaそのものを読める状態**にします。ただし、Javaの文法を最初から順に教えることはしません。TypeScriptを12年読み書きしてきた経験の上に、Javaの考え方を差分として足していきます。

## この部でできるようになること

- `javac` → `.class` → JVM という実行の流れを、図で説明できる
- Javaの型（名前で決まる型）を、TypeScriptの型と対比して読める
- `record`・`enum`・`sealed`・`switch` で、QuickVoteの状態を型で表せる
- `null` と例外の扱い方を、レビューで指摘できる
- コレクション・ラムダ・Streamで、投票の集計が書ける
- `==` と `equals`、`double` と `BigDecimal`、`Instant` と `LocalDateTime`、文字数とバイト数の違いを、実際の事故として説明できる
- JUnit と AssertJ で、最初のテストを書ける
- サーバーが「同時に動く」ことと、共有状態の危険を、動かして確かめられる

## 各章

<div class="grid cards" markdown>

-   :material-engine-outline: **[第2章 Javaが動くまで](ch02-how-java-runs.md)**

    ---

    `javac`、`.class`、JVM、jar、クラスパス。「Hello」の裏側を図で追います。

-   :material-shape-outline: **[第3章 型の世界](ch03-types.md)**

    ---

    名前で決まる型、プリミティブと参照、class・interface・継承、パッケージ。

-   :material-source-branch: **[第4章 データの形](ch04-data-shapes.md)**

    ---

    record・enum・sealed・switch。TypeScriptの判別可能ユニオンをJavaで書きます。

-   :material-null: **[第5章 nullと例外](ch05-null-exceptions.md)**

    ---

    NPEを起こして読む。Optional、checked/unchecked、try-with-resources。

-   :material-format-list-bulleted: **[第6章 コレクション・ジェネリクス・ラムダ・Stream](ch06-collections-streams.md)**

    ---

    List/Set/Map、不変コレクション、型消去、`map/filter/reduce`との対応。

-   :material-alert-octagon-outline: **[第7章 値の落とし穴](ch07-value-pitfalls.md)**

    ---

    `equals`、`BigDecimal`、時刻、文字。得票率が合計100%にならない事故を直します。

-   :material-test-tube: **[第8章 JUnitで最初のテスト](ch08-junit.md)**

    ---

    Springなしでテスト。境界値、`@ParameterizedTest`、AssertJ。

-   :material-transit-connection-variant: **[第9章 スレッドと共有状態](ch09-threads.md)**

    ---

    サーバーは同時に動く。`counter++` が壊れる様子を観察します。動画つき。

</div>

## 第1部の流れ

```mermaid
flowchart LR
    A["第2章<br/>動く仕組み"] --> B["第3章<br/>型"]
    B --> C["第4章<br/>データの形"]
    C --> D["第5章<br/>null・例外"]
    D --> E["第6章<br/>コレクション"]
    E --> F["第7章<br/>値の落とし穴"]
    F --> G["第8章<br/>テスト"]
    G --> H["第9章<br/>スレッド"]
```

第7章で作る得票率の計算と、第4章で定義する投票の状態は、どちらも第8章でテストします。第9章は、第2部で扱うDIコンテナ（Beanは全員で共有される）を理解するための土台です。

## 実験コードの置き場

この部のコードは、`code/java-basics/` にあります。

```text
code/java-basics/
├── ch02/   Hello.java など
├── ch03/   DuckLicense.java など
├── ...
├── ch07/   Percentages.java など
├── ch08-junit/   Maven Wrapper つきのテスト用プロジェクト
└── ch09/   LostUpdate.java など
```

ほとんどが `java ファイル名.java` で単体実行できます。

## 第1部のチェックリスト

第9章を終えたら、次の項目を確かめてください。**すべてに ✅ を付けられたら、第2部へ進めます。** 目安は「③ 検証できる」（反例を作って確かめられる）です。

- [ ] `.java` から `.class`、JVM、jar までの流れを、図を描いて説明できる
- [ ] `javac` のエラー、`ClassNotFoundException`、実行時例外を、どの段階で起きたかで区別できる
- [ ] TypeScriptの構造的型付けと、Javaの名前的型付けの違いを、コード例つきで説明できる
- [ ] プリミティブ型と参照型の違いを説明し、`final` が「再代入禁止」であって「不変」ではないことを例で示せる
- [ ] `sealed interface` と `record` で状態を表し、`switch` の網羅漏れがコンパイルエラーになることを確かめられる
- [ ] `record` の中に可変な `List` を持つ危険を指摘し、防御的コピーで直せる
- [ ] NPEのメッセージを読んで原因の行を特定でき、`Optional` を戻り値専用に使う理由を説明できる
- [ ] checked例外とunchecked例外の違い、例外を握りつぶすことの危険を説明できる
- [ ] `try-with-resources` が何を保証するか説明できる
- [ ] `List.of` が不変であること、`Arrays.asList` が固定長であることを知っていて、ループ中の削除を安全に書ける
- [ ] `==` と `equals` の違い、`equals` と `hashCode` をセットで書く理由を、`HashSet` の実験で示せる
- [ ] `double` の誤差と `BigDecimal` の使い分けを説明し、得票率を合計100%にする計算（最大剰余法）を説明できる
- [ ] `Instant` と `LocalDateTime` の違いを説明し、タイムゾーンのずれで起きる事故を再現できる
- [ ] JUnit と AssertJ で、正常・境界・異常のテストを書き、AIが書いたコードの誤りをテストで示せる
- [ ] 共有フィールドへの `counter++` が壊れる理由を説明し、`AtomicInteger` や `ConcurrentHashMap` で直せる

!!! tip "チェックの付け方"
    項目を読んで「たぶん分かる」と思っただけでは、チェックを付けません。白紙に自分の言葉で書いて説明できたときに付けてください。
    説明できない項目があれば、戻る先の章へのリンクが各章の「まとめ」にあります。

## 統合課題：Spring なしで「得票率」と「投票の状態」を作る

Spring を使わず、素の Java（`code/java-basics/` と同じ作り）で、次を実装してください。

1. 投票の状態を `sealed interface` と `record` で表す（例：募集中／締切済み。締切済みは締切の時刻を持つ）。状態の変化は「募集中 → 締切済み」だけにし、締切済みをもう一度締め切ろうとしたら例外にする
2. 選択肢ごとの票数から、得票率（％）を `BigDecimal` と最大剰余法で計算する。合計はちょうど100になり、0票のときは全選択肢が0％になるようにする
3. JUnit と AssertJ で、正常・境界・異常を固定する。境界は「0票」「同票」「3等分で割り切れない」、異常は「締切済みをもう一度締める」を必ず入れる

??? success "解答の観点"
    - 型：状態を `boolean closed` ではなく `sealed interface` ＋ `record` で表せているか。`switch` で網羅漏れがコンパイルエラーになるか（第4章）
    - 値：得票率の計算に `double` を使っていないか。端数の配り方が決まっていて、同票でも結果が毎回同じか（第7章）
    - 時刻：「いま」を内部で取らず、`Instant` を引数で受け取っているか。受け取っていればテストで固定できる（第7章）
    - 例外：状態の矛盾を握りつぶさず、メッセージ付きの例外にしているか（第5章）
    - テスト：0票・同票・3等分が、期待値つきのテスト（`@ParameterizedTest` でもよい）になっているか。実装を書き換えたら、どれかが赤くなるか（第8章）
