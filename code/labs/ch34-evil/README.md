# 第34章のラボ：悪意のあるページ（静的ファイル）

別オリジンから QuickVote（`../part06-session-lab/`、ポート 18034）を攻撃する、ただの HTML と JavaScript です。**自分のラボに対してだけ**使ってください。

```bash
jwebserver -p 18134 -d code/labs/ch34-evil          # JDK 付属の簡易サーバー（127.0.0.1 だけで待ち受ける）
```

| ページ | 内容 |
| --- | --- |
| `close.html?target=<被害者のオリジン>&poll=<投票ID>` | 空の `<form method="post">` で `POST …/close` を送る（CSRF） |
| `fetch.html?target=<被害者のオリジン>&poll=<投票ID>` | `fetch` で `GET`（Cookie つき）と JSON の `POST` を試し、読めたかを表示する（CORS） |

- **同じサイトの別オリジン**：`http://localhost:18134/...`（ポート違い。`SameSite=Lax` は守らない）
- **別サイト**：`http://127.0.0.1:18134/...`（ホスト違い。`SameSite=Lax` が Cookie を止める）

被害者側は、先に `http://localhost:18034/victim.html` で「投票を作る」を押して、主催者の Cookie を持った状態にします。
