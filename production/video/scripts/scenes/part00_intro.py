"""第0部 はじめに：導入動画（part00-intro）

場面構成（行番号は part00-intro.json の lines）
  0      A タイトル
  1-3    B 困りごと : 霧の分かれ道 → 霧の中の旗 → 空のノートPC（YELLOW の「？」を3つ）
  4-10   C 第0章 歩き方 : 道と4つの手順 → 扱わない範囲と200時間 → AIと学ぶ3原則 → QuickVote
  11-15  C 第1章 環境を整える : 4つのタイル → Hello と DB の確認 → 鎖でつながり、詰まる
  16-17  D 前→後
  18     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import part_intro_kit as P

K = P.K
I = P.Intro(0, "はじめに", [(0, "歩き方"), (1, "環境を整える")])
A, E, M = I.A, I.E, I.M
INK = P.INK


def pops(g, objs, f, step=3):
    for i, o in enumerate(objs):
        g.pop(o, f + i * step)
    return objs


# ======================================================================
# A タイトル
# ======================================================================
I.title_card()

# ======================================================================
# B 困りごと
# ======================================================================
b = I.trouble()
me = P.person((-5.9, 0.3), K.ORANGE, 1.0)
b.pop(me, A(1, 0.2))

# 行1：霧の分かれ道。先の道は見えない
trunk = P.seg((-5.1, 0.3), (-2.4, 0.3), 0.5, P.ROAD)
up = P.seg((-2.4, 0.3), (1.9, 2.1), 0.5, P.ROAD)
down = P.seg((-2.4, 0.3), (1.9, -0.7), 0.5, P.ROAD)
joint = K.dot((-2.4, 0.3), 0.25, P.ROAD, None, z=0.0)
for o, d in ((trunk, 0.5), (joint, 0.9), (up, 1.1), (down, 1.3)):
    b.pop(o, A(1, d))
fog1 = []
for dx, dy, r in ((-0.5, 1.0, 0.9), (0.5, -0.3, 0.9), (0.6, 1.0, 1.0), (-0.3, 0.1, 0.95), (1.3, 0.4, 0.85)):
    fog1.append(K.dot((2.3 + dx, 0.7 + dy), r, P.FOG, None, z=0.4 + 0.01 * len(fog1)))
pops(b, fog1, A(1, 1.5), 3)
for i, o in enumerate(fog1):
    K.bob(o, A(1, 1.9) + i * 4, A(3, 0.0), 0.08, 36)
q1 = P.qmark((2.3, 0.75), 0.6, z=0.9)
b.pop(q1, A(1, 2.2))
K.pulse(q1, A(1, 2.2) + 12, 1.2, 8)

# 行2：道の終点の旗も霧の中
fl = P.flag((5.6, -0.3), 1.0, K.GREEN)
b.pop(fl, A(2, 0.3))
dl = K.dashed_line((3.7, 0.5), (4.8, 0.2), K.GRAY, 0.07)
b.pop(dl, A(2, 0.5))
fog2 = []
for dx, dy, r in ((-0.1, 0.4, 0.95), (0.7, 1.3, 0.9), (0.8, 0.1, 0.85), (-0.5, 1.3, 0.7)):
    fog2.append(K.dot((5.6 + dx, -0.3 + dy), r, P.FOG, None, z=0.4 + 0.01 * len(fog2)))
pops(b, fog2, A(2, 1.0), 3)
for i, o in enumerate(fog2):
    K.bob(o, A(2, 1.4) + i * 4, A(3, 0.0), 0.08, 36)
q2 = P.qmark((5.6, 0.95), 0.6, z=0.9)
b.pop(q2, A(2, 1.5))
K.pulse(q2, A(2, 1.5) + 12, 1.2, 8)

# 行3：空のノートPC。環境がない
for o in [trunk, up, down, joint, q1, dl, fl, q2] + fog1 + fog2:
    P.out(o, A(3) - 2, 8)
K.move(me, A(3), (-4.9, 0.3), 14)
desk = K.card("", (0.4, -0.66), 9.6, 0.34, P.ROAD, z=-0.03)
b.pop(desk, A(3, 0.1))
base = K.card("", (0.4, -0.4), 5.4, 0.3, K.GRAY)
screen = P.framed((0.4, 0.95), 4.6, 2.4, K.BG2, K.GRAY, 0.08)
b.pop(base, A(3, 0.3))
b.pop(screen, A(3, 0.5))
scr_t = P.label("環境なし", (0.4, 0.95), 0.62, K.WHITE, 900, z=0.2)
b.pop(scr_t, A(3, 1.1))
q3 = P.qmark((3.2, 2.35), 0.6)
b.pop(q3, A(3, 1.9))
K.pulse(q3, A(3, 1.9) + 12, 1.2, 8)
I.cam(A(3), to=(0, 0.4), zoom=14.8, dur=24)
I.cam(A(4) - 6, to=(0, 0), zoom=16, dur=16)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()
c0 = I.chapter(0)

# ---- 第0章 行4-5：歩き方。霧が晴れた道と4つの手順
s1 = c0.sub()
rd = P.person((-6.3, 0.95), K.ORANGE, 0.8)
s1.pop(rd, c0.A(0, 0.1))
road = K.dashed_line((-5.4, 1.0), (5.5, 1.0), K.GRAY, 0.1)
s1.pop(road, c0.A(0, 0.4))
goal = P.flag((6.3, 0.2), 0.9, K.GREEN)
s1.pop(goal, c0.A(0, 0.9))
XS = [-4.05, -1.35, 1.35, 4.05]
steps = []
for x, lab in zip(XS, ("決める", "作る", "壊す", "確かめる")):
    steps.append(K.card(lab, loc=(x, 1.0), w=2.1, h=1.3, color=P.CUST, size=0.5))
for i, o in enumerate(steps):
    s1.pop(o, c0.A(1, 0.3) + i * 5)
for i in range(3):
    ar = K.arrow((XS[i] + 1.1, 1.0), (XS[i + 1] - 1.1, 1.0), K.WHITE, 0.09, 0.28)
    K.grow_x(ar, c0.A(1, 0.45) + i * 5, 8)
    s1.add(ar)
for i, fr in enumerate((0.45, 0.57, 0.70, 0.80)):
    f = c0.M(1, fr)
    P.shade(steps[i], [(f, K.BLUE)], P.CUST)
    K.pulse(steps[i], f, 1.15, 8)

# ---- 行6：扱わない範囲 → 約200時間
f6 = c0.A(2)
s1.out(f6 - 2)
s2 = c0.sub()
frame = P.framed((-3.3, 1.15), 7.4, 3.5, K.BG2, K.GRAY, 0.06)
s2.pop(frame, f6 + 2)
ft = P.label("扱わない範囲", (-3.3, 2.5), 0.42, K.GRAY, 700)
s2.pop(ft, f6 + 4)
chips = []
for lab, (x, y) in (("Kubernetes", (-5.0, 1.6)), ("Kafka", (-1.9, 1.6)),
                    ("マイクロサービス", (-5.0, 0.5)), ("WebFlux", (-1.7, 0.5))):
    chips.append(P.chip(lab, (x, y), P.CUST, 0.42))
for i, o in enumerate(chips):
    s2.pop(o, f6 + 8 + i * 5)
crosses = []
for i, o in enumerate(chips):
    cx = P.nomark((o.location[0] + o["w"] / 2 - 0.05, o.location[1] + 0.35), 0.28)
    s2.pop(cx, c0.A(2, 1.2) + i * 8, 8)
    crosses.append(cx)
f6b = c0.A(2, 2.6)
s2.out(f6b - 2)
s3 = c0.sub()
CL = (-1.6, 1.3)
ring = K.dot(CL, 1.35, K.WHITE, None, z=0.3)
inner = K.dot(CL, 1.15, K.BG2, None, z=0.34)
hub = K.dot(CL, 0.1, K.WHITE, None, z=0.5)
mh = K.bpy.data.objects.new("mhand", None)
K.bpy.context.scene.collection.objects.link(mh)
mh.location = (CL[0], CL[1], 0.4)
mp = K.plane("mhp", 0.1, 0.95, (0, 0.47, 0), K.WHITE)
mp.parent = mh
hh = K.bpy.data.objects.new("hhand", None)
K.bpy.context.scene.collection.objects.link(hh)
hh.location = (CL[0], CL[1], 0.42)
hp = K.plane("hhp", 0.14, 0.6, (0, 0.3, 0), K.ORANGE)
hp.parent = hh
for o in (ring, inner, mh, hh, hub):
    s3.pop(o, f6b + 2, 10)
P.spin(mh, f6b + 8, c0.E(2, 0.0), 6.0)
P.spin(hh, f6b + 8, c0.E(2, 0.0), 0.5)
t200 = P.label("約200時間", (3.3, 1.3), 1.0, K.WHITE, 900)
s3.pop(t200, c0.M(2, 0.8))
K.pulse(t200, c0.M(2, 0.8) + 12, 1.1, 8)

# ---- 行7-8：AIと学ぶ3原則
f7 = c0.A(3)
s3.out(f7 - 3)
s4 = c0.sub()
cap = P.label("AIと学ぶ原則", (0, 2.75), 0.45, K.GRAY, 700)
s4.pop(cap, f7 + 2)
pr = []
for x, lab in zip((-4.0, 0.0, 4.0), ("予測", "動かす", "説明できる")):
    pr.append(K.card(lab, loc=(x, 1.2), w=3.4, h=1.6, color=P.CUST, size=0.62))
for i, o in enumerate(pr):
    s4.pop(o, f7 + 6 + i * 6)
for i, f in enumerate((c0.M(3, 0.62), c0.A(4, 0.3), c0.M(4, 0.58))):
    P.shade(pr[i], [(f, K.BLUE)], P.CUST)
    K.pulse(pr[i], f, 1.12, 8)
ng = P.chip("説明できないコード", (4.0, -0.5), P.CUST, 0.4)
s4.pop(ng, c0.M(4, 0.6))
ngx = P.nomark((4.0 - ng["w"] / 2 - 0.05, -0.5), 0.3)
s4.pop(ngx, c0.M(4, 0.6) + 6)
K.shake(ng, c0.M(4, 0.6) + 14, 0.1, 4, 2)

# ---- 行9-10：QuickVote
f9 = c0.A(5)
s4.out(f9 - 3)
s5 = c0.sub()
board = K.card("", (-2.4, 1.15), 6.8, 3.6, K.DARK)
s5.pop(board, f9 + 2)
bt = P.label("QuickVote", (-2.4, 2.45), 0.6, K.WHITE, 900)
s5.pop(bt, f9 + 8)
bars = []
for i, (y, fr) in enumerate(((1.5, 0.72), (0.65, 0.45), (-0.2, 0.28))):
    bar, fill = K.progress_bar((-2.4, y), 5.4, 0.42, K.BLUE, K.BG)
    s5.pop(bar, f9 + 14 + i * 6)
    K.bar_to(fill, f9 + 40 + i * 10, fr, 36)
    bars.append((bar, fill))
f10 = c0.A(6)
for i in range(3):
    pp = P.person((2.9 + i * 1.5, -0.1), K.ORANGE, 0.6)
    s5.pop(pp, f10 + 6 + i * 6)
K.bar_to(bars[0][1], f10 + 24, 0.88, 24)
code = K.card("参加コード", loc=(4.4, 2.35), w=3.9, h=0.95, color=K.WHITE, text_color=INK, size=0.5)
s5.pop(code, c0.A(6, 1.3))
boxes = []
for i in range(8):
    bx = K.rounded_rect("cell", 0.38, 0.5, (2.75 + i * 0.5, 1.4, 0.0), K.GRAY, 0.1)
    s5.pop(bx, c0.A(6, 1.5) + i * 2, 6)
t8 = P.label("8文字", (4.4, 0.8), 0.42, K.GRAY, 700)
s5.pop(t8, c0.A(6, 2.2))

# ---- 第1章 行11-15：環境を整える
c1 = I.chapter(1)
XT = [-5.1, -1.7, 1.7, 5.1]
slots = []
for x in XT:
    sl = K.card("", (x, 1.9), 3.1, 1.7, K.DARK)
    inn = K.rounded_rect("slot_in", 2.9, 1.5, (0, 0, 0.02), K.BG, 0.26)
    inn.parent = sl
    inn.location = (0, 0, 0.02)
    slots.append(sl)
for i, o in enumerate(slots):
    c1.pop(o, c1.A(0, 0.3) + i * 4)
tiles, marks = [], []
for x, lab, f in zip(XT, ("JDK", "IDE", "Docker", "mvnw"),
                     (c1.A(1, 0.3), c1.A(1, 1.4), c1.A(1, 2.6), c1.A(2, 0.4))):
    t = K.card(lab, loc=(x, 1.9), w=2.9, h=1.5, color=P.CUST, size=0.62)
    t.location = (x, 1.9, 0.05)
    c1.pop(t, f)
    K.pulse(t, f + 12, 1.1, 8)
    tiles.append(t)
# 行14：確認は2つ
chk = []
for x, lab in ((-3.0, "Hello"), (3.0, "DB")):
    ck = P.framed((x, -0.1), 4.4, 1.1, K.BG, K.GRAY, 0.06)
    tx = P.label(lab, (x, -0.1), 0.55, K.WHITE, 900)
    chk.append((ck, tx, x))
for i, (ck, tx, x) in enumerate(chk):
    c1.pop(ck, c1.A(3, 0.2) + i * 6)
    c1.pop(tx, c1.A(3, 0.4) + i * 6)
for (ck, tx, x), f in zip(chk, (c1.M(3, 0.58), c1.M(3, 0.9))):
    tk = P.tick((x - 1.55, -0.1), 0.34)
    c1.pop(tk, f, 8)
    K.pulse(ck, f, 1.08, 8)
# 行15：鎖でつながり、1つが詰まる
chain = []
for i in range(3):
    ch = P.seg((XT[i] + 1.3, 1.9), (XT[i + 1] - 1.3, 1.9), 0.18, K.GRAY, z=0.02)
    c1.pop(ch, c1.A(4, 0.1) + i * 4, 8)
    chain.append(ch)
f15 = c1.A(4)
for i in (0, 1):
    f = f15 + 20 + i * 8
    P.shade(tiles[i], [(f, P.OKBG)], P.CUST)
    m = P.tick((XT[i] + 1.2, 2.5), 0.3)
    c1.pop(m, f + 4, 8)
f_stuck = c1.M(4, 0.28)
P.shade(tiles[2], [(f_stuck, P.WAITBG), (c1.M(4, 0.6), P.OKBG)], P.CUST)
m3 = P.bang((XT[2] + 1.2, 2.5), 0.3)
c1.pop(m3, f_stuck + 2, 8)
K.shake(tiles[2], f_stuck + 4, 0.1, 4, 2)
stuck = P.chip("詰まり", (XT[2], 3.15), K.YELLOW, 0.38, INK)
c1.pop(stuck, f_stuck + 4)
f_fix = c1.M(4, 0.6)
P.out(stuck, f_fix - 2)
P.out(m3, f_fix - 2)
m3ok = P.tick((XT[2] + 1.2, 2.5), 0.3)
c1.pop(m3ok, f_fix + 4, 8)
P.shade(tiles[3], [(f_stuck + 6, K.GRAY), (c1.M(4, 0.85), P.OKBG)], P.CUST)
K.shake(tiles[3], f_stuck + 8, 0.08, 4, 2)
m4 = P.tick((XT[3] + 1.2, 2.5), 0.3)
c1.pop(m4, c1.M(4, 0.85) + 4, 8)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(16, 0.15))
dk = K.card("", (d.LX, 0.2), 3.8, 0.28, P.ROAD, z=0.1)
d.g.pop(dk, A(16, 0.7))
dq = P.qmark((d.LX, 1.35), 0.75)
d.g.pop(dq, A(16, 1.0))
K.pulse(dq, A(16, 1.0) + 12, 1.15, 8)
dn = P.label("環境なし", (d.LX, -0.3), 0.42, K.GRAY, 700)
d.g.pop(dn, A(16, 1.3))
pcb = K.card("", (d.RX, -0.17), 5.4, 0.28, K.GRAY, z=0.1)
pcs = P.framed((d.RX, 1.05), 4.8, 2.2, K.BG, K.GRAY, 0.07, z=0.1)
d.g.pop(pcb, A(16, 1.2))
d.g.pop(pcs, A(16, 1.4))
for lab, y, f in (("Hello", 1.6, A(17, 1.0)), ("DB", 0.55, A(17, 2.0))):
    tx = P.label(lab, (d.RX - 0.9, y), 0.6, K.WHITE, 900)
    d.g.pop(tx, f - 8)
    tk = P.tick((d.RX + 1.5, y), 0.36)
    d.g.pop(tk, f, 8)
    K.pulse(tk, f + 10, 1.2, 8)

# ======================================================================
# E 最後の1行
# ======================================================================
I.final("最初に、進め方と環境をそろえる。")
I.render()
