"""第5部 再送・期限・定期処理：導入動画（part05-intro）

場面構成（行番号は part05-intro.json の lines）
  0      A タイトル
  1-4    B 困りごと : スマホ→サーバー→DB → 応答がトンネルで消える → 再送で行が2つ → カレンダーと箒に「？」
  5-8    C 第27章 冪等性 : 伝票番号 #A1 → 番号簿 → 同じ番号は結果を返す → 中身が違えば 422
  9-12   C 第28章 期限と時刻 : 時間軸と締切 → 直前・ちょうど・直後 → Clock で時間を進める → 拒否と削除は別のレーン
  13-16  C 第29章 定期処理 : 行の山を箒が1回分ずつ掃く → 途中で失敗して続きから → 2台で1台だけ動く
  17-19  D 前→後
  20     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
import part_intro_kit as P

K = P.K
I = P.Intro(5, "再送・期限・定期処理", [(27, "冪等性"), (28, "期限と時刻"), (29, "定期処理")])
A, E, M = I.A, I.E, I.M
INK = P.INK
PBG = K.hexc("#7c52d6")          # 白い文字を載せられる暗めの紫（保存された行）


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
    """封筒。tag を渡すと、上に伝票番号の札が付く（封筒の動きに付いてくる）。"""
    color = color or K.ORANGE
    root = K.card("", (loc[0], loc[1]), 0.95 * s, 0.64 * s, color, z=z)
    flap = P.poly((0, 0.17 * s), 0.56 * s, 3, dark(color), math.pi, 0.06)
    flap.scale = (1, 0.55, 1)
    flap.parent = root
    if tag:
        t = P.chip(tag, (0, 0.62 * s), K.WHITE, 0.3, INK, z=0.1)
        t.parent = root
    return root


def tunnel(loc, z=0.2):
    root = K.card("", (loc[0], loc[1]), 1.1, 0.9, K.GRAY, z=z)
    a = K.dot((0, 0.1), 0.3, K.BG, None, z=0.08)
    a.parent = root
    r = K.plane("arch", 0.6, 0.55, (0, -0.175, 0.08), K.BG)
    r.parent = root
    return root


def broom(loc, s=1.0, rot=0.35, z=0.3):
    root = empty(loc, z)
    root.rotation_euler = (0, 0, rot)
    st = K.plane("stick", 0.1 * s, 1.5 * s, (0, 0.55 * s, 0), K.WHITE)
    st.parent = root
    hd = K.rounded_rect("bristle", 0.85 * s, 0.55 * s, (0, -0.4 * s, 0.02), K.YELLOW, 0.12 * s)
    hd.parent = root
    bd = K.plane("band", 0.85 * s, 0.07 * s, (0, -0.18 * s, 0.04), dark(K.YELLOW, 0.55))
    bd.parent = root
    return root


def calendar(loc, s=1.0, z=0.3):
    root = K.card("", (loc[0], loc[1]), 1.5 * s, 1.5 * s, K.WHITE, z=z)
    hd = K.rounded_rect("calhead", 1.5 * s, 0.42 * s, (0, 0.54 * s, 0.05), K.ORANGE, 0.14 * s)
    hd.parent = root
    for i in range(3):
        for j in range(2):
            d = K.rounded_rect("calcell", 0.28 * s, 0.22 * s, ((i - 1) * 0.42 * s, (-0.02 - j * 0.38) * s, 0.05), K.GRAY, 0.05 * s)
            d.parent = root
    return root


def clock(loc, r=0.8, z=0.3):
    root = K.dot(loc, r, K.WHITE, None, z=z)
    mh = empty((0, 0), 0.1)
    mh.parent = root
    K.plane("mh", 0.09, r * 0.8, (0, r * 0.4, 0), INK).parent = mh
    hh = empty((0, 0), 0.12)
    hh.parent = root
    K.plane("hh", 0.12, r * 0.55, (0, r * 0.27, 0), K.ORANGE).parent = hh
    K.dot((0, 0), 0.08, INK, None, z=0.15).parent = root
    return root, mh, hh


def lock(loc, s=1.0, hole=None, z=0.4, opened=True):
    """南京錠。(root, shackle) を返す。shackle を (0, 0.3*s) へ動かすと閉まる。"""
    hole = hole or K.BG
    root = empty(loc, z)
    sh = empty((0, (0.58 if opened else 0.3) * s), 0.0)
    sh.parent = root
    K.dot((0, 0), 0.27 * s, K.YELLOW, None, z=0.0).parent = sh
    K.dot((0, 0), 0.15 * s, hole, None, z=0.01).parent = sh
    body = K.rounded_rect("lockbody", 0.8 * s, 0.6 * s, (0, -0.1 * s, 0.05), K.YELLOW, 0.12 * s)
    body.parent = root
    return root, sh


def pops(g, objs, f, step=3):
    for i, o in enumerate(objs):
        g.pop(o, f + i * step)
    return objs


# ======================================================================
# A タイトル
# ======================================================================
I.title_card()

# ======================================================================
# B 困りごと：スマホ→サーバー→DB。応答が消え、再送で行が2つ。期限と掃除は「？」
# ======================================================================
b = I.trouble()
PX, SX, DX = -5.9, -2.0, 1.6
phone = K.card("スマホ", (PX, 1.2), 1.5, 2.3, K.ORANGE, size=0.34)
server = K.card("サーバー", (SX, 1.2), 2.5, 1.8, K.BLUE, size=0.46)
dbp = P.framed((DX, 1.1), 2.9, 3.3, K.BG2, K.PURPLE, 0.07)
dbt = P.chip("DB", (DX, 2.35), K.PURPLE, 0.4, z=0.1)
b.pop(phone, A(1, 0.1))
b.pop(server, A(1, 0.3))
b.pop(dbp, A(1, 0.5))
b.pop(dbt, A(1, 0.65))
lane1 = K.arrow((-5.05, 1.75), (-3.4, 1.75), K.GRAY, 0.06, 0.22)
lane2 = K.arrow((-3.4, 0.65), (-5.05, 0.65), K.GRAY, 0.06, 0.22)
lane3 = K.arrow((-0.7, 1.15), (0.1, 1.15), K.GRAY, 0.06, 0.2)
K.grow_x(lane1, A(1, 0.7), 10)
K.grow_x(lane3, A(1, 0.8), 10)
b.add(lane1, lane3)

# 行1：同時に来る3つのリクエストを、サーバーはさばく
for k in range(3):
    f = A(1, 1.0) + k * 10
    e = envelope((-4.95, 1.75), K.ORANGE, 0.8)
    b.pop(e, f, 6)
    K.move(e, f + 4, (-3.5, 1.75), 12, "SINE", "EASE_IN_OUT")
    P.out(e, f + 17, 5)
    K.pulse(server, f + 17, 1.08, 6)
okc = P.chip("同時でも耐える", (SX, 2.7), P.OKBG, 0.34)
b.pop(okc, A(1, 2.5))
okt = P.tick((SX + 1.2, 1.95), 0.3, z=0.5)
b.pop(okt, A(1, 2.7), 8)

# 行2：1回のリクエストは保存されるが、応答がトンネルで消える。スマホは失敗と思う
P.out(okc, A(2, 0.0), 6)
P.out(okt, A(2, 0.0), 6)
fe = A(2, 0.1)
e = envelope((-4.95, 1.75), K.ORANGE, 0.8)
b.pop(e, fe, 6)
K.move(e, fe + 4, (-3.5, 1.75), 12, "SINE", "EASE_IN_OUT")
P.out(e, fe + 17, 5)
K.pulse(server, fe + 17, 1.08, 6)
e2 = envelope((-0.9, 1.15), K.ORANGE, 0.6)
b.pop(e2, A(2, 0.75), 6)
K.move(e2, A(2, 0.75) + 4, (0.75, 1.15), 12, "SINE", "EASE_IN_OUT")
P.out(e2, A(2, 0.75) + 17, 5)
row1 = K.card("選択肢 A", (DX, 1.45), 2.4, 0.6, PBG, size=0.3, z=0.1)
b.pop(row1, A(2, 1.15))
tun = tunnel((-4.2, 0.65))
b.pop(tun, A(2, 0.3))
tl = P.label("トンネル", (-4.2, -0.05), 0.3, K.GRAY, 700)
b.pop(tl, A(2, 0.45))
K.grow_x(lane2, A(2, 0.4), 10)
b.add(lane2)
e3 = envelope((-3.4, 0.65), K.BLUE, 0.8, z=0.5)
b.pop(e3, A(2, 1.4), 6)
K.move(e3, A(2, 1.4) + 4, (-4.2, 0.65), 14, "SINE", "EASE_IN_OUT")
P.out(e3, A(2, 1.4) + 17, 8)
fail = P.chip("失敗？", (-4.4, 2.75), K.YELLOW, 0.36, INK)
b.pop(fail, A(2, 2.3))
K.pulse(fail, A(2, 2.3) + 12, 1.12, 8)
K.shake(phone, A(2, 2.4), 0.1, 4, 2)
# 再送
fr = A(2, 3.0)
e4 = envelope((-4.95, 1.75), K.ORANGE, 0.8)
b.pop(e4, fr, 6)
K.move(e4, fr + 4, (-3.5, 1.75), 12, "SINE", "EASE_IN_OUT")
P.out(e4, fr + 17, 5)
K.pulse(server, fr + 17, 1.08, 6)
P.out(fail, fr - 3, 6)
resend = P.chip("再送", (-4.4, 2.75), K.ORANGE, 0.36)
b.pop(resend, fr - 1)

# 行3：もう一度保存されて、行が2つ（二重）
e5 = envelope((-0.9, 1.15), K.ORANGE, 0.6)
b.pop(e5, A(3, 0.25), 6)
K.move(e5, A(3, 0.25) + 4, (0.75, 1.15), 12, "SINE", "EASE_IN_OUT")
P.out(e5, A(3, 0.25) + 17, 5)
row2 = K.card("選択肢 A", (DX, 0.7), 2.4, 0.6, P.NGBG, size=0.3, z=0.1)
b.pop(row2, A(3, 0.65))
x2 = P.nomark((DX + 0.9, 0.7), 0.24)
b.pop(x2, A(3, 0.85), 8)
K.shake(row2, A(3, 0.9), 0.1, 4, 2)
dbl = P.chip("二重登録", (DX, -0.95), P.NGBG, 0.36)
b.pop(dbl, A(3, 1.3))
K.pulse(dbl, A(3, 1.3) + 12, 1.12, 8)

# 行4：期限の判定も、掃除の処理も、まだない
cal = calendar((4.7, 1.5))
brm = broom((6.45, 1.35), 1.0)
b.pop(cal, A(4, 0.2))
b.pop(brm, A(4, 0.7))
cl = P.chip("期限", (4.7, 0.35), K.GRAY, 0.34)
bl = P.chip("掃除", (6.45, 0.35), K.GRAY, 0.34)
b.pop(cl, A(4, 0.45))
b.pop(bl, A(4, 0.95))
for x, f in ((5.4, 1.4), (7.0, 2.0)):
    q = P.qmark((x, 2.35 if x < 6 else 2.25), 0.3, z=0.5)
    b.pop(q, A(4, 0) + int(f * 30), 8)
    K.pulse(q, A(4, 0) + int(f * 30) + 12, 1.2, 8)

I.cam(A(2, 0.6), to=(-0.9, 0.4), zoom=15.0, dur=24)
I.cam(A(4, -0.1), to=(0, 0), zoom=16, dur=16)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---------------------------------------------------------------- 第27章 冪等性
c = I.chapter(27)
PHX, SVX, LGX = -5.9, -1.3, 4.45
ph = K.card("スマホ", (PHX, 1.3), 1.7, 2.4, K.ORANGE, size=0.4)
sv = K.card("サーバー", (SVX, 1.3), 3.0, 2.1, K.BLUE, size=0.52)
lg = P.framed((LGX, 1.2), 4.1, 3.5, K.BG2, K.GRAY, 0.06)
lt = P.chip("番号簿", (LGX, 2.5), K.GRAY, 0.44, z=0.1)
c.pop(ph, c.A(0, 0.1))
c.pop(sv, c.A(0, 0.3))
c.pop(lg, c.A(0, 0.5))
c.pop(lt, c.A(0, 0.7))
ar_l = K.arrow((0.35, 1.3), (2.3, 1.3), K.GRAY, 0.07, 0.24)
K.grow_x(ar_l, c.A(0, 0.9), 10)
c.add(ar_l)

# 行6：初めての番号は、処理して記録する
f6 = c.A(1, 0.1)
e = envelope((-4.9, 1.3), K.ORANGE, 1.05, tag="#A1")
c.pop(e, f6, 6)
K.move(e, f6 + 4, (-3.1, 1.3), 16, "SINE", "EASE_IN_OUT")
P.out(e, f6 + 21, 5)
K.pulse(sv, f6 + 21, 1.08, 6)
rec = P.chip("処理して記録", (SVX, 2.95), K.GRAY, 0.38)
c.pop(rec, c.A(1, 1.0))
row_l = K.card("#A1　結果", (LGX, 1.45), 3.5, 0.8, P.CUST, size=0.42, z=0.1)
c.pop(row_l, c.A(1, 1.7))
dbc = P.chip("DB 1行", (SVX, -0.4), PBG, 0.4)
c.pop(dbc, c.A(1, 2.2))
K.pulse(dbc, c.A(1, 2.2) + 12, 1.12, 8)

# 行7：同じ番号は、処理せずに保存した結果を返す
P.out(rec, c.A(2, 0.0), 6)
f7 = c.A(2, 0.1)
e = envelope((-4.9, 1.3), K.ORANGE, 1.05, tag="#A1")
c.pop(e, f7, 6)
K.move(e, f7 + 4, (-3.1, 1.3), 16, "SINE", "EASE_IN_OUT")
P.out(e, f7 + 21, 5)
K.pulse(sv, f7 + 21, 1.08, 6)
nop = P.chip("処理しない", (SVX, 2.95), K.GRAY, 0.38)
c.pop(nop, c.A(2, 0.75))
P.shade(row_l, [(c.A(2, 0.9), K.BLUE)], P.CUST)
K.pulse(row_l, c.A(2, 0.9), 1.08, 8)
slip = K.card("結果", (LGX - 1.1, 1.45), 1.4, 0.65, K.WHITE, text_color=INK, size=0.38, z=0.6)
c.pop(slip, c.A(2, 1.0), 6)
K.move(slip, c.A(2, 1.2), (PHX + 2.0, 2.0), 36, "SINE", "EASE_IN_OUT")
okd = P.tick((SVX + 1.3, -0.4), 0.3)
c.pop(okd, c.A(2, 1.2), 8)
okp = P.tick((PHX + 0.85, 2.35), 0.32, z=0.5)
c.pop(okp, c.A(2, 2.2), 8)
P.out(slip, c.A(2, 2.4), 6)

# 行8：同じ番号で中身が違えば、422で断る
P.out(nop, c.A(3, 0.0), 6)
P.out(okp, c.A(3, 0.0), 6)
f8 = c.A(3, 0.1)
e = envelope((-4.9, 1.3), K.ORANGE, 1.05, tag="#A1")
c.pop(e, f8, 6)
diff = P.chip("中身が違う", (-4.9, 0.3), K.YELLOW, 0.34, INK)
c.pop(diff, f8 + 3)
K.move(e, f8 + 4, (-3.1, 1.3), 16, "SINE", "EASE_IN_OUT")
K.move(diff, f8 + 4, (-3.1, 0.3), 16, "SINE", "EASE_IN_OUT")
stamp = K.card("422", (SVX, 1.3), 1.9, 1.05, P.NGBG, size=0.68, z=0.7)
stamp.rotation_euler = (0, 0, 0.18)
c.pop(stamp, f8 + 24, 8)
K.shake(stamp, f8 + 34, 0.12, 6, 2)
K.pulse(stamp, f8 + 50, 1.1, 8)
P.out(e, f8 + 38, 6)
P.out(diff, f8 + 38, 6)
rej = P.chip("断る", (SVX, 2.95), P.NGBG, 0.4)
c.pop(rej, f8 + 42)
x422 = P.nomark((PHX + 0.85, 2.35), 0.32)
c.pop(x422, f8 + 46, 8)

# ---------------------------------------------------------------- 第28章 期限と時刻
c = I.chapter(28)
axis = P.seg((-6.9, 0.2), (6.9, 0.2), 0.1, K.GRAY, z=0.0)
c.pop(axis, c.A(0, 0.1))
dl = P.seg((0, -0.6), (0, 2.45), 0.1, K.WHITE, z=0.05)
c.pop(dl, c.A(0, 0.5))
dlc = P.chip("締切", (0, 2.9), K.WHITE, 0.38, INK)
c.pop(dlc, c.A(0, 0.7))
# 行10：直前・ちょうど・直後
marks = []
for x, lab, col, tc, bg, f in (
        (-3.4, "直前", K.GREEN, K.WHITE, P.OKBG, c.A(1, 0.1)),
        (0.0, "ちょうど", K.YELLOW, INK, K.YELLOW, c.A(1, 1.0)),
        (3.4, "直後", K.RED, K.WHITE, P.NGBG, c.A(1, 1.95))):
    dt = K.dot((x, 0.2), 0.22, col, None, z=0.4)
    ch = P.chip(lab, (x, 1.25), bg, 0.42, tc, z=0.3)
    c.pop(dt, f, 8)
    c.pop(ch, f + 3)
    K.pulse(ch, f + 15, 1.12, 8)
rj = P.chip("拒否", (3.4, -0.45), P.NGBG, 0.36)
c.pop(rj, c.A(1, 2.35))
# 行11：Clock を差し替えて、時間を進める
cl_root, mh, hh = clock((-5.6, 2.15), 0.75)
c.pop(cl_root, c.A(2, 0.1))
clc = P.chip("Clock", (-5.6, 0.85), K.PURPLE, 0.34)
c.pop(clc, c.A(2, 0.3))
P.spin(mh, c.A(2, 0.5), c.A(2, 2.5), 5.0)
P.spin(hh, c.A(2, 0.5), c.A(2, 2.5), 0.42)
now = K.dot((-6.2, 0.2), 0.26, K.ORANGE, None, z=0.6)
c.pop(now, c.A(2, 0.5), 8)
K.move(now, c.A(2, 0.8), (1.0, 0.2), 55, "SINE", "EASE_IN_OUT")
tkc = P.tick((-5.6 + 0.95, 1.1), 0.3, z=0.6)
c.pop(tkc, c.A(2, 2.5), 8)

# 行12：締切後の拒否と、7日後の削除は、別のレーン
s2 = c.sub()
f12 = c.A(3)
# 行10-11 の要素を片づけてから、2本のレーンを出す
c.out(f12 - 2)
lane_a = K.card("", (0, 2.3), 12.8, 1.1, K.DARK)
lane_b = K.card("", (0, 0.3), 12.8, 1.1, K.DARK)
s2.pop(lane_a, f12 + 2)
a1 = P.chip("締切後", (-4.6, 2.3), K.GRAY, 0.4, z=0.1)
a2 = P.chip("拒否", (-0.3, 2.3), P.NGBG, 0.4, z=0.1)
ara = K.arrow((-3.3, 2.3), (-1.4, 2.3), K.WHITE, 0.09, 0.28, z=0.1)
s2.pop(a1, f12 + 6)
K.grow_x(ara, f12 + 12, 10)
s2.add(ara)
s2.pop(a2, f12 + 22)
s2.pop(lane_b, c.A(3, 1.2))
b1 = P.chip("作成", (-5.2, 0.3), K.GRAY, 0.4, z=0.1)
b2 = P.chip("7日後", (0.7, 0.3), K.YELLOW, 0.4, INK, z=0.1)
bm = broom((4.1, 0.3), 0.6, 0.3, z=0.2)
b3 = P.chip("削除", (5.45, 0.3), K.ORANGE, 0.4, z=0.1)
arb = K.arrow((-4.2, 0.3), (-0.6, 0.3), K.WHITE, 0.09, 0.28, z=0.1)
arb2 = K.arrow((1.9, 0.3), (3.3, 0.3), K.WHITE, 0.09, 0.28, z=0.1)
s2.pop(b1, c.A(3, 1.3))
K.grow_x(arb, c.A(3, 1.45), 14)
s2.add(arb)
s2.pop(b2, c.A(3, 1.8))
K.grow_x(arb2, c.A(3, 2.0), 10)
s2.add(arb2)
s2.pop(bm, c.A(3, 2.1))
s2.pop(b3, c.A(3, 2.3))
sep = P.chip("別の仕組み", (0, 1.3), K.YELLOW, 0.36, INK, z=0.3)
s2.pop(sep, c.A(3, 2.75))
K.pulse(sep, c.A(3, 2.75) + 12, 1.12, 8)

# ---------------------------------------------------------------- 第29章 定期処理
c = I.chapter(29)
PILE = (-4.3, 1.2)
pf = P.framed(PILE, 4.9, 3.1, K.BG2, K.GRAY, 0.06)
c.pop(pf, c.A(0, 0.1))
cells = []                                # cells[col][row]
for col in range(3):
    colc = []
    for row in range(4):
        cc = K.card("", (PILE[0] - 1.45 + col * 1.45, 2.15 - row * 0.7), 1.3, 0.5, PBG, z=0.1)
        c.pop(cc, c.A(0, 0.3) + (col * 4 + row) * 2, 6)
        colc.append(cc)
    cells.append(colc)
pl = P.chip("期限切れの投票", (PILE[0], -0.85), K.GRAY, 0.34, z=0.2)
c.pop(pl, c.A(0, 0.9))
brm2 = broom((-0.5, 1.1), 1.1, 0.35, z=0.4)
c.pop(brm2, c.A(0, 1.1))
trk, fill = K.progress_bar((4.1, 1.2), 5.0, 0.42, K.GREEN, K.DARK)
c.pop(trk, c.A(0, 1.3))
pgl = P.chip("進み", (2.3, 2.15), K.GRAY, 0.34)
c.pop(pgl, c.A(0, 1.4))
once = P.chip("1回分ずつ", (5.0, 2.15), K.GRAY, 0.34)
c.pop(once, c.A(0, 1.5))


def sweep(col, f, n=4, bar_to=None):
    """第 col 列の n 個を、箒が掃いて消す。"""
    K.move(brm2, f, (-0.5, 1.1 + (0.5 if col % 2 == 0 else -0.3)), 10, "SINE", "EASE_IN_OUT")
    for r in range(n):
        P.out(cells[col][r], f + 6 + r * 3, 6)
    K.shake(brm2, f + 4, 0.12, 6, 2)
    K.pulse(once, f + 6, 1.15, 8)
    if bar_to is not None:
        K.bar_to(fill, f + 6, bar_to, 20)


# 行14：件数を区切って削除
sweep(0, c.A(1, 0.2), 4, 1 / 3)
sweep(1, c.A(1, 1.5), 4, 2 / 3)
# 行15：途中で失敗して、続きから
f15 = c.A(2, 0.1)
K.move(brm2, f15, (-0.5, 1.6), 10, "SINE", "EASE_IN_OUT")
for r in range(2):
    P.out(cells[2][r], f15 + 6 + r * 3, 6)
bolt = [P.seg((-0.2, 3.15), (-0.55, 2.55), 0.13, K.RED, z=0.7),
        P.seg((-0.55, 2.55), (-0.05, 2.45), 0.13, K.RED, z=0.7),
        P.seg((-0.05, 2.45), (-0.45, 1.8), 0.13, K.RED, z=0.7)]
for i, o in enumerate(bolt):
    c.pop(o, f15 + 20 + i * 2, 6)
    P.out(o, c.A(2, 1.3), 6)
xf = P.nomark((0.4, 0.55), 0.3)
c.pop(xf, f15 + 26, 8)
K.shake(brm2, f15 + 26, 0.14, 6, 2)
stop = P.chip("失敗", (4.1, 0.35), P.NGBG, 0.4)
c.pop(stop, f15 + 28)
f_re = c.A(2, 1.4)
P.out(xf, f_re - 2, 6)
P.out(stop, f_re - 2, 6)
cont = P.chip("続きから", (3.4, 0.35), P.OKBG, 0.4)
c.pop(cont, f_re)
for r in range(2, 4):
    P.out(cells[2][r], f_re + 8 + (r - 2) * 4, 6)
K.shake(brm2, f_re + 6, 0.12, 6, 2)
K.bar_to(fill, f_re + 8, 1.0, 24)
tkb = P.tick((6.9, 1.2), 0.3, z=0.6)
c.pop(tkb, f_re + 34, 8)

# 行16：2台で同時に走っても、動くのは1台だけ
s3 = c.sub()
f16 = c.A(3)
c.out(f16 - 2)
sv1 = K.card("1台目", (-4.2, 1.4), 2.6, 1.4, K.BLUE, size=0.5)
sv2 = K.card("2台目", (4.2, 1.4), 2.6, 1.4, K.BLUE, size=0.5)
s3.pop(sv1, f16 + 3)
s3.pop(sv2, f16 + 7)
brm3 = broom((0.0, 1.4), 1.2, 0.35, z=0.4)
s3.pop(brm3, f16 + 12)
a_l = K.arrow((-2.8, 1.4), (-0.9, 1.4), K.GRAY, 0.07, 0.24)
a_r = K.arrow((2.8, 1.4), (0.9, 1.4), K.GRAY, 0.07, 0.24)
K.grow_x(a_l, f16 + 20, 10)
K.grow_x(a_r, f16 + 22, 10)
s3.add(a_l, a_r)
lk, shk = lock((0.0, -0.15), 1.0, z=0.5)
s3.pop(lk, c.A(3, 1.0))
K.move(shk, c.A(3, 1.25), (0, 0.3), 8, "SINE", "EASE_IN_OUT")
K.move(lk, c.A(3, 1.5), (-0.9, -0.15), 16, "SINE", "EASE_IN_OUT")
run = P.chip("動く", (-4.2, 0.3), P.OKBG, 0.4)
s3.pop(run, c.A(3, 1.9))
wait = P.chip("待ち", (4.2, 0.3), K.YELLOW, 0.4, INK)
s3.pop(wait, c.A(3, 2.2))
K.pulse(wait, c.A(3, 2.2) + 12, 1.12, 8)
tk1 = P.tick((-4.2 + 1.15, 2.0), 0.3, z=0.5)
s3.pop(tk1, c.A(3, 2.0), 8)
K.move(brm3, c.A(3, 1.5), (-0.7, 1.4), 16, "SINE", "EASE_IN_OUT")
K.shake(brm3, c.A(3, 2.0), 0.1, 6, 2)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(17, 0.1))
d.row("before", "2行（二重登録）", "ng", A(17, 0.5))
d.row("after", "再送しても1行のまま", "ok", A(17, 2.2))
d.row("before", "期限の判定なし", "ng", A(18, 0.1))
d.row("after", "締切後は断る", "ok", A(18, 0.9))
d.row("before", "掃除の処理なし", "ng", A(19, 0.1))
d.row("after", "7日後に削除", "ok", A(19, 1.2))
note = P.label("同じ訪問者・番号簿の保持期間内", (d.RX, -1.0), 0.3, K.GRAY, 700)
d.g.pop(note, A(17, 3.0))

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["再送も時間切れも、", "前提にして作る。"])
if os.environ.get("V57_CHECK"):          # 退場し忘れの確認（描画前に、最後のキーが 0 でない物を表示）
    for o in bpy.data.objects:
        if o.parent is None and o.animation_data and o.animation_data.action:
            for fc in K._fcurves(o):
                if fc.data_path == "scale" and fc.array_index == 0 and fc.keyframe_points:
                    kp = fc.keyframe_points[-1]
                    if kp.co.y > 0.01:
                        print("LEFT", o.name, int(kp.co.x), round(kp.co.y, 2), tuple(round(v, 1) for v in o.location))
I.render()
