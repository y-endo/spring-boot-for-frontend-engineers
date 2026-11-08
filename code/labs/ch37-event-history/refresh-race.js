// 静的クライアントの refresh() が「応答順の逆転」で古い状態を描画してしまう問題（シニアレビュー指摘10）の再現。
// ブラウザのコンソール、または Node（node refresh-race.js）で動く。ネットワークは setTimeout で模擬する。
//
// サーバーの状態は lastEventSeq = 1, 2, 3 と進む。通知のたびに GET して描画する。
// 1回目のGETは遅く（300ms）、2回目は速い（50ms）。応答は 2回目 → 1回目の順に返ってくる。

function makeServer() {
  let seq = 0;
  // GET /api/polls/{id}: 呼ばれた時点の seq のスナップショットを、delay ミリ秒後に返す
  return {
    advance: () => ++seq,
    get: (delay) => {
      const snapshot = seq;
      return new Promise((resolve) => setTimeout(() => resolve({ lastEventSeq: snapshot }), delay));
    },
  };
}

// 悪い版: 通知のたびに並行して GET し、返ってきた順に描画する
async function naive() {
  const server = makeServer();
  const rendered = [];
  const delays = [300, 50];
  const jobs = [];
  for (let i = 0; i < 2; i++) {
    server.advance();                                    // 通知が届く（seq 1, 2）
    jobs.push(server.get(delays[i]).then((p) => rendered.push(p.lastEventSeq)));
    await new Promise((r) => setTimeout(r, 10));
  }
  await Promise.all(jobs);
  return rendered;
}

// 良い版: app.js と同じ。同時に取得するのは1つ。描画済みより古い応答は捨てる
async function guarded() {
  const server = makeServer();
  const rendered = [];
  let shownSeq = -1;
  let refreshing = false;
  let refreshAgain = false;
  const delays = [300, 50];
  let call = 0;
  async function refresh() {
    if (refreshing) { refreshAgain = true; return; }       // 取得中の通知は1回にまとめる
    refreshing = true;
    try {
      do {
        refreshAgain = false;
        const p = await server.get(delays[Math.min(call++, 1)]);
        if (p.lastEventSeq < shownSeq) continue;           // 古い応答は捨てる
        shownSeq = p.lastEventSeq;
        rendered.push(p.lastEventSeq);
      } while (refreshAgain);
    } finally {
      refreshing = false;
    }
  }
  for (let i = 0; i < 2; i++) {
    server.advance();
    refresh();
    await new Promise((r) => setTimeout(r, 10));
  }
  await new Promise((r) => setTimeout(r, 600));
  return rendered;
}

(async () => {
  console.log('悪い版が描画した lastEventSeq の順番 :', JSON.stringify(await naive()), '← 最後が 1。画面が巻き戻る');
  console.log('守る版が描画した lastEventSeq の順番 :', JSON.stringify(await guarded()), '← 最後は常に最新');
})();
