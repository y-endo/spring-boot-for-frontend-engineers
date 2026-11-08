"""第7部 リアルタイム通知：導入動画（part07-intro）

場面構成（行番号は part07-intro.json の lines）
  0-1    A タイトル → ラジオから電波が広がり、棒グラフが伸びる
  2-3    B 困りごと : 2台のスマホ。A の棒だけ伸びる → B は「古い表示」→ 再読み込みで初めて伸びる
  4-8    C 第36章 SSE入門 : ノック・ラジオ・電話 → 一方向の流れ → ハートビートとタイムアウト → 遅いクライアント
  9-12   C 第37章 取りこぼしを防ぐ : 台帳 → 切れている間に積まれる → 最後は #42 から送り直し → 連番はコミット順
  13-15  C 第38章 2台構成 : 隣のサーバーには届かない → DB の鈴（LISTEN / NOTIFY）でつなぐ
  16-18  D 前→後
  19     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
import part_intro_kit as P

K = P.K
I = P.Intro(7, "リアルタイム通知", [(36, "SSE入門"), (37, "取りこぼしを防ぐ"), (38, "2台構成")])
A, E, M = I.A, I.E, I.M
INK = P.INK
PBG = K.hexc("#7c52d6")


# ======================================================================
# この動画だけで使う部品
# ======================================================================
def empty(loc, z=0.0):
    o = bpy.data.objects.new("grp", None)
    bpy.context.scene.collection.objects.link(o)
    o.location = (loc[0], loc[1], z)
    return o


def vbar(loc, w, H, color, frac, z=0.1):
    """縦の棒。(outer, inner) を返す。outer を pop し、inner の scale.y で長さを変える（根もと固定）。"""
    outer = empty(loc, z)
    inner = empty((0, 0), 0)
    inner.parent = outer
    inner.scale = (1, frac, 1)
    K._key(inner, "scale", 1, (1, frac, 1), "CONSTANT")
    body = K.rounded_rect("vbar", w, H, (0, H / 2, 0), color, min(0.12, w / 2 - 0.01))
    body.parent = inner
    return outer, inner


def vbar_to(inner, f, a, b, dur=14):
    K._key(inner, "scale", f, (1, a, 1), "SINE", "EASE_IN_OUT")
    K._key(inner, "scale", f + dur, (1, b, 1), "SINE", "EASE_IN_OUT")


def wave(loc, f, r=1.6, th=0.13, color=None, bg=None, dur=26, z=0.05):
    """電波の輪。f から広がって消える（登録不要）。"""
    root = empty(loc, z)
    K.dot((0, 0), r, color or K.BLUE, None, z=0.0).parent = root
    K.dot((0, 0), r - th, bg or K.BG, None, z=0.01).parent = root
    K._key(root, "scale", 1, (0, 0, 0), "CONSTANT")
    K._key(root, "scale", max(f - 1, 2), (0, 0, 0), "CONSTANT")
    K._key(root, "scale", f, (0.2, 0.2, 0.2), "SINE", "EASE_OUT")
    K._key(root, "scale", f + dur, (1, 1, 1), "LINEAR")
    K._key(root, "scale", f + dur + 1, (0, 0, 0), "CONSTANT")
    return root


def radio(loc, s=1.0, body=None, z=0.3):
    root = K.card("", (loc[0], loc[1]), 1.5 * s, 1.0 * s, body or K.DARK, z=z)
    for i in range(3):
        g = K.plane("grill", 0.5 * s, 0.07 * s, (-0.35 * s, (0.22 - i * 0.2) * s, 0.06), K.GRAY)
        g.parent = root
    d = K.dot((0.4 * s, 0.0), 0.2 * s, K.ORANGE, None, z=0.08)
    d.parent = root
    ant = K.plane("antenna", 0.07 * s, 0.9 * s, (0.5 * s, 0.85 * s, -0.01), K.GRAY)
    ant.rotation_euler = (0, 0, -0.45)
    ant.parent = root
    return root


def refresh(loc, s=1.0, z=0.4):
    """↻ の図形（輪の上に切れ目と矢じり）。回すと再読み込み。"""
    root = empty(loc, z)
    K.dot((0, 0), 0.5 * s, K.WHITE, None, z=0.0).parent = root
    K.dot((0, 0), 0.36 * s, K.BG, None, z=0.01).parent = root
    K.plane("gap", 0.46 * s, 0.4 * s, (0.1 * s, 0.43 * s, 0.02), K.BG).parent = root
    ah = P.poly((0.24 * s, 0.43 * s), 0.24 * s, 3, K.WHITE, -math.pi / 2, 0.03)
    ah.parent = root
    return root


def heart(loc, s=1.0, color=None, z=0.4):
    color = color or K.GREEN
    root = empty(loc, z)
    for dx in (-0.22, 0.22):
        K.dot((dx * s, 0.12 * s), 0.3 * s, color, None, z=0.0).parent = root
    P.poly((0, -0.1 * s), 0.52 * s, 3, color, math.pi, 0.0).parent = root
    return root


def hourglass(loc, s=1.0, z=0.4):
    root = empty(loc, z)
    P.poly((0, 0.3 * s), 0.42 * s, 3, K.YELLOW, math.pi, 0.0).parent = root
    P.poly((0, -0.3 * s), 0.42 * s, 3, K.YELLOW, 0.0, 0.0).parent = root
    for y in (0.66, -0.66):
        K.plane("cap", 1.0 * s, 0.09 * s, (0, y * s, 0.02), K.WHITE).parent = root
    return root


def bell(loc, s=1.0, z=0.5):
    root = empty(loc, z)
    K.dot((0, 0.08 * s), 0.36 * s, K.YELLOW, None, z=0.0).parent = root
    K.plane("bellbase", 0.86 * s, 0.2 * s, (0, -0.2 * s, 0.0), K.YELLOW).parent = root
    K.dot((0, -0.4 * s), 0.09 * s, K.YELLOW, None, z=0.0).parent = root
    return root


def knock_loop(o, f0, f1, dx=0.4, period=14):
    """f0〜f1 のあいだ、o を左右に往復させる（ノック）。"""
    base = K._cur(o, "location")
    f = f0
    K._key(o, "location", f, base, "SINE", "EASE_IN_OUT")
    while f + period <= f1:
        K._key(o, "location", f + period // 2, (base[0] + dx, base[1], base[2]), "SINE", "EASE_IN_OUT")
        K._key(o, "location", f + period, base, "SINE", "EASE_IN_OUT")
        f += period
    o.location = base


def pops(g, objs, f, step=3):
    for i, o in enumerate(objs):
        g.pop(o, f + i * step)
    return objs


# ======================================================================
# A タイトル → ラジオから電波が広がり、棒が伸びる
# ======================================================================
I.title_card()
ga = P.Group(I, A(2) - 3)
rd = radio((-3.4, 0.7), 1.9, K.DARK, z=0.3)
ga.pop(rd, A(1, 0.15))
panel = K.card("", (2.7, 1.0), 5.2, 3.4, K.DARK)
ga.pop(panel, A(1, 0.4))
bars = []
for x, fr in ((1.3, 0.35), (2.7, 0.5), (4.1, 0.3)):
    ob, inn = vbar((x, -0.2), 0.8, 2.6, K.BLUE, fr, z=0.1)
    ga.pop(ob, A(1, 0.6) + len(bars) * 4, 8)
    bars.append(inn)
for k in range(4):
    wave((-2.3, 2.35), A(1, 1.0) + k * 14, r=1.5, bg=K.BG, dur=26, z=0.02)
vbar_to(bars[1], A(1, 1.6), 0.5, 0.85, 14)
vbar_to(bars[0], A(1, 2.0), 0.35, 0.6, 14)
vbar_to(bars[2], A(1, 2.3), 0.3, 0.5, 14)
vbar_to(bars[1], A(1, 2.6), 0.85, 1.0, 12)

# ======================================================================
# B 困りごと：2台のスマホ。A の棒だけ伸びる
# ======================================================================
b = I.trouble()
PXA, PXB, PY = -3.6, 3.6, 1.1
pa = P.framed((PXA, PY), 3.0, 3.7, K.BG2, K.GRAY, 0.07, 0.3)
pb = P.framed((PXB, PY), 3.0, 3.7, K.BG2, K.GRAY, 0.07, 0.3)
b.pop(pa, A(2, 0.1))
b.pop(pb, A(2, 0.3))
la = P.chip("A", (PXA, PY + 1.45), K.ORANGE, 0.4, z=0.1)
lb = P.chip("B", (PXB, PY + 1.45), K.ORANGE, 0.4, z=0.1)
b.pop(la, A(2, 0.4))
b.pop(lb, A(2, 0.55))
bar_a, bar_b = [], []
for k, (dx, fr) in enumerate(((-0.85, 0.4), (0.0, 0.55), (0.85, 0.35))):
    oa, ia = vbar((PXA + dx, PY - 1.45), 0.6, 2.2, K.BLUE, fr, z=0.1)
    ob, ib = vbar((PXB + dx, PY - 1.45), 0.6, 2.2, K.BLUE, fr, z=0.1)
    b.pop(oa, A(2, 0.65) + k * 4, 8)
    b.pop(ob, A(2, 0.8) + k * 4, 8)
    bar_a.append(ia)
    bar_b.append(ib)
# A で投票すると、A の棒だけ伸びる
plus = K.dot((-6.2, 0.9), 0.42, K.ORANGE, "+1", INK, z=0.6)
b.pop(plus, A(2, 1.0), 8)
K.move(plus, A(2, 1.2), (PXA - 0.4, 0.4), 26, "SINE", "EASE_IN_OUT")
P.out(plus, A(2, 1.2) + 28, 6)
vbar_to(bar_a[1], A(2, 1.2) + 28, 0.55, 0.95, 14)
K.pulse(pa, A(2, 1.2) + 30, 1.04, 8)
stale = P.chip("古い表示", (PXB, 3.4), K.YELLOW, 0.38, INK)
b.pop(stale, A(2, 2.6))
K.pulse(stale, A(2, 2.6) + 12, 1.12, 8)
# 行3：再読み込みして初めて B の棒が伸びる
rf = refresh((6.3, 1.5), 1.1)
b.pop(rf, A(3, 0.15))
P.spin(rf, A(3, 0.3), A(3, 1.5), 1.5)
rl = P.chip("再読み込み", (6.1, 0.35), K.GRAY, 0.34)
b.pop(rl, A(3, 0.4))
vbar_to(bar_b[1], A(3, 1.55), 0.55, 0.95, 14)
P.out(stale, A(3, 1.5), 6)
tkb = P.tick((PXB + 1.3, 2.7), 0.3, z=0.5)
b.pop(tkb, A(3, 1.9), 8)
I.cam(A(2, 0.6), to=(0.4, 0.3), zoom=15.2, dur=24)
I.cam(A(4) - 10, to=(0, 0), zoom=16, dur=16)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---------------------------------------------------------------- 第36章 SSE入門
c = I.chapter(36)
s1 = c.sub()
TX = (-4.6, 0.0, 4.6)
tiles = []
for x in TX:
    t = K.card("", (x, 1.25), 4.0, 3.5, P.CUST)
    tiles.append(t)
for i, t in enumerate(tiles):
    s1.pop(t, c.A(0, 0.1) + i * 6)
for x, a_, b_ in zip(TX, ("ノック", "ラジオ", "電話"), ("ポーリング", "SSE", "WebSocket")):
    ta = P.label(a_, (x, 0.35), 0.55, K.WHITE, 900)
    tb = P.label(b_, (x, -0.2), 0.34, K.GRAY, 700)
    s1.pop(ta, c.A(0, 0.5) + int((x + 4.6) / 4.6) * 8)
    s1.pop(tb, c.A(0, 0.55) + int((x + 4.6) / 4.6) * 8)
# ノックのアイコン：拳が扉を叩く
kd = P.framed((TX[0] + 0.7, 1.95), 0.95, 1.35, K.BG, K.GRAY, 0.05, 0.12, z=0.1)
fist = K.dot((TX[0] - 0.55, 1.95), 0.3, K.SKIN, None, z=0.3)
s1.pop(kd, c.A(0, 0.7))
s1.pop(fist, c.A(0, 0.8))
knock_loop(fist, c.A(0, 1.2), c.A(2, 0.0), 0.3, 14)
# ラジオのアイコン：電波
rd2 = radio((TX[1] - 0.2, 1.8), 1.0, K.DARK, z=0.15)
s1.pop(rd2, c.A(0, 0.8))
for k in range(7):
    wave((TX[1] + 0.35, 2.3), c.A(0, 1.2) + k * 20, r=0.75, bg=P.CUST, dur=22, z=0.12)
# 電話のアイコン：双方向の矢印
pa1 = K.dot((TX[2] - 1.05, 1.95), 0.3, K.ORANGE, None, z=0.15)
pb1 = K.dot((TX[2] + 1.05, 1.95), 0.3, K.BLUE, None, z=0.15)
ar1 = K.arrow((TX[2] - 0.6, 2.1), (TX[2] + 0.6, 2.1), K.WHITE, 0.07, 0.22, z=0.15)
ar2 = K.arrow((TX[2] + 0.6, 1.8), (TX[2] - 0.6, 1.8), K.WHITE, 0.07, 0.22, z=0.15)
s1.pop(pa1, c.A(0, 0.9))
s1.pop(pb1, c.A(0, 1.0))
K.grow_x(ar1, c.A(0, 1.3), 8)
K.grow_x(ar2, c.A(0, 1.4), 8)
s1.add(ar1, ar2)
# 行5：順に光り、ラジオが残る
K.pulse(tiles[0], c.A(1, 0.3), 1.06, 8)
K.pulse(tiles[2], c.A(1, 3.0), 1.06, 8)
P.shade(tiles[1], [(c.A(1, 1.7), K.BLUE)], P.CUST)
K.pulse(tiles[1], c.A(1, 1.7), 1.08, 8)
# 行6：サーバーから一方向に、つなぎっぱなしで届ける
f6 = c.A(2, 0.0)
s1.out(f6 - 3)
s2 = c.sub()
svr = K.card("サーバー", (-5.1, 1.2), 2.6, 1.8, K.BLUE, size=0.46)
phn = K.card("スマホ", (5.4, 1.2), 1.7, 2.4, K.ORANGE, size=0.4)
s2.pop(svr, f6 + 4)
s2.pop(phn, f6 + 8)
arw = K.arrow((-3.5, 1.2), (4.3, 1.2), K.GRAY, 0.1, 0.34)
K.grow_x(arw, f6 + 14, 14)
s2.add(arw)
oneway = P.chip("一方向", (0, 2.2), K.GRAY, 0.4)
keep = P.chip("つなぎっぱなし", (0, 0.2), K.GRAY, 0.4)
s2.pop(oneway, f6 + 22)
s2.pop(keep, f6 + 30)
for k in range(9):
    f = c.A(2, 0.5) + k * 9
    dt = K.dot((-3.4, 1.2), 0.24, K.BLUE, None, z=0.4)
    s2.pop(dt, f, 6)
    K.move(dt, f + 3, (4.2, 1.2), 34, "LINEAR", "AUTO")
    P.out(dt, f + 38, 4)
# 行7：ハートビートとタイムアウト
f7 = c.A(3, 0.0)
P.out(oneway, f7 + 2, 6)
P.out(keep, f7 + 2, 6)
hb = heart((-1.8, 2.5), 1.3)
hg = hourglass((1.8, 2.5), 1.2)
s2.pop(hb, f7 + 8)
for k in range(3):
    K.pulse(hb, f7 + 22 + k * 14, 1.25, 10)
hl = P.chip("ハートビート", (-1.8, 0.35), K.GRAY, 0.36)
s2.pop(hl, f7 + 14)
s2.pop(hg, c.A(3, 1.1))
K.pulse(hg, c.A(3, 1.1) + 14, 1.15, 8)
tl = P.chip("タイムアウト", (1.8, 0.35), K.GRAY, 0.36)
s2.pop(tl, c.A(3, 1.25))
# 行8：遅いクライアントが全員を止める
f8 = c.A(4, 0.0)
s2.out(f8 - 3)
s3 = c.sub()
svr3 = K.card("サーバー", (-5.3, 1.3), 2.4, 1.6, K.BLUE, size=0.42)
s3.pop(svr3, f8 + 4)
RY = (2.4, 1.45, 0.5)
LBL = ("A", "B", "遅")
rows = []
for y, lab in zip(RY, LBL):
    col = P.WAITBG if lab == "遅" else K.ORANGE
    ph_ = K.card(lab, (-1.3, y), 1.0, 0.75, col, size=0.4)
    tr_, fl_ = K.progress_bar((3.4, y), 4.0, 0.3, K.GREEN, K.DARK)
    ln_ = K.arrow((-3.9, 1.3), (-1.95, y), K.GRAY, 0.05, 0.2, z=-0.01)
    s3.pop(ph_, f8 + 8 + len(rows) * 4)
    s3.pop(tr_, f8 + 10 + len(rows) * 4)
    K.grow_x(ln_, f8 + 12 + len(rows) * 3, 8)
    s3.add(ln_)
    rows.append((ph_, fl_, y))
K.bar_to(rows[0][1], c.A(4, 0.55), 0.55, 18)
K.bar_to(rows[1][1], c.A(4, 0.65), 0.55, 18)
K.bar_to(rows[2][1], c.A(4, 0.75), 0.1, 18)
slow = P.chip("遅いクライアント", (-1.3, -0.35), K.YELLOW, 0.34, INK)
s3.pop(slow, c.A(4, 1.0))
for i in range(3):
    pk = K.card("", (-2.9, 0.2 + i * 0.3), 0.42, 0.3, K.WHITE, z=0.2)
    s3.pop(pk, c.A(4, 1.0) + i * 6, 6)
    P.out(pk, c.A(4, 2.0) + i * 2, 6)
stuck = []
for k in range(2):
    m = P.nomark((5.8, RY[k] + 0.0), 0.26)
    s3.pop(m, c.A(4, 1.4) + k * 6, 8)
    stuck.append(m)
lane = P.framed((-1.3, -0.55), 3.4, 0.95, K.BG, K.GRAY, 0.05, 0.2, z=-0.01)
s3.pop(lane, c.A(4, 1.75))
lbl = P.chip("専用レーン", (1.6, -0.55), K.GRAY, 0.3)
s3.pop(lbl, c.A(4, 1.95))
P.out(slow, c.A(4, 1.85), 6)
K.move(rows[2][0], c.A(4, 1.9), (-1.3, -0.55), 16, "SINE", "EASE_IN_OUT")
for k, m in enumerate(stuck):
    P.out(m, c.A(4, 2.3) + k * 3, 6)
    t = P.tick((5.8, RY[k]), 0.26)
    s3.pop(t, c.A(4, 2.4) + k * 4, 8)
K.bar_to(rows[0][1], c.A(4, 2.3), 1.0, 20)
K.bar_to(rows[1][1], c.A(4, 2.35), 1.0, 20)

# ---------------------------------------------------------------- 第37章 取りこぼしを防ぐ
c = I.chapter(37)
s1 = c.sub()
LGX, LGY = -3.3, 1.2
led = P.framed((LGX, LGY), 3.2, 3.9, K.BG2, K.PURPLE, 0.07, 0.3)
s1.pop(led, c.A(0, 0.1))
lt = P.chip("台帳", (LGX, 2.85), K.PURPLE, 0.4, z=0.1)
s1.pop(lt, c.A(0, 0.3))
EY = (2.05, 1.5, 0.95, 0.4, -0.15)
ents = []
for i, (n, y) in enumerate(zip((41, 42, 43, 44, 45), EY)):
    en = K.card(f"#{n}", (LGX, y), 2.4, 0.46, P.CUST, size=0.3, z=0.1)
    ents.append(en)
s1.pop(ents[0], c.A(0, 0.7))
s1.pop(ents[1], c.A(0, 1.3))
phn = K.card("スマホ", (4.4, 1.3), 1.9, 2.6, K.ORANGE, size=0.4)
s1.pop(phn, c.A(0, 0.5))
conn = K.arrow((-1.5, 1.3), (3.2, 1.3), K.BLUE, 0.1, 0.34)
K.grow_x(conn, c.A(0, 1.0), 14)
s1.add(conn)
for i, dly in enumerate((1.5, 2.0)):
    d_ = P.chip(f"#{41 + i}", (-1.5, 1.3), K.BLUE, 0.3, z=0.5)
    s1.pop(d_, c.A(0, dly), 6)
    K.move(d_, c.A(0, dly) + 3, (3.1, 1.3), 24, "SINE", "EASE_IN_OUT")
    P.out(d_, c.A(0, dly) + 28, 5)
# 行10：切れている間も、台帳に積まれる
P.out(conn, c.A(1, 0.0), 6)
dash = K.dashed_line((-1.5, 1.3), (3.2, 1.3), K.GRAY, 0.08)
s1.pop(dash, c.A(1, 0.05))
cut = P.chip("切断中", (4.4, 3.05), K.YELLOW, 0.38, INK)
s1.pop(cut, c.A(1, 0.15))
cx_ = P.nomark((1.0, 1.3), 0.3)
s1.pop(cx_, c.A(1, 0.2), 8)
for i in (2, 3, 4):
    s1.pop(ents[i], c.A(1, 0.3) + (i - 2) * 22)
    K.pulse(led, c.A(1, 0.3) + (i - 2) * 22 + 12, 1.03, 8)
# 行11：再接続 → 最後は #42 → #43 以降を送り直す
f11 = c.A(2, 0.0)
P.out(dash, f11 + 2, 6)
P.out(cx_, f11 + 2, 6)
P.out(cut, f11 + 2, 6)
conn2 = K.arrow((-1.5, 1.3), (3.2, 1.3), K.BLUE, 0.1, 0.34)
K.grow_x(conn2, f11 + 6, 12)
s1.add(conn2)
last = P.chip("最後は #42", (4.4, 3.05), K.WHITE, 0.4, INK, z=0.6)
s1.pop(last, f11 + 10)
K.move(last, c.A(2, 0.55), (-0.2, 3.0), 28, "SINE", "EASE_IN_OUT")
P.shade(ents[0], [(c.A(2, 1.4), K.GRAY)], P.CUST)
P.shade(ents[1], [(c.A(2, 1.4), K.GRAY)], P.CUST)
P.out(last, c.A(2, 1.7), 6)
for i in (2, 3, 4):
    f = c.A(2, 1.4) + (i - 2) * 12
    P.shade(ents[i], [(f, K.BLUE)], P.CUST)
    sd = P.chip(f"#{41 + i}", (-1.5, EY[i]), K.BLUE, 0.3, z=0.6)
    s1.pop(sd, f + 2, 6)
    K.move(sd, f + 4, (3.1, 1.3), 26, "SINE", "EASE_IN_OUT")
    P.out(sd, f + 32, 5)
okp = P.tick((5.0, 2.65), 0.32, z=0.5)
s1.pop(okp, c.A(2, 3.0), 8)
K.pulse(phn, c.A(2, 3.0), 1.06, 8)
# 行12：連番は、コミット順に付ける
f12 = c.A(3, 0.0)
s1.out(f12 - 3)
s2 = c.sub()
LY = (2.1, 0.65)
la_ = K.card("", (0, LY[0]), 12.4, 1.15, K.DARK)
lb_ = K.card("", (0, LY[1]), 12.4, 1.15, K.DARK)
s2.pop(la_, f12 + 2)
s2.pop(lb_, f12 + 6)
fl = P.seg((4.2, 0.0), (4.2, 2.8), 0.1, K.WHITE, z=0.1)
s2.pop(fl, f12 + 8)
cm = P.chip("コミット", (4.2, 3.15), K.WHITE, 0.34, INK)
s2.pop(cm, f12 + 10)
rA = K.card("", (-5.4, LY[0]), 1.0, 0.8, K.ORANGE, z=0.2)
rB = K.card("", (-5.4, LY[1]), 1.0, 0.8, K.BLUE, z=0.2)
s2.pop(rA, f12 + 3)
s2.pop(rB, f12 + 5)
f_swap = c.A(3, 1.45)
K.number_ticker([(f12 + 3, "5", K.WHITE), (f_swap, "6", K.WHITE)], (0, 0, 0.05), 0.5, parent=rA, end=None)
K.number_ticker([(f12 + 5, "6", K.WHITE), (f_swap, "5", K.WHITE)], (0, 0, 0.05), 0.5, parent=rB, end=None)
K.move(rB, c.A(3, 0.35), (3.4, LY[1]), 16, "SINE", "EASE_IN_OUT")
K.move(rA, c.A(3, 0.75), (3.4, LY[0]), 18, "SINE", "EASE_IN_OUT")
rev = P.chip("番号が逆", (-1.2, 1.37), P.NGBG, 0.34, z=0.3)
s2.pop(rev, c.A(3, 1.1))
P.out(rev, f_swap - 2, 6)
okc = P.chip("連番はコミット順", (-1.2, 1.37), P.OKBG, 0.36, z=0.3)
s2.pop(okc, f_swap)
K.pulse(rA, f_swap + 2, 1.15, 8)
K.pulse(rB, f_swap + 2, 1.15, 8)

# ---------------------------------------------------------------- 第38章 2台構成
c = I.chapter(38)
u1 = K.person((-6.2, 2.0), K.ORANGE, 0.8, z=0.3)
u2 = K.person((-6.2, 0.3), K.ORANGE, 0.8, z=0.3)
ng = K.card("nginx", (-3.7, 1.15), 1.9, 1.2, K.GRAY, size=0.4)
sA = K.card("サーバーA", (0.3, 2.0), 2.7, 1.2, K.BLUE, size=0.4)
sB = K.card("サーバーB", (0.3, 0.3), 2.7, 1.2, K.BLUE, size=0.4)
for i, o in enumerate((u1, u2, ng, sA, sB)):
    c.pop(o, c.A(0, 0.1) + i * 6)
LINKS = (((-5.5, 1.9), (-4.75, 1.4)), ((-5.5, 0.4), (-4.75, 0.9)),
         ((-2.7, 1.4), (-1.1, 1.9)), ((-2.7, 0.9), (-1.1, 0.4)))
for i, (p1, p2) in enumerate(LINKS):
    ar = K.arrow(p1, p2, K.GRAY, 0.06, 0.2)
    K.grow_x(ar, c.A(0, 0.9) + i * 5, 8)
    c.add(ar)
# 行14：A で起きたイベントは、B につないだ人に届かない
f14 = c.A(1, 0.0)
ev = P.chip("票", (-5.3, 2.35), K.BLUE, 0.34, z=0.6)
c.pop(ev, f14 + 8, 6)
K.move(ev, f14 + 14, (-3.7, 2.3), 22, "SINE", "EASE_IN_OUT")
K.move(ev, f14 + 38, (0.3, 2.0), 22, "SINE", "EASE_IN_OUT")
K.pulse(sA, f14 + 60, 1.08, 6)
P.out(ev, f14 + 62, 5)
ev3 = P.chip("票", (0.3, 2.0), K.BLUE, 0.34, z=0.6)
c.pop(ev3, f14 + 66, 6)
K.move(ev3, f14 + 70, (0.3, 1.15), 18, "SINE", "EASE_IN_OUT")
nx = P.nomark((0.3, 1.15), 0.3)
c.pop(nx, f14 + 88, 8)
P.out(ev3, f14 + 90, 5)
tku = P.tick((-5.15, 2.55), 0.28, z=0.7)
c.pop(tku, f14 + 64, 8)
miss = P.chip("届かない", (-5.2, -0.8), K.YELLOW, 0.36, INK)
c.pop(miss, f14 + 94)
K.pulse(miss, f14 + 106, 1.12, 8)
# 行15：DB の鈴でサーバー同士をつなぐ
f15 = c.A(2, 0.0)
P.out(nx, f15, 6)
P.out(miss, f15, 6)
P.out(tku, f15, 6)
dbn = K.card("PostgreSQL", (5.4, 1.15), 2.5, 1.7, K.PURPLE, size=0.36, text_color=INK)
c.pop(dbn, f15 + 6)
bl = bell((5.4, 2.55), 1.0)
c.pop(bl, f15 + 14)
aN = K.arrow((1.8, 1.9), (4.0, 1.5), K.GRAY, 0.07, 0.24)
aL = K.arrow((4.0, 0.8), (1.8, 0.4), K.GRAY, 0.07, 0.24)
K.grow_x(aN, f15 + 20, 10)
K.grow_x(aL, f15 + 24, 10)
c.add(aN, aL)
cn = P.chip("NOTIFY", (3.0, 2.55), K.YELLOW, 0.34, INK)
cl = P.chip("LISTEN", (3.0, -0.1), K.YELLOW, 0.34, INK)
c.pop(cn, f15 + 28)
c.pop(cl, f15 + 34)
e1 = P.chip("票", (0.3, 2.0), K.BLUE, 0.34, z=0.6)
c.pop(e1, c.A(2, 1.2), 6)
K.move(e1, c.A(2, 1.2) + 4, (4.9, 1.2), 26, "SINE", "EASE_IN_OUT")
P.out(e1, c.A(2, 1.2) + 32, 5)
K.shake(bl, c.A(2, 1.2) + 30, 0.12, 6, 2)
K.pulse(dbn, c.A(2, 1.2) + 30, 1.06, 8)
e2_ = P.chip("票", (4.9, 1.2), K.BLUE, 0.34, z=0.6)
c.pop(e2_, c.A(2, 2.35), 6)
K.move(e2_, c.A(2, 2.35) + 4, (0.9, 0.4), 26, "SINE", "EASE_IN_OUT")
P.out(e2_, c.A(2, 2.35) + 32, 5)
e3_ = P.chip("票", (0.3, 0.3), K.BLUE, 0.34, z=0.6)
c.pop(e3_, c.A(2, 3.35), 6)
K.move(e3_, c.A(2, 3.35) + 4, (-5.3, 0.4), 28, "SINE", "EASE_IN_OUT")
P.out(e3_, c.A(2, 3.35) + 34, 5)
tk38 = P.tick((-5.2, 1.0), 0.32, z=0.7)
c.pop(tk38, c.A(2, 3.35) + 34, 8)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(16, 0.1))
d.row("before", "再読み込みが必要", "ng", A(16, 0.4))
d.row("after", "票がすぐ伸びる", "ok", A(16, 1.3))
d.row("before", "切れると取りこぼす", "ng", A(17, 0.0))
d.row("after", "切れても続きから", "ok", A(17, 0.6))
d.row("before", "2台だと届かない", "ng", A(18, 0.0))
d.row("after", "2台でも届く", "ok", A(18, 0.5))
t2 = P.tick((d.RX + 2.4, d.ROWS_Y[2]), 0.26, z=0.5)
d.g.pop(t2, A(18, 1.0), 8)

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["切れても、台が増えても、", "続きから届ける。"])
if os.environ.get("V57_CHECK"):          # 退場し忘れの確認（描画前に、最後のキーが 0 でない物を表示）
    for o in bpy.data.objects:
        if o.parent is None and o.animation_data and o.animation_data.action:
            for fc in K._fcurves(o):
                if fc.data_path == "scale" and fc.array_index == 0 and fc.keyframe_points:
                    kp = fc.keyframe_points[-1]
                    if kp.co.y > 0.01:
                        print("LEFT", o.name, int(kp.co.x), round(kp.co.y, 2), tuple(round(v, 1) for v in o.location))
I.render()
