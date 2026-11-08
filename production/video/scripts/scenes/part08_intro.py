"""第8部 JPAを理解して使い分ける：導入動画（part08-intro）

場面構成（行番号は part08-intro.json の lines）
  0-1    A タイトル → 本線（太い矢印）から枝が分かれ、「別の実験」の脇道へ
  2-4    B 困りごと : 左の「JdbcClient」は中の SQL が見える箱、右の「JPA」は蓋の閉じた箱（中は「？」）、周りに人形
  5-6    C 第39章 JPAが肩代わり : 永続コンテキストのカゴ → Entity の値が変わる → save は押さないのに COMMIT で UPDATE
  7-10   C 第40章 N+1 : SQLログに短冊が流れ 1→101（RED）→ fetch join で 3 以下（GREEN）→ LazyInitializationException
  11-13  C 第41章 使い分け : 天秤（JdbcClient / JPA）→ 罠の旗が5本立つ
  14-16  D 前→後 : 蓋の閉じた箱 → 蓋が開き、虫眼鏡で SQL を数える（101 → 3）。本線は JdbcClient のまま
  17     E 最後の1行

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
I = P.Intro(8, "JPAを理解して使い分ける", [(39, "JPAが肩代わり"), (40, "N+1"), (41, "使い分け")])
A, E, M = I.A, I.E, I.M
INK = P.INK


def pops(g, objs, f, step=3):
    for i, o in enumerate(objs):
        g.pop(o, f + i * step)
    return objs


def strip(loc, w, h=0.5, color=K.BLUE, label="", size=0.3, z=0.1):
    return K.card(label, loc=loc, w=w, h=h, color=color, size=size, z=z)


# ======================================================================
# A タイトル → 本線から「別の実験」の脇道へ
# ======================================================================
I.title_card()
ga = P.Group(I, A(2) - 3)
main = K.arrow((-6.6, 0.9), (6.8, 0.9), K.BLUE, 0.34, 0.8)
K.grow_x(main, A(1, 0.2), 16)
ga.add(main)
ml = P.chip("QuickVote本線", (-3.6, 1.95), K.BLUE, 0.46, z=0.1)
ga.pop(ml, A(1, 0.4))
br1 = P.seg((1.0, 0.9), (2.6, -0.35), 0.3, K.GRAY)
br2 = P.seg((2.6, -0.35), (6.3, -0.35), 0.3, K.GRAY)
bj = K.dot((2.6, -0.35), 0.24, K.GRAY, None, z=0.0)
ga.pop(br1, A(1, 1.4), 8)
ga.pop(bj, A(1, 1.7), 6)
ga.pop(br2, A(1, 1.8), 8)
bt = P.chip("別の実験", (4.5, 0.55), K.WHITE, 0.46, INK, z=0.1)
ga.pop(bt, A(1, 2.2))
K.pulse(bt, A(1, 2.2) + 12, 1.12, 8)
walker = K.dot((-0.5, 0.9), 0.26, K.ORANGE, None, z=0.3)
ga.pop(walker, A(1, 1.0), 6)
K.move(walker, A(1, 1.5), (1.0, 0.9), 8, "LINEAR")
K.move(walker, A(1, 1.5) + 8, (2.6, -0.35), 12, "LINEAR")
K.move(walker, A(1, 1.5) + 20, (6.0, -0.35), 36, "LINEAR")

# ======================================================================
# B 困りごと：見える箱と、蓋の閉じた箱
# ======================================================================
b = I.trouble()
PW, PH, PY = 6.2, 3.3, 1.0
LX, RX = -3.9, 3.9
lp = P.framed((LX, PY), PW, PH, K.DARK, K.BLUE, 0.07, 0.3)
b.pop(lp, A(2, 0.3))
lh = P.chip("JdbcClient", (LX, PY + 1.2), K.BLUE, 0.42, z=0.1)
b.pop(lh, A(2, 0.5))
for i, y in enumerate((1.45, 0.85, 0.25)):
    s = strip((LX, y), 4.2, 0.46, K.BLUE, "SQL", 0.3)
    b.pop(s, A(2, 1.6) + i * 8, 8)
rp = P.framed((RX, PY), PW, PH, P.CUST, K.GRAY, 0.07, 0.3)
b.pop(rp, A(2, 3.0))
rh = P.chip("JPA", (RX, PY + 1.2), K.GRAY, 0.42, z=0.1)
b.pop(rh, A(2, 3.2))
lid = K.card("", (RX, PY + 0.35), PW - 0.5, 0.55, P.FOG, size=0.3, z=0.1)
b.pop(lid, A(2, 3.5), 8)
# 行3：現場でよく見る → 右の箱のまわりに人形
ppos = [(1.6, 3.5), (3.9, 3.55), (6.2, 3.5), (1.6, -1.05), (3.9, -1.1), (6.2, -1.05)]
for i, loc in enumerate(ppos):
    p = P.person(loc, K.ORANGE, 0.55)
    b.pop(p, A(3, 0.4) + i * 6)
# 行4：SQLを書かない → 蓋に「？」
q = P.qmark((RX, PY - 0.35), 0.75, z=0.4)
b.pop(q, A(4, 0.4))
K.pulse(q, A(4, 0.4) + 12, 1.2, 8)
K.pulse(q, A(4, 1.6), 1.15, 8)
K.bob(q, A(4, 2.0), A(5) - 8, 0.1, 24)

I.cam(A(4, 0.2), to=(0.3, 0.4), zoom=15.2, dur=30)
I.cam(A(5) - 14, to=(0, 0), zoom=16, dur=14)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---- 第39章：永続コンテキストが肩代わり（save なしで UPDATE）
c = I.chapter(39)
BX, BY = -3.7, 1.0
basket = P.framed((BX, BY), 5.2, 3.0, K.DARK, K.GRAY, 0.07, 0.3)
c.pop(basket, c.A(0, 0.1))
bh = P.chip("永続コンテキスト", (BX, BY + 1.1), K.GRAY, 0.38, z=0.1)
c.pop(bh, c.A(0, 0.3))
ent = K.card("", (BX, BY - 0.15), 2.8, 1.5, K.WHITE, size=0.4, z=0.1)
c.pop(ent, c.A(0, 0.7))
K.text("Entity", 0.4, (0, 0.42, 0.05), INK, 900, parent=ent)
f_chg = c.A(1, 0.6)
K.number_ticker([(1, "3", INK), (f_chg, "4", K.RED)], (0, -0.28, 0.05), 0.8, parent=ent, end=None)
tx = P.chip("トランザクション内", (BX, BY - 1.85), P.CUST, 0.34, z=0.1)
c.pop(tx, c.A(0, 1.6))
# save（灰色のまま押されない）
sv = K.card("save", (0.9, 2.45), 2.0, 0.9, P.CUST, text_color=K.GRAY, size=0.42)
c.pop(sv, c.A(0, 2.0))
svn = P.chip("呼ばない", (0.9, 1.65), K.GRAY, 0.32, z=0.1)
c.pop(svn, c.A(1, 1.7))
# COMMIT
cm = K.card("COMMIT", (0.9, 0.1), 2.4, 0.9, K.BLUE, size=0.42)
c.pop(cm, c.A(1, 2.3))
# DB
db = K.card("DB", (5.8, 1.1), 1.9, 2.4, K.PURPLE, size=0.6)
c.pop(db, c.A(0, 2.4))
# 行6：値が書き換わる → COMMIT で UPDATE の短冊
K.pulse(ent, f_chg, 1.08, 8)
K.shake(ent, f_chg + 2, 0.1, 4, 2)
up = strip((BX + 0.3, BY - 0.15), 2.2, 0.62, K.BLUE, "UPDATE", 0.34, z=0.5)
f_up = c.A(1, 3.1)
c.pop(up, f_up - 8, 6)
K.move(up, f_up, (BX + 2.6, BY - 0.15), 8, "SINE", "EASE_OUT")
K.move(up, f_up + 10, (3.5, BY + 0.1), 18, "SINE", "EASE_IN_OUT")
K.pulse(cm, f_up - 10, 1.15, 8)
P.out(up, f_up + 30, 6)
K.pulse(db, f_up + 30, 1.1, 8)
okd = P.tick((6.6, 2.2), 0.3)
c.pop(okd, f_up + 32, 8)

# ---- 第40章：N+1。SQLが101本 → fetch join で3本以下 → LazyInitializationException
c = I.chapter(40)
s1 = c.sub()
CXL, CYL = -4.2, 1.5
cnt = K.card("", (CXL, CYL), 3.4, 2.6, K.DARK)
s1.pop(cnt, c.A(0, 0.1))
K.text("SQLの本数", 0.4, (0, 0.85, 0.05), K.WHITE, 700, parent=cnt)
f101 = c.A(1, 2.2)
f3 = c.A(2, 1.0)
seq = [(1, "0", K.WHITE), (c.A(1, 0.15), "1", K.WHITE)]
for j, v in enumerate((9, 21, 38, 57, 76, 93)):
    seq.append((c.A(1, 0.7) + j * 8, str(v), K.WHITE))
seq.append((f101, "101", K.WHITE))
seq.append((f3, "3", K.WHITE))
K.number_ticker(seq, (0, -0.45, 0.05), 1.1, parent=cnt, end=None)
P.shade(cnt, [(f101, P.NGBG), (f3, P.OKBG)], K.DARK)
K.shake(cnt, f101, 0.14, 6, 2)
# 応答時間の棒
rl = P.label("応答時間", (CXL, -0.15), 0.34, K.GRAY, 700, z=0.2)
s1.pop(rl, c.A(0, 0.4), 8)
bar_r, fill_r = K.progress_bar((CXL, -0.6), 3.4, 0.3, K.RED, K.DARK, z=0.1)
s1.pop(bar_r, c.A(0, 0.5), 8)
K.bar_to(fill_r, c.A(1, 0.3), 1.0, int(30 * 1.9))
P.out(bar_r, f3 - 6, 6)
bar_g, fill_g = K.progress_bar((CXL, -0.6), 3.4, 0.3, K.GREEN, K.DARK, z=0.2)
s1.pop(bar_g, f3 - 6, 8)
K.bar_to(fill_g, f3, 0.12, 14)
# ログの枠
LGX, LGY = 3.0, 1.2
lg = K.card("", (LGX, LGY), 7.4, 3.9, K.DARK)
s1.pop(lg, c.A(0, 0.1))
lgh = P.chip("SQLログ", (LGX - 2.7, LGY + 1.55), K.GRAY, 0.34, z=0.1)
s1.pop(lgh, c.A(0, 0.4))
first = strip((LGX, LGY + 1.0), 5.8, 0.42, K.PURPLE, "一覧", 0.3, z=0.1)
s1.pop(first, c.A(1, 0.1), 8)
# 滝のように流れる短冊
rnd = random.Random(8)
rows = 16
pitch = 0.135
ytop = LGY + 0.5
strips = []
for r_ in range(rows):
    yy = ytop - (rows - 1 - r_) * pitch      # 下から積み上がる
    ww = 5.4 + rnd.random() * 0.4
    s = K.plane("lg", ww, 0.09, (0, 0, 0.12), K.BLUE if r_ % 5 else K.hexc("#6aa3ff"))   # K.plane は原点が世界の (0,0)
    f = c.A(1, 0.6) + r_ * 4
    K.pop_in(s, f, 5)
    K._key(s, "location", f, (LGX, ytop + 1.0, 0), "SINE", "EASE_IN")
    K._key(s, "location", f + 9, (LGX, yy, 0), "SINE", "EASE_IN")
    strips.append((s, f))
    s1.add(s)
for s, f in strips:
    P.out(s, f3 - 8 + (f % 3), 6)
P.out(first, f3 - 8, 6)
# 行9：fetch join の太い1本
fj = K.card("fetch join", (LGX, LGY + 0.3), 5.6, 1.0, K.BLUE, size=0.46, z=0.1)
s1.pop(fj, f3 - 2)
dt = P.chip("DTO", (LGX + 2.0, LGY - 0.75), K.PURPLE, 0.36, z=0.2)
s1.pop(dt, c.A(2, 2.0), 8)
okf = P.tick((LGX + 2.6, LGY + 0.9), 0.3, z=0.4)
s1.pop(okf, f3 + 14, 8)
# 行10：LazyInitializationException
s2 = c.sub()
for o in (cnt, rl, bar_g, lg, lgh, fj, dt, okf):
    P.out(o, c.A(3, 0.0) - 6, 6)
tb = P.framed((-3.2, 1.2), 5.2, 3.0, K.DARK, K.GRAY, 0.07, 0.3)
s2.pop(tb, c.A(3, 0.0))
tbh = P.chip("トランザクション", (-3.2, 2.35), K.GRAY, 0.36, z=0.1)
s2.pop(tbh, c.A(3, 0.15))
en2 = K.card("Entity", (-3.2, 1.2), 2.6, 1.3, K.WHITE, text_color=INK, size=0.46, z=0.1)
s2.pop(en2, c.A(3, 0.3))
who = P.person((5.9, 1.2), K.ORANGE, 0.9)
s2.pop(who, c.A(3, 0.5))
ar = K.arrow((5.0, 1.2), (-0.35, 1.2), K.ORANGE, 0.16, 0.45, z=0.05)
K.grow_x(ar, c.A(3, 1.0), 14)
s2.add(ar)
lz = K.card("LazyInitializationException", (3.0, 3.25), 6.6, 0.95, P.NGBG, size=0.34, z=0.2)
s2.pop(lz, c.A(3, 1.6))
K.shake(lz, c.A(3, 1.6) + 10, 0.16, 6, 2)
nm = P.nomark((-0.55, 1.2), 0.34)
s2.pop(nm, c.A(3, 1.55), 8)
K.shake(en2, c.A(3, 1.7), 0.1, 4, 2)

# ---- 第41章：天秤 → 罠の旗
c = I.chapter(41)
sb = c.sub()
# 天秤：支柱・台・梁（梁と皿は同じ親の下で少し傾く）
post = K.plane("post", 0.22, 2.6, (0, 0, 0.0), K.GRAY)
post.location = (0, 0.8, 0)
base = K.rounded_rect("base", 2.6, 0.3, (0, -0.55, 0.0), K.GRAY, 0.12)
sb.pop(post, c.A(0, 0.1))
sb.pop(base, c.A(0, 0.1))
beam = bpy.data.objects.new("beam_root", None)
bpy.context.scene.collection.objects.link(beam)
beam.location = (0, 2.2, 0.05)
bm = K.plane("beamplane", 7.2, 0.2, (0, 0, 0), K.WHITE)
bm.parent = beam
pans = {}
for side, x, lab, col in (("L", -3.4, "JdbcClient", K.BLUE), ("R", 3.4, "JPA", P.CUST)):
    ropeL = K.plane("rope", 0.06, 0.9, (x, -0.5, -0.01), K.GRAY)
    ropeL.parent = beam
    pan = K.card("", (x, -1.6), 3.4, 1.5, col, size=0.4)
    pan.parent = beam
    pan.location = (x, -1.6, 0.05)
    K.text(lab, 0.4, (0, 0.38, 0.05), K.WHITE, 700, parent=pan)
    pans[side] = pan
sb.pop(beam, c.A(0, 0.3))
# 皿に載るチップ（梁の親の外に置いて自前で動かす）
chipL = P.chip("SQLが見える", (0, -0.25), K.WHITE, 0.38, INK, z=0.15)
chipR = P.chip("手間が減る", (0, -0.25), K.WHITE, 0.38, INK, z=0.15)
chipL.parent = pans["L"]
chipR.parent = pans["R"]
sb.pop(chipL, c.A(1, 0.2))
sb.pop(chipR, c.A(1, 0.9))
# 梁をゆらす
K._key(beam, "rotation_euler", c.A(1, 0.0), (0, 0, 0), "SINE", "EASE_IN_OUT")
K._key(beam, "rotation_euler", c.A(1, 0.7), (0, 0, 0.06), "SINE", "EASE_IN_OUT")
K._key(beam, "rotation_euler", c.A(1, 1.4), (0, 0, -0.06), "SINE", "EASE_IN_OUT")
K._key(beam, "rotation_euler", c.A(1, 2.2), (0, 0, 0), "SINE", "EASE_IN_OUT")
# 行13：罠の旗
sc2 = c.sub()
for o in (post, base, beam):
    P.out(o, c.A(2, 0.0) - 6, 6)
paper = K.card("", (0, 1.2), 7.0, 3.3, K.DARK, z=0.0)
sc2.pop(paper, c.A(2, 0.0))
for i, y in enumerate((2.55, 2.15)):
    ln = K.plane("ln", 5.0 - 0.7 * (i % 2), 0.12, (0, 0, 0.05), K.hexc("#8d96b8"))
    ln.parent = paper
    ln.location = (-0.3 - 0.35 * (i % 2), y - 1.2, 0)
pc = P.chip("AIが書いたJPAコード", (-2.2, 3.15), K.GRAY, 0.34, z=0.1)
sc2.pop(pc, c.A(2, 0.15))
for i in range(5):
    fl = P.flag((-2.8 + i * 1.4, -0.05), 0.85, K.RED)
    sc2.pop(fl, c.A(2, 0.5) + i * 14, 8)
tp = P.chip("罠 ×5", (3.0, 3.15), P.NGBG, 0.4, z=0.1)
sc2.pop(tp, c.A(2, 1.8))
K.pulse(tp, c.A(2, 1.8) + 12, 1.12, 8)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(14, 0.1))
# 前：蓋の閉じた箱
cb = K.card("", (-3.85, 1.55), 3.2, 1.5, P.CUST, z=0.1)
d.g.pop(cb, A(14, 0.4))
cbl = K.card("", (-3.85, 2.0), 3.2, 0.5, P.FOG, z=0.15)
d.g.pop(cbl, A(14, 0.45))
cq = P.qmark((-3.85, 1.35), 0.5, z=0.5)
d.g.pop(cq, A(14, 0.7))
K.pulse(cq, A(14, 0.7) + 12, 1.15, 8)
d.row("before", "中が見えない", "q", A(14, 1.0), k=2)
# 後：蓋が開き、虫眼鏡で SQL を数える
ob = K.card("", (3.0, 1.35), 3.0, 1.4, P.CUST, z=0.1)
d.g.pop(ob, A(14, 2.6))
olid = K.card("", (2.2, 2.45), 1.8, 0.4, P.FOG, z=0.15)
olid.rotation_euler = (0, 0, 0.55)
d.g.pop(olid, A(14, 2.8))
for i, y in enumerate((1.75, 1.3, 0.85)):
    s = strip((3.0, y), 2.4, 0.34, K.BLUE, "", 0.2, z=0.2)
    d.g.pop(s, A(14, 3.0) + i * 6, 8)
lens = K.dot((5.2, 1.85), 0.6, K.WHITE, None, z=0.4)
lens_in = K.dot((5.2, 1.85), 0.46, K.hexc("#273055"), None, z=0.45)
handle = P.seg((5.62, 1.43), (6.05, 1.0), 0.2, K.WHITE, z=0.4)
for o in (lens, lens_in, handle):
    d.g.pop(o, A(15, 0.2), 8)
K.move(lens, A(15, 0.9), (3.5, 1.4), 20)
K.move(lens_in, A(15, 0.9), (3.5, 1.4), 20)
K.move(handle, A(15, 0.9), (3.92, 0.98), 20)
d.row("after", "101 → 3", "ok", A(15, 1.2), k=2)
bl = P.chip("QuickVote本線はJdbcClientのまま", (3.85, -1.15), K.BLUE, 0.34, z=0.1)
d.g.pop(bl, A(16, 0.2))
K.pulse(bl, A(16, 0.2) + 12, 1.1, 8)

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["JPAは、", "発行されるSQLを数えて使う。"])
I.render()
