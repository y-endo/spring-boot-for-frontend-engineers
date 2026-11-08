"""第4部 トランザクションと同時実行：導入動画（part04-intro）

場面構成（行番号は part04-intro.json の lines）
  0      A タイトル
  1-4    B 困りごと : 混んだ日のカレンダー → 3つの事故パネル（51 / 50、主催者のいない投票、空の傘立てと行列）
  5-15   C 第22〜26章（5枚のカード。章ごとのミニ動き）
  16-19  D 前→後 : 3つの事故 ✕ → 守られた3つ ✓、原因はログで見つかる
  20     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import part_intro_kit as P

K = P.K
I = P.Intro(4, "トランザクションと同時実行", [
    (22, "TXの境界"), (23, "プロキシ"), (24, "接続プール"), (25, "残り1枠に2人"), (26, "別の直し方")])
A, E, M = I.A, I.E, I.M
INK = P.INK
PD = K.hexc("#7a52d6")
SLOT = K.hexc("#d9dce8")


def envelope(loc, s=1.0, z=0.3):
    """ORANGE の封筒（リクエスト）。"""
    r = K.card("", loc, 0.95 * s, 0.64 * s, K.ORANGE, z=z)
    for sx in (-1, 1):
        sg = P.seg((sx * 0.47 * s, 0.31 * s), (0, 0.0), 0.07 * s, INK, z=0.08)
        sg.parent = r
    return r


def umbrella(loc, s=1.0, z=0.3):
    """傘（接続）。BLUE の丸い傘面と柄。"""
    root = K.dot(loc, 0.3 * s, K.BLUE, None, z=z)
    for p1, p2 in (((0, -0.25 * s), (0, -0.78 * s)), ((0, -0.78 * s), (0.17 * s, -0.78 * s))):
        sg = P.seg(p1, p2, 0.08 * s, K.WHITE, z=0.0)
        sg.parent = root
    return root


def padlock(loc, s=1.0, z=0.5):
    root = K.card("", loc, 0.8 * s, 0.6 * s, K.YELLOW, z=z)
    sh = K.dot((0, 0.42 * s), 0.26 * s, K.YELLOW, None, z=0.0)
    hl = K.dot((0, 0.42 * s), 0.14 * s, K.DARK, None, z=0.02)
    sh.parent = root
    hl.parent = root
    return root


def magnifier(loc, s=1.0, z=0.4):
    root = K.dot(loc, 0.3 * s, K.WHITE, None, z=z)
    inner = K.dot((0, 0), 0.2 * s, P.BG, None, z=0.05)
    inner.parent = root
    hd = P.seg((0.22 * s, -0.22 * s), (0.62 * s, -0.62 * s), 0.12 * s, K.WHITE, z=0.0)
    hd.parent = root
    return root


def bolt(pts, color=K.RED, w=0.17, z=0.6):
    """稲妻（折れ線）。root を返す。"""
    root = bpy.data.objects.new("bolt", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (0, 0, z)
    for p1, p2 in zip(pts[:-1], pts[1:]):
        sg = P.seg(p1, p2, w, color, z=0.0)
        sg.parent = root
    return root


# ======================================================================
# A タイトル
# ======================================================================
I.title_card()

# ======================================================================
# B 困りごと
# ======================================================================
b = I.trouble()
# 行1：混んだ日のカレンダーと人混み
cal = K.card("", loc=(-2.0, 1.2), w=3.6, h=3.0, color=K.WHITE)
hd_ = K.card("混んだ日", loc=(-2.0, 2.2), w=3.6, h=0.75, color=K.ORANGE, size=0.42, z=0.1)
b.pop(cal, A(1, 0.8))
b.pop(hd_, A(1, 0.95))
cells = []
for r_, y in enumerate((1.4, 0.7)):
    for k_ in range(4):
        hot = (r_ == 0 and k_ == 2)
        cell = K.card("", loc=(-3.05 + 0.7 * k_, y), w=0.6, h=0.5, color=K.hexc("#d6d9e6"), z=0.1)
        b.pop(cell, A(1, 1.15) + (r_ * 4 + k_) * 2, 8)
        cells.append((cell, hot))
hot_cell = [c_ for c_, h_ in cells if h_][0]
P.shade(hot_cell, [(A(1, 1.9), P.NGBG)], K.hexc("#d6d9e6"))
K.pulse(hot_cell, A(1, 1.9), 1.2, 8)
crowd = []
for i, x in enumerate((1.0, 1.7, 2.4, 3.1, 3.8)):
    pr = P.person((x, 1.0 + 0.2 * (i % 2)), K.ORANGE, 0.55)
    b.pop(pr, A(1, 2.0) + i * 3, 8)
    crowd.append(pr)
for o in [cal, hd_] + [c_ for c_, _ in cells] + crowd:
    P.out(o, A(2) - 3, 8)

# 行2：参加者が上限の50人を超える
p1 = K.card("", loc=(-4.9, 1.1), w=4.5, h=3.5, color=K.DARK)
b.pop(p1, A(2, 0.1))
l1 = P.chip("参加者", (-4.9, 2.5), K.GRAY, 0.36, z=0.1)
b.pop(l1, A(2, 0.3))
K.number_ticker([(1, "50 / 50", K.WHITE), (A(2, 1.7), "51 / 50", K.RED)], (0, -0.05, 0.1), 0.95, parent=p1, end=None)
lm = P.chip("上限は50人", (-4.9, -0.1), P.CUST, 0.34, z=0.1)
b.pop(lm, A(2, 0.6))
K.shake(p1, A(2, 1.75), 0.14, 6, 2)
K.pulse(p1, A(2, 1.75) + 14, 1.04, 8)

# 行3：主催者のいない投票
p2 = K.card("", loc=(0.0, 1.1), w=4.5, h=3.5, color=K.DARK)
b.pop(p2, A(3, 0.1))
slip = K.card("投票", loc=(0.0, 1.1), w=3.6, h=2.6, color=K.WHITE, text_color=INK, size=0.4, z=0.1)
slip.children[-1].location = (0, 0.95, 0.05)           # 「投票」の文字を上に寄せる
b.pop(slip, A(3, 0.3))
r1 = K.card("選択肢", loc=(0.0, 1.45), w=2.9, h=0.55, color=K.BLUE, size=0.34, z=0.2)
r2 = K.card("主催者", loc=(0.0, 0.65), w=2.9, h=0.55, color=SLOT, text_color=K.hexc("#8a90aa"), size=0.34, z=0.2)
b.pop(r1, A(3, 0.6))
b.pop(r2, A(3, 0.9))
x2 = P.nomark((1.2, 0.65), 0.22)
b.pop(x2, A(3, 1.7), 8)
K.shake(p2, A(3, 1.75), 0.14, 6, 2)

# 行4：傘立てが空で、行列ができる
p3 = K.card("", loc=(4.9, 1.1), w=4.5, h=3.5, color=K.DARK)
b.pop(p3, A(4, 0.1))
l3 = P.chip("傘（接続）が空", (4.9, 2.5), K.GRAY, 0.34, z=0.1)
b.pop(l3, A(4, 0.3))
for i, x in enumerate((3.5, 4.2, 4.9, 5.6, 6.3)):
    ring_ = K.dot((x, 1.6), 0.27, K.GRAY, None, z=0.1)
    ring2 = K.dot((x, 1.6), 0.17, K.DARK, None, z=0.12)
    b.pop(ring_, A(4, 0.45) + i * 3, 8)
    b.pop(ring2, A(4, 0.45) + i * 3, 8)
for i, x in enumerate((3.4, 4.2, 5.0, 5.8)):
    pr = P.person((x, 0.55), K.YELLOW, 0.5)
    b.pop(pr, A(4, 1.0) + i * 6, 8)
wt = P.chip("待ち", (4.6, -0.2), K.YELLOW, 0.34, INK, z=0.1)
b.pop(wt, A(4, 1.9))
K.shake(p3, A(4, 1.95), 0.14, 6, 2)
I.cam(A(2), to=(0, 0.5), zoom=15.4, dur=24)
I.cam(A(5) - 16, to=(0, 0), zoom=16, dur=14)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---- 第22章：BEGIN…COMMIT の中の2つの INSERT。例外で ROLLBACK → 全部なし。括弧なしでは片方だけ残る
c = I.chapter(22)
fr = P.framed((0, 2.3), 10.2, 1.5, K.BG2, K.BLUE, 0.06)
c.pop(fr, c.A(0, 0.1))
bg = P.chip("BEGIN", (-6.5, 2.3), K.GRAY, 0.36)
cm = P.chip("COMMIT", (6.3, 2.3), K.GRAY, 0.36)
c.pop(bg, c.A(0, 0.3))
c.pop(cm, c.A(0, 0.5))
ins1 = []
for i, x in enumerate((-2.3, 2.3)):
    t = P.chip("INSERT", (x, 2.3), K.BLUE, 0.4, z=0.1)
    c.pop(t, c.A(0, 1.0) + i * 8)
    ins1.append(t)
nb = P.chip("括弧なし", (-6.2, 0.4), K.GRAY, 0.36)
c.pop(nb, c.A(0, 1.5))
ins2 = []
for i, x in enumerate((-2.3, 2.3)):
    t = P.chip("INSERT", (x, 0.4), K.BLUE, 0.4, z=0.1)
    c.pop(t, c.A(0, 1.8) + i * 8)
    ins2.append(t)
bl = bolt([(0.0, 3.15), (-0.4, 2.2), (0.3, 1.5), (-0.3, 0.7), (0.2, -0.3)])
c.pop(bl, c.A(1, 0.1), 6)
K.shake(fr, c.A(1, 0.15), 0.1, 4, 2)
P.out(cm, c.A(1, 0.45), 6)
rb = P.chip("ROLLBACK", (6.3, 2.3), K.GRAY, 0.36)
c.pop(rb, c.A(1, 0.5), 8)
P.out(ins1[0], c.A(1, 0.75), 8)
P.out(ins1[1], c.A(1, 0.8), 8)
none = P.chip("全部なし", (0, 2.3), P.OKBG, 0.44)
c.pop(none, c.A(1, 1.05))
K.pulse(none, c.A(1, 1.05) + 12, 1.12, 8)
P.out(ins2[1], c.A(1, 1.25), 8)
half = P.chip("片方だけ", (2.3, 0.4), P.NGBG, 0.44)
c.pop(half, c.A(1, 1.5))
K.pulse(half, c.A(1, 1.5) + 12, 1.12, 8)
P.out(bl, c.A(1, 0.65), 8)

# ---- 第23章：秘書（プロキシ）を通ると TX の札。自己呼び出しは迂回して付かない
c = I.chapter(23)
sec = K.card("秘書", loc=(-1.0, 1.5), w=2.8, h=1.7, color=K.BLUE, size=0.55, sub="プロキシ")
boss = K.card("社長", loc=(3.9, 1.5), w=2.8, h=1.7, color=P.CUST, size=0.55, sub="Service")
cl = P.chip("Controller", (-5.9, 1.5), K.GRAY, 0.38)
c.pop(sec, c.A(0, 0.1))
c.pop(boss, c.A(0, 0.4))
c.pop(cl, c.A(0, 0.7))
for p1_, p2_, dd in (((-4.4, 1.5), (-2.55, 1.5), 1.0), ((0.55, 1.5), (2.4, 1.5), 1.25)):
    a = K.arrow(p1_, p2_, K.WHITE, 0.09, 0.28)
    K.grow_x(a, c.A(0, dd), 8)
    c.add(a)
ev = envelope((-3.9, 1.5), 0.85, z=0.5)
c.pop(ev, c.A(0, 1.7), 8)
K.move(ev, c.A(0, 1.9), (-1.0, 1.5), 20)
K.pulse(sec, c.A(0, 1.9) + 20, 1.08, 8)
tx = P.chip("TX", (-1.0, 2.75), K.WHITE, 0.4, INK, z=0.5)
c.pop(tx, c.A(0, 2.4), 8)
K.move(ev, c.A(1, 0.1), (2.1, 1.5), 18)
K.move(tx, c.A(1, 0.1), (2.1, 2.3), 18)
K.pulse(boss, c.A(1, 0.1) + 18, 1.08, 8)
P.out(ev, c.A(1, 0.85), 8)
P.out(tx, c.A(1, 0.85), 8)
ut = bolt([(4.7, 0.6), (4.7, -0.2), (3.1, -0.2), (3.1, 0.4)], K.WHITE, 0.09, 0.2)
hd = P.poly((3.1, 0.5), 0.24, 3, K.WHITE, 0.0, 0.0)
hd.parent = ut
c.pop(ut, c.A(1, 1.0), 8)
sc = P.chip("自己呼び出し", (6.4, -0.2), K.GRAY, 0.34)
c.pop(sc, c.A(1, 1.3))
nt = P.nomark((3.9, -0.2), 0.3)
c.pop(nt, c.A(1, 1.6), 8)
nx = P.chip("TXなし", (3.9, -0.85), P.NGBG, 0.38)
c.pop(nx, c.A(1, 1.75))
K.pulse(nx, c.A(1, 1.75) + 12, 1.1, 8)

# ---- 第24章：傘立ての傘（接続）を、遅い人が持ったまま。後ろに行列
c = I.chapter(24)
rk = P.framed((-4.2, 2.0), 4.8, 1.5, K.BG2, K.GRAY, 0.07)
c.pop(rk, c.A(0, 0.1))
rl = P.chip("傘", (-4.2, 3.2), K.GRAY, 0.38)
c.pop(rl, c.A(0, 0.3))
umbs = []
for i, x in enumerate((-5.7, -4.8, -3.9, -3.0)):
    u = umbrella((x, 2.1), 1.0, z=0.3)
    c.pop(u, c.A(0, 0.5) + i * 7, 8)
    umbs.append(u)
for i, x in enumerate((1.8, 3.2, 4.6, 6.0)):
    f = c.A(1, 0.2) + i * 10
    sp_ = P.person((x, 1.8), K.ORANGE, 0.6)
    c.pop(sp_, f, 8)
    K.move(umbs[3 - i], f + 4, (x + 0.7, 2.4), 20)
slow = P.chip("傘を持ったまま", (3.9, 0.55), K.GRAY, 0.38)
c.pop(slow, c.A(1, 1.3))
for i, x in enumerate((-5.8, -4.9, -4.0, -3.1)):
    q = P.person((x, 0.35), K.YELLOW, 0.5)
    c.pop(q, c.A(1, 1.9) + i * 5, 8)
wt = P.chip("待ち", (-1.4, 0.35), K.YELLOW, 0.4, INK)
c.pop(wt, c.A(1, 2.5))
K.pulse(wt, c.A(1, 2.5) + 12, 1.12, 8)

# ---- 第25章：残り1枠に2人。2人とも 49 を見て 51。鍵を掛けると B は待ち、満員で拒否
c = I.chapter(25)
pa = P.person((-6.2, 2.4), K.ORANGE, 0.7)
pb = P.person((-6.2, 0.35), K.ORANGE, 0.7)
c.pop(pa, c.A(0, 0.1))
c.pop(pb, c.A(0, 0.25))
la = P.chip("A", (-5.0, 2.4), P.CUST, 0.38)
lb = P.chip("B", (-5.0, 0.35), P.CUST, 0.38)
c.pop(la, c.A(0, 0.3))
c.pop(lb, c.A(0, 0.4))
pc = K.card("", loc=(0.6, 1.45), w=3.4, h=3.2, color=K.DARK)
c.pop(pc, c.A(0, 0.5))
pl = P.chip("polls", (0.6, 2.65), K.GRAY, 0.38, z=0.1)
c.pop(pl, c.A(0, 0.7))
f1, f2, f3 = c.A(0, 2.3), c.A(1, 0.0), c.A(1, 2.0)
K.number_ticker([(1, "49", K.WHITE), (f1, "51", K.WHITE), (f2, "49", K.WHITE), (f3, "50", K.GREEN)],
                (0, -0.15, 0.1), 1.3, parent=pc, end=None)
P.shade(pc, [(f1, P.NGBG), (f2, K.DARK)], K.DARK)
k49 = []
for y in (2.4, 0.35):
    t = P.chip("49", (-0.9, y), K.WHITE, 0.4, INK, z=0.5)
    c.pop(t, c.A(0, 1.3), 8)
    K.move(t, c.A(0, 1.5), (-3.4, y), 22)
    P.out(t, c.A(1, 0.1), 8)
    k49.append(t)
K.shake(pc, f1 + 1, 0.14, 6, 2)
lk = padlock((2.2, 2.7), 1.0)
c.pop(lk, c.A(1, 0.25))
K.pulse(lk, c.A(1, 0.25) + 12, 1.15, 8)
wt = P.chip("待ち", (-3.1, 0.35), K.YELLOW, 0.4, INK)
c.pop(wt, c.A(1, 1.1))
P.out(wt, f3, 8)
fu = P.chip("満員 409", (-3.0, 0.35), P.OKBG, 0.4)
c.pop(fu, f3 + 9)
tka = P.tick((-3.3, 2.4), 0.3)
c.pop(tka, f3 + 2, 8)

# ---- 第26章：道具箱から4つの道具。100回OK は根拠にならない。ラッチで同時スタートして衝突を起こす
c = I.chapter(26)
tb = K.card("道具箱", loc=(-5.6, 1.4), w=2.6, h=1.8, color=K.GRAY, size=0.5)
c.pop(tb, c.A(0, 0.2))
tools = []
for lab, (tx_, ty_), t in (("version", (0.9, 2.3), 0.15), ("条件付きUPDATE", (0.9, 1.0), 0.5),
                           ("ON CONFLICT", (5.0, 2.3), 1.0), ("分離レベル", (5.0, 1.0), 1.6)):
    ch = P.chip(lab, (-5.6, 1.4), K.BLUE, 0.4, z=0.1)
    c.pop(ch, c.A(1, t), 8)
    K.move(ch, c.A(1, t) + 4, (tx_, ty_), 18)
    tools.append(ch)
f_c = c.A(2, -0.05)
for o in tools + [tb]:
    P.out(o, f_c, 8)
ok = P.chip("100回OK", (-3.4, 2.75), P.OKBG, 0.4)
c.pop(ok, c.A(2, 0.15))
okt = P.tick((-1.9, 2.75), 0.28)
c.pop(okt, c.A(2, 0.65), 8)
nj = P.chip("根拠にならない", (1.9, 2.75), P.NGBG, 0.4)
c.pop(nj, c.A(2, 1.15))
njx = P.nomark((-4.5, 3.1), 0.28)
c.pop(njx, c.A(2, 1.5), 8)
sl_ = P.seg((-3.2, -0.7), (-3.2, 1.3), 0.09, K.WHITE, z=0.0)
c.pop(sl_, c.A(2, 1.3))
lt = P.chip("ラッチ", (-3.2, 1.65), K.GRAY, 0.32)
c.pop(lt, c.A(2, 1.45))
tg = K.card("polls", loc=(4.6, 0.45), w=2.2, h=1.6, color=PD, size=0.44)
c.pop(tg, c.A(2, 1.5))
r1_ = P.person((-4.4, 1.0), K.ORANGE, 0.55)
r2_ = P.person((-4.4, -0.1), K.ORANGE, 0.55)
c.pop(r1_, c.A(2, 1.7), 8)
c.pop(r2_, c.A(2, 1.7), 8)
K.move(r1_, c.A(2, 2.0), (2.5, 0.95), 20)
K.move(r2_, c.A(2, 2.0), (2.5, -0.15), 20)
st1 = P.poly((3.0, 0.4), 0.75, 4, K.RED, math.pi / 4, 0.7)
st2 = P.poly((3.0, 0.4), 0.75, 4, K.RED, 0.0, 0.71)
for o in (st1, st2):
    c.pop(o, c.A(2, 2.55), 8)
K.pulse(st1, c.A(2, 2.55) + 8, 1.15, 8)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(16, 0.1))
d.row("before", "51 / 50", "ng", A(16, 0.5))
d.row("before", "主催者のいない投票", "ng", A(16, 0.9))
d.row("before", "接続が足りない", "ng", A(16, 1.3))
d.row("after", "20人同時 → 50 / 50", "ok", A(16, 2.0), k=0)
d.row("after", "1人1票 → 必ず1行", "ok", A(17, 0.5), k=1)
d.row("after", "全部成功か全部なし", "ok", A(18, 0.9), k=2)
mg = magnifier((2.3, -1.0), 0.8)
d.g.pop(mg, A(19, 0.4))
lg = P.chip("原因はログで見つかる", (4.7, -1.12), K.WHITE, 0.38, INK)
d.g.pop(lg, A(19, 0.6))
K.pulse(lg, A(19, 0.6) + 12, 1.1, 8)

# ======================================================================
# E 最後の1行
# ======================================================================
I.final("同時に来ても、約束は破らせない。")
I.render()
