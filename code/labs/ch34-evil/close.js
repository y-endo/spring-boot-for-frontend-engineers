// 使い方: http://<このページのホスト>:18134/close.html?target=http://localhost:18034&poll=<投票ID>
const q = new URLSearchParams(location.search);
const f = document.getElementById('f');
f.action = `${q.get('target')}/api/polls/${q.get('poll')}/close`;
f.submit();
