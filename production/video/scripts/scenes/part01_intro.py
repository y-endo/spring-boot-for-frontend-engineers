"""第1部 TypeScript脳で読むJava：導入動画（part01-intro）

場面構成（行番号は part01-intro.json の lines）
  0-1    A タイトル → 「TypeScript」のブロックに「違い」が乗る
  2-3    B 困りごと : 左右のカード（TypeScript は整然、Java は「？」だらけ）→「型」「例外」が揺れて「少し違う」
  4-19   C 第2〜9章（8枚のカード。章ごとのミニ動き）
  20-21  D 前→後
  22     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import part_intro_kit as P

K = P.K
I = P.Intro(1, "TypeScript脳で読むJava", [
    (2, "動く/仕組み"), (3, "名前で/決まる型"), (4, "データの形"), (5, "nullと/例外"),
    (6, "コレクション"), (7, "値の/落とし穴"), (8, "JUnit"), (9, "スレッド")])
A, E, M = I.A, I.E, I.M
INK = P.INK


def pops(g, objs, f, step=3):
    for i, o in enumerate(objs):
        g.pop(o, f + i * step)
    return objs


# ======================================================================
# A タイトル → TypeScript に「違い」を足す
# ======================================================================
I.title_card()
ga = P.Group(I, A(2) - 3)
big = K.card("TypeScript", loc=(0, 0.1), w=7.8, h=2.6, color=K.BLUE, size=1.1)
ga.pop(big, A(1, 0.2))
diff = K.card("違い", loc=(0, 4.8), w=2.6, h=1.3, color=K.ORANGE, size=0.75)
diff.location = (0, 4.8, 0.2)
ga.pop(diff, A(1, 0.9), 6)
K.move(diff, A(1, 1.3), (0, 2.05), 16, "BOUNCE", "EASE_OUT")
K.pulse(big, A(1, 1.3) + 14, 1.05, 8)

# ======================================================================
# B 困りごと：左右のカード
# ======================================================================
b = I.trouble()
PW, PH, PY = 6.5, 3.7, 1.25
LX, RX = -3.9, 3.9
lp = K.card("", (LX, PY), PW, PH, K.DARK)
rp = K.card("", (RX, PY), PW, PH, K.DARK)
b.pop(lp, A(2, 0.1))
lh = P.chip("TypeScript", (LX, PY + 1.3), K.BLUE, 0.42, z=0.1)
b.pop(lh, A(2, 0.3))
# 左：型のタグと ✓ が整然と並ぶ
k = 0
for col, tx in enumerate((-5.4, -2.5)):
    for row, ty in enumerate((1.55, 0.8, 0.05)):
        tg = K.card("", (tx, ty), 1.7, 0.5, K.BLUE, size=0.3, z=0.1)
        tk = P.tick((tx + 1.15, ty), 0.25)
        f = A(2, 0.5) + k * 5
        b.pop(tg, f, 8)
        b.pop(tk, f + 4, 8)
        k += 1
# 右：見慣れない形のタグが散らばり、「？」が浮く
b.pop(rp, A(2, 1.7))
rh = P.chip("Java", (RX, PY + 1.3), K.ORANGE, 0.42, z=0.1)
b.pop(rh, A(2, 1.9))
shapes = [((1.8, 1.55), 0.5, 6, K.ORANGE, 0.3), ((3.4, 0.5), 0.55, 3, K.GRAY, 0.9),
          ((5.2, 1.5), 0.5, 4, K.PURPLE, 0.2), ((6.1, 0.2), 0.45, 5, K.ORANGE, 0.5),
          ((2.4, 0.1), 0.42, 5, K.PURPLE, 1.1), ((4.5, 1.0), 0.4, 3, P.CUST, 2.2)]
for i, (loc, r, n, col, rot) in enumerate(shapes):
    pg = P.poly(loc, r, n, col, rot, 0.1)
    b.pop(pg, A(2, 2.0) + i * 4, 8)
qs = []
for i, loc in enumerate(((2.7, 1.0), (4.3, 1.9), (5.6, 0.7), (3.2, -0.15))):
    q = P.qmark(loc, 0.3, z=0.4)
    b.pop(q, A(2, 2.5) + i * 7, 8)
    K.bob(q, A(2, 2.8) + i * 7, A(3, 0.0), 0.1, 24)
    qs.append(q)
for q in qs:
    P.out(q, A(3, 0.1))
# 行3：「型」「例外」の2つのチップが右から左へ揺れて「少し違う」
t1 = P.chip("型", (5.6, 1.2), K.WHITE, 0.46, INK, z=0.5)
t2 = P.chip("例外", (5.6, 0.3), K.WHITE, 0.46, INK, z=0.5)
b.pop(t1, A(3, 0.3))
b.pop(t2, A(3, 0.6))
for t, y, d in ((t1, 1.2, 0), (t2, 0.3, 8)):
    K.move(t, A(3, 1.0) + d, (0.0, y), 26)
    K.shake(t, A(3, 1.0) + d + 28, 0.22, 6, 3)
sl = P.chip("少し違う", (0, -1.05), K.YELLOW, 0.42, INK)
b.pop(sl, A(3, 2.0))
K.pulse(sl, A(3, 2.0) + 12, 1.12, 8)

I.cam(A(3), to=(0, 0.5), zoom=15.4, dur=24)       # 章カードが出る前に、少し寄ってまた引く
I.cam(A(4) - 8, to=(0, 0), zoom=16, dur=16)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---- 第2章：.java → .class → JVM
c = I.chapter(2)
BX = [-4.6, 0.0, 4.6]
boxes = []
for x, lab, col in zip(BX, (".java", ".class", "JVM"), (P.CUST, P.CUST, K.BLUE)):
    boxes.append(K.card(lab, loc=(x, 1.1), w=3.0, h=1.7, color=col, size=0.65))
for i, o in enumerate(boxes):
    c.pop(o, c.A(0, 0.1) + i * 5)
a1 = K.arrow((BX[0] + 1.6, 1.1), (BX[1] - 1.6, 1.1), K.WHITE, 0.11, 0.34)
a2 = K.arrow((BX[1] + 1.6, 1.1), (BX[2] - 1.6, 1.1), K.WHITE, 0.11, 0.34)
K.grow_x(a1, c.A(1, 0.3), 10)
K.grow_x(a2, c.A(1, 1.3), 10)
c.add(a1, a2)
jc = P.chip("javac", (-2.3, 2.5), K.GRAY, 0.42, z=0.1)
c.pop(jc, c.A(1, 0.4))
K.pulse(boxes[2], c.A(1, 2.2), 1.18, 10)
K.pulse(boxes[2], c.A(1, 2.2) + 14, 1.12, 10)
okj = P.tick((BX[2] + 1.25, 1.85), 0.3)
c.pop(okj, c.A(1, 2.4), 8)

# ---- 第3章：形ではなく名前で決まる
c = I.chapter(3)
panels = []
for x, hdr, col in ((-3.8, "TypeScript", K.BLUE), (3.8, "Java", K.ORANGE)):
    pn = K.card("", (x, 1.2), 6.3, 3.5, K.DARK)
    c.pop(pn, c.A(0, 0.1))
    h = P.chip(hdr, (x, 2.55), col, 0.38, z=0.1)
    c.pop(h, c.A(0, 0.3))
    ic = []
    for dx, nm in ((-1.5, "A"), (1.5, "B")):
        o = K.card(nm, loc=(x + dx, 1.35), w=1.3, h=1.3, color=P.CUST, size=0.62)
        o.location = (x + dx, 1.35, 0.1)
        c.pop(o, c.A(0, 0.6) + (6 if dx > 0 else 0))
        ic.append(o)
    ar = K.arrow((x - 0.7, 1.35), (x + 0.7, 1.35), K.WHITE, 0.1, 0.3, z=0.15)
    K.grow_x(ar, c.A(1, 0.3), 8)
    c.add(ar)
    panels.append((x, ic))
okt = P.tick((-3.8 - 2.3, 0.35), 0.3)
c.pop(okt, c.A(1, 0.9), 8)
t_ok = P.label("形が同じならOK", (-3.8 - 1.9, 0.35), 0.38, K.WHITE, 700, "LEFT")
c.pop(t_ok, c.A(1, 1.0), 8)
ngm = P.nomark((3.8 - 2.5, 0.35), 0.3)
c.pop(ngm, c.A(1, 2.0), 8)
t_ng = P.label("名前が違うとNG", (3.8 - 2.1, 0.35), 0.38, K.WHITE, 700, "LEFT")
c.pop(t_ng, c.A(1, 2.1), 8)
K.shake(panels[1][1][1], c.A(1, 2.2), 0.12, 4, 2)

# ---- 第4章：募集中／締切済み → 判定表 → 書き漏れ
c = I.chapter(4)
tl = K.card("募集中", loc=(-2.3, 2.45), w=3.6, h=1.1, color=K.BLUE, size=0.55)
tr = K.card("締切済み", loc=(2.3, 2.45), w=3.6, h=1.1, color=P.CUST, size=0.55)
c.pop(tl, c.A(0, 0.1))
c.pop(tr, c.A(0, 0.5))
r1 = K.card("", loc=(0, 1.05), w=8.4, h=0.85, color=K.DARK, z=0.0)
r2 = K.card("", loc=(0, 0.05), w=8.4, h=0.85, color=K.DARK, z=0.0)
l1 = P.label("募集中", (-3.8, 1.05), 0.46, K.WHITE, 700, "LEFT")
l2 = P.label("締切済み", (-3.8, 0.05), 0.46, K.WHITE, 700, "LEFT")
k1 = P.tick((3.7, 1.05), 0.3)
k2 = P.tick((3.7, 0.05), 0.3)
c.pop(r1, c.A(1, 1.3))
c.pop(l1, c.A(1, 1.4))
c.pop(r2, c.A(1, 1.7))
c.pop(l2, c.A(1, 1.8))
c.pop(k1, c.A(1, 2.4), 8)
c.pop(k2, c.A(1, 2.9), 8)
# 行10：書き漏れ → コンパイルエラー
for o in (r2, l2, k2):
    P.out(o, c.A(2, 0.4))
ce = K.card("コンパイルエラー", loc=(0, 0.05), w=5.4, h=0.95, color=P.NGBG, size=0.5)
ce.location = (0, 0.05, 0.1)
c.pop(ce, c.A(2, 0.9))
K.shake(ce, c.A(2, 1.4), 0.2, 6, 2)
K.pulse(ce, c.A(2, 1.9), 1.08, 8)

# ---- 第5章：null → NPE → Optional
c = I.chapter(5)
nb = P.framed((2.0, 1.2), 3.4, 2.0, K.DARK, K.GRAY, 0.07)
c.pop(nb, c.A(0, 0.1))
nt = P.label("null", (2.0, 1.2), 0.75, K.GRAY, 900)
c.pop(nt, c.A(0, 0.4))
ar = K.arrow((-4.6, 1.2), (0.1, 1.2), K.BLUE, 0.16, 0.45)
K.grow_x(ar, c.A(1, 0.1), 12)
c.add(ar)
star1 = P.poly((2.0, 1.2), 1.55, 4, K.RED, math.pi / 4, 0.6)
star2 = P.poly((2.0, 1.2), 1.55, 4, K.RED, 0.0, 0.61)
npe = P.label("NPE", (2.0, 1.2), 0.7, K.WHITE, 900, z=0.8)
for o in (star1, star2, npe):
    c.pop(o, c.A(1, 0.55), 8)
K.shake(nb, c.A(1, 0.65), 0.15, 6, 2)
K.pulse(star1, c.A(1, 0.7), 1.12, 8)
for o in (star1, star2, npe):
    P.out(o, c.A(1, 1.3))
P.out(ar, c.A(1, 1.3))
opt = P.chip("Optional", (3.1, 2.5), K.BLUE, 0.42, z=0.3)
c.pop(opt, c.A(1, 1.4))
em = P.chip("空かも", (2.0, -0.15), K.YELLOW, 0.42, INK)
c.pop(em, c.A(1, 1.85))
K.pulse(em, c.A(1, 1.85) + 12, 1.12, 8)

# ---- 第6章：コンベアで流れる票。filter で1枚外れ、map で形が変わる
c = I.chapter(6)
belt = K.card("", (0, 0.3), 13.4, 0.5, P.ROAD, z=-0.01)
c.pop(belt, c.A(0, 0.1))
SY = 0.95
slips = []
# 並び順は右から：0 = 先頭（filter で外れる1枚）、1〜3 = そのあとに続く3枚
for i, x in enumerate((-2.8, -4.0, -5.2, -6.4)):
    sp = K.card("票", loc=(x, SY), w=0.85, h=0.85, color=K.WHITE, text_color=INK, size=0.42, z=0.2)
    c.pop(sp, c.A(0, 0.5) + i * 4)
    slips.append(sp)
fun = P.poly((-0.2, 2.3), 0.72, 3, K.YELLOW, math.pi, 0.1)
c.pop(fun, c.A(0, 1.2))
fc = P.chip("filter", (1.45, 2.4), K.GRAY, 0.4, z=0.1)
c.pop(fc, c.A(0, 1.4))
mf = P.framed((3.8, 0.95), 2.3, 2.0, K.BG, K.PURPLE, 0.07, z=-0.02)
c.pop(mf, c.A(0, 1.6))
mc = P.chip("map", (3.8, 2.45), K.PURPLE, 0.4, z=0.1)
c.pop(mc, c.A(0, 1.8))
f0 = c.A(1, -0.55)
# 先頭の1枚：filter の下で止まり、外れる（落ちて ✕）
K.move(slips[0], f0, (-0.2, SY), 22, "SINE", "EASE_IN_OUT")
K.pulse(fun, f0 + 24, 1.25, 8)
K.move(slips[0], f0 + 32, (-0.2, -0.85), 16, "SINE", "EASE_IN")
ngs = P.nomark((-0.2, -0.85), 0.3)
c.pop(ngs, f0 + 46, 8)
P.out(slips[0], f0 + 56)
# 続く3枚：filter を通って map へ。枠の中で形が変わる
for sidx, st, dur in ((1, 30, 40), (2, 38, 42), (3, 46, 44)):
    K.move(slips[sidx], f0 + st, (3.3, SY), dur, "SINE", "EASE_IN_OUT")
    tw = K.dot((3.8, SY), 0.42, K.ORANGE, "票", INK, z=0.3)
    P.out(slips[sidx], f0 + st + dur, 4)
    K.pop_in(tw, f0 + st + dur, 8)
    K.move(tw, f0 + st + dur, (6.5, SY), 20)
    c.add(tw)

# ---- 第7章：33.3% を3つ足すと 99.9%
c = I.chapter(7)
bx = [-5.9, -2.6, 0.7]
bars = []
for i, x in enumerate(bx):
    cd = K.card("", loc=(x, 1.3), w=2.3, h=1.4, color=P.CUST)
    bars.append(cd)
    c.pop(cd, c.A(0, 0.1) + i * 5)
for x in (-4.25, -0.95):
    pl = P.label("＋", (x, 1.3), 0.7, K.WHITE, 900)
    c.pop(pl, c.A(0, 0.5))
eq = P.label("＝", (2.45, 1.3), 0.7, K.WHITE, 900)
c.pop(eq, c.A(0, 0.7))
for i, cd in enumerate(bars[:2]):
    K.text("33.3%", 0.56, (0, 0, 0.05), K.WHITE, 900, parent=cd)
f_fix = c.A(1, 2.3)
K.number_ticker([(1, "33.3%", K.WHITE), (f_fix, "33.4%", K.WHITE)], (0, 0, 0.05), 0.56, parent=bars[2], end=None)
P.shade(bars[2], [(f_fix, K.BLUE)], P.CUST)
K.pulse(bars[2], f_fix, 1.15, 8)
sm = K.card("", loc=(4.9, 1.3), w=2.8, h=1.4, color=P.NGBG)
c.pop(sm, c.A(1, 0.5))
K.number_ticker([(1, "99.9%", K.WHITE), (f_fix + 6, "100%", K.WHITE)], (0, 0, 0.05), 0.68, parent=sm, end=None)
P.shade(sm, [(f_fix + 6, P.OKBG)], P.NGBG)
K.shake(sm, c.A(1, 0.9), 0.14, 6, 2)
K.pulse(sm, f_fix + 6, 1.15, 8)
cap = P.label("丸めると合計が合わない", (0, 2.95), 0.42, K.GRAY, 700)
c.pop(cap, c.A(1, 1.1))
okp = P.tick((6.4, 2.15), 0.3)
c.pop(okp, f_fix + 10, 8)

# ---- 第8章：JUnit 正常・境界・異常
c = I.chapter(8)
rows = []
for i, (lab, y) in enumerate((("正常", 2.3), ("境界", 1.15), ("異常", 0.0))):
    r = K.card("", loc=(0, y), w=7.6, h=0.9, color=K.DARK)
    t = P.label(lab, (-3.2, y), 0.5, K.WHITE, 700, "LEFT")
    ring = K.dot((3.1, y), 0.33, K.GRAY, None, z=0.2)
    inn = K.dot((3.1, y), 0.22, K.DARK, None, z=0.25)
    c.pop(r, c.A(0, 0.2) + i * 6)
    c.pop(t, c.A(0, 0.3) + i * 6)
    c.pop(ring, c.A(0, 0.5) + i * 6, 8)
    c.pop(inn, c.A(0, 0.5) + i * 6, 8)
    rows.append((r, y))
for i, ((r, y), f) in enumerate(zip(rows, (c.A(0, 2.0), c.A(0, 2.8), c.A(0, 3.6)))):
    tk = P.tick((3.1, y), 0.36, z=0.5)
    c.pop(tk, f, 8)
    K.pulse(r, f, 1.04, 8)
jc = P.chip("JUnit", (-5.8, 2.3), K.GRAY, 0.4)
c.pop(jc, c.A(0, 0.5))

# ---- 第9章：共有ノートに2人が同時に +1 → 1
c = I.chapter(9)
note = K.card("", loc=(0, 1.5), w=3.4, h=2.5, color=K.WHITE)
c.pop(note, c.A(0, 0.1))
K.text("共有ノート", 0.4, (0, 0.8, 0.05), INK, 700, parent=note)
f_w = c.A(1, 1.9)
K.number_ticker([(1, "0", INK), (f_w, "1", K.RED)], (0, -0.25, 0.05), 1.5, parent=note, end=None)
pa = P.person((-5.2, 0.9), K.ORANGE, 1.0)
pb = P.person((5.2, 0.9), K.BLUE, 1.0)
c.pop(pa, c.A(1, 0.1))
c.pop(pb, c.A(1, 0.25))
da = K.dot((-3.7, 1.7), 0.48, K.ORANGE, "+1", INK)
db = K.dot((3.7, 1.7), 0.48, K.ORANGE, "+1", INK)
c.pop(da, c.A(1, 0.8), 8)
c.pop(db, c.A(1, 0.8), 8)
K.move(da, c.A(1, 1.2), (-1.55, 1.6), 20)
K.move(db, c.A(1, 1.2), (1.55, 1.6), 20)
P.out(da, f_w - 2, 6)
P.out(db, f_w - 2, 6)
K.shake(note, f_w + 2, 0.14, 6, 2)
K.pulse(note, f_w + 14, 1.06, 8)
bad = P.chip("2のはずが1", (0, -0.35), P.NGBG, 0.46)
c.pop(bad, c.A(1, 2.4))
K.pulse(bad, c.A(1, 2.4) + 12, 1.12, 8)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(20, 0.1))
d.row("before", "Javaは読めない", "q", A(20, 0.5))
d.row("after", "投票の状態（型）", "ok", A(20, 1.8))
d.row("after", "得票率 合計100%", "ok", A(20, 2.9))
sp = P.chip("Springなし", (5.5, 3.15), K.GRAY, 0.4, z=0.4)
d.g.pop(sp, A(20, 3.6))
d.row("after", "テスト", "ok", A(21, 0.4))

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["Javaは、", "TypeScriptとの違いで読む。"])
I.render()
