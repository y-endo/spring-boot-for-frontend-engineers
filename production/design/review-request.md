あなたは Java / Spring Boot 4 / Spring Security 7 / PostgreSQL に精通したシニアバックエンドエンジニア兼レビュアーです。
D:/Workspace/web/java-study/code/quickvote/ は、学習教材の「通しの題材」となるリファレンス実装です。教材本文はこのコードを「正しい書き方」として学習者に見せます。
仕様は D:/Workspace/web/java-study/production/CURRICULUM.md の「3. 通しの題材」、実装メモは code/quickvote/IMPLEMENTATION_NOTES.md です。

ファイルは編集せず、レビュー結果だけを日本語で返してください。観点:
1. 正しさのバグ（特に同時実行・ロック・トランザクション境界・冪等性・招待の一度限り・SSEのイベント採番と再送・期限判定・スケジューラ）。具体的な再現シナリオ付きで。
2. セキュリティ（認可漏れ・IDOR・存在の漏えい・Cookie属性・CSRF設定・トークンの扱い・ログへの機密出力・レート制限の抜け）。
3. Spring Boot 4.1 / Security 7 / Jackson 3 として古い書き方・非推奨・誤用。
4. 教材として不適切な箇所（学習者が真似すると危険、過度に複雑、読みにくい、命名が分かりにくい）。
5. 重大度順（高・中・低）に並べ、各指摘にファイルパスと行番号、修正案を付ける。問題がない領域は「問題なし」と明記。
