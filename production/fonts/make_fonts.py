"""Noto Sans JP の可変フォントから、動画用の 400 / 700 / 900 の太さを切り出す。

使い方:
    python production/fonts/make_fonts.py <NotoSansJP[wght].ttf のパス>

Blender と ffmpeg（ASS字幕）は可変フォントの太さを選べないので、太さごとの静的フォントを作る。
"""
import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

OUT_DIR = Path(__file__).resolve().parent
WEIGHTS = (400, 700, 900)


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("使い方: python production/fonts/make_fonts.py <NotoSansJP[wght].ttf>")
    src = Path(sys.argv[1])
    for weight in WEIGHTS:
        font = instantiateVariableFont(TTFont(src), {"wght": weight})
        out = OUT_DIR / f"NotoSansJP-{weight}.ttf"
        font.save(out)
        print(f"作成: {out}")


if __name__ == "__main__":
    main()
