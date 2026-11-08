"""台本JSONから解説動画を作る共通パイプライン。

使い方:
    python make_video.py <台本.json>

台本JSONの形:
{
  "id": "di-container",                 # 出力ファイル名
  "title": "60秒でわかる DIコンテナ",
  "speaker": 14,                          # VOICEVOXの話者ID（省略時14）
  "speed": 1.2,                           # 読み上げ速度（省略時1.2＝すこし速め）
  "blender_script": "scenes/di_container.py",  # Blenderの場面スクリプト（台本と同じフォルダからの相対パス）
  "lines": [
    {"text": "字幕と読み上げの文", "pause": 0.3},
    {"text": "AtomicInteger", "voice": "アトミックインテジャー"},  # voice は読み上げだけ差し替える（任意）
    ...
  ]
}

処理の流れ:
  1. VOICEVOX（127.0.0.1:50021）で各行を合成し、長さを測る
  2. 各行の開始・終了時刻を timing.json に書き出す
  3. Blender をバックグラウンドで起動し、timing.json を渡して映像を描画する
  4. ffmpeg で 映像＋音声＋ASS字幕（Noto Sans JP）を合成する
"""
import json
import subprocess
import sys
import urllib.parse
import urllib.request
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BLENDER = r"F:\Application\Blender\blender.exe"
FFMPEG = "ffmpeg"
ENGINE = "http://127.0.0.1:50021"
FONTS_DIR = ROOT / "production" / "fonts"
OUT_DIR = ROOT / "docs" / "assets" / "videos"
FPS = 30
LEAD_IN = 0.6   # 冒頭の無音（秒）
TAIL = 1.2      # 末尾の余韻（秒）


def synth(text: str, speaker: int, speed: float, path: Path) -> float:
    q = urllib.parse.urlencode({"text": text, "speaker": speaker})
    req = urllib.request.Request(f"{ENGINE}/audio_query?{q}", method="POST")
    query = json.loads(urllib.request.urlopen(req).read())
    query["speedScale"] = speed
    query["intonationScale"] = 1.1
    query["prePhonemeLength"] = 0.05
    query["postPhonemeLength"] = 0.08
    query["outputSamplingRate"] = 48000
    body = json.dumps(query).encode("utf-8")
    req = urllib.request.Request(
        f"{ENGINE}/synthesis?speaker={speaker}", data=body,
        headers={"Content-Type": "application/json"}, method="POST")
    path.write_bytes(urllib.request.urlopen(req).read())
    with wave.open(str(path)) as w:
        return w.getnframes() / w.getframerate()


def ass_time(t: float) -> str:
    h = int(t // 3600); m = int(t % 3600 // 60); s = t % 60
    return f"{h}:{m:02d}:{s:05.2f}"


def wrap_jp(text: str, width: int = 26) -> str:
    """字幕を2行以内に収めるため、句読点の近くで改行する。"""
    if len(text) <= width:
        return text
    best = len(text) // 2
    found = False
    for i in range(len(text) // 2, 0, -1):
        if text[i - 1] in "、。，！？）」":
            best = i
            found = True
            break
    if not found:
        # 前半に句読点が無いとき、後半で中央に最も近い句読点を使う（語の途中で折れるのを防ぐ）
        def vis(t):  # 半角は全角の約0.6幅として数える
            return sum(0.6 if ord(c) < 0x80 else 1.0 for c in t)
        for i in range(len(text) // 2 + 1, len(text)):
            if text[i - 1] in "、。，！？）」" and vis(text[:i]) <= width and vis(text[i:]) <= width:
                best = i
                break
    return text[:best] + r"\N" + text[best:]


def write_ass(timing, path: Path, w: int, h: int):
    head = f"""[Script Info]
ScriptType: v4.00+
PlayResX: {w}
PlayResY: {h}
WrapStyle: 2

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Sub,Noto Sans JP,{int(h*0.052)},&H00FFFFFF,&H00FFFFFF,&H00201814,&H96000000,-1,0,0,0,100,100,1,0,1,{max(3,int(h*0.006))},2,2,80,80,{int(h*0.06)},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    rows = []
    for t in timing["lines"]:
        txt = wrap_jp(t["text"])
        # 0.15秒のフェードで字幕を出し入れする
        rows.append(f"Dialogue: 0,{ass_time(t['start'])},{ass_time(t['end'])},Sub,,0,0,0,,{{\\fad(150,120)}}{txt}")
    path.write_text(head + "\n".join(rows) + "\n", encoding="utf-8-sig")


def main(script_path: str):
    sp = Path(script_path).resolve()
    spec = json.loads(sp.read_text(encoding="utf-8"))
    vid = spec["id"]
    work = ROOT / "production" / "video" / "build" / vid
    work.mkdir(parents=True, exist_ok=True)
    speaker = spec.get("speaker", 14)
    speed = spec.get("speed", 1.2)
    w, h = spec.get("width", 1920), spec.get("height", 1080)

    # 1. 音声合成
    cursor = LEAD_IN
    lines = []
    wavs = []
    for i, line in enumerate(spec["lines"]):
        wav = work / f"line_{i:03d}.wav"
        dur = synth(line.get("voice", line["text"]), speaker, speed, wav)  # voice: 読み上げ用の別表記（任意）
        lines.append({"index": i, "text": line["text"], "start": round(cursor, 3),
                      "end": round(cursor + dur, 3), "cue": line.get("cue", "")})
        wavs.append((wav, cursor))
        cursor += dur + line.get("pause", 0.25)
    total = cursor + TAIL
    timing = {"id": vid, "title": spec.get("title", ""), "fps": FPS, "width": w, "height": h,
              "duration": round(total, 3), "frames": int(total * FPS) + 1, "lines": lines,
              "fonts_dir": str(FONTS_DIR).replace("\\", "/")}
    (work / "timing.json").write_text(json.dumps(timing, ensure_ascii=False, indent=2), encoding="utf-8")

    # 音声を1本にまとめる（各行を開始時刻に配置）
    inputs, filters = [], []
    for k, (wav, start) in enumerate(wavs):
        inputs += ["-i", str(wav)]
        ms = int(start * 1000)
        filters.append(f"[{k}:a]adelay={ms}|{ms}[a{k}]")
    mix = "".join(f"[a{k}]" for k in range(len(wavs)))
    filters.append(f"{mix}amix=inputs={len(wavs)}:normalize=0,apad=whole_dur={total:.3f},loudnorm=I=-16:TP=-1.5[out]")
    subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error", *inputs,
                    "-filter_complex", ";".join(filters), "-map", "[out]", "-ar", "48000",
                    str(work / "voice.wav")], check=True)

    # 2. 字幕
    write_ass(timing, work / "subs.ass", w, h)

    # 3. Blender で映像を描画（JPEG連番）
    frames_dir = work / "frames"
    if frames_dir.exists():
        for f in frames_dir.glob("*.jpg"):
            f.unlink()
    frames_dir.mkdir(exist_ok=True)
    scene_py = (sp.parent / spec["blender_script"]).resolve()
    subprocess.run([BLENDER, "-b", "--factory-startup", "--python-exit-code", "1", "-P", str(scene_py), "--",
                    str(work / "timing.json"), str(frames_dir / "f_")], check=True)
    if not list(frames_dir.glob("f_*.jpg")):
        raise SystemExit("Blenderの出力が見つかりません")

    # 4. 合成（字幕焼き込み＋音声）
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / f"{vid}.mp4"
    ass = str(work / "subs.ass").replace("\\", "/").replace(":", "\\:")
    fonts = str(FONTS_DIR).replace("\\", "/").replace(":", "\\:")
    subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
                    "-framerate", str(FPS), "-i", str(frames_dir / "f_%04d.jpg"), "-i", str(work / "voice.wav"),
                    "-vf", f"ass='{ass}':fontsdir='{fonts}'",
                    "-c:v", "libx264", "-preset", "slow", "-crf", "22", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", "-shortest",
                    str(out)], check=True)
    # サムネイル。poster_sec があればその秒の字幕なしのコマ、なければ中盤の1コマ
    if "poster_sec" in spec:
        frame = frames_dir / f"f_{int(spec['poster_sec'] * FPS) + 1:04d}.jpg"
        subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
                        "-i", str(frame), "-q:v", "3", str(OUT_DIR / f"{vid}.jpg")], check=True)
    else:
        subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error", "-ss", f"{total*0.45:.2f}",
                        "-i", str(out), "-frames:v", "1", "-q:v", "3", str(OUT_DIR / f"{vid}.jpg")], check=True)
    print(f"完成: {out}（{total:.1f}秒）")


if __name__ == "__main__":
    main(sys.argv[1])
