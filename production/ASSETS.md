# 素材の置き場所と名前（本文から参照してよいもの）

## 挿絵（Codexで生成、WebP）

本文から `![代替テキスト](../assets/illustrations/<名前>.webp)` で参照する（章のmdファイルから見た相対パス）。

| ファイル名 | 内容 | 主に使う章 |
| --- | --- | --- |
| `part00.webp`〜`part10.webp` | 各部の扉イラスト | 各部の index.md |
| `quickvote-scene.webp` | 会議室でスマホから投票し、画面の棒グラフが伸びる場面 | 00 |
| `duck-license.webp` | 鴨の免許証（名前的型付けの例え） | 03 |
| `shop-staff.webp` | 1人の超高速店員（Node）と、客ごとに付く店員たち（Java） | 09 |
| `power-strip.webp` | 配線済みの電源タップ（Spring Boot） | 10 |
| `restaurant.webp` | レストラン：ホール係・厨房・食材庫・注文票（Webアプリの層） | 10, 12 |
| `theater-casting.webp` | 演出家が配役表を見て役者に相手役を引き合わせる（DIコンテナ） | 11 |
| `bank-transfer.webp` | 銀行振込：全部成功か、全部なかったことに（トランザクション） | 22 |
| `secretary-proxy.webp` | 社長の前に立つ秘書（プロキシ） | 23 |
| `umbrella-stand.webp` | 数に限りのある貸出用の傘立て（コネクションプール） | 24 |
| `delivery-slip.webp` | 宅配の伝票番号（冪等性キー） | 27 |
| `id-card-floors.webp` | 入館証とフロアごとの入室権（認証と認可） | 30 |
| `radio.webp` | ラジオ局とラジオ（SSE） | 36 |
| `shopping-basket.webp` | レジのカゴ（永続コンテキスト） | 39 |

## 動画（Blender＋VOICEVOX、MP4）

本文では次のHTMLで埋め込む（rawHTMLなので、章ページからは `../../assets/videos/` になる点に注意）。

```html
<div class="video-card">
  <video controls preload="metadata" poster="../../assets/videos/<ID>.jpg">
    <source src="../../assets/videos/<ID>.mp4" type="video/mp4">
  </video>
  <p class="video-caption">▶ <タイトル></p>
</div>
```

動画の直後に、静止画の代替として「動画の要点」を箇条書き3〜5行で必ず書く（音を出せない環境の読者のため）。

| ID | タイトル | 章 |
| --- | --- | --- |
| `thread-model` | 店員モデル：NodeとJavaの同時処理 | 09 |
| `di-container` | DIコンテナ：配役表で役者をつなぐ | 11 |
| `request-journey` | リクエストの旅 | 12 |
| `tx-proxy` | @Transactionalと秘書（プロキシ） | 23 |
| `connection-pool` | コネクションプール：傘が足りない | 24 |
| `last-seat-race` | 残り1枠に2人：競合とロック | 25 |
| `idempotency` | 応答が消えても二重登録しない | 27 |
| `csrf-cookie` | ブラウザはCookieを自動で添える | 34 |
| `sse-resume` | SSEの切断とLast-Event-ID | 37 |
| `n-plus-one` | N+1：SQLの滝 | 40 |

## 技術図（SVG）

各章の執筆者が作る。置き場所は `docs/assets/diagrams/chNN-<名前>.svg`。

- viewBox指定、幅は 960 前後。フォントは `font-family="Noto Sans JP, sans-serif"`。
- 背景は透明。文字色・線色はライト/ダークどちらでも読める中間色（例：線 `#7a8099`、強調 `#ff6d3a`、補助 `#14b8a6`、箱の塗り `#ff6d3a22` のような半透明）。
- 文字は14px以上。1枚に詰め込みすぎない。
