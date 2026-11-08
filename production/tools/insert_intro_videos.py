"""各部の扉ページ（docs/partNN/index.md）の見出しの直後に、導入動画のカードを入れる。

台本 production/video/scripts/partNN-intro.json の字幕（text）から「字幕全文」も作る。
すでにカードが入っているページは、そのカードを作り直す。
"""
import json
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
ROOT = Path(__file__).resolve().parents[2]
START, END = "<!-- part-intro:start -->", "<!-- part-intro:end -->"


def card(n: int) -> str:
    vid = f"part{n:02d}-intro"
    script = json.loads((ROOT / "production/video/scripts" / f"{vid}.json").read_text(encoding="utf-8"))
    subtitles = "\n".join(f"    {line['text']}" for line in script["lines"])
    return f"""{START}
<div class="video-card">
  <video controls preload="metadata" poster="../assets/videos/{vid}.jpg">
    <source src="../assets/videos/{vid}.mp4" type="video/mp4">
  </video>
  <p class="video-caption">▶ 第{n}部でやること（約1分）</p>
  <p class="video-credit">音声：VOICEVOX:冥鳴ひまり</p>
</div>

??? note "字幕全文"
{subtitles}
{END}"""


def main():
    for n in range(11):
        path = ROOT / "docs" / f"part{n:02d}" / "index.md"
        text = path.read_text(encoding="utf-8")
        text = re.sub(rf"{re.escape(START)}.*?{re.escape(END)}\n\n", "", text, flags=re.S)
        head, rest = text.split("\n", 1)
        if not head.startswith("# "):
            raise SystemExit(f"{path}: 1行目が見出しではありません")
        path.write_text(f"{head}\n\n{card(n)}\n\n{rest.lstrip(chr(10))}", encoding="utf-8")
        print(f"入れました: {path.relative_to(ROOT).as_posix()}")


if __name__ == "__main__":
    main()
