"""第3部 PostgreSQLに正しく保存する：導入動画（part03-intro）

場面構成（行番号は part03-intro.json の lines）
  0      A タイトル
  1-2    B 困りごと : メモリの箱（票と棒グラフ）→ 再起動のボタンで中身が消え、RED の「0」
  3-14   C 第17〜21章（5枚のカード。章ごとのミニ動き）
  15-18  D 前→後 : メモリの箱 ✕ → DB の円筒に札が残る。YELLOW のチップ2つ
  19     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import part_intro_kit as P

K = P.K
I = P.Intro(3, "PostgreSQLに正しく保存する", [
    (17, "DBとFlyway"), (18, "制約の設計"), (19, "JdbcClient"), (20, "インデックス"), (21, "実DBテスト")])
A, E, M = I.A, I.E, I.M
INK = P.INK
PD = K.hexc("#7a52d6")            # 白い文字が載る濃いめの PURPLE（DB の表・壁）
PL = K.hexc("#cdb9ff")            # 円筒の上面


def cylinder(loc, w=1.8, h=1.2, color=K.PURPLE, label=None, lsize=0.28, z=0.2):
    """DB の円筒（PURPLE）。原点は円筒の中心。"""
    root = bpy.data.objects.new("cyl", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    body = K.plane("cylbody", w, h, (0, 0, 0), color)
    body.parent = root
    bot = K.dot((0, -h / 2), w / 2, color, None, z=0.0)
    top = K.dot((0, h / 2), w / 2, PL if color == K.PURPLE else K.hexc("#aab1cc"), None, z=0.03)
    for e in (bot, top):
        for ch in e.children:
            ch.scale = (1, 0.34, 1)
        e.parent = root
    if label:
        K.text(label, lsize, (0, -0.05, 0.1), INK if color != K.GRAY else K.WHITE, 900, parent=root)
    return root


def ring_arrow(loc, r=0.5, color=K.WHITE, z=0.3):
    """回転する矢印（再起動）。root（円盤）と、その子の ring（回す側）を返す。"""
    root = K.dot(loc, r * 1.7, P.CUST, None, z=z)
    ring = bpy.data.objects.new("ring", None)
    bpy.context.scene.collection.objects.link(ring)
    ring.parent = root
    ring.location = (0, 0, 0.05)
    a0, a1, n = math.radians(40), math.radians(330), 14
    for i in range(n):
        s1 = a0 + (a1 - a0) * i / n
        s2 = a0 + (a1 - a0) * (i + 1) / n
        sg = P.seg((r * math.cos(s1), r * math.sin(s1)), (r * math.cos(s2), r * math.sin(s2)), 0.13, color, z=0.0)
        sg.parent = ring
    hd = P.poly((r * math.cos(a1), r * math.sin(a1)), 0.26, 3, color, a1 + math.pi / 2 - math.pi / 2, 0.0)
    hd.parent = ring
    return root, ring


def hbar(x0, y, w, h, label, color, size=0.45):
    """左端が x0 の横棒。root を grow_x で左から伸ばせる。"""
    wr = bpy.data.objects.new("hbar", None)
    bpy.context.scene.collection.objects.link(wr)
    wr.location = (x0, y, 0)
    cd = K.card(label, loc=(w / 2, 0), w=w, h=h, color=color, size=size)
    cd.parent = wr
    return wr


# ======================================================================
# A タイトル
# ======================================================================
I.title_card()

# ======================================================================
# B 困りごと：メモリの箱 → 再起動で中身が消える
# ======================================================================
b = I.trouble()
MX, MY = -1.0, 1.1
mem = P.framed((MX, MY), 6.2, 3.4, K.DARK, K.BLUE, 0.08, 0.3)
b.pop(mem, A(1, 0.2))
mlab = P.chip("メモリ上のMap", (MX, 3.3), K.BLUE, 0.4, z=0.1)
b.pop(mlab, A(1, 0.45))
items = []
for i, y in enumerate((2.0, 1.1, 0.2)):
    sl = K.card("投票", loc=(-2.8, y), w=2.0, h=0.7, color=K.WHITE, text_color=INK, size=0.4, z=0.1)
    b.pop(sl, A(1, 0.9) + i * 6, 8)
    items.append(sl)
for i, (x, h) in enumerate(((-0.2, 1.9), (0.7, 1.3), (1.6, 0.8))):
    br = K.card("", loc=(x, 0.0 + h / 2), w=0.6, h=h, color=K.BLUE, z=0.1)
    b.pop(br, A(1, 1.7) + i * 6, 8)
    items.append(br)
# 行2：再起動のボタンが回ると、中身が消える
btn, rg = ring_arrow((5.5, 1.1), 0.5)
b.pop(btn, A(2, 0.1))
rs = P.chip("再起動", (5.5, -0.15), K.GRAY, 0.4)
b.pop(rs, A(2, 0.25))
arr = K.arrow((4.45, 1.1), (2.35, 1.1), K.WHITE, 0.1, 0.32)
K.grow_x(arr, A(2, 0.5), 10)
b.add(arr)
P.spin(rg, A(2, 0.8), A(2, 1.7), 1.0)
K.shake(mem, A(2, 1.65), 0.14, 6, 2)
for i, o in enumerate(items):
    P.out(o, A(2, 1.95) + i * 2, 8)
zero = K.text("0", 2.6, (MX, MY - 0.1, 0.5), K.RED, 900)
b.pop(zero, A(2, 2.35), 10)
K.pulse(zero, A(2, 2.35) + 12, 1.12, 8)
I.cam(A(2), to=(0, 0.5), zoom=15.4, dur=24)
I.cam(A(3) - 16, to=(0, 0), zoom=16, dur=14)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---- 第17章：Docker から円筒が現れる。Flyway の履歴 V1・V2。書き換えると起動が止まる
c = I.chapter(17)
dk = P.framed((-5.2, 1.3), 3.0, 2.6, K.BG2, K.BLUE, 0.07)
c.pop(dk, c.A(0, 0.2))
dl = P.chip("Docker", (-5.2, 2.25), K.BLUE, 0.38, z=0.1)
c.pop(dl, c.A(0, 0.5))
db = cylinder((-5.2, 0.85), 1.7, 1.1, K.PURPLE, "PostgreSQL", 0.27, z=0.3)
c.pop(db, c.A(1, 0.5))
K.move(db, c.A(1, 1.4), (-1.8, 1.3), 22)
hist = []
for nm, y, f in (("V1", 2.3, 3.0), ("V2", 1.0, 3.8)):
    v = K.card(nm, loc=(2.6, y), w=1.8, h=0.8, color=P.CUST, size=0.46)
    dt = K.dot((1.1, y), 0.15, K.WHITE, None, z=0.1)
    c.pop(v, c.A(1, f))
    c.pop(dt, c.A(1, f), 8)
    hist.append(v)
ln = P.seg((1.1, 2.3), (1.1, 1.0), 0.07, K.GRAY, z=0.05)
c.pop(ln, c.A(1, 3.4))
ar = K.arrow((3.7, 1.0), (5.5, 1.0), K.WHITE, 0.1, 0.3)
K.grow_x(ar, c.A(1, 4.1), 8)
c.add(ar)
go = P.chip("起動", (6.3, 1.0), P.CUST, 0.4)
c.pop(go, c.A(1, 4.2))
# 行5：V1 を書き換えようとすると、チェックサム不一致で起動が止まる
P.shade(hist[0], [(c.A(2, 0.4), P.WAITBG)], P.CUST)
K.shake(hist[0], c.A(2, 0.5), 0.12, 6, 2)
cs = P.chip("チェックサム不一致", (5.4, 2.3), P.NGBG, 0.36)
c.pop(cs, c.A(2, 1.5))
fx = P.nomark((3.45, 2.7), 0.28)
c.pop(fx, c.A(2, 1.6), 8)
fx2 = P.nomark((4.6, 1.0), 0.3)
c.pop(fx2, c.A(2, 2.2), 8)
K.shake(go, c.A(2, 2.25), 0.1, 4, 2)

# ---- 第18章：ER 図（3つの表）→ 4つの壁に不正な行が弾かれる。アプリの if が手前、壁が最後の守り
c = I.chapter(18)
tabs = []
for x, nm in zip((-4.8, 0.0, 4.8), ("polls", "options", "votes")):
    t = K.card(nm, loc=(x, 1.5), w=3.2, h=1.8, color=PD, size=0.52)
    tabs.append(t)
    c.pop(t, c.A(0, 0.5) + 14 * len(tabs))
lns = []
for p1, p2, dd in (((-3.2, 1.5), (-1.6, 1.5), 0.2), ((1.6, 1.5), (3.2, 1.5), 0.7)):
    s = P.seg(p1, p2, 0.1, K.WHITE, z=0.05)
    K.grow_x(s, c.A(1, dd), 8)
    c.add(s)
    lns.append(s)
f_t = c.A(1, 1.6)
for o in tabs + lns:
    P.out(o, f_t, 8)
bricks = []
BXS = (-4.125, -1.375, 1.375, 4.125)
for i, (x, nm) in enumerate(zip(BXS, ("NOT NULL", "UNIQUE", "CHECK", "外部キー"))):
    br = K.card(nm, loc=(x, 0.7), w=2.6, h=1.0, color=PD, size=0.4)
    c.pop(br, c.A(1, 1.75) + i * 4)
    bricks.append(br)
for i, x in enumerate(BXS):
    f = c.A(1, 2.2) + i * 6
    bad = P.chip("不正な行", (x, 3.1), P.NGBG, 0.34, z=0.4)
    c.pop(bad, f, 6)
    K.move(bad, f + 4, (x, 1.55), 10, "SINE", "EASE_IN")
    K.shake(bricks[i], f + 14, 0.08, 4, 2)
    K.move(bad, f + 16, (x, 2.7), 10, "SINE", "EASE_OUT")
    P.out(bad, f + 26, 6)
    nm_ = P.nomark((x + 1.05, 1.2), 0.26, )
    c.pop(nm_, f + 14, 8)
# 行8：手前に「アプリの if」、その奥に壁
al = K.dashed_line((-5.4, 2.3), (5.4, 2.3), K.BLUE, 0.1, 0.8, 0.5, z=0.2)
c.pop(al, c.A(2, 0.3))
at_ = P.chip("アプリのif", (-3.8, 2.85), K.BLUE, 0.38)
c.pop(at_, c.A(2, 0.45))
last = P.chip("最後の守り", (0.0, -0.4), P.OKBG, 0.4)
c.pop(last, c.A(2, 1.9))
for br in bricks:
    K.pulse(br, c.A(2, 2.0), 1.06, 8)
K.pulse(last, c.A(2, 2.0) + 12, 1.1, 8)

# ---- 第19章：SQL の札に穴。値がはまる。文字をつなぐ版は貫通、穴にはめる版は値として止まる
c = I.chapter(19)
sq = K.card("SQL", loc=(0, 1.5), w=5.4, h=1.2, color=P.CUST, size=0.6)
hole = K.dot((1.8, 0), 0.44, P.BG, None, z=0.06)
hole.parent = sq
qm = P.qmark((1.8, 0), 0.34, z=0.12)
qm.parent = sq
val = P.chip("値", (1.8, -2.0), P.OKBG, 0.4, z=0.3)
val.parent = sq
vk = P.tick((2.45, 0.5), 0.24, z=0.3)
vk.parent = sq
c.pop(sq, c.A(0, 0.2))
K.pop_in(qm, c.A(0, 0.55), 8)
bv = P.chip("バインド変数", (0, 2.95), K.BLUE, 0.4)
c.pop(bv, c.A(0, 0.7))
K.pop_in(val, c.A(0, 1.5), 8)
K.move(val, c.A(0, 1.7), (1.8, 0), 14)
K.pop_in(vk, c.A(0, 2.1), 8)
K.pulse(sq, c.A(0, 2.1), 1.04, 8)
# 行10：左右に並べて比べる
K.move(sq, c.A(1, 0.1), (3.9, 1.5), 18)
K.move(bv, c.A(1, 0.1), (3.9, 2.95), 18)
sq2 = K.card("SQL", loc=(-3.9, 1.5), w=5.2, h=1.2, color=P.CUST, size=0.6)
c.pop(sq2, c.A(1, 0.3))
cc = P.chip("文字をつなぐ", (-3.9, 2.95), K.GRAY, 0.4)
c.pop(cc, c.A(1, 0.45))
ev1 = P.chip("悪意のある入力", (-3.9, -0.5), P.NGBG, 0.36, z=0.5)
c.pop(ev1, c.A(1, 0.9))
K.move(ev1, c.A(1, 1.3), (-3.9, 2.0), 16)
P.shade(sq2, [(c.A(1, 1.3) + 6, P.NGBG)], P.CUST)
K.shake(sq2, c.A(1, 1.3) + 8, 0.12, 4, 2)
n1 = P.nomark((-1.6, 2.0), 0.3, )
c.pop(n1, c.A(1, 1.75), 8)
P.out(ev1, c.A(1, 2.0), 8)
ev2 = P.chip("悪意のある入力", (3.9, -0.5), P.NGBG, 0.36, z=0.5)
c.pop(ev2, c.A(1, 2.2))
K.move(ev2, c.A(1, 2.5), (3.9, 0.45), 14)
k2 = P.tick((6.0, 0.45), 0.28)
c.pop(k2, c.A(1, 2.95), 8)
K.pulse(ev2, c.A(1, 2.95), 1.05, 8)

# ---- 第20章：3行×3行 → 9個（JOIN で行が増える）。長い赤い棒と短い緑の棒
c = I.chapter(20)
g1 = []
for i, y in enumerate((2.3, 1.5, 0.7)):
    t = K.card("行", loc=(-5.0, y), w=1.5, h=0.62, color=K.BLUE, size=0.36)
    c.pop(t, c.A(0, 0.1) + i * 4)
    t2 = K.card("行", loc=(-2.2, y), w=1.5, h=0.62, color=PD, size=0.36)
    c.pop(t2, c.A(0, 0.6) + i * 4)
    g1 += [t, t2]
mu = P.label("×", (-3.6, 1.5), 0.8, K.WHITE, 900)
c.pop(mu, c.A(0, 0.5))
eq = P.label("＝", (-0.6, 1.5), 0.8, K.WHITE, 900)
c.pop(eq, c.A(0, 0.95))
g1 += [mu, eq]
jn = P.chip("JOIN", (-1.4, 3.0), K.GRAY, 0.36)
c.pop(jn, c.A(0, 0.95))
g1.append(jn)
k = 0
for yy in (2.3, 1.5, 0.7):
    for xx in (1.2, 2.55, 3.9):
        t = K.card("行", loc=(xx, yy), w=1.1, h=0.55, color=P.CUST, size=0.32)
        c.pop(t, c.A(0, 1.1) + k * 2, 8)
        g1.append(t)
        k += 1
more = P.chip("行が増える", (6.0, 1.5), K.YELLOW, 0.38, INK)
c.pop(more, c.A(0, 1.95))
g1.append(more)
f_b = c.A(1, -0.1)
for o in g1:
    P.out(o, f_b, 8)
ex = P.chip("EXPLAIN ANALYZE", (-2.6, 3.0), K.GRAY, 0.38)
c.pop(ex, c.A(1, 0.15))
b1 = hbar(-6.2, 1.9, 10.8, 0.9, "全件を読む", P.NGBG)
K.grow_x(b1, c.A(1, 0.8), 40)
c.add(b1)
b2 = hbar(-6.2, 0.6, 3.0, 0.9, "インデックス", P.OKBG)
K.grow_x(b2, c.A(1, 1.9), 10)
c.add(b2)
K.pulse(b2, c.A(1, 1.9) + 14, 1.06, 8)

# ---- 第21章：Docker から実 PostgreSQL が立ち上がり、テストが ✓。H2 は ✕
c = I.chapter(21)
dk2 = P.framed((-5.3, 1.3), 3.0, 2.6, K.BG2, K.BLUE, 0.07)
c.pop(dk2, c.A(0, 0.2))
dl2 = P.chip("Docker", (-5.3, 2.25), K.BLUE, 0.38, z=0.1)
c.pop(dl2, c.A(0, 0.5))
pg = cylinder((-5.3, 0.4), 1.7, 1.1, K.PURPLE, "PostgreSQL", 0.27, z=0.3)
c.pop(pg, c.A(0, 0.9))
K.move(pg, c.A(0, 1.0), (-5.3, 0.9), 14)
ts = K.card("テスト", loc=(-1.2, 1.3), w=2.6, h=1.0, color=P.CUST, size=0.5)
c.pop(ts, c.A(0, 1.5))
ta = K.arrow((-2.5, 1.3), (-3.55, 1.3), K.WHITE, 0.1, 0.28)
K.grow_x(ta, c.A(0, 1.8), 8)
c.add(ta)
tk = P.tick((0.0, 1.85), 0.3)
c.pop(tk, c.A(0, 2.3), 8)
h2 = cylinder((3.8, 1.0), 1.7, 1.1, K.GRAY, "H2", 0.4, z=0.3)
c.pop(h2, c.A(1, 0.2))
t2 = P.tick((4.95, 1.85), 0.3)
c.pop(t2, c.A(1, 0.9), 8)
P.out(t2, c.A(1, 2.2), 8)
x2 = P.nomark((4.95, 1.85), 0.3)
c.pop(x2, c.A(1, 2.9), 8)
K.shake(h2, c.A(1, 2.9), 0.12, 4, 2)
df = P.chip("SQL・型・制約が違う", (3.8, -0.3), K.YELLOW, 0.36, INK)
c.pop(df, c.A(1, 1.9))
K.pulse(df, c.A(1, 1.9) + 12, 1.1, 8)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(15, 0.1))
d.row("before", "メモリ上のMap", "ng", A(15, 0.5))
d.row("before", "再起動で消える", "ng", A(15, 1.2))
cy = cylinder((2.0, 1.55), 1.5, 1.0, K.PURPLE, None, z=0.3)
d.g.pop(cy, A(15, 1.2))
for i, y in enumerate((2.0, 1.55, 1.1)):
    sl = K.card("投票", loc=(3.6, y), w=1.3, h=0.38, color=K.WHITE, text_color=INK, size=0.26, z=0.1)
    d.g.pop(sl, A(15, 1.5) + i * 4, 8)
btn2, rg2 = ring_arrow((5.6, 1.55), 0.32)
d.g.pop(btn2, A(15, 1.9))
P.spin(rg2, A(15, 2.2), A(15, 2.9), 1.0)
ck = P.tick((2.85, 2.2), 0.28, z=0.5)
d.g.pop(ck, A(15, 2.7), 8)
d.row("after", "実PostgreSQLのテスト", "ok", A(16, 0.6), k=2, size=0.38)
# 行17〜18：まだ守れていないもの
pend = ["途中で失敗 → 片方だけ残る", "同時 → 上限を超える"]
ws = [P.text_w(s, 0.36) + 0.36 * 1.7 for s in pend]
x = 7.4 - (sum(ws) + 0.2)
x = max(x, -6.8)
for s, w, f in zip(pend, ws, (A(18, 0.3), A(18, 2.0))):
    c_ = P.chip(s, (x + w / 2, -1.12), K.YELLOW, 0.36, INK, w=w)
    d.g.pop(c_, f)
    K.pulse(c_, f + 12, 1.12, 8)
    x += w + 0.2

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["保存するだけでなく、", "約束をDBに置く。"])
I.render()
