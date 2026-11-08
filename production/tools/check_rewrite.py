"""文章の書き直しで、壊してはいけない部分が変わっていないかを確かめる。

使い方:
    python production/tools/check_rewrite.py docs/part04/ch24-connection-pool.md [...]

git の HEAD の版と今のファイルを比べて、次のどれかが変わっていたら一覧を出して終了コード1で終わる。
- コードブロック（``` で囲んだ部分。Mermaid も含む）
- 見出し（# で始まる行。アンカーが変わるとリンクが切れる）
- リンク先（](...) の中身）
- 動画カード（<div class="video-card"> から </div> まで）
- 字幕全文（??? note "字幕全文" の中身。動画の音声と一致させているため）
- 注意書きの種類と題名（!!! / ??? の行）
"""
import re
import subprocess
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

FENCE = re.compile(r"^(\s*)(```+|~~~+)")


def fenced_blocks(lines):
    blocks, cur, marker = [], None, None
    for line in lines:
        m = FENCE.match(line)
        if cur is None:
            if m:
                cur, marker = [line.rstrip()], m.group(2)
        else:
            cur.append(line.rstrip())
            if m and m.group(2).startswith(marker[0]) and line.strip() == m.group(2):
                blocks.append("\n".join(cur))
                cur = None
    return blocks


def outside_code(lines):
    out, inside, marker = [], False, None
    for line in lines:
        m = FENCE.match(line)
        if not inside and m:
            inside, marker = True, m.group(2)
            continue
        if inside:
            if m and line.strip() == m.group(2) and m.group(2).startswith(marker[0]):
                inside = False
            continue
        out.append(line)
    return out


def subtitle_blocks(lines):
    blocks, i = [], 0
    while i < len(lines):
        if lines[i].lstrip().startswith('??? note "字幕全文"'):
            block = [lines[i].rstrip()]
            i += 1
            while i < len(lines) and (lines[i].startswith("    ") or not lines[i].strip()):
                block.append(lines[i].rstrip())
                i += 1
            blocks.append("\n".join(block).rstrip())
        else:
            i += 1
    return blocks


def video_cards(text):
    return re.findall(r'<div class="video-card">.*?</div>', text, flags=re.S)


def extract(text):
    lines = text.splitlines()
    prose = outside_code(lines)
    return {
        "コードブロック": fenced_blocks(lines),
        "見出し": [l.rstrip() for l in prose if re.match(r"^#{1,6} ", l)],
        "リンク先": sorted(re.findall(r"\]\(([^)\s]+)", "\n".join(prose))),
        "動画カード": video_cards(text),
        "字幕全文": subtitle_blocks(lines),
        "注意書きの行": [l.strip() for l in prose if re.match(r"^\s*(!!!|\?\?\?\+?) ", l)],
    }


def check(path):
    rel = Path(path).as_posix()
    try:
        before = subprocess.run(["git", "show", f"HEAD:{rel}"], capture_output=True,
                                check=True).stdout.decode("utf-8")
    except subprocess.CalledProcessError:
        print(f"[skip] {rel}: HEAD に無いファイル")
        return True
    after = Path(path).read_text(encoding="utf-8")
    a, b = extract(before), extract(after)
    ok = True
    for key in a:
        if a[key] != b[key]:
            ok = False
            removed = [x for x in a[key] if x not in b[key]]
            added = [x for x in b[key] if x not in a[key]]
            print(f"[NG] {rel}: {key}が変わっています（件数 {len(a[key])} → {len(b[key])}）")
            for x in removed[:3]:
                print("   - 消えた:", x[:160].replace("\n", " ⏎ "))
            for x in added[:3]:
                print("   + 増えた:", x[:160].replace("\n", " ⏎ "))
            if not removed and not added:
                print("   （中身は同じで順番が変わっています）")
    if ok:
        print(f"[OK] {rel}")
    return ok


if __name__ == "__main__":
    results = [check(p) for p in sys.argv[1:]]
    sys.exit(0 if all(results) else 1)
