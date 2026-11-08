'use strict';
// QuickVote の最小クライアント。ビルド不要。fetch で API を呼び、EventSource（SSE）で得票をリアルタイムに更新する。
// 画面の文字はすべて textContent で入れる（innerHTML は使わない）。投票のタイトルや選択肢は他人が入力した値なので、
// HTML として解釈させるとスクリプトを埋め込まれる（XSS）。

const $ = (id) => document.getElementById(id);
const RECENT_KEY = 'quickvote.recent';

// 仕様上の上限（サーバーの PollRules と同じ値）。サーバーが最終的に検証するが、画面でも先に確かめて、すぐ教える。
const MAX_TEXT_CHARS = 100; // タイトル・選択肢ラベルの文字数。単位は Unicode のコードポイント数
const MAX_LABEL_BYTES = 400; // 選択肢ラベルの UTF-8 のバイト数
const MAX_OPTIONS = 10;
const MIN_CLOSES_IN_MINUTES = 5;
const MAX_CLOSES_IN_MINUTES = 7 * 24 * 60;

let currentPollId = null;
let eventSource = null;
let pendingOption = null; // 選択肢追加の「再送用キー」を保持する（下の submit 処理を参照）

// 詳細の取得（refresh）が「古い応答」を描画しないための状態。
let viewGeneration = 0; // 開いている画面（投票）が切り替わるたびに増やす
let shownSeq = -1; // いま描画している詳細の lastEventSeq
let refreshing = false; // 詳細を取得中か（同時に1つだけ取得する）
let refreshAgain = false; // 取得中に更新の通知が来た（終わった後にもう一度だけ取り直す）

// ---------- API 呼び出し ----------

class ApiError extends Error {
  constructor(status, problem) {
    super((problem && problem.detail) || `HTTP ${status}`);
    this.status = status;
    this.code = problem && problem.code;
  }
}

// CSRF対策: サーバーが Cookie（XSRF-TOKEN）で渡した値を、更新系リクエストのヘッダー X-XSRF-TOKEN に入れて返す。
// 他サイトのページは、この Cookie の値を読めないので、正しいヘッダーを付けられない。
function xsrfToken() {
  const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : '';
}

async function api(method, path, body, headers = {}, signal = undefined) {
  const init = { method, credentials: 'same-origin', signal, headers: { Accept: 'application/json', ...headers } };
  if (method !== 'GET') init.headers['X-XSRF-TOKEN'] = xsrfToken();
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const res = await fetch(path, init);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

// 文字数はコードポイントで数える（サーバーの BoundedText と同じ）。
// String.length や <input maxlength> は UTF-16 の単位（サロゲートペアの絵文字は2）で数えるので、
// 絵文字を51個入れると「100文字」を超えたことになり、サーバーが許す100文字まで入力できない。
// Array.from(文字列) は文字列をコードポイントごとに分けるので、その長さがコードポイント数になる。
// 結合文字や ZWJ でつないだ絵文字（家族の絵文字など）は、見た目が1文字でも複数のコードポイントとして数える（仕様）。
function codePointCount(text) {
  return Array.from(text).length;
}

function utf8ByteCount(text) {
  return new TextEncoder().encode(text).length;
}

// 問題があれば画面に出す文言を返す。なければ null。
function textProblem(name, text, { checkBytes = false } = {}) {
  if (text.length === 0) return `${name}を入力してください`;
  if (codePointCount(text) > MAX_TEXT_CHARS) {
    return `${name}は${MAX_TEXT_CHARS}文字までです（Unicodeのコードポイント数で数えます。現在 ${codePointCount(text)} 文字）`;
  }
  if (checkBytes && utf8ByteCount(text) > MAX_LABEL_BYTES) {
    return `${name}が長すぎます（UTF-8で${MAX_LABEL_BYTES}バイトまで）`;
  }
  return null;
}

function randomKey() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, '0')).join('');
}

// ---------- 画面 ----------

function showMessage(text, isError = false) {
  const el = $('message');
  el.textContent = text;
  el.className = isError ? 'error' : '';
  el.hidden = !text;
}

function showError(e) {
  showMessage(e instanceof ApiError ? `${e.message}（${e.code || e.status}）` : `通信に失敗しました: ${e.message}`, true);
}

function show(section) {
  for (const id of ['home', 'invite', 'poll']) $(id).hidden = id !== section;
}

function loadRecent() {
  try { return JSON.parse(localStorage.getItem(RECENT_KEY)) || []; } catch { return []; }
}

function saveRecent(id, title) {
  const list = loadRecent().filter((p) => p.id !== id);
  list.unshift({ id, title });
  try { localStorage.setItem(RECENT_KEY, JSON.stringify(list.slice(0, 10))); } catch { /* 保存できなくても動く */ }
}

function forgetRecent(id) {
  try { localStorage.setItem(RECENT_KEY, JSON.stringify(loadRecent().filter((p) => p.id !== id))); } catch { /* 無視 */ }
}

function renderRecent() {
  const ul = $('recent');
  ul.replaceChildren();
  const list = loadRecent();
  if (list.length === 0) {
    const li = document.createElement('li');
    li.className = 'hint';
    li.textContent = 'まだありません';
    ul.append(li);
  }
  for (const p of list) {
    const li = document.createElement('li');
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'link';
    b.textContent = p.title;
    b.addEventListener('click', () => openPoll(p.id));
    li.append(b);
    ul.append(li);
  }
}

function renderPoll(p) {
  $('poll-title').textContent = p.title;
  const closes = new Date(p.closesAt).toLocaleString();
  $('poll-meta').textContent =
    `${p.status === 'OPEN' ? '受付中' : '締切済み'}（締切 ${closes}）／参加者 ${p.participantCount}人／総投票数 ${p.totalVotes}／あなたは${p.role === 'HOST' ? '主催者' : '参加者'}`;
  $('host-row').hidden = p.role !== 'HOST';
  $('host-actions').hidden = p.role !== 'HOST';
  if (p.joinCode) $('join-code').textContent = p.joinCode;
  const open = p.status === 'OPEN';
  $('option-form').hidden = !open || (p.role !== 'HOST' && !p.allowMemberOptions);
  $('close-btn').disabled = !open;

  const ul = $('options');
  ul.replaceChildren();
  for (const o of p.options) {
    const li = document.createElement('li');
    li.className = 'option' + (o.id === p.myVoteOptionId ? ' mine' : '');

    const row = document.createElement('div');
    row.className = 'row';
    const label = document.createElement('span');
    label.className = 'label';
    label.textContent = o.label;
    const stat = document.createElement('span');
    stat.textContent = `${o.votes}票 ${o.percent.toFixed(1)}%`;
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = o.id === p.myVoteOptionId ? '投票済み' : '投票';
    btn.disabled = !open || o.id === p.myVoteOptionId;
    btn.addEventListener('click', () => vote(o.id));
    row.append(label, stat, btn);

    const bar = document.createElement('div');
    bar.className = 'bar';
    const fill = document.createElement('div');
    fill.className = 'fill';
    fill.style.width = `${o.percent}%`; // CSSOM経由で設定する（CSP で inline style 属性は許可していない）
    bar.append(fill);

    li.append(row, bar);
    ul.append(li);
  }
  if (p.options.length === 0) {
    const li = document.createElement('li');
    li.className = 'hint';
    li.textContent = 'まだ選択肢がありません';
    ul.append(li);
  }
}

// 詳細を取り直して描画する。更新の通知（SSE）のたびに呼ばれる。
// 通知ごとに並行して GET すると、応答が返る順番が入れ替わったときに、古い状態を後から描画して表示が巻き戻ったり、
// 別の投票へ移った後に前の投票の応答が届いて、別の投票の画面に描いてしまう。そこで次のようにする:
//  1. 同時に取得するのは1つだけ。取得中の通知は1回にまとめ、終わった後にもう一度だけ取り直す
//  2. 開始時の画面（viewGeneration）と、応答を受けたときの画面が違えば、その応答は捨てる
//  3. 同じ投票で、描画済みより古い状態（lastEventSeq が小さい）の応答は捨てる
async function refresh() {
  if (!currentPollId) return;
  if (refreshing) {
    refreshAgain = true;
    return;
  }
  refreshing = true;
  try {
    do {
      refreshAgain = false;
      const generation = viewGeneration;
      const pollId = currentPollId;
      if (!pollId) break;
      try {
        // 応答が返らないまま、以後の更新が止まってしまうことがないよう、取得には時間の上限を付ける。
        const p = await api('GET', `/api/polls/${pollId}`, undefined, {}, AbortSignal.timeout(15000));
        if (generation !== viewGeneration) continue; // 取得している間に別の画面へ移った
        if (p.lastEventSeq < shownSeq) continue; // すでに、より新しい状態を描画している
        shownSeq = p.lastEventSeq;
        renderPoll(p);
      } catch (e) {
        if (generation !== viewGeneration) continue;
        if (e instanceof ApiError && e.status === 404) {
          // 削除された、または参加していない（Cookie を消した等）
          forgetRecent(pollId);
          goHome('この投票は見つかりません（削除されたか、参加していません）', true);
        } else {
          showError(e);
        }
      }
    } while (refreshAgain);
  } finally {
    refreshing = false;
  }
}

// ---------- SSE ----------

function connectEvents() {
  closeEvents();
  // EventSource は切断されると自動で再接続し、最後に受け取ったイベントの id を Last-Event-ID ヘッダーで送る。
  // サーバーはそれより後のイベントを再送する。
  const es = new EventSource(`/api/polls/${currentPollId}/events`);
  eventSource = es;
  const types = ['PARTICIPANT_JOINED', 'OPTION_ADDED', 'VOTE_CAST', 'POLL_CLOSED'];
  for (const t of types) es.addEventListener(t, () => refresh());
  // RESYNC は「サーバーの状態が作り直された」合図。連番が巻き戻りうるので、描画済みの連番を忘れてから取り直す。
  es.addEventListener('RESYNC', () => { shownSeq = -1; refresh(); });
  // 接続できた（再接続を含む）たびに全体を取り直す。最初の取得と接続の間の取りこぼしを防ぐ。
  es.onopen = () => { $('conn-status').textContent = 'リアルタイム更新: 接続中'; refresh(); };
  es.onerror = () => {
    $('conn-status').textContent = es.readyState === EventSource.CLOSED
      ? 'リアルタイム更新: 切断されました（再読み込みしてください）'
      : 'リアルタイム更新: 再接続中…';
  };
}

function closeEvents() {
  if (eventSource) eventSource.close();
  eventSource = null;
}

// ---------- 操作 ----------

async function openPoll(id) {
  const generation = ++viewGeneration;
  currentPollId = id;
  shownSeq = -1;
  showMessage('');
  $('invite-result').hidden = true;
  pendingOption = null;
  try {
    const p = await api('GET', `/api/polls/${id}`);
    if (generation !== viewGeneration) return; // 待っている間に、別の投票を開いた（遅れて届いた応答は捨てる）
    saveRecent(id, p.title);
    show('poll');
    shownSeq = p.lastEventSeq;
    renderPoll(p);
    connectEvents();
  } catch (e) {
    if (generation !== viewGeneration) return;
    forgetRecent(id);
    goHome(null);
    showError(e);
  }
}

function goHome(message, isError = false) {
  viewGeneration++; // 取得中の応答があっても、この画面にはもう描かない
  closeEvents();
  currentPollId = null;
  history.replaceState(null, '', location.pathname);
  show('home');
  renderRecent();
  showMessage(message || '', isError);
}

async function vote(optionId) {
  try {
    await api('PUT', `/api/polls/${currentPollId}/my-vote`, { optionId });
    await refresh();
  } catch (e) { showError(e); }
}

$('create-form').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  const f = new FormData(ev.target);
  const title = String(f.get('title')).trim();
  const options = String(f.get('options')).split('\n').map((s) => s.trim()).filter(Boolean);
  // 締切は「今から何分後か」を送る。サーバーが自分の時計から締切時刻を計算する
  // （ブラウザの時計のずれや通信時間のぶん、「5分後」が5分を割って拒否されることがない）。
  const closesInMinutes = Number(f.get('minutes'));
  const minutesOk = Number.isInteger(closesInMinutes)
    && closesInMinutes >= MIN_CLOSES_IN_MINUTES && closesInMinutes <= MAX_CLOSES_IN_MINUTES;
  const problem = textProblem('タイトル', title)
    || (minutesOk ? null : `締切は${MIN_CLOSES_IN_MINUTES}〜${MAX_CLOSES_IN_MINUTES}分（整数）で指定してください`)
    || (options.length > MAX_OPTIONS ? `選択肢は${MAX_OPTIONS}個までです` : null)
    || options.map((label) => textProblem('選択肢', label, { checkBytes: true })).find(Boolean)
    || null;
  if (problem) {
    showMessage(problem, true);
    return;
  }
  try {
    const p = await api('POST', '/api/polls', {
      title, closesInMinutes, allowMemberOptions: f.get('allowMemberOptions') === 'on', options,
    });
    ev.target.reset();
    await openPoll(p.id);
  } catch (e) { showError(e); }
});

$('join-form').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  try {
    const r = await api('POST', '/api/join', { code: String(new FormData(ev.target).get('code')) });
    ev.target.reset();
    await openPoll(r.pollId);
  } catch (e) { showError(e); }
});

// 選択肢の追加は Idempotency-Key 付き。
// 通信エラーで応答を受け取れなかったとき、同じキーで再送すれば二重に登録されない。
// 成功したらキーを捨てる。入力内容が変わったら別の操作なので、新しいキーを使う。
$('option-form').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  const label = String(new FormData(ev.target).get('label')).trim();
  const problem = textProblem('選択肢', label, { checkBytes: true });
  if (problem) {
    showMessage(problem, true);
    return;
  }
  if (!pendingOption || pendingOption.label !== label) pendingOption = { key: randomKey(), label };
  try {
    await api('POST', `/api/polls/${currentPollId}/options`, { label }, { 'Idempotency-Key': pendingOption.key });
    pendingOption = null;
    ev.target.reset();
    await refresh();
  } catch (e) {
    showError(e);
    if (e instanceof ApiError) pendingOption = null; // サーバーが判断した結果なので、再送ではなく新規扱いにする
  }
});

$('invite-btn').addEventListener('click', async () => {
  try {
    const r = await api('POST', `/api/polls/${currentPollId}/invites`);
    // トークンは URL の「#」以降（フラグメント）に入れる。フラグメントはブラウザからサーバーに送られないので、
    // アクセスログやリファラに残らない。
    $('invite-link').value = `${location.origin}/#invite=${r.token}`;
    $('invite-result').hidden = false;
    $('invite-link').select();
  } catch (e) { showError(e); }
});

$('close-btn').addEventListener('click', async () => {
  try { await api('POST', `/api/polls/${currentPollId}/close`); await refresh(); } catch (e) { showError(e); }
});

$('delete-btn').addEventListener('click', async () => {
  if (!confirm('この投票を削除します。元に戻せません。')) return;
  try {
    await api('DELETE', `/api/polls/${currentPollId}`);
    forgetRecent(currentPollId);
    goHome('削除しました');
  } catch (e) { showError(e); }
});

$('back-btn').addEventListener('click', () => goHome(null));

$('accept-btn').addEventListener('click', async () => {
  const token = new URLSearchParams(location.hash.slice(1)).get('invite');
  try {
    // 招待の受諾は POST。リンクを開いただけ（プレビューや先読みを含む）では消費されない。
    const r = await api('POST', `/api/invites/${encodeURIComponent(token)}/accept`);
    history.replaceState(null, '', location.pathname);
    await openPoll(r.pollId);
  } catch (e) { showError(e); }
});

// ---------- 起動 ----------

if (new URLSearchParams(location.hash.slice(1)).has('invite')) {
  show('invite');
} else {
  goHome(null);
}
