const q = new URLSearchParams(location.search);
const target = q.get('target');
const poll = q.get('poll');
const out = document.getElementById('out');
const lines = [`このページのオリジン: ${location.origin}`, `相手のオリジン: ${new URL(target).origin}`, ''];

async function attempt(name, url, init) {
  try {
    const res = await fetch(url, init);
    const text = await res.text();
    lines.push(`${name}: 読めた status=${res.status} body=${text.slice(0, 80)}`);
  } catch (e) {
    lines.push(`${name}: 失敗 ${e.name}: ${e.message}`);
  }
  out.textContent = lines.join('\n');
}

await attempt('1) GET + credentials', `${target}/api/polls/${poll}`, { credentials: 'include' });
await attempt('2) POST JSON + credentials', `${target}/api/polls/${poll}/options`, {
  method: 'POST',
  credentials: 'include',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ label: 'うどん' }),
});
