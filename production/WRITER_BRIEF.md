# 執筆担当への共通依頼

あなたは「世界一わかりやすい」技術教材の執筆者です。Java/Spring バックエンド教材（MkDocs Material）の担当章を書きます。

## 必ず最初に全文を読むもの

1. `production/STYLE_GUIDE.md`（文体・記法・禁止事項。厳守）
2. `production/CURRICULUM.md`（教材の芯、技術決定、題材QuickVote、章構成、中心の例え。担当章の行を特に）
3. `production/ASSETS.md`（挿絵・動画の名前と埋め込み方）
4. `code/quickvote/IMPLEMENTATION_NOTES.md`（**リファレンス実装で実際に確認したAPI・import・設定キー・ハマりどころ。ここにないAPIを使うときは自分で実際にビルドして確かめる**）
5. `code/quickvote/README.md` と、担当章に関係する `code/quickvote/src` のコード
5.5 `docs/appendix/boot3-to-4.md`（Boot 3→4 の差分を実際にコンパイルして確かめた早見表。例：`HttpStatus.UNPROCESSABLE_ENTITY` は非推奨で `UNPROCESSABLE_CONTENT`、Testcontainers 2 のアーティファクト名など）
6. 品質の手本：`docs/part01/ch03-types.md`、`docs/part01/ch09-threads.md`（既に完成した章。このトーン・密度・構成の工夫に合わせる）
7. `mkdocs.yml`（ファイル名はナビゲーションに合わせる）
8. 担当章に動画があれば、その台本 `production/video/scripts/<ID>.json` と `production/video/STORYBOARDS.md`（動画の要点を本文に書くため。台本がまだ無ければSTORYBOARDSを参照）

作業ディレクトリ：`D:\Workspace\web\java-study`

## 読者

フロントエンド歴12年以上（React/TypeScript実務レベル）、バックエンドは専門外。変数・DB・HTTP・Gitの基礎説明は不要。Java・Spring・サーバー特有の概念は初見として丁寧に。TypeScript/ブラウザ/Node との対比を最大の武器にする。第1部（Java基礎、`docs/part01/`）は既に読み終えている前提。

## 品質要件

- 各章 6,000〜12,000字程度（コード除く目安）。濃く、しかし読みやすく。
- 冒頭に `<div class="goal-box" markdown>` で「この章でできるようになること」。
- 章のタイプ（概念・構築・事故・読解・設計）に合わせて入り方を変える。毎回同じ構成にしない。
- 概念ごとに、テキスト・表・図（Mermaid/SVG）・動画・実行のどれが一番わかるかを判断して選ぶ。同じ種類の図を3つ以上続けない。
- 例え話は CURRICULUM.md の中心の例えに従い、必ず「外れる点」を添える。
- 専門用語は初出で `!!! note "用語：○○"`（2〜4行）。
- 「予測カード」「この時点の保証」「壊してみる」「現場ではこうする」を適宜使う。
- 章末に「まとめ3点セット」（一言で言うと／現場でこう聞かれる／自分のサービスならどこに当たるか）と演習（確認・応用・説明。解答は `??? success "解答"`）。
- 部の最後の章の後ろ（または部の index.md）に「第N部のチェックリスト」（can-do文、`- [ ]`、10〜15項目）。
- 部の扉 `docs/partNN/index.md`：部の導入動画、この部で何ができるようになるか、アプリの到達状態、各章へのカードリンク（`<div class="grid cards" markdown>`）。手本は `docs/part01/index.md`。
- 動画がある章は ASSETS.md の形式で埋め込み、直後に「動画の要点」を箇条書き3〜5行で書く。
- 挿絵は ASSETS.md の一覧にあるものだけ使う。技術図は自分で SVG（`docs/assets/diagrams/chNN-*.svg`）か Mermaid で作る。
- 特定の実在サービスには一切触れない。

## コードの正しさ（最重要）

- **本文のコードは、リファレンス実装（`code/quickvote/`）と矛盾させない。** 抜粋するときは実際のコードから取る（パス付きタイトル `title="src/main/java/..."`）。
- 途中段階のコード（例：メモリ上のMap版、ロックなし版）を見せるときは「この時点の保証」で何が守れていないかを明記する。
- 章固有の実験コードは `code/labs/chNN-<名前>/` に置き、**実際にビルド・実行して出力を確かめてから本文に載せる**。Spring Bootの実験はInitializrで雛形を取る（`curl https://start.spring.io/starter.zip -d bootVersion=4.1.2 -d javaVersion=25 -d type=maven-project ...`）。
- `code/quickvote/` は**読み取り専用**（変更しない）。バグや矛盾を見つけたら報告に書く。
- 環境：JDK 25 は `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`。Docker CLI は `export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"`（Docker Desktop 起動済み）。他の担当者も同時にビルドしているので、ポートは章ごとに変える（例：アプリ 18000+章番号、DB 25400+章番号）。起動したコンテナやプロセスは最後に必ず止める。
- psql での観察を載せる場合は `docker exec` で実際に実行した出力を貼る。

## 検証

- `./.venv/Scripts/mkdocs build 2>&1 | grep -E "WARNING|ERROR" | grep -E "<担当ファイル名の一部>"` で担当ファイル由来の警告がないこと（他の未作成ページのナビ警告は無視）。
- Mermaid はノード文字に括弧・引用符・コロンがあれば `["..."]` で囲む。

## 完了報告（日本語・簡潔に）

作成ファイル一覧、各章の文字数、実行確認したコード、リファレンス実装との矛盾やバグの発見、判断に迷った点。
