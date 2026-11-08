# 第47章 レビュー演習（欠陥入りコード3本）

AIが書いたようなコードを**レビューする**ための演習です。どの欠陥入りコードも、コンパイルでき、一見きれいに動きます。
詳しい手順と解答は、教材の「第47章 AIと組むレビュー術」にあります。

| プロジェクト | レビュー対象 | 主な観点 | 要 Docker |
| --- | --- | --- | --- |
| `review1-transaction` | `src/main/java/.../review1/bad/` | 同時実行・トランザクション | 要 |
| `review2-boundary` | `src/main/java/.../review2/bad/` | 認可・ブラウザ境界・ログ | 不要 |
| `review3-api-sse` | `src/main/java/.../review3/bad/` | API契約・エラー応答・SSE・共有状態 | 要 |

各プロジェクトは次の構成です。

```text
src/main/java/.../bad/      ← レビューする対象（欠陥入り）
src/main/java/.../infra/    ← 共通の部品（レビュー対象ではない）
src/main/java/.../fixed/    ← 修正版（レビューが終わるまで開かない）
src/test/java/...           ← 守る約束をテストにしたもの（bad と fixed に同じテストを流す）
```

## 進め方

1. `bad/` のコードだけを読み、レビューコメントを書く（テストは動かさない）
2. 教材の解答と照らし合わせる
3. 欠陥入りコードに約束のテストを流し、**赤くなる**ことを確かめる
4. `fixed/` を読み、**緑になる**ことを確かめる

## 実行

```bash
# 修正版に対するテスト（緑）。Docker Desktop を起動しておく（review2 は不要）
./mvnw test                      # 3プロジェクトすべて
cd review2-boundary && ../mvnw test   # 1つだけ

# 欠陥入りに対するテスト（赤になるのが正しい）
./mvnw test -Dreview.excluded=none -Dgroups=red
```

Windows は `.\mvnw.cmd` に読み替えてください。欠陥入りのテストだけを動かすと、`BUILD FAILURE` になります（意図どおりです）。
`-Dgroups=red` は、`@Tag("red")` を付けた「欠陥入りに対するテスト」だけを選びます。

## grep で拾える範囲を確かめる

```bash
./review-grep.sh review3-api-sse/src/main/java/com/example/review3/bad
./review-grep.sh review3-api-sse/src/main/java/com/example/review3/fixed
```

grep が拾うのは「疑う場所」です。修正版にも当たりが出ます（読んで判断するのがレビューです）。
認可の抜け・競合・冪等性のように、**読まないと分からない欠陥は grep では見つかりません**。

## 環境

Java 25、Spring Boot 4.1.2、PostgreSQL 18（Testcontainers）。ポートは Testcontainers と Spring のランダムポートを使うので、固定のポートは使いません。
