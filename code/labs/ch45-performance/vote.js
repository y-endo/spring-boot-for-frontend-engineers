// QuickVote の負荷試験（k6）。50人が1つの投票に参加し、投票と詳細取得を繰り返す。
//   docker run --rm -i --network qv45_default -e BASE=http://app:8080 -e VUS=49 -e DURATION=30s \
//     -v "$PWD":/scripts grafana/k6 run /scripts/vote.js
//
// 設計上の注意:
//  - 参加者の上限は50人（主催者を含む）。VU は最大49にして、setup が作る主催者と合わせて50人にする。
//  - 参加（POST /api/join）には IP 単位の回数制限（容量100）がある。各VUは最初の1回だけ参加する。
//  - 各 VU は自分の Cookie の入れ物（cookie jar）を持つ。VU ＝ 1人の訪問者。
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '49');
const DURATION = __ENV.DURATION || '30s';

export const options = {
  // k6 は既定でイテレーションごとに Cookie を捨てる。VU ＝ 1人の訪問者にするため、捨てない。
  noCookiesReset: true,
  vus: VUS,
  duration: DURATION,
  thresholds: {
    // 「合格ライン」を先に決めておく。超えたら k6 は終了コード 99 で終わる。
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:vote}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{kind:detail}': ['p(95)<300', 'p(99)<800'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function xsrf(url) {
  const cookies = http.cookieJar().cookiesForURL(url);
  return cookies['XSRF-TOKEN'] ? cookies['XSRF-TOKEN'][0] : '';
}

function post(path, body) {
  return http.post(`${BASE}${path}`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf(BASE + '/') },
  });
}

// 試験の前に1回だけ実行される。投票を1つ作り、参加コードと選択肢IDを全 VU に渡す。
export function setup() {
  http.get(`${BASE}/`); // XSRF-TOKEN の Cookie を受け取る
  const res = post('/api/polls', {
    title: '負荷試験', closesInMinutes: 60, allowMemberOptions: false,
    options: ['A', 'B', 'C', 'D'],
  });
  check(res, { '投票を作れた': (r) => r.status === 201 });
  const poll = res.json();
  return { pollId: poll.id, code: poll.joinCode, optionIds: poll.options.map((o) => o.id) };
}

let joined = false;

export default function (data) {
  if (!joined) {
    http.get(`${BASE}/`);
    const r = post('/api/join', { code: data.code });
    check(r, { '参加できた': (x) => x.status === 200 });
    joined = r.status === 200;
    return;
  }
  const optionId = data.optionIds[Math.floor(Math.random() * data.optionIds.length)];
  const vote = http.put(`${BASE}/api/polls/${data.pollId}/my-vote`, JSON.stringify({ optionId }), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf(BASE + '/') },
    tags: { kind: 'vote' },
  });
  if (__ENV.DEBUG && vote.status !== 200 && __ITER < 4) console.log(`vote ${vote.status} ${vote.body}`);
  check(vote, { '投票できた': (r) => r.status === 200 });
  const detail = http.get(`${BASE}/api/polls/${data.pollId}`, { tags: { kind: 'detail' } });
  check(detail, { '詳細を取得できた': (r) => r.status === 200 });
}
