# 第20章の実験：検索・ページング・インデックス

PostgreSQL 18 に、QuickVote のスキーマ（`part03-quickvote-jdbc` の V1・V2 と同じ）と、実験用のデータ
（投票3万件・選択肢12万件・参加者42万件・票約26万件）を入れて、`EXPLAIN ANALYZE` を読みます。

```bash
./setup.sh                         # コンテナ ch20-pg を 25320 番ポートで起動し、スキーマとデータを入れる（約2分）
./psql.sh < sql/01-join-multiplies.sql
./psql.sh < sql/04-paging.sql      # 以降 05〜08 も同じ形で
docker rm -f ch20-pg               # 後片付け
```

| ファイル | 内容 |
| --- | --- |
| `sql/01-join-multiplies.sql` | JOIN で行が掛け算になる。集計の直し方3つ |
| `sql/02-aggregate-null.sql` | LEFT JOIN と count、NULL の比較、NOT IN の落とし穴 |
| `sql/03-stable-sort.sql` | 同じ時刻が並ぶ列だけで並べると、ページをまたいで重複と欠落が出る |
| `sql/04-paging.sql` | OFFSET と キーセット。インデックスの有無で EXPLAIN ANALYZE |
| `sql/05-offset-shift.sql` | ページの途中でデータが増えると OFFSET は同じ行を2回見せる |
| `sql/06-composite-index.sql` | 複合インデックスの列順 |
| `sql/07-explain-reading.sql` | 統計の有無、本線のクエリ（findWithVoteCounts・findActive）の実行計画 |
| `sql/08-write-cost.sql` | インデックスを増やすと INSERT が遅くなる |

数値は環境で変わります。見るのは桁と形（Seq Scan か Index Scan か、読んだ行数）です。
