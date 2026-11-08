# ゼロからプロへ Java × Spring Boot バックエンド

フロントエンドエンジニアが、Java 25 と Spring Boot 4 でバックエンドをプロとして担当できるようになるための教材です。

**教材サイト：https://y-endo.github.io/spring-boot-for-frontend-engineers/**

- 全11部49章と付録6本。TypeScript との対比で Java を読むところから始め、DI、トランザクション、同時実行、冪等性、認可、SSE、運用までを扱います。
- 通しの題材は、会員登録のいらない匿名投票アプリ「QuickVote」です。章を追うごとに育てていきます。
- ほとんどの章に「動かしてみる」と「壊してみる」があります。本文のコードはすべて実際に動かして確かめています。
- 順番が大事な概念は、字幕つきの短い解説動画でも説明しています。

## この教材の作り方

フロントエンドエンジニアの作者がバックエンドを学ぶために、AI（Claude と Codex）と一緒に作りました。文章・図・動画の多くは AI が下書きし、作者が方針を決めて手を入れています。本文のコードと題材アプリは実際に動かして確かめています。
バックエンドの専門家による確認はまだ受けていないので、誤りに気付いたら Issue で教えてください。

## 対象読者

React / TypeScript などフロントエンドの実務経験があり、バックエンドを専門にしてこなかったエンジニアを想定しています。
変数・HTTP・SQLの基本・Git は説明しません。Java・Spring・サーバー特有の考え方は、初めて見る前提で説明します。

## 技術の前提

| 項目 | バージョン |
| --- | --- |
| Java | 25（LTS） |
| Spring Boot | 4.1.2（Spring Framework 7 / Spring Security 7） |
| PostgreSQL | 18 |
| ビルドツール | Maven（Maven Wrapper） |

## フォルダ構成

| フォルダ | 中身 |
| --- | --- |
| `docs/` | 教材の本文（MkDocs） |
| `code/quickvote/` | 題材アプリ QuickVote の完成形 |
| `code/labs/` | 章ごとの実験用プロジェクトとスクリプト |
| `code/java-basics/`・`code/jpa-lab/` | 第1部と第8部の練習用コード |
| `production/` | 教材の設計書、執筆ルール、動画の生成スクリプト |

## 手元でサイトを見る

Python 3.10 以降が必要です。

```bash
python -m venv .venv
```

```bash
.venv/bin/pip install -r requirements.txt
```

```bash
.venv/bin/mkdocs serve
```

Windows では `.venv/bin/` を `.venv\Scripts\` に読み替えてください。ブラウザで http://127.0.0.1:8000 を開くと見られます。

## クレジット

- 解説動画の音声：VOICEVOX:冥鳴ひまり
- 動画の字幕フォント：[Noto Sans JP](https://fonts.google.com/noto/specimen/Noto+Sans+JP)（SIL Open Font License 1.1）

## ライセンス

本文・図・動画・サンプルコードは [クリエイティブ・コモンズ 表示 4.0 国際（CC BY 4.0）](https://creativecommons.org/licenses/by/4.0/deed.ja) で公開しています。条文は [LICENSE](LICENSE) にあります。
出典（この教材の名前とURL）を示せば、改変や商用利用も自由です。
ただし、上の動画音声（VOICEVOX:冥鳴ひまり）は、それぞれの利用規約に従ってください。
