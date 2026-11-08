// CSP が inline script を許さないので、外部ファイルにしてある（default-src 'self'）。
function xsrf() {
  const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : '';
}
document.getElementById('make').addEventListener('click', async () => {
  await fetch('/victim.html'); // XSRF-TOKEN の Cookie をもらう
  const res = await fetch('/api/polls', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf() },
    body: JSON.stringify({ title: 'ランチどこ行く？', closesInMinutes: 30, allowMemberOptions: false, options: ['カレー', 'そば'] }),
  });
  const body = await res.json();
  document.getElementById('out').textContent = res.status + ' ' + JSON.stringify({ id: body.id, status: body.status }, null, 2);
});
