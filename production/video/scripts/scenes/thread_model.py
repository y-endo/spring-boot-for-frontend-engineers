"""店員モデル：NodeとJavaの同時処理（第9章）

場面構成（行番号は thread-model.json の lines）
  0      タイトル
  1-6    Node  : 超高速な店員1人 → テーブルを回る → DB待ちは別の客へ → イベントループ
                 → 書くのは1人だけで安全 → 重い計算で全員が待つ
  7-10   Java  : 客ごとに店員（スレッド）→ 全員同時 → 待っても大丈夫（ブロッキング）
  11-16  事故  : 共有ノートに2人が同時に+1 → 1しか増えない → 割り込みのタイミング図
  17-18  対策  : 共有しない／AtomicInteger で 0→1→2
  19     仮想スレッド（安く大量に）
  20     まとめ
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
import bpy
import bl_kit as K

T = K.setup()
A = lambda i, off=0.0: K.at(T, i, off)       # 行 i の開始フレーム
E = lambda i, off=0.0: K.end_of(T, i, off)   # 行 i の終了フレーム

CUST = K.hexc("#34406b")     # 客のテーブルの色
INK = K.BG                   # 明るい地の上の文字色


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None):
    """ラベル用の小さなカード。幅は文字数から見積もる。"""
    w = w or (len(label) * 0.78 * size + 0.9)
    return K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size)


def hdr(label, color, tc=K.WHITE):
    """左上の見出しチップ（左端をそろえる）"""
    w = len(label) * 0.78 * 0.4 + 0.9
    return K.card(label, loc=(-6.7 + w / 2, 3.65), w=w, h=0.8, color=color, text_color=tc, size=0.4)


def out(objs, f, step=2):
    """まとめて退場（少しずつずらす）"""
    for i, o in enumerate(objs):
        K.pop_out(o, f + i * step)


def tour(staff, stops, tabs, f0, f1, idx, hop=8, dwell=3):
    """店員が stops を順に高速で回る。到着のたびにテーブルが脈打つ。(終了フレーム, 次の番号) を返す"""
    f = f0
    while f + hop + dwell <= f1:
        K.move(staff, f, stops[idx], hop)
        K.pulse(tabs[idx], f + hop, 1.1, 6)
        f += hop + dwell
        idx = (idx + 1) % len(stops)
    return f, idx


# ======================================================================
# 0. タイトル
# ======================================================================
title_out = K.title_card(T, "店員モデル", "NodeとJavaの同時処理", until_line=0)

# 背景の「お店」の床（全場面で出しっぱなし）
stage = K.rounded_rect("stage", 15.2, 6.7, (0, 0.85, -1), K.BG2, 0.5)
K.pop_in(stage, A(1) - 4, 12)

# ======================================================================
# 1-6. Node：超高速な店員1人
# ======================================================================
a1, a2, a3, a4, a5, a6, a7 = (A(i) for i in range(1, 8))

node_chip = hdr("Node：店員は1人", K.TEAL)
K.pop_in(node_chip, a1 + 4)

HOME = (0, 0.5)
staff = K.person(HOME, K.TEAL, 1.15)
K.pop_in(staff, a1 + 12)
K.pulse(staff, a1 + 30, 1.12, 8)
tag_fast = chip("超高速", (0, 2.9), K.YELLOW, 0.45, INK)
K.pop_in(tag_fast, a1 + 34)
K.pop_out(tag_fast, a2 - 2)
for d, x in ((46, -3.6), (54, 3.6), (66, 0.0)):      # 超高速のデモ：左右にダッシュ
    K.move(staff, a1 + d, (x, 0.5), 6 if d != 54 else 11)

TB = [(-5.3, 1.9), (5.3, 1.9), (5.3, -0.8), (-5.3, -0.8)]          # 客A,B,C,D
STOPS = [(-3.0, 1.7), (3.0, 1.7), (3.0, -0.6), (-3.0, -0.6)]       # 店員が立つ位置
BADGE = [(x, y + 0.72) for x, y in TB]                             # 状態バッジの位置（テーブルの上）
tables = []
for i, (p, n) in enumerate(zip(TB, "ABCD")):
    t = K.card(f"客{n}", loc=p, w=2.2, h=1.1, color=CUST, size=0.45)
    K.pop_in(t, a2 + i * 7)
    tables.append(t)

# 2: 順番に高速で回る
f, idx = tour(staff, STOPS, tables, a2 + 10, a2 + 84, 0)

# 3: DBの返事待ち → 別の客へ
db = K.card("DB", loc=(0, 3.2), w=1.7, h=0.9, color=K.PURPLE, size=0.45)
K.pop_in(db, a3)
K.move(staff, a3, STOPS[0], 8)
link = K.dashed_line((-4.1, 2.2), (-0.9, 3.0), K.GRAY, 0.08)
K.pop_in(link, a3 + 12)
wait_a = K.dot(BADGE[0], 0.5, K.YELLOW, "待ち")
K.pop_in(wait_a, a3 + 18)
K.pulse(wait_a, a3 + 26, 1.2, 8)
for k, (ti, d) in enumerate([(1, 28), (2, 40), (3, 52)]):
    K.move(staff, a3 + d, STOPS[ti], 8)
    K.pulse(tables[ti], a3 + d + 8, 1.1, 6)
K.pulse(db, a3 + 66, 1.2, 8)
K.pop_out(wait_a, a3 + 66)
ok_a = K.dot(BADGE[0], 0.5, K.GREEN, "返事")
K.pop_in(ok_a, a3 + 70)
K.move(staff, a3 + 72, STOPS[0], 8)
K.pulse(tables[0], a3 + 80, 1.1, 6)

# 4: イベントループ（巡回ルートを点線で見せる）
for o in (db, link, ok_a):
    K.pop_out(o, a4 + 1)
loop = [K.dashed_line(STOPS[i], STOPS[(i + 1) % 4], K.YELLOW, 0.08) for i in range(4)]
for i, o in enumerate(loop):
    K.pop_in(o, a4 + 2 + i * 2)
loop_chip = chip("イベントループ", (0, 0.55), K.YELLOW, 0.5, INK)
K.pop_in(loop_chip, a4 + 4)
K.pulse(loop_chip, a4 + 22, 1.1, 8)
f, idx = tour(staff, STOPS, tables, a4 + 4, a4 + 46, 1, hop=7, dwell=2)

# 5: 書くのは1人だけ → 安全
out([loop_chip] + loop, a5, 2)
memo = K.card("共有メモ", loc=(0, 3.0), w=3.2, h=1.1, color=K.YELLOW, text_color=INK, size=0.45)
K.pop_in(memo, a5 + 6)
K.move(staff, a5 + 8, (0, 0.9), 14)
K.shake(staff, a5 + 30, 0.08, 6, 2)
K.pulse(memo, a5 + 32, 1.1, 8)
safe = K.dot((2.6, 3.0), 0.55, K.GREEN, "安全")
K.pop_in(safe, a5 + 46)
K.pulse(safe, a5 + 58, 1.2, 8)

# 6: 重い計算で手が止まる → 全員が待つ（寄りの演出）
K.camera_move(a6, to=(0, 0.7), zoom=13, dur=24)
K.pop_out(node_chip, a6)
K.pop_out(memo, a6 + 2)
K.pop_out(safe, a6 + 4)
heavy = chip("重い計算", (0, 3.0), K.RED, 0.55)
K.pop_in(heavy, a6 + 16)
K.shake(heavy, a6 + 26, 0.18, 6, 2)
K.recolor(staff, a6 + 20, K.RED)
K.shake(staff, a6 + 24, 0.14, 8, 2)
waits = []
for i in range(4):
    w = K.dot(BADGE[i], 0.5, K.YELLOW, "待ち")
    K.pop_in(w, a6 + 44 + i * 6)
    K.pulse(w, a6 + 70 + i * 3, 1.2, 8)
    waits.append(w)
K.shake(staff, a6 + 70, 0.14, 8, 2)

# Node 場面を片づけて Java へ（カメラも引く）
f_out = a7 - 16
K.camera_move(f_out, to=(0, 0), zoom=16, dur=20)
out([staff, heavy] + tables + waits, f_out, 2)

# ======================================================================
# 7-10. Java：客ごとに店員（スレッド）
# ======================================================================
a8, a9, a10, a11 = (A(i) for i in range(8, 12))

j_chip = hdr("Java（Spring MVC）", K.ORANGE)
K.pop_in(j_chip, a7 + 4)

JX = [-5.25, -1.75, 1.75, 5.25]
jt, js, jlab, jbar, jfill = [], [], [], [], []
for i, x in enumerate(JX):
    t = K.card(f"客{'ABCD'[i]}", loc=(x, 2.0), w=2.8, h=1.1, color=CUST, size=0.45)
    K.pop_in(t, a7 + 10 + i * 22)
    p = K.person((x, 0.25), K.ORANGE, 1.0)
    K.pop_in(p, a7 + 18 + i * 22)
    jt.append(t)
    js.append(p)

# 8: スレッド名と進捗バー（全員が同時に進む）
for i, x in enumerate(JX):
    lb = K.text(f"スレッド{i + 1}", 0.34, (x, -0.85, 0.2), K.WHITE, 700)
    K.pop_in(lb, a8 + i * 4)
    jlab.append(lb)
    bar, fill = K.progress_bar((x, -1.35), 2.4, 0.22, K.GREEN, K.DARK)
    K.pop_in(bar, a8 + 12 + i * 4)
    K.bar_to(fill, a8 + 20, 0.5, 36 + i * 4)
    jbar.append(bar)
    jfill.append(fill)
    K.bob(js[i], a8 + 4 + i * 3, a9 + 12, 0.14, 18)
    K.pulse(jt[i], a8 + 4 + i * 3, 1.08, 8)

# 9: ひとりが返事待ち（ブロッキング）、他は動き続ける
K.recolor(js[1], a9 + 8, K.YELLOW)
wait_b = K.dot((-0.65, 2.6), 0.5, K.YELLOW, "待ち")
K.pop_in(wait_b, a9 + 10)
K.pulse(wait_b, a9 + 24, 1.2, 8)
for i in (0, 2, 3):
    K.bar_to(jfill[i], a9 + 12, 1.0, 44)
    K.bob(js[i], a9 + 14, a10 + 4, 0.14, 18)
for i in (0, 2, 3):
    K.pulse(jt[i], a9 + 60 + i * 2, 1.1, 8)
block = chip("ブロッキング", (0, -2.0), K.RED, 0.42)
K.pop_in(block, a9 + 78)
K.pulse(block, a9 + 92, 1.1, 8)

# 10: 店員は他にもいる
K.pop_out(block, a10)
others = chip("他の店員は動く", (0, -2.0), K.GREEN, 0.42, INK)
K.pop_in(others, a10 + 6)
for i in (0, 2, 3):
    K.bar_to(jfill[i], a10 + 2, 0.0, 6)
    K.bar_to(jfill[i], a10 + 10, 1.0, 70)
    K.bob(js[i], a10 + 6, a11 - 18, 0.16, 16)
K.pulse(js[0], a10 + 30, 1.15, 8)
K.pulse(js[2], a10 + 36, 1.15, 8)
K.pulse(js[3], a10 + 42, 1.15, 8)
K.pulse(others, a10 + 44, 1.1, 8)

f_out = a11 - 14
out([j_chip, wait_b, others] + jt + js + jlab + jbar, f_out, 1)

# ======================================================================
# 11-16. 事故：共有ノートに2人が同時に +1
# ======================================================================
a12, a13, a14, a15, a16, a17 = (A(i) for i in range(12, 18))

acc_chip = hdr("共有ノートの事故", K.RED)
K.pop_in(acc_chip, a11 + 4)

note = K.card("", loc=(0, 1.5), w=4.4, h=2.8, color=K.WHITE, size=0.4)
K.text("共有ノート", 0.42, (0, 0.95, 0.05), INK, 700, parent=note)
K.pop_in(note, a11 + 10)
fA, fB = a14 + 32, a14 + 52     # 店員A・Bが書き込む瞬間
K.number_ticker([(1, "0", INK), (fA, "1", INK), (fB, "1", K.RED)], (0, -0.3, 0.05), 1.7, parent=note)

sA = K.person((-5.2, 0.2), K.BLUE, 1.1)
sB = K.person((5.2, 0.2), K.PURPLE, 1.1)
K.pop_in(sA, a11 + 26)
K.pop_in(sB, a11 + 32)
labA = K.text("店員A", 0.42, (-5.2, -1.0, 0.2), K.WHITE, 700)
labB = K.text("店員B", 0.42, (5.2, -1.0, 0.2), K.WHITE, 700)
K.pop_in(labA, a11 + 40)
K.pop_in(labB, a11 + 44)
K.pulse(sA, a11 + 60, 1.1, 8)
K.pulse(sB, a11 + 60, 1.1, 8)

# 12: 2人が同時に +1 しようとする（同じフレームで動く）
arrA = K.arrow((-4.35, 0.7), (-2.35, 1.1), K.BLUE, 0.1, 0.34)
arrB = K.arrow((4.35, 0.7), (2.35, 1.1), K.PURPLE, 0.1, 0.34)
K.grow_x(arrA, a12 + 4, 14)
K.grow_x(arrB, a12 + 4, 14)
pA = K.dot((-3.35, 0.9), 0.5, K.ORANGE, "+1")
pB = K.dot((3.35, 0.9), 0.5, K.ORANGE, "+1")
K.pop_in(pA, a12 + 8)
K.pop_in(pB, a12 + 8)
K.pulse(pA, a12 + 34, 1.25, 8)
K.pulse(pB, a12 + 34, 1.25, 8)
K.pulse(note, a12 + 34, 1.05, 8)
K.pulse(pA, a12 + 62, 1.25, 8)
K.pulse(pB, a12 + 62, 1.25, 8)
out([arrA, arrB, pA, pB], a13 - 6, 0)

# 13: 2人とも、まず今の数字を読む
rA = K.arrow((-2.4, 1.9), (-4.15, 2.45), K.BLUE, 0.1, 0.34)
rB = K.arrow((2.4, 1.9), (4.15, 2.45), K.PURPLE, 0.1, 0.34)
K.grow_x(rA, a13 + 3, 12)
K.grow_x(rB, a13 + 3, 12)
hA0 = K.card("0", loc=(-5.2, 2.6), w=1.9, h=1.4, color=CUST, size=0.6, sub="読んだ")
hB0 = K.card("0", loc=(5.2, 2.6), w=1.9, h=1.4, color=CUST, size=0.6, sub="読んだ")
K.pop_in(hA0, a13 + 14)
K.pop_in(hB0, a13 + 14)
K.pulse(hA0, a13 + 44, 1.12, 8)
K.pulse(hB0, a13 + 44, 1.12, 8)

# 14: それぞれ1を足して、書き込む
out([rA, rB], a14, 0)
K.pop_out(hA0, a14 + 2)
K.pop_out(hB0, a14 + 2)
hA1 = K.card("1", loc=(-5.2, 2.6), w=1.9, h=1.4, color=K.ORANGE, size=0.6, sub="足した")
hB1 = K.card("1", loc=(5.2, 2.6), w=1.9, h=1.4, color=K.ORANGE, size=0.6, sub="足した")
K.pop_in(hA1, a14 + 10)
K.pop_in(hB1, a14 + 10)
wA = K.arrow((-4.15, 2.45), (-2.4, 1.9), K.ORANGE, 0.1, 0.34)
wB = K.arrow((4.15, 2.45), (2.4, 1.9), K.ORANGE, 0.1, 0.34)
K.grow_x(wA, fA - 8, 8)
K.pulse(note, fA, 1.1, 8)
K.grow_x(wB, fB - 8, 8)
K.pulse(note, fB, 1.1, 8)
K.shake(note, fB + 2, 0.14, 6, 2)

# 15: 2回足したのに増えたのは1だけ（寄る）
K.camera_move(a15, to=(0, 0.9), zoom=13, dur=20)
K.pop_out(acc_chip, a15)
c2 = chip("足したのは2回", (-2.5, -0.7), K.TEAL, 0.4)
c1 = chip("増えたのは1", (2.5, -0.7), K.RED, 0.4)
K.pop_in(c2, a15 + 12)
K.pop_in(c1, a15 + 30)
K.pulse(c1, a15 + 44, 1.15, 8)
K.pulse(note, a15 + 44, 1.08, 8)

# 16: 読む→足す→書く の間に割り込まれた（タイミング図）
K.camera_move(a16 - 2, to=(0, 0), zoom=16, dur=20)
out([note, sA, sB, labA, labB, hA1, hB1, wA, wB, c2, c1], a16 - 2, 1)
laneA = K.text("店員A", 0.42, (-5.6, 1.9, 0.2), K.BLUE, 900)
laneB = K.text("店員B", 0.42, (-5.6, 0.1, 0.2), K.PURPLE, 900)
K.pop_in(laneA, a16 + 4)
K.pop_in(laneB, a16 + 30)
xa = [-3.2, -0.4, 2.4]
xb = [-1.8, 1.0, 3.8]
stepsA, stepsB = [], []
for k, name in enumerate(("読む", "足す", "書く")):
    ca = K.card(name, loc=(xa[k], 1.9), w=1.9, h=1.0, color=K.BLUE, size=0.45)
    K.pop_in(ca, a16 + 8 + k * 8)
    stepsA.append(ca)
    cb = K.card(name, loc=(xb[k], 0.1), w=1.9, h=1.0, color=K.PURPLE, size=0.45)
    K.pop_in(cb, a16 + 34 + k * 8)
    stepsB.append(cb)
axis = K.arrow((-4.8, -1.9), (6.2, -1.9), K.GRAY, 0.07, 0.3)
K.grow_x(axis, a16 + 10, 20)
axl = K.text("時間", 0.4, (6.95, -1.9, 0.2), K.GRAY, 700)
K.pop_in(axl, a16 + 24)
K.recolor(stepsB[0], a16 + 72, K.RED)
K.pulse(stepsB[0], a16 + 72, 1.2, 8)
K.pulse(stepsA[2], a16 + 80, 1.2, 8)
late = K.text("書かれる前に読んだ", 0.4, (-1.8, -0.95, 0.2), K.RED, 700)
K.pop_in(late, a16 + 78)
wline = K.dashed_line((2.4, 1.3), (2.4, -0.4), K.RED, 0.07)
K.pop_in(wline, a16 + 84)
wtxt = K.text("ここで書く", 0.4, (2.4, -0.95, 0.2), K.RED, 700)
K.pop_in(wtxt, a16 + 88)

# ======================================================================
# 17-18. 対策：共有しない／AtomicInteger
# ======================================================================
a18, a19, a20 = A(18), A(19), A(20)

out([laneA, laneB, axis, axl, late, wline, wtxt] + stepsA + stepsB, a17 - 2, 1)
fix_chip = hdr("対策", K.GREEN, INK)
K.pop_in(fix_chip, a17 + 6)

shared = K.card("", loc=(0, 1.5), w=3.6, h=1.8, color=K.WHITE, size=0.4)
K.text("共有ノート", 0.5, (0, 0, 0.05), INK, 700, parent=shared)
K.pop_in(shared, a17 + 2)
xmark = K.cross_mark((0, 1.5), 2.6, K.RED, 0.3)
K.pop_in(xmark, a17 + 14)
K.pulse(xmark, a17 + 24, 1.15, 8)
fA2 = K.person((-4.8, 0.2), K.BLUE, 1.1)
fB2 = K.person((4.8, 0.2), K.PURPLE, 1.1)
K.pop_in(fA2, a17 + 26)
K.pop_in(fB2, a17 + 30)
mA = chip("自分のメモ", (-4.8, 2.2), K.TEAL, 0.42)
mB = chip("自分のメモ", (4.8, 2.2), K.TEAL, 0.42)
K.pop_in(mA, a17 + 40)
K.pop_in(mB, a17 + 46)
K.pulse(mA, a17 + 56, 1.1, 8)
K.pulse(mB, a17 + 56, 1.1, 8)

# 18: 持つなら安全な道具（AtomicInteger）。0→1→2 と正しく増える
out([shared, xmark, mA, mB], a18, 1)
atom = K.card("", loc=(0, 1.5), w=4.8, h=2.8, color=K.GREEN, size=0.4)
K.text("AtomicInteger", 0.42, (0, 0.95, 0.05), INK, 700, parent=atom)
K.pop_in(atom, a18 + 8)
g1, g2 = a18 + 66, a18 + 86
K.number_ticker([(1, "0", INK), (g1, "1", INK), (g2, "2", INK)], (0, -0.3, 0.05), 1.7, parent=atom)
gA = K.arrow((-4.15, 0.9), (-2.55, 1.3), K.BLUE, 0.1, 0.34)
gB = K.arrow((4.15, 0.9), (2.55, 1.3), K.PURPLE, 0.1, 0.34)
K.grow_x(gA, g1 - 20, 12)
K.grow_x(gB, g2 - 20, 12)
dA = K.dot((-3.35, 1.1), 0.45, K.ORANGE, "+1")
dB = K.dot((3.35, 1.1), 0.45, K.ORANGE, "+1")
K.pop_in(dA, g1 - 20)
K.pop_in(dB, g2 - 20)
K.pulse(atom, g1, 1.06, 8)
K.pulse(atom, g2, 1.06, 8)
out([gA, dA], g1 + 12, 0)
out([gB, dB], g2 + 12, 0)
okb = K.dot((3.3, 3.2), 0.55, K.GREEN, "OK")
K.pop_in(okb, g2 + 6)
K.pulse(okb, g2 + 18, 1.2, 8)
fixed = [fix_chip, fA2, fB2, atom, okb]

# ======================================================================
# 19. 仮想スレッド：店員を安く大量に
# ======================================================================
out(fixed, a19 - 2, 1)
vt = chip("仮想スレッド", (0, 3.3), K.PURPLE, 0.55)
K.pop_in(vt, a19 + 4)
cols = [K.ORANGE, K.TEAL, K.BLUE, K.PURPLE, K.YELLOW, K.GREEN, K.RED]
crowd = []
n = 0
for row, y in enumerate((1.3, -0.3)):
    for c in range(8):
        x = -5.25 + c * 1.5
        p = K.person((x, y), cols[(n + row) % len(cols)], 0.62)
        K.pop_in(p, a19 + 14 + n * 3, 8)
        K.bob(p, a19 + 14 + n * 3 + 10, a20 - 20, 0.1, 18)
        crowd.append(p)
        n += 1
cheap = chip("安く、たくさん雇える", (0, -1.7), K.GREEN, 0.5, INK)
K.pop_in(cheap, a19 + 80)
K.pulse(cheap, a19 + 94, 1.1, 8)

# ======================================================================
# 20. まとめ
# ======================================================================
out([vt, cheap] + crowd, a20 - 14, 1)
m1 = K.text("Javaのサーバーは", 0.9, (0, 2.3, 0.5), K.WHITE, 900)
m2 = K.text("最初から同時に動いている", 0.85, (0, 0.95, 0.5), K.ORANGE, 900)
K.pop_in(m1, a20 + 4)
K.pop_in(m2, a20 + 14)
fin = []
for i, x in enumerate((-4.5, -1.5, 1.5, 4.5)):
    p = K.person((x, -1.0), K.ORANGE, 1.0)
    K.pop_in(p, a20 + 24 + i * 5)
    K.bob(p, a20 + 36 + i * 3, a20 + 200, 0.16, 18)
    fin.append(p)

# ======================================================================
# 描画（確認用に環境変数で静止画だけ出せる）
#   TM_LIST=300,600  TM_OUT=出力フォルダ  TM_PCT=50
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
