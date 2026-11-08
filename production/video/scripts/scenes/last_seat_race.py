"""残り1枠に2人：競合とロック（第25章）

行番号は last-seat-race.json の lines。
  0      タイトル
  1      投票ルーム 上限50人、いま49人（残り1枠）
  2-8    時間レーンにAとB。2人とも数える → 49と見える → 入れると判断 → 登録 → 51人（赤）→ 競合状態
  9-11   トランザクションを付ければ？ → それだけでは防げない（両方とも自分の中で49を見ている）
  12-13  対策：数える前に行に鍵（SELECT FOR UPDATE）
  14-17  Aが鍵 → Bは待つ → Aが数えて登録・コミットで鍵が開く → Bが鍵を取り数え直す50 → 満員で409
  18     なぜ最新が見えるか（READ COMMITTED）
  19     まとめ
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
import bpy
import bl_kit as K

T = K.setup()
A = lambda i, off=0.0: K.at(T, i, off)

INK = K.BG
PANEL = K.hexc("#2b3350")
BAND = K.hexc("#222a42")
COLS = [-4.4, -2.2, 0.0, 2.2, 4.4, 6.6]        # 時間の列
LY = {"A": 0.85, "B": -0.95}                    # 参加者ごとのレーンの高さ
PCOL = {"A": K.BLUE, "B": K.PURPLE}
ROOM_C = (2.0, 3.0)
LOCK_C = (-1.8, 3.0)
CHIP_Y = -2.25                                   # 下側の説明チップの高さ（字幕より上）


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None):
    w = w or (len(label) * 0.78 * size + 0.9)
    return K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size)


def hdr(label, color, tc=K.WHITE, cam=(0.0, 0.0, 16.0)):
    w = len(label) * 0.78 * 0.4 + 0.9
    cx, cy, z = cam
    return K.card(label, loc=(cx - z / 2 + 0.7 + w / 2, cy + z * 9 / 32 - 0.85), w=w, h=0.8,
                  color=color, text_color=tc, size=0.4)


def ext_stage(windows):
    """寄りの間だけ、舞台の板を画面いっぱいに広げる（板の縁が映り込むのを防ぐ）。
    背景と同じ色から板の色へなめらかに変え、寄りが終わると元に戻す。windows=[(開始, 終了), ...]"""
    o = K.rounded_rect("stage_ext", 40, 30, (0, 0.85, -1.05), K.BG, 0.5)
    m = K.mat(K.BG, name="ext_stage_mat")
    o.data.materials[0] = m
    sock = [n for n in m.node_tree.nodes if n.type == "EMISSION"][0].inputs["Color"]
    sock.default_value = K.BG
    sock.keyframe_insert("default_value", frame=1)
    for a, b in windows:
        for f, c in ((a, K.BG), (a + 12, K.BG2), (b - 12, K.BG2), (b, K.BG)):
            sock.default_value = c
            sock.keyframe_insert("default_value", frame=f)
    return o


def out(objs, f, step=2):
    for i, o in enumerate(objs):
        K.pop_out(o, f + i * step)


def step_card(who, col, label, sub, f, color=None, tc=K.WHITE, w=2.0, cx=None):
    c = K.card(label, loc=(COLS[col] if cx is None else cx, LY[who]), w=w, h=1.1,
               color=color or PCOL[who], text_color=tc, size=0.5, sub=sub)
    K.pop_in(c, f)
    return c


def lock_icon(loc, s=1.0, color=K.GREEN, opened=False, label=None):
    """南京錠。opened=True で鍵が開いた形（輪が持ち上がる）。"""
    root = bpy.data.objects.new("lock", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], 0.4)
    cx, cy = (0.2 * s, 0.38 * s) if opened else (0.0, 0.3 * s)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.24 * s, minor_radius=0.065 * s, major_segments=32,
                                     minor_segments=8, location=(cx, cy, -0.12))
    ring = bpy.context.active_object
    ring.data.materials.append(K.mat(color))
    ring.parent = root
    body = K.rounded_rect("lbody", 0.82 * s, 0.62 * s, (0, -0.05 * s, 0.05), color, 0.12 * s)
    body.parent = root
    if label:
        K.text(label, 0.34 * s, (0, -0.05 * s, 0.1), INK, 900, parent=root)
    return root


def kpulse(o, f, amount=1.2, dur=8):
    """show_between で出し入れしている物用の脈動（基準の大きさは1）。"""
    K._key(o, "scale", f, (1, 1, 1), "SINE", "EASE_IN_OUT")
    K._key(o, "scale", f + dur // 2, (amount,) * 3, "SINE", "EASE_IN_OUT")
    K._key(o, "scale", f + dur, (1, 1, 1), "SINE", "EASE_IN_OUT")


def room_card(f_in, seq, f_out=None):
    """投票ルームの表示。seq=[(フレーム, "49人", 色)]"""
    rc = K.card("", loc=ROOM_C, w=6.0, h=1.7, color=PANEL)
    K.text("投票ルーム", 0.44, (-1.6, 0.38, 0.05), K.WHITE, 700, parent=rc)
    K.text("上限50人", 0.36, (-1.6, -0.35, 0.05), K.WHITE, 400, parent=rc)
    K.number_ticker(seq, (1.45, -0.02, 0.05), 1.0, parent=rc)
    K.pop_in(rc, f_in)
    if f_out:
        K.pop_out(rc, f_out)
    return rc


# ======================================================================
# 0. タイトル
# ======================================================================
K.title_card(T, "残り1枠に2人", "競合とロック", until_line=0)
stage = K.rounded_rect("stage", 15.2, 6.7, (0, 0.85, -1), K.BG2, 0.5)
K.pop_in(stage, A(1) - 4, 12)
a = {i: A(i) for i in range(1, 20)}
ext_stage([(a[7], a[8] + 24), (a[17], a[18] + 24)])

# ======================================================================
# 1. 投票ルーム：上限50人、いま49人
# ======================================================================
h1 = hdr("2人が同時に入室", K.TEAL)
K.pop_in(h1, a[1] + 2)
room1 = room_card(a[1] + 6, [(a[1] + 20, "49人", K.WHITE), (a[6] + 38, "50人", K.YELLOW), (a[7] + 4, "51人", K.WHITE)])
left1 = chip("残り1枠", (6.4, 3.0), K.YELLOW, 0.42, INK, w=2.2)
K.pop_in(left1, a[1] + 44)
K.pulse(left1, a[1] + 58, 1.15, 8)
K.pulse(room1, a[1] + 58, 1.05, 8)

# ======================================================================
# 2. 時間レーンにAさんとBさん
# ======================================================================
bands, people, names = [], [], []
for who in "AB":
    band = K.rounded_rect(f"lane_{who}", 14.6, 1.5, (0, LY[who], -0.5), BAND, 0.3)
    K.pop_in(band, a[2] + (0 if who == "A" else 10))
    bands.append(band)
    p = K.person((-6.75, LY[who] + 0.2), PCOL[who], 0.5)
    K.pop_in(p, a[2] + 14 + (0 if who == "A" else 8))
    people.append(p)
    nm = K.text(f"{who}さん", 0.32, (-6.75, LY[who] - 0.5, 0.2), K.WHITE, 700)
    K.pop_in(nm, a[2] + 20 + (0 if who == "A" else 8))
    names.append(nm)
K.pulse(people[0], a[2] + 56, 1.15, 8)
K.pulse(people[1], a[2] + 56, 1.15, 8)

# ======================================================================
# 3-6. 数える → 49と見える → 入れると判断 → 登録
# ======================================================================
p1 = []
for col, (lab, sub, f, color, tc) in enumerate([
        ("数える", "SELECT count", a[3] + 6, None, K.WHITE),
        ("49人", "と見えた", a[4] + 4, None, K.WHITE),
        ("入れる", "49 < 50", a[5] + 10, K.GREEN, INK),
        ("登録", "INSERT", a[6] + 4, K.ORANGE, INK)]):
    for who in "AB":
        p1.append(step_card(who, col, lab, sub, f, color, tc))
    # AとBの「同時」を示す縦の点線
    ln = K.dashed_line((COLS[col], LY["A"] - 0.58), (COLS[col], LY["B"] + 0.58), K.GRAY, 0.06, 0.1, 0.08)
    K.pop_in(ln, f + 2)
    p1.append(ln)
K.pulse(room1, a[3] + 18, 1.06, 8)
K.pulse(room1, a[4] + 10, 1.06, 8)
K.pulse(p1[3], a[4] + 40, 1.12, 8)
K.pulse(p1[4], a[4] + 40, 1.12, 8)

# 6-7: 51人（赤）
K.pop_out(left1, a[6])
K.pulse(room1, a[6] + 38, 1.08, 8)
K.camera_move(a[7], to=(0.0, 0.5), zoom=15.8, dur=20)
K.pop_out(h1, a[7])
K.recolor(room1, a[7] + 4, K.RED)
K.shake(room1, a[7] + 8, 0.18, 8, 2)
over = chip("上限を超えた", (6.3, 3.0), K.RED, 0.4, w=2.5)
K.pop_in(over, a[7] + 16)
K.pulse(over, a[7] + 34, 1.12, 8)

# 8: 競合状態
K.camera_move(a[8], to=(0, 0), zoom=16, dur=20)
h8 = hdr("競合状態", K.RED)
K.pop_in(h8, a[8] + 10)
K.pulse(h8, a[8] + 30, 1.12, 8)
race = chip("同じ数字を見て、同時に動いた", (0.4, CHIP_Y), K.RED, 0.4)
K.pop_in(race, a[8] + 24)
K.shake(race, a[8] + 40, 0.1, 6, 2)

# ======================================================================
# 9-11. トランザクションを付ければ？ → 防げない
# ======================================================================
K.pop_out(race, a[9])
K.pop_out(over, a[9])
frames, flabels = [], []
for who in "AB":
    fo = K.rounded_rect(f"tx_o_{who}", 9.2, 1.42, (-1.05, LY[who], -0.45), K.TEAL, 0.3)
    fi = K.rounded_rect(f"tx_i_{who}", 9.0, 1.22, (-1.05, LY[who], -0.44), BAND, 0.25)
    for o in (fo, fi):
        K.pop_in(o, a[9] + (6 if who == "A" else 16))
    fl = K.text(f"{who}のトランザクション", 0.36, (5.9, LY[who], 0.2), K.TEAL, 700)
    K.pop_in(fl, a[9] + (14 if who == "A" else 24))
    frames += [fo, fi]
    flabels.append(fl)
q = chip("トランザクションを付ければ？", (0.4, CHIP_Y), K.TEAL, 0.4)
K.pop_in(q, a[9] + 30)
K.pop_out(h8, a[9])

K.pop_out(q, a[10])
no = chip("それだけでは防げない", (0.4, CHIP_Y), K.RED, 0.42)
K.pop_in(no, a[10] + 4)
K.shake(no, a[10] + 18, 0.14, 8, 2)

K.pop_out(no, a[11])
both = chip("どちらも、自分の中では49人", (0.4, CHIP_Y), K.YELLOW, 0.4, INK)
K.pop_in(both, a[11] + 4)
for k, off in enumerate((24, 64, 104)):
    K.pulse(p1[3], a[11] + off, 1.15, 8)
    K.pulse(p1[4], a[11] + off, 1.15, 8)

# ======================================================================
# 12-13. 対策：数える前に鍵を掛ける
# ======================================================================
f12 = a[12]
out(p1 + frames + flabels + [both, room1], f12 - 2, 1)
h12 = hdr("対策：先に鍵", K.GREEN, INK)
K.pop_in(h12, f12 + 6)
room2 = room_card(f12 + 4, [(f12 + 12, "49人", K.WHITE), (a[15] + 62, "50人", K.WHITE)])
left2 = chip("残り1枠", (6.4, 3.0), K.YELLOW, 0.42, INK, w=2.2)
K.pop_in(left2, f12 + 16)

lk_free1 = lock_icon(LOCK_C, 1.0, K.GREEN, True)
lk_a = lock_icon(LOCK_C, 1.0, K.BLUE, False, "A")
lk_free2 = lock_icon(LOCK_C, 1.0, K.GREEN, True)
lk_b = lock_icon(LOCK_C, 1.0, K.PURPLE, False, "B")
f_a_on, f_a_off = a[14] + 22, a[15] + 84
f_b_on = a[16] + 10
K.show_between(lk_free1, f12 + 24, f_a_on)
K.show_between(lk_a, f_a_on, f_a_off)
K.show_between(lk_free2, f_a_off, f_b_on)
K.show_between(lk_b, f_b_on, None)
before = chip("数える前に、鍵を掛ける", (0.4, CHIP_Y), K.YELLOW, 0.42, INK)
K.pop_in(before, f12 + 30)
K.pulse(before, f12 + 60, 1.1, 8)
K.pulse(room2, f12 + 60, 1.05, 8)

K.pop_out(before, a[13])
sfu = chip("SELECT … FOR UPDATE", (0.4, CHIP_Y), K.ORANGE, 0.42, INK, w=5.6)
K.pop_in(sfu, a[13] + 4)
K.pulse(sfu, a[13] + 24, 1.1, 8)
kpulse(lk_free1, a[13] + 40, 1.25, 8)

# ======================================================================
# 14. Aが鍵を取る。Bは鍵が開くまで待つ
# ======================================================================
K.pop_out(sfu, a[14])
K.pop_out(left2, a[14])
ca1 = step_card("A", 0, "鍵を取る", "FOR UPDATE", a[14] + 8)
cb1 = K.card("待ち（鍵が開くまで）", loc=(-2.2, LY["B"]), w=6.4, h=1.1, color=K.YELLOW, text_color=INK, size=0.46)
K.pop_in(cb1, a[14] + 40)
kpulse(lk_a, f_a_on + 8, 1.25, 8)
K.pulse(cb1, a[14] + 70, 1.06, 8)
K.pulse(cb1, a[14] + 100, 1.06, 8)

# ======================================================================
# 15. Aが数えて49 → 登録 → コミット → 鍵が開く
# ======================================================================
ca2 = step_card("A", 1, "数える", "49人", a[15] + 6)
ca3 = step_card("A", 2, "登録", "INSERT", a[15] + 36, K.ORANGE, INK)
ca4 = step_card("A", 3, "コミット", "鍵が開く", a[15] + 70, K.GREEN, INK)
K.pulse(room2, a[15] + 62, 1.08, 8)

# ======================================================================
# 16. Bが鍵を取り、数え直すと50人
# ======================================================================
K.pop_out(cb1, a[16] + 4)
cb_lock = step_card("B", 3, "鍵を取る", "FOR UPDATE", a[16] + 8)
cb_cnt = step_card("B", 4, "数え直す", "50人", a[16] + 50)
kpulse(lk_b, f_b_on + 8, 1.25, 8)
K.pulse(room2, a[16] + 54, 1.05, 8)

# ======================================================================
# 17. 満員 → 409
# ======================================================================
K.camera_move(a[17], to=(0.0, 0.4), zoom=15.8, dur=20)
K.pop_out(h12, a[17])
cb_ng = step_card("B", 5, "拒否", "409", a[17] + 8, K.RED, K.WHITE)
K.shake(cb_ng, a[17] + 22, 0.14, 8, 2)
K.pulse(cb_ng, a[17] + 40, 1.12, 8)
full = chip("満員", (6.4, 3.0), K.RED, 0.42, w=2.2)
K.pop_in(full, a[17] + 30)
K.pulse(full, a[17] + 46, 1.15, 8)
K.pulse(room2, a[17] + 50, 1.05, 8)

# ======================================================================
# 18. なぜ最新が見えるか（READ COMMITTED）
# ======================================================================
K.camera_move(a[18], to=(0, 0), zoom=16, dur=20)
rc_chip = chip("READ COMMITTED", (-3.3, CHIP_Y), K.TEAL, 0.4, w=4.4)
K.pop_in(rc_chip, a[18] + 20)
latest = chip("鍵を取ったあとは、最新が見える", (2.6, CHIP_Y), K.YELLOW, 0.4, INK)
K.pop_in(latest, a[18] + 60)
K.pulse(ca3, a[18] + 76, 1.15, 8)
K.pulse(cb_cnt, a[18] + 92, 1.2, 8)
K.pulse(cb_cnt, a[18] + 118, 1.2, 8)

# ======================================================================
# 19. まとめ
# ======================================================================
f19 = a[19]
out([rc_chip, latest, ca1, ca2, ca3, ca4, cb_lock, cb_cnt, cb_ng, full, room2,
     lk_b] + bands + people + names, f19 - 14, 1)
m1 = K.text("数えてから入れるなら、", 0.82, (0, 2.5, 0.5), K.WHITE, 900)
m2 = K.text("数える前に鍵を掛ける", 0.9, (0, 1.1, 0.5), K.ORANGE, 900)
K.pop_in(m1, f19 + 4)
K.pop_in(m2, f19 + 14)
big_lock = lock_icon((0, -1.0), 1.9, K.GREEN, False)
K.pop_in(big_lock, f19 + 26)
K.bob(big_lock, f19 + 40, f19 + 220, 0.12, 20)

# ======================================================================
# 描画（確認用に環境変数で静止画だけ出せる）  TM_LIST=300,600  TM_OUT=出力フォルダ  TM_PCT=50
# ======================================================================
sc = bpy.context.scene
if os.environ.get("TM_LIST"):
    sc.render.resolution_percentage = int(os.environ.get("TM_PCT", "50"))
    for fr in os.environ["TM_LIST"].split(","):
        sc.frame_set(int(fr))
        sc.render.filepath = os.path.join(os.environ["TM_OUT"], f"s_{int(fr):04d}.png")
        sc.render.image_settings.file_format = "PNG"
        bpy.ops.render.render(write_still=True)
else:
    K.render(T)
