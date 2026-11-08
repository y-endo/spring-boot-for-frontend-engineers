# 第21章の実験：H2 で通っても安心しない

本線のマイグレーション（`../part03-quickvote-jdbc/.../db/migration`）と、本線の upsert の SQL を、PostgreSQL 互換モードの H2 に流します。

```bash
./mvnw test      # Windows は .\mvnw.cmd。[demo] の行に、H2 が返したエラーが出る
```

H2 は、このプロジェクトのテスト範囲でしか使いません（本線は PostgreSQL ＋ Testcontainers）。
