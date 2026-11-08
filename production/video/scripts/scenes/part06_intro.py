"""第6部 匿名利用者と認可：導入動画（part06-intro）

場面構成（行番号は part06-intro.json の lines）
  0-1    A タイトル → 扉に鍵が掛かる（GREEN）
  2-4    B 困りごと : ログインのない扉3つ → 名札（書き換えられるヘッダー）→ 他人の扉に入る → 8つの枠を総当たり
  5-19   C 第30〜35章（6枚のカード）
           第30章 入館証と入室権 → 権限表 → 他人の投票は404
           第31章 5つの門が全部開いている → 閉じて、許可リストの門だけ開く
           第32章 ブラウザ → サーバー → ハッシュ → DB（元のトークンは置かない）
           第33章 8つの枠を総当たり → 回数制限の壁 → 招待リンクは一度きり
           第34章 Cookie は自動で付く → CSRFトークンの盾
           第35章 バケツのトークン → 429 → サイズ上限 → 穴を見つけて再現→修正
  20-21  D 前→後
  22     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
import part_intro_kit as P

K = P.K
I = P.Intro(6, "匿名利用者と認可", [
    (30, "識別と/認可"), (31, "Securityの/地図"), (32, "匿名/セッション"),
    (33, "参加コード/と招待"), (34, "ブラウザ/との境界"), (35, "レート/制限")])
A, E, M = I.A, I.E, I.M
INK = P.INK
PBG = K.hexc("#7c52d6")


# ======================================================================
# この動画だけで使う部品
# ======================================================================
def dark(c, k=0.62):
    return (c[0] * k, c[1] * k, c[2] * k, c[3])


def empty(loc, z=0.0):
    o = bpy.data.objects.new("grp", None)
    bpy.context.scene.collection.objects.link(o)
    o.location = (loc[0], loc[1], z)
    return o


def envelope(loc, color=None, s=1.0, tag=None, z=0.4):
    color = color or K.ORANGE
    root = K.card("", (loc[0], loc[1]), 0.95 * s, 0.64 * s, color, z=z)
    flap = P.poly((0, 0.17 * s), 0.56 * s, 3, dark(color), math.pi, 0.06)
    flap.scale = (1, 0.55, 1)
    flap.parent = root
    if tag:
        t = P.chip(tag, (0, 0.62 * s), K.WHITE, 0.3, INK, z=0.1)
        t.parent = root
    return root


def door(loc, w=1.9, h=2.8, z=0.0):
    """扉。(root, leaf) を返す。leaf の scale.x を 1→0.2 にすると開く。"""
    root = P.framed(loc, w, h, K.BG, K.GRAY, 0.07, 0.22, z=z)
    leaf = empty((-w / 2 + 0.08, 0), 0.1)
    leaf.parent = root
    body = K.card("", (w / 2 - 0.08, 0), w - 0.16, h - 0.16, P.CUST, z=0.0)
    body.parent = leaf
    knob = K.dot((w - 0.5, -0.1), 0.11, K.WHITE, None, z=0.15)
    knob.parent = leaf
    return root, leaf


def door_open(leaf, f, dur=12):
    K._key(leaf, "scale", f, (1, 1, 1), "SINE", "EASE_IN_OUT")
    K._key(leaf, "scale", f + dur, (0.2, 1, 1), "SINE", "EASE_IN_OUT")


def lock(loc, s=1.0, hole=None, z=0.4, opened=True):
    """南京錠。(root, shackle)。shackle を (0, 0.3*s) へ動かすと閉まる。"""
    hole = hole or K.BG
    root = empty(loc, z)
    sh = empty((0, (0.58 if opened else 0.3) * s), 0.0)
    sh.parent = root
    K.dot((0, 0), 0.27 * s, K.YELLOW, None, z=0.0).parent = sh
    K.dot((0, 0), 0.15 * s, hole, None, z=0.01).parent = sh
    body = K.rounded_rect("lockbody", 0.8 * s, 0.6 * s, (0, -0.1 * s, 0.05), K.YELLOW, 0.12 * s)
    body.parent = root
    kh = K.dot((0, -0.08 * s), 0.09 * s, K.BG, None, z=0.1)
    kh.parent = root
    return root, sh


def magnifier(loc, s=1.0, z=0.5):
    root = empty(loc, z)
    K.dot((0, 0), 0.5 * s, K.WHITE, None, z=0.0).parent = root
    K.dot((0, 0), 0.38 * s, K.BG2, None, z=0.02).parent = root
    h = K.plane("handle", 0.16 * s, 0.7 * s, (0.5 * s, -0.5 * s, 0), K.WHITE)
    h.rotation_euler = (0, 0, math.pi / 4)
    h.parent = root
    return root


def pops(g, objs, f, step=3):
    for i, o in enumerate(objs):
        g.pop(o, f + i * step)
    return objs


# ======================================================================
# A タイトル → 扉に鍵が掛かる
# ======================================================================
I.title_card()
ga = P.Group(I, A(2) - 3)
dr, dleaf = door((0.8, 1.0), 2.6, 3.4)
ga.pop(dr, A(1, 0.15))
visitor = K.person((-5.4, 0.9), K.ORANGE, 1.0, z=0.5)
ga.pop(visitor, A(1, 0.3))
lk, shk = lock((0.8, 0.6), 1.5, hole=P.CUST, z=0.6)
ga.pop(lk, A(1, 0.9))
K.move(shk, A(1, 1.5), (0, 0.3 * 1.5), 8, "SINE", "EASE_IN_OUT")
K.pulse(lk, A(1, 1.5) + 10, 1.12, 8)
K.move(visitor, A(1, 1.7), (-2.6, 0.9), 20, "SINE", "EASE_IN_OUT")
K.shake(dr, A(1, 2.3), 0.12, 6, 2)
tka = P.tick((2.0, 2.3), 0.4, z=0.7)
ga.pop(tka, A(1, 2.6), 8)

# ======================================================================
# B 困りごと
# ======================================================================
b1 = P.Group(I, A(4, 1.9))            # 行2-4前半：扉と訪問者
b2 = I.trouble()                      # 行4後半：総当たり
me = K.person((-5.6, 1.4), K.ORANGE, 1.0, z=0.5)
b1.pop(me, A(2, 0.1))
DX = (-0.3, 2.5, 5.3)
doors = []
for x, lab in zip(DX, ("投票A", "投票B", "投票C")):
    d_root, d_leaf = door((x, 1.2), 1.9, 2.8)
    pl = P.chip(lab, (x, 3.05), K.GRAY, 0.34)
    doors.append((d_root, d_leaf))
    b1.pop(d_root, A(2, 0.3) + len(doors) * 5)
    b1.pop(pl, A(2, 0.4) + len(doors) * 5)
nologin = P.chip("ログインなし", (-3.1, 2.0), K.GRAY, 0.36)
b1.pop(nologin, A(2, 1.3))
nlx = P.nomark((-3.1 - nologin["w"] / 2 - 0.05, 2.0), 0.28)
b1.pop(nlx, A(2, 1.5), 8)

# 行3：名札（書き換えられるヘッダー）
f_t = A(3, 0.3)
tagA = K.card("", (-5.6, 0.2), 1.9, 0.6, K.WHITE, z=0.6)
K.number_ticker([(f_t, "訪問者A", INK), (A(4, 0.55), "訪問者B", INK)], (0, 0, 0.05), 0.34, parent=tagA, end=None)
yl = P.chip("書き換えられるヘッダー", (-3.7, -0.75), K.YELLOW, 0.36, INK)
K.show_between(tagA, f_t, None)
b1.add(tagA)
b1.pop(yl, A(3, 1.0))
K.pulse(yl, A(3, 1.0) + 12, 1.12, 8)

# 行4前半：名札を書き換えて、他人の扉の中へ
K.shake(tagA, A(4, 0.5), 0.12, 6, 2)
K.move(me, A(4, 0.8), (2.5, 1.4), 26, "SINE", "EASE_IN_OUT")
door_open(doors[1][1], A(4, 1.15), 12)
P.out(me, A(4, 1.35), 8)
see = P.chip("他人の投票が見える", (3.2, -0.75), P.NGBG, 0.36)
b1.pop(see, A(4, 1.45))
K.pulse(see, A(4, 1.45) + 12, 1.12, 8)
# 行4後半：参加コードの総当たり（8つの枠）
f4 = A(4, 2.0)
b1.out(f4)
pad, pad_sh = lock((-4.6, 1.4), 1.9, hole=K.BG, z=0.3, opened=False)
b2.pop(pad, f4 + 3)
SLOTS = []
for k in range(8):
    sl = K.card("", (-1.9 + 0.88 * k, 1.5), 0.74, 1.0, P.CUST, z=0.0)
    b2.pop(sl, f4 + 4 + k * 2, 6)
    SLOTS.append(sl)
cc = P.chip("参加コード", (0.65, 2.75), K.GRAY, 0.38)
b2.pop(cc, f4 + 4)
for k in range(8):
    fk = f4 + 14 + k * 4
    ar = K.arrow((-1.9 + 0.88 * k, 2.85), (-1.9 + 0.88 * k, 2.1), K.ORANGE, 0.1, 0.3, z=0.2)
    b2.pop(ar, fk, 6)
    P.shade(SLOTS[k], [(fk + 6, P.NGBG)], P.CUST)
    K.shake(SLOTS[k], fk + 7, 0.05, 2, 2)
    P.out(ar, fk + 12, 6)
brute = P.chip("総当たり", (0.65, 0.35), P.NGBG, 0.4)
b2.pop(brute, f4 + 50)
K.pulse(brute, f4 + 62, 1.12, 8)

I.cam(A(3, 0.3), to=(-0.6, 0.4), zoom=15.0, dur=24)
I.cam(A(5) - 10, to=(0, 0), zoom=16, dur=16)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---------------------------------------------------------------- 第30章 識別と認可
c = I.chapter(30)
s1 = c.sub()
idc = K.card("", (-2.4, 2.65), 3.6, 1.35, P.CUST)
ath = K.card("", (2.4, 2.65), 3.6, 1.35, P.CUST)
for cd, t1, t2 in ((idc, "入館証", "識別"), (ath, "入室権", "認可")):
    K.text(t1, 0.55, (0, 0.2, 0.05), K.WHITE, 900, parent=cd)
    K.text(t2, 0.4, (0, -0.37, 0.05), K.GRAY, 700, parent=cd)
s1.pop(idc, c.A(0, 0.1))
s1.pop(ath, c.A(0, 0.6))
K.pulse(idc, c.A(0, 1.4), 1.08, 8)
K.pulse(ath, c.A(0, 2.0), 1.08, 8)
COLX = (-1.0, 0.95, 2.9)
hdrs = []
for x, lab, col in zip(COLX, ("非参加者", "MEMBER", "HOST"), (K.GRAY, K.BLUE, K.ORANGE)):
    h = P.chip(lab, (x, 1.4), col, 0.3, w=1.85, h=0.5)
    s1.pop(h, c.A(1, 0.1) + len(hdrs) * 4)
    hdrs.append(h)
MAT = (("見る", (0, 1, 1)), ("投票", (0, 1, 1)), ("締切", (0, 0, 1)))
cells = []
for r, (lab, row) in enumerate(MAT):
    y = 0.8 - r * 0.55
    rl = P.chip(lab, (-2.95, y), P.CUST, 0.3, w=1.3, h=0.5)
    s1.pop(rl, c.A(1, 0.4) + r * 4)
    for col, v in enumerate(row):
        f = c.A(1, 0.7) + (r * 3 + col) * 7
        m = P.tick((COLX[col], y), 0.22) if v else P.nomark((COLX[col], y), 0.22)
        s1.pop(m, f, 8)
# 行7：他人の投票は、存在しない投票と同じ404
f7 = c.A(2, 0.0)
s1.out(f7 - 3)
s2 = c.sub()
urlb = K.card("", (-4.7, 1.5), 2.8, 0.9, K.WHITE, z=0.0)
s2.pop(urlb, f7 + 3)
K.number_ticker([(f7 + 6, "投票 1", INK), (c.A(2, 0.7), "投票 2", INK)], (0, 0, 0.05), 0.48, parent=urlb, end=None)
K.shake(urlb, c.A(2, 0.7), 0.1, 4, 2)
ar7 = K.arrow((-3.2, 1.5), (-1.9, 1.5), K.WHITE, 0.09, 0.28)
K.grow_x(ar7, f7 + 10, 8)
s2.add(ar7)
dx_root, dx_leaf = door((-0.7, 1.4), 1.9, 2.5)
dy_root = P.framed((3.9, 1.4), 1.9, 2.5, K.BG, K.GRAY, 0.07, 0.22)
s2.pop(dx_root, f7 + 8)
s2.pop(dy_root, f7 + 14)
p1 = P.chip("他人の投票", (-0.7, 3.0), K.GRAY, 0.34)
p2 = P.chip("存在しない投票", (3.9, 3.0), K.GRAY, 0.34)
s2.pop(p1, f7 + 12)
s2.pop(p2, f7 + 18)
st1 = K.card("404", (-0.7, 1.4), 1.5, 0.85, P.NGBG, size=0.55, z=0.6)
st1.rotation_euler = (0, 0, 0.15)
st2 = K.card("404", (3.9, 1.4), 1.5, 0.85, P.NGBG, size=0.55, z=0.6)
st2.rotation_euler = (0, 0, -0.15)
s2.pop(st1, c.A(2, 1.3), 8)
s2.pop(st2, c.A(2, 2.3), 8)
K.shake(st1, c.A(2, 1.5), 0.1, 4, 2)
K.shake(st2, c.A(2, 2.5), 0.1, 4, 2)
eq = P.label("＝", (1.6, 1.4), 1.0, K.WHITE, 900)
s2.pop(eq, c.A(2, 3.0))
K.pulse(eq, c.A(2, 3.0) + 12, 1.2, 8)

# ---------------------------------------------------------------- 第31章 Securityの地図
c = I.chapter(31)
GX = (-5.6, -2.8, 0.0, 2.8, 5.6)
gates = []
for k, x in enumerate(GX):
    fr = P.framed((x, 1.2), 1.95, 2.3, K.BG2, K.GRAY, 0.06, 0.2)
    lL = K.card("", (x - 0.62, 1.2), 0.56, 1.85, P.CUST, z=0.1)
    lR = K.card("", (x + 0.62, 1.2), 0.56, 1.85, P.CUST, z=0.1)
    c.pop(fr, c.A(0, 0.1) + k * 4)
    c.pop(lL, c.A(0, 0.2) + k * 4)
    c.pop(lR, c.A(0, 0.2) + k * 4)
    gates.append((fr, lL, lR, x))
ev = envelope((-7.4, 1.2), K.ORANGE, 1.0, z=0.7)
c.pop(ev, c.A(0, 0.9), 6)
K.move(ev, c.A(0, 1.0), (7.4, 1.2), 44, "LINEAR", "AUTO")
P.out(ev, c.A(0, 1.0) + 44, 4)
allopen = P.chip("何も守られていない", (0, 3.05), P.NGBG, 0.42)
c.pop(allopen, c.A(0, 1.6))
K.pulse(allopen, c.A(0, 1.6) + 12, 1.1, 8)
# 行9：全部閉じて、許可リストの門だけ開く
P.out(allopen, c.A(1, 0.1), 6)
for k, (fr, lL, lR, x) in enumerate(gates):
    f = c.A(1, 0.2) + k * 5
    K.move(lL, f, (x - 0.28, 1.2), 10, "SINE", "EASE_IN_OUT")
    K.move(lR, f, (x + 0.28, 1.2), 10, "SINE", "EASE_IN_OUT")
    P.shade(lL, [(f + 2, P.OKBG)], P.CUST)
    P.shade(lR, [(f + 2, P.OKBG)], P.CUST)
allow = P.chip("許可リスト", (0, 3.05), P.OKBG, 0.42)
c.pop(allow, c.A(1, 1.1))
K.pulse(allow, c.A(1, 1.1) + 12, 1.1, 8)
_, lL2, lR2, x2 = gates[2]
f_re = c.A(1, 1.5)
K.move(lL2, f_re, (x2 - 0.62, 1.2), 10, "SINE", "EASE_IN_OUT")
K.move(lR2, f_re, (x2 + 0.62, 1.2), 10, "SINE", "EASE_IN_OUT")
tk31 = P.tick((x2, -0.3), 0.32, z=0.5)
c.pop(tk31, f_re + 8, 8)
K.pulse(tk31, f_re + 20, 1.2, 8)

# ---------------------------------------------------------------- 第32章 匿名セッション
c = I.chapter(32)
NX = (-5.6, -1.9, 1.8, 5.5)
brw = K.card("ブラウザ", (NX[0], 1.3), 2.3, 1.5, K.ORANGE, size=0.42)
srv = K.card("サーバー", (NX[1], 1.3), 2.3, 1.5, K.BLUE, size=0.42)
hsh = K.card("ハッシュ", (NX[2], 1.3), 2.3, 1.5, P.CUST, size=0.42)
dbn = K.card("DB", (NX[3], 1.3), 2.3, 1.5, K.PURPLE, size=0.5, text_color=INK)
for i, o in enumerate((brw, srv, hsh, dbn)):
    c.pop(o, c.A(0, 0.1) + i * 7)
for i in range(3):
    ar = K.arrow((NX[i] + 1.25, 1.3), (NX[i + 1] - 1.25, 1.3), K.GRAY, 0.07, 0.24)
    K.grow_x(ar, c.A(0, 0.7) + i * 7, 8)
    c.add(ar)
# 行11：乱数のトークンをCookieで渡し、DBにはハッシュだけ置く
tok = K.card("トークン", (NX[0], 2.95), 1.7, 0.62, K.WHITE, text_color=INK, size=0.32, z=0.6)
c.pop(tok, c.A(1, 0.1), 8)
ck = P.chip("Cookie", (NX[0], 0.15), K.ORANGE, 0.34)
c.pop(ck, c.A(1, 0.5))
K.move(tok, c.A(1, 0.7), (NX[1], 2.95), 22, "SINE", "EASE_IN_OUT")
K.pulse(srv, c.A(1, 0.7) + 24, 1.08, 6)
K.move(tok, c.A(1, 1.45), (NX[2], 2.95), 22, "SINE", "EASE_IN_OUT")
K.pulse(hsh, c.A(1, 1.45) + 24, 1.08, 6)
P.out(tok, c.A(1, 1.45) + 26, 6)
hv = P.chip("ハッシュ", (NX[2], 2.95), PBG, 0.34, z=0.6)
c.pop(hv, c.A(1, 2.2), 8)
K.move(hv, c.A(1, 2.45), (NX[3], 2.95), 22, "SINE", "EASE_IN_OUT")
P.out(hv, c.A(1, 2.45) + 24, 6)
K.pulse(dbn, c.A(1, 2.45) + 24, 1.08, 6)
tk32 = P.tick((NX[3] + 1.2, 2.0), 0.3, z=0.5)
c.pop(tk32, c.A(1, 3.0), 8)
no_tok = K.card("トークン", (NX[3], 3.0), 1.7, 0.62, P.CUST, text_color=K.GRAY, size=0.32, z=0.2)
c.pop(no_tok, c.A(1, 3.0))
nx32 = P.nomark((NX[3], 3.0), 0.3, K.RED)
c.pop(nx32, c.A(1, 3.2), 8)

# ---------------------------------------------------------------- 第33章 参加コードと招待
c = I.chapter(33)
s1 = c.sub()
SX0 = -2.6
slots = []
for k in range(8):
    sl = K.card("", (SX0 + 0.9 * k, 1.0), 0.76, 1.0, P.CUST, z=0.0)
    s1.pop(sl, c.A(0, 0.1) + k * 2, 6)
    slots.append(sl)
cc = P.chip("参加コード", (-5.0, 1.0), K.GRAY, 0.38)
s1.pop(cc, c.A(0, 0.5))
big = P.chip("31の8乗通り", (0.55, -0.4), K.WHITE, 0.52, INK)
s1.pop(big, c.A(0, 1.2))
K.pulse(big, c.A(0, 1.2) + 12, 1.1, 8)
# 行13：総当たりで破られる → 回数制限の壁で止まる
f13 = c.A(1, 0.1)
for k in range(8):
    fk = f13 + k * 4
    ar = K.arrow((SX0 + 0.9 * k, 2.45), (SX0 + 0.9 * k, 1.65), K.ORANGE, 0.1, 0.3, z=0.2)
    s1.pop(ar, fk, 6)
    P.shade(slots[k], [(fk + 6, P.NGBG), (c.A(1, 1.55), P.CUST)], P.CUST)
    P.out(ar, fk + 12, 6)
brk = P.chip("破られる", (-5.0, -0.4), P.NGBG, 0.38)
s1.pop(brk, f13 + 36)
P.out(brk, c.A(1, 1.55), 6)
wall = K.card("", (0.55, 1.95), 8.3, 0.28, K.YELLOW, z=0.3)
s1.pop(wall, c.A(1, 1.75))
wl = P.chip("回数制限", (6.0, 1.95), K.YELLOW, 0.36, INK)
s1.pop(wl, c.A(1, 1.85))
for k in range(8):
    fk = c.A(1, 2.1) + k * 3
    ar = K.arrow((SX0 + 0.9 * k, 3.15), (SX0 + 0.9 * k, 2.35), K.ORANGE, 0.1, 0.3, z=0.2)
    s1.pop(ar, fk, 6)
K.shake(wall, c.A(1, 2.5), 0.1, 6, 2)
okw = P.tick((6.6, 0.8), 0.3)
s1.pop(okw, c.A(1, 2.9), 8)
# 行14：招待リンクは、一度使ったら使えない
f14 = c.A(2, 0.0)
s1.out(f14 - 3)
s2 = c.sub()
link = K.card("招待リンク", (-0.4, 1.7), 3.8, 1.2, K.BLUE, size=0.5)
s2.pop(link, f14 + 3)
u1 = K.person((-5.4, 1.1), K.ORANGE, 0.9, z=0.5)
u2 = K.person((5.4, 1.1), K.ORANGE, 0.9, z=0.5)
s2.pop(u1, f14 + 6)
s2.pop(u2, f14 + 10)
K.move(u1, c.A(2, 0.3), (-2.9, 1.1), 20, "SINE", "EASE_IN_OUT")
P.shade(link, [(c.A(2, 0.9), P.CUST)], K.BLUE)
tk33 = P.tick((-2.9, 2.3), 0.3, z=0.7)
s2.pop(tk33, c.A(2, 0.9), 8)
used = P.chip("使用済み", (-0.4, 0.45), K.GRAY, 0.38)
s2.pop(used, c.A(2, 1.0))
K.move(u2, c.A(2, 1.5), (2.2, 1.1), 20, "SINE", "EASE_IN_OUT")
nx33 = P.nomark((2.2, 2.3), 0.3, K.RED)
s2.pop(nx33, c.A(2, 2.1), 8)
K.shake(link, c.A(2, 2.1), 0.1, 4, 2)

# ---------------------------------------------------------------- 第34章 ブラウザとの境界
c = I.chapter(34)
brw = K.card("ブラウザ", (-5.6, 1.3), 2.3, 1.6, K.ORANGE, size=0.42)
srv = K.card("サーバー", (5.5, 1.3), 2.3, 1.6, K.BLUE, size=0.42)
evl = K.card("悪意のあるサイト", (-0.2, 3.0), 4.1, 0.85, P.NGBG, size=0.38)
c.pop(brw, c.A(0, 0.1))
c.pop(srv, c.A(0, 0.3))
c.pop(evl, c.A(0, 0.6))
e1 = envelope((-4.0, 1.3), K.ORANGE, 1.0, tag="Cookie", z=0.7)
c.pop(e1, c.A(0, 0.5), 6)
K.move(e1, c.A(0, 0.6), (4.0, 1.3), 30, "SINE", "EASE_IN_OUT")
P.out(e1, c.A(0, 0.6) + 32, 5)
K.pulse(srv, c.A(0, 0.6) + 32, 1.08, 6)
e2 = envelope((-0.2, 2.45), K.ORANGE, 1.0, tag="Cookie", z=0.7)
c.pop(e2, c.A(0, 1.1), 6)
K.move(e2, c.A(0, 1.2), (4.0, 1.7), 28, "SINE", "EASE_IN_OUT")
P.out(e2, c.A(0, 1.2) + 30, 5)
bg34 = P.bang((6.5, 2.4), 0.3, K.RED, z=0.6)
c.pop(bg34, c.A(0, 1.2) + 30, 8)
# 行16：CSRFトークンの盾
P.out(bg34, c.A(1, 0.1), 6)
shield = P.poly((2.2, 1.3), 1.0, 5, P.OKBG, math.pi, 0.4)
c.pop(shield, c.A(1, 0.1))
sc = P.chip("CSRFトークン", (2.2, -0.15), P.OKBG, 0.36)
c.pop(sc, c.A(1, 0.4))
e3 = envelope((-4.0, 1.3), K.ORANGE, 1.0, tag="Cookie", z=0.7)
c.pop(e3, c.A(1, 0.9), 6)
K.move(e3, c.A(1, 1.0), (4.0, 1.3), 40, "SINE", "EASE_IN_OUT")
P.out(e3, c.A(1, 1.0) + 42, 5)
tk34 = P.tick((2.2, 1.3), 0.3, z=0.9)
c.pop(tk34, c.A(1, 1.0) + 18, 8)
P.out(tk34, c.A(1, 1.0) + 34, 6)
tk34b = P.tick((6.7, 2.2), 0.3, z=0.7)
c.pop(tk34b, c.A(1, 1.0) + 44, 8)
e4 = envelope((-0.2, 2.45), K.ORANGE, 1.0, tag="Cookie", z=0.7)
c.pop(e4, c.A(1, 2.0), 6)
K.move(e4, c.A(1, 2.1), (1.3, 1.85), 22, "SINE", "EASE_IN_OUT")
K.shake(shield, c.A(1, 2.1) + 22, 0.12, 6, 2)
K.move(e4, c.A(1, 2.1) + 26, (-0.2, 2.45), 16, "SINE", "EASE_IN_OUT")
nx34 = P.nomark((1.0, 1.35), 0.3, K.RED)
c.pop(nx34, c.A(1, 2.1) + 22, 8)
P.out(e4, c.A(1, 2.1) + 46, 6)

# ---------------------------------------------------------------- 第35章 レート制限
c = I.chapter(35)
s1 = c.sub()
BKX = -3.8
bucket = P.framed((BKX, 1.2), 2.8, 2.7, K.BG, K.BLUE, 0.08, 0.3)
s1.pop(bucket, c.A(0, 0.1))
bl = P.chip("トークンのバケツ", (BKX, 3.0), K.GRAY, 0.34)
s1.pop(bl, c.A(0, 0.4))
DROPS = ((BKX - 0.6, 0.55), (BKX + 0.6, 0.55), (BKX, 1.3))
drops = []
for i, (x, y) in enumerate(DROPS):
    dr_ = K.dot((x, 3.6), 0.28, K.BLUE, None, z=0.4)
    s1.pop(dr_, c.A(0, 0.5) + i * 14, 6)
    K.move(dr_, c.A(0, 0.5) + i * 14 + 4, (x, y), 18, "BOUNCE", "EASE_OUT")
    drops.append(dr_)
srv = K.card("サーバー", (5.0, 1.2), 2.4, 1.6, K.BLUE, size=0.42)
s1.pop(srv, c.A(0, 1.2))
# 行18：リクエストが来るたびに減る。空なら 429
for k in range(3):
    f = c.A(1, 0.1 + 0.45 * k)
    e = envelope((-7.0, 1.2), K.ORANGE, 0.9, z=0.7)
    s1.pop(e, f, 6)
    K.move(e, f + 4, (BKX, 1.2), 16, "SINE", "EASE_IN_OUT")
    P.out(drops[k], f + 20, 6)
    K.move(e, f + 22, (3.4, 1.2), 24, "SINE", "EASE_IN_OUT")
    P.out(e, f + 46, 5)
f4 = c.A(1, 1.6)
e = envelope((-7.0, 1.2), K.ORANGE, 0.9, z=0.7)
s1.pop(e, f4, 6)
K.move(e, f4 + 4, (-5.4, 1.2), 14, "SINE", "EASE_IN_OUT")
K.shake(bucket, f4 + 20, 0.1, 4, 2)
K.move(e, f4 + 20, (-6.6, 1.2), 12, "SINE", "EASE_IN_OUT")
P.out(bl, f4 + 14, 6)
st429 = K.card("429", (BKX, 3.0), 1.8, 0.9, P.NGBG, size=0.6, z=0.6)
s1.pop(st429, f4 + 18, 8)
K.pulse(st429, f4 + 32, 1.1, 8)
P.out(e, f4 + 36, 6)
# サイズの上限
gate_top = K.card("", (2.0, 2.2), 0.32, 1.3, K.GRAY, z=0.3)
gate_bot = K.card("", (2.0, 0.2), 0.32, 1.3, K.GRAY, z=0.3)
s1.pop(gate_top, c.A(1, 2.5))
s1.pop(gate_bot, c.A(1, 2.55))
pk = K.card("大きな荷物", (-1.2, 1.2), 2.6, 1.5, K.WHITE, text_color=INK, size=0.4, z=0.6)
s1.pop(pk, c.A(1, 2.7))
K.move(pk, c.A(1, 2.9), (0.55, 1.2), 14, "SINE", "EASE_IN_OUT")
K.shake(pk, c.A(1, 2.9) + 16, 0.1, 4, 2)
nxs = P.nomark((0.55, 2.3), 0.3, K.RED)
s1.pop(nxs, c.A(1, 2.9) + 16, 8)
szc = P.chip("サイズ上限", (2.0, -0.85), K.YELLOW, 0.36, INK)
s1.pop(szc, c.A(1, 3.1))
# 行19：レビューで見つかった穴を再現してから直す
f19 = c.A(2, 0.0)
s1.out(f19 - 3)
s2 = c.sub()
wall2 = K.card("", (-1.7, 1.3), 6.6, 2.7, K.DARK)
s2.pop(wall2, f19 + 3)
hole_r = K.dot((-3.1, 1.5), 0.68, K.RED, None, z=0.2)
hole_i = K.dot((-3.1, 1.5), 0.52, K.BG, None, z=0.21)
s2.pop(hole_r, f19 + 8)
s2.pop(hole_i, f19 + 8)
mg = magnifier((-5.6, 3.1), 1.3, z=0.7)
s2.pop(mg, c.A(2, 0.3))
K.move(mg, c.A(2, 0.55), (-2.0, 2.45), 22, "SINE", "EASE_IN_OUT")
K.pulse(mg, c.A(2, 0.55) + 24, 1.1, 8)
rep = P.chip("再現", (4.7, 2.15), P.NGBG, 0.42)
fix = P.chip("修正", (4.7, 0.45), P.OKBG, 0.42)
ar19 = K.arrow((4.7, 1.7), (4.7, 0.95), K.WHITE, 0.09, 0.28)
s2.pop(rep, c.A(2, 1.2))
K.grow_x(ar19, c.A(2, 1.6), 10)
s2.add(ar19)
s2.pop(fix, c.A(2, 1.9))
patch = K.dot((-3.1, 1.5), 0.72, P.OKBG, None, z=0.5)
s2.pop(patch, c.A(2, 2.3), 8)
tk35 = P.tick((-3.1, 1.5), 0.36, z=0.6)
s2.pop(tk35, c.A(2, 2.45), 8)
for r in I.row_cards:                    # I.pulse_row は退場後の大きさ 0 を基準にしてしまうので、1 を基準にして脈打たせる
    K._last[(r.name, "scale")] = (1, 1, 1)
    K.pulse(r, A(19, 2.85), 1.12, 8)
    K._last[(r.name, "scale")] = (0, 0, 0)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(20, 0.1))
d.row("before", "他人の投票が見える", "ng", A(20, 0.4))
d.row("before", "名札は書き換え放題", "ng", A(20, 1.0))
d.row("before", "参加コードを総当たり", "ng", A(20, 1.6))
d.row("after", "他人の投票は404", "ok", A(20, 2.2))
d.row("after", "主催者だけ締切", "ok", A(20, 3.1))
d.row("after", "総当たりは止まる", "ok", A(20, 3.9))
d.pending(["メモリ上の回数制限", "複数台で緩む"], A(21, 0.5), step=40)

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["ログインがなくても、", "他人には触らせない。"])
if os.environ.get("V57_CHECK"):          # 退場し忘れの確認（描画前に、最後のキーが 0 でない物を表示）
    for o in bpy.data.objects:
        if o.parent is None and o.animation_data and o.animation_data.action:
            for fc in K._fcurves(o):
                if fc.data_path == "scale" and fc.array_index == 0 and fc.keyframe_points:
                    kp = fc.keyframe_points[-1]
                    if kp.co.y > 0.01:
                        print("LEFT", o.name, int(kp.co.x), round(kp.co.y, 2), tuple(round(v, 1) for v in o.location))
I.render()
