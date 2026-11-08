"""第10部 卒業：導入動画（part10-intro）

場面構成（行番号は part10-intro.json の lines）
  0-1    A タイトル → 書く側の机から、確かめる側（虫眼鏡）の席へ人形が移る
  2-5    B 困りごと : 152個の緑のマス（全部通る）→ 床の下に18個の赤い穴 →「通っただけでは足りない」
  6-9    C 第47章 AIと組むレビュー術 : チェックリストと AI のコードの照合 → grep / コンパイル / テストの3つの漏斗 → 欠陥入りコード3本が赤いテストになる
  10-15  C 第48章 卒業制作 : 12の枠が埋まる / 30マスと4段の階段 → 次に学ぶものの地図 → 新しい技術は ✕、既存の道具が組み合わさる
  16-17  D 前→後 : AIのコード？ → 判定 ✓ ✕ と、12の枠が埋まった設計書
  18     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import math
import os
import random
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import part_intro_kit as P

K = P.K
I = P.Intro(10, "卒業", [(47, "AIと組むレビュー術"), (48, "卒業制作")])
A, E, M = I.A, I.E, I.M
INK = P.INK
STAGE = (K.hexc("#3a63b8"), K.hexc("#4c8dff"), K.hexc("#7fb0ff"), K.hexc("#bcd6ff"))


def square(loc, size, color, z=0.0):
    """角の小さい正方形のマス（P.shade で色を替えられるよう、本体は body という名前）。"""
    root = bpy.data.objects.new("sq", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    body = K.rounded_rect("body", size, size, (0, 0, 0), color, size * 0.18)
    body.parent = root
    return root


def lens(g, loc, r, f, inner=None):
    """虫眼鏡（輪＋柄）。inner は輪の中に見せる色（なければ暗い地）。登録して (ring, in, handle) を返す。"""
    rg = K.dot(loc, r, K.WHITE, None, z=0.4)
    inn = K.dot(loc, r * 0.78, K.hexc("#1d2440"), None, z=0.45)
    hd = P.seg((loc[0] + r * 0.7, loc[1] - r * 0.7), (loc[0] + r * 1.7, loc[1] - r * 1.7), 0.2 * (r / 0.6), K.WHITE, z=0.4)
    objs = [rg, inn, hd]
    if inner is not None:
        objs.append(K.rounded_rect("zoom", r * 0.7, r * 0.7, (loc[0], loc[1], 0.5), inner, 0.1))
    for o in objs:
        g.pop(o, f, 8)
    return objs


# ======================================================================
# A タイトル → 書く側の机から、確かめる側（虫眼鏡）の席へ
# ======================================================================
I.title_card()
ga = P.Group(I, A(2) - 3)
desk = K.card("書く側", (-4.8, 0.2), 3.2, 1.0, P.CUST, size=0.46)
ga.pop(desk, A(1, 0.1))
who = P.person((-4.8, 1.5), K.ORANGE, 1.0)
ga.pop(who, A(1, 0.2))
seat = K.card("確かめる側", (4.4, 0.2), 3.6, 1.0, K.BLUE, size=0.46)
ga.pop(seat, A(1, 0.5))
lens(ga, (5.7, 2.4), 0.75, A(1, 0.7))
K.move(who, A(1, 1.1), (1.2, 2.0), 14, "SINE", "EASE_IN_OUT")
K.move(who, A(1, 1.1) + 14, (3.6, 1.5), 20, "SINE", "EASE_IN_OUT")
# 席に着いた人形が虫眼鏡をのぞく（少し跳ねる）
K.pulse(who, A(1, 1.1) + 36, 1.1, 8)

# ======================================================================
# B 困りごと：緑のマス → 床の下に赤い穴
# ======================================================================
b = I.trouble()
COLS, ROWS, PITCH, CELL = 19, 8, 0.32, 0.26
GCX, GCY = 2.2, 1.35
x0 = GCX - (COLS - 1) / 2 * PITCH
y0 = GCY + (ROWS - 1) / 2 * PITCH
floor = K.card("", (GCX, GCY), COLS * PITCH + 0.5, ROWS * PITCH + 0.45, K.DARK)
b.pop(floor, A(2, 0.4))
aic = P.chip("AIのコード", (GCX, GCY + 1.95), K.GRAY, 0.4, z=0.1)
b.pop(aic, A(2, 0.6))
cnt1 = K.card("", (-5.2, 1.95), 3.0, 1.55, K.DARK)
b.pop(cnt1, A(2, 1.0))
K.text("テスト", 0.36, (0, 0.5, 0.05), K.WHITE, 700, parent=cnt1)
cnt2 = K.card("", (-5.2, 0.1), 3.0, 1.55, K.DARK)
b.pop(cnt2, A(2, 1.4))
K.text("穴", 0.36, (0, 0.5, 0.05), K.WHITE, 700, parent=cnt2)
cells = []
rnd = random.Random(10)
holes = set(rnd.sample(range(COLS * ROWS), 18))
for col in range(COLS):
    for row in range(ROWS):
        idx = col * ROWS + row
        x, y = x0 + col * PITCH, y0 - row * PITCH
        if idx in holes:
            rc = K.rounded_rect("hole", CELL, CELL, (x, y, 0.06), K.RED, 0.05)
            b.pop(rc, A(3, 0.2) + col * 3, 5)
        gc = K.rounded_rect("cell", CELL, CELL, (x, y, 0.1), K.GREEN, 0.05)
        b.pop(gc, A(3, 0.2) + col * 3, 5)
        if idx in holes:
            cells.append(gc)
# 行3：152 に増える（全部 GREEN）
seq1 = [(1, "0", K.WHITE)]
for col in (2, 5, 8, 11, 14, 18):
    seq1.append((A(3, 0.2) + col * 3 + 3, str(8 * (col + 1)), K.WHITE))
K.number_ticker(seq1, (0, -0.3, 0.05), 0.95, parent=cnt1, end=None)
P.shade(cnt1, [(A(3, 0.2) + 18 * 3 + 3, P.OKBG)], K.DARK)
# 行4：床板が外れて、下に18個の赤い穴
seq2 = [(1, "0", K.WHITE)]
for i, cell in enumerate(cells):
    P.out(cell, A(4, 0.5) + i * 4, 6)
    if i in (2, 5, 8, 11, 14, 17):
        seq2.append((A(4, 0.5) + i * 4 + 6, str(i + 1), K.WHITE))
K.number_ticker(seq2, (0, -0.3, 0.05), 0.95, parent=cnt2, end=None)
P.shade(cnt2, [(A(4, 0.5) + 17 * 4 + 6, P.NGBG)], K.DARK)
K.shake(cnt2, A(4, 0.5) + 17 * 4 + 8, 0.12, 6, 2)
lens(b, (-2.1, 1.3), 0.7, A(4, 0.3), inner=K.RED)
# 行5：通っただけでは足りない
ys = P.chip("通っただけでは足りない", (GCX, -0.55), K.YELLOW, 0.44, INK, z=0.1)
b.pop(ys, A(5, 0.6))
K.pulse(ys, A(5, 0.6) + 12, 1.12, 8)
K.pulse(ys, A(5, 1.8), 1.08, 8)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---- 第47章：AIと組むレビュー術
c = I.chapter(47)
s1 = c.sub()
PXL, PXR = -3.9, 3.9
cl = K.card("", (PXL, 1.2), 3.8, 3.5, K.DARK)
cd = K.card("", (PXR, 1.2), 3.8, 3.5, P.CUST)
s1.pop(cl, c.A(0, 0.2))
s1.pop(cd, c.A(0, 0.4))
clh = P.chip("チェックリスト", (PXL, 2.6), K.GRAY, 0.34, z=0.1)
cdh = P.chip("AIのコード", (PXR, 2.6), K.GRAY, 0.34, z=0.1)
s1.pop(clh, c.A(0, 0.5))
s1.pop(cdh, c.A(0, 0.7))
ROWY = (1.7, 1.05, 0.4)
tags = []
for j, y in enumerate(ROWY):
    for cx_ in (PXL, PXR):
        bar = K.card("", (cx_ + 0.3, y), 2.2, 0.2, K.GRAY, z=0.1)
        s1.pop(bar, c.A(0, 0.9) + j * 4, 8)
    tg = K.card("", (PXL - 1.2, y), 0.5, 0.36, K.BLUE, z=0.15)
    s1.pop(tg, c.A(0, 0.95) + j * 4, 8)
    tags.append(tg)
lens(s1, (PXL + 0.4, -0.2), 0.5, c.A(0, 1.6))
# 行7：約束の札が AI のコードの行と照合される
verdict = ((True, c.A(1, 0.9)), (True, c.A(1, 1.3)), (False, c.A(1, 1.7)))
for j, (tg, y) in enumerate(zip(tags, ROWY)):
    ok, f = verdict[j]
    cp = K.card("", (PXL - 1.2, y), 0.5, 0.36, K.BLUE, z=0.3)
    s1.pop(cp, c.A(1, 0.1) + j * 5, 6)
    K.move(cp, c.A(1, 0.3) + j * 8, (PXR - 1.2, y), 22, "SINE", "EASE_IN_OUT")
    mk = P.tick((PXR + 1.45, y), 0.22, z=0.4) if ok else P.nomark((PXR + 1.45, y), 0.22)
    s1.pop(mk, f, 8)
# 行8：grep → コンパイル → テスト の3つの漏斗が古い書き方を取り除く
s2 = c.sub()
for o in list(s1.objs):
    P.out(o, c.A(2, 0.0) - 6, 6)
FX = (-4.4, 0.0, 4.4)
belt = K.card("", (0, 0.3), 13.6, 0.4, P.ROAD, z=-0.01)
s2.pop(belt, c.A(2, 0.1))
for j, (fx, lab) in enumerate(zip(FX, ("grep", "コンパイル", "テスト"))):
    fn = P.poly((fx, 1.75), 0.85, 3, K.YELLOW, math.pi, 0.1)
    s2.pop(fn, c.A(2, 0.3) + j * 5)
    ch_ = P.chip(lab, (fx, 2.95), K.GRAY, 0.4, z=0.1)
    s2.pop(ch_, c.A(2, 0.4) + j * 5)
    # 古い書き方（RED の札）が漏斗の下で止まり、落ちて ✕
    t0 = c.A(2, 0.4 + 1.0 * j)
    old = K.card("古い", (-6.6, 0.8), 0.9, 0.55, K.RED, size=0.26, z=0.2)
    s2.pop(old, t0, 6)
    K.move(old, t0 + 4, (fx, 0.8), 26, "SINE", "EASE_IN_OUT")
    K.pulse(fn, t0 + 32, 1.2, 8)
    K.move(old, t0 + 34, (fx, -0.55), 12, "SINE", "EASE_IN")
    nmk = P.nomark((fx, -0.55), 0.26)
    s2.pop(nmk, t0 + 46, 8)
    P.out(old, t0 + 48, 6)
okend = P.tick((6.5, 0.9), 0.3, z=0.4)
s2.pop(okend, c.A(2, 3.5), 8)
# 行9：欠陥入りのコード3本 → 赤いテストの札
s3 = c.sub()
for o in list(s2.objs):
    P.out(o, c.A(3, 0.0) - 6, 6)
dh = P.chip("欠陥入りコード ×3", (0, 3.2), K.GRAY, 0.4, z=0.1)
s3.pop(dh, c.A(3, 0.1))
for j, x in enumerate((-4.5, 0.0, 4.5)):
    pp = K.card("", (x, 1.6), 3.3, 1.9, P.CUST)
    s3.pop(pp, c.A(3, 0.25) + j * 6)
    for i, y in enumerate((2.15, 1.7, 1.25)):
        col = K.RED if i == 1 else K.GRAY
        ln = K.card("", (x - 0.2, y), 2.3 if i != 2 else 1.6, 0.18, col, z=0.1)
        s3.pop(ln, c.A(3, 0.45) + j * 6 + i * 3, 8)
    tc = K.card("テスト", (x, 0.1), 2.0, 0.75, P.NGBG, size=0.4, z=0.1)
    f = c.A(3, 1.0) + j * 14
    s3.pop(tc, f)
    K.shake(tc, f + 10, 0.1, 4, 2)
    ar = K.arrow((x, 0.7), (x, 0.48), K.WHITE, 0.07, 0.2, z=0.2)
    s3.pop(ar, f - 2, 6)

# ---- 第48章：卒業制作
c = I.chapter(48)
s1 = c.sub()
SX0, SY = -6.2, (2.0, 0.95)
slots = []
for k in range(12):
    row, col = divmod(k, 6)
    x, y = SX0 + col * 0.98, SY[row]
    sl = K.card("", (x, y), 0.82, 0.82, K.DARK, z=0.0)
    s1.pop(sl, c.A(0, 0.1) + k * 2, 6)
    slots.append((sl, x, y))
t12 = P.chip("12項目", (-3.7, 3.1), K.GRAY, 0.4, z=0.1)
s1.pop(t12, c.A(0, 0.5))
dsg = P.chip("設計書", (-3.7, -0.15), K.GRAY, 0.36, z=0.1)
s1.pop(dsg, c.A(0, 0.7))
for k, (sl, x, y) in enumerate(slots):
    f = c.A(1, 0.2) + k * 8
    fl = K.card("", (x, y), 0.82, 0.82, K.GREEN, z=0.1)
    s1.pop(fl, f, 8)
    K.pulse(fl, f + 6, 1.1, 8)
# 30項目のマスと4段の階段（行12）
GXs, GYs, GP = 1.3, 2.4, 0.5
grid = []
sizes = (8, 8, 7, 7)
t30 = P.chip("30項目", (2.8, 3.1), K.GRAY, 0.4, z=0.1)
s1.pop(t30, c.A(2, 0.2))
for k in range(30):
    row, col = divmod(k, 6)
    x, y = GXs + col * GP, GYs - row * GP
    sq = square((x, y), 0.4, K.DARK)
    s1.pop(sq, c.A(2, 0.3) + k, 5)
    grid.append(sq)
stage_of = []
for st, n in enumerate(sizes):
    stage_of += [st] * n
for k, sq in enumerate(grid):
    st = stage_of[k]
    P.shade(sq, [(c.A(2, 1.2 + st * 0.65), STAGE[st])], K.DARK)
for st in range(4):
    h = 0.5 + st * 0.5
    stp = K.card("", (5.55 + st * 0.5, -0.1 + h / 2), 0.4, h, STAGE[st], z=0.0)
    s1.pop(stp, c.A(2, 1.2 + st * 0.65), 8)
    K.pulse(stp, c.A(2, 1.2 + st * 0.65) + 6, 1.1, 8)
# 行13：次に学ぶものを選ぶ地図
s2 = c.sub()
for o in list(s1.objs):
    P.out(o, c.A(3, 0.0) - 6, 6)
mp = K.card("", (-2.4, 1.2), 6.4, 3.4, K.DARK)
s2.pop(mp, c.A(3, 0.1))
road = P.ROAD
r1 = P.seg((-4.8, 0.2), (-2.6, 1.3), 0.28, road, z=0.05)
r2 = P.seg((-2.6, 1.3), (-0.6, 2.4), 0.28, road, z=0.05)
r3 = P.seg((-2.6, 1.3), (-0.6, 0.3), 0.28, road, z=0.05)
for i, o in enumerate((r1, r2, r3)):
    s2.pop(o, c.A(3, 0.4) + i * 5, 8)
pin = P.poly((-4.8, 0.55), 0.38, 3, K.RED, math.pi, 0.2)
s2.pop(pin, c.A(3, 0.3), 8)
for loc, col in (((-0.6, 2.4), K.BLUE), ((-0.6, 0.3), K.ORANGE), ((-2.6, 1.3), K.GRAY)):
    dd = K.dot(loc, 0.3, col, None, z=0.3)
    s2.pop(dd, c.A(3, 0.7), 8)
nx = P.chip("次に学ぶもの", (4.2, 1.3), K.WHITE, 0.46, INK, z=0.1)
s2.pop(nx, c.A(3, 1.5))
ar = K.arrow((1.0, 1.3), (2.45, 1.3), K.WHITE, 0.1, 0.34)
K.grow_x(ar, c.A(3, 1.3), 10)
s2.add(ar)
# 行14〜15：新しい技術は出てこない。ここまでの道具を組み合わせる
s3 = c.sub()
for o in list(s2.objs):
    P.out(o, c.A(4, 0.0) - 6, 6)
nt = P.chip("新しい技術", (-1.0, 3.35), K.GRAY, 0.42, z=0.1)
s3.pop(nt, c.A(4, 0.2))
nm = P.nomark((1.3, 3.35), 0.34)
s3.pop(nm, c.A(4, 0.6), 8)
K.pulse(nm, c.A(4, 0.6) + 10, 1.2, 8)
TOOLS = (("Java", K.BLUE), ("Spring", K.BLUE), ("DB", K.PURPLE), ("テスト", P.OKBG), ("運用", P.CUST))
tools = []
for j, (lab, col) in enumerate(TOOLS):
    x = -5.4 + j * 2.7
    tc = K.card(lab, (x, 1.2), 2.3, 1.2, col, size=0.46)
    s3.pop(tc, c.A(4, 1.0) + j * 6)
    tools.append((tc, x))
gl = P.chip("ここまでの道具", (0, 2.4), K.GRAY, 0.36, z=0.1)
s3.pop(gl, c.A(4, 1.2))
# 行15：近づいて組み合わさる
for j, (tc, x) in enumerate(tools):
    K.move(tc, c.A(5, 0.2) + j * 2, (-4.4 + j * 2.2, 1.2), 22, "SINE", "EASE_IN_OUT")
for j in range(4):
    lk = K.card("", (-4.4 + j * 2.2 + 1.1, 1.2), 0.34, 0.34, K.WHITE, z=0.3)
    s3.pop(lk, c.A(5, 0.9) + j * 4, 6)
fin = P.chip("組み合わせて使う", (0.4, -0.1), K.WHITE, 0.4, INK, z=0.1)
s3.pop(fin, c.A(5, 1.4))
K.pulse(fin, c.A(5, 1.4) + 12, 1.1, 8)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(16, 0.1))
d.row("before", "AIのコード", "q", A(16, 0.5))
d.row("before", "基準がない", "ng", A(16, 1.2))
d.row("after", "約束どおり", "ok", A(16, 1.8))
d.row("after", "約束と違う", "ng", A(16, 2.7))
# 設計書：12の枠が埋まる
dr = K.card("", (3.85, -0.15), 5.5, 0.8, K.DARK, size=0.3, z=0.1)
d.g.pop(dr, A(17, 0.3), 8)
dl = P.chip("設計書", (1.75, -0.15), K.GRAY, 0.34, z=0.2)
d.g.pop(dl, A(17, 0.4))
for k in range(12):
    sq = square((2.85 + k * 0.38, -0.15), 0.3, K.GREEN, z=0.2)
    d.g.pop(sq, A(17, 0.7) + k * 5, 6)

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["コードを書く側から、", "約束を決める側へ。"])
I.render()
