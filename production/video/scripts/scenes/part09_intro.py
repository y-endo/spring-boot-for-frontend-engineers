"""第9部 運用：公開後に責任を持つ：導入動画（part09-intro）

場面構成（行番号は part09-intro.json の lines）
  0-1    A タイトル → QuickVote のアイコンに「公開」の旗が立ち、歯車が回り続ける
  2-5    B 困りごと : 夜空に3枚の警告カード（夜中に遅い／投票できない／DB停止）。YELLOW → RED の点滅
  6-7    C 第42章 Docker : 層の断面（依存は厚く、アプリは薄い）、root ✕、メモリ制限の枠とヒープの枠
  8-9    C 第43章 ログ : ログの行が並び、同じ相関IDの行だけが光る。本文・トークンは出ない
  10-11  C 第44章 メトリクスとヘルス : メーター2つと信号の2灯。DB が止まると readiness だけ 503
  12-14  C 第45章 性能と停止 : タイムアウトの表 → k6 の山と p95 → スレッドダンプ（扉の前で全員待つ）
  15-17  C 第46章 リリースと復旧 : CI の流れ → バックアップから復元 → 公開前チェックリスト
  18-19  D 前→後 : 3枚の警告 → チェックリスト ✓ とゲージ3つ
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
I = P.Intro(9, "運用：公開後に責任を持つ", [
    (42, "Docker"), (43, "ログ"), (44, "メトリクスとヘルス"), (45, "性能と停止"), (46, "リリースと復旧")])
A, E, M = I.A, I.E, I.M
INK = P.INK
SKY = K.hexc("#171e3c")
YEL_D, RED_D = P.WAITBG, P.NGBG


def gauge(g, loc, r, f_pop, a0, a1, f_swing, dur=24, needle=K.WHITE, ring=P.CUST):
    """円形のメーター。針が a0 から a1（ラジアン）へ振れる。g に登録して (ring, inner, needle_root) を返す。"""
    rg = K.dot(loc, r, ring, None, z=0.1)
    inn = K.dot(loc, r * 0.82, K.DARK, None, z=0.12)
    nd = P.seg((loc[0], loc[1]), (loc[0] + r * 0.72, loc[1]), 0.09 * (r / 0.85 + 0.4), needle, z=0.2)
    hub = K.dot(loc, r * 0.14, needle, None, z=0.25)
    for o in (rg, inn, nd, hub):
        g.pop(o, f_pop, 8)
    K._key(nd, "rotation_euler", max(f_pop + 8, 2), (0, 0, a0), "SINE", "EASE_IN_OUT")
    K._key(nd, "rotation_euler", f_swing, (0, 0, a0), "SINE", "EASE_IN_OUT")
    K._key(nd, "rotation_euler", f_swing + dur, (0, 0, a1), "SINE", "EASE_IN_OUT")
    return rg, inn, nd, hub


# ======================================================================
# A タイトル → QuickVote を公開（旗）、歯車は回り続ける
# ======================================================================
I.title_card()
ga = P.Group(I, A(2) - 3)
qv = K.card("QuickVote", (-1.4, 0.6), 4.4, 1.9, K.BLUE, size=0.7)
ga.pop(qv, A(1, 0.2))
fl = P.flag((0.1, 1.55), 1.0, K.GREEN)
ga.pop(fl, A(1, 0.8), 8)
pb = P.chip("公開", (2.7, 2.9), K.GREEN, 0.42, INK, z=0.1)
ga.pop(pb, A(1, 1.1))
K.pulse(pb, A(1, 1.1) + 12, 1.12, 8)
gear = P.poly((3.7, 0.7), 0.95, 8, K.GRAY, 0.0, 0.1)
gh = K.dot((3.7, 0.7), 0.4, K.BG, None, z=0.2)
gear.rotation_euler = (0, 0, 0)
ga.pop(gear, A(1, 1.6))
ga.pop(gh, A(1, 1.6))
P.spin(gear, A(1, 1.6), A(2) - 3, 1.4)

# ======================================================================
# B 困りごと：夜空に3枚の警告
# ======================================================================
b = I.trouble()
sky = K.card("", (0, 1.25), 15.4, 6.0, SKY, z=-0.03)
b.pop(sky, A(2, 0.0), 12)
moon = K.dot((6.2, 3.35), 0.55, K.WHITE, None, z=0.1)
moon_c = K.dot((6.5, 3.5), 0.5, SKY, None, z=0.12)
b.pop(moon, A(2, 0.3), 8)
b.pop(moon_c, A(2, 0.3), 8)
for i, (sx, sy) in enumerate(((4.9, 3.6), (3.6, 3.25), (-2.6, 3.7), (-0.8, 3.45), (1.8, 3.75))):
    st = K.dot((sx, sy), 0.07, K.WHITE, None, z=0.1)
    b.pop(st, A(2, 0.4) + i * 3, 6)
ok_qv = K.card("QuickVote", (0, 1.2), 4.6, 1.7, K.BLUE, size=0.7, z=0.1)
b.pop(ok_qv, A(2, 0.5))
okt = P.tick((2.0, 2.1), 0.35, z=0.4)
b.pop(okt, A(2, 1.4), 8)
P.out(ok_qv, A(3, 0.0), 6)
P.out(okt, A(3, 0.0), 6)

CW, CH, CY = 4.4, 3.3, 0.85
CX = (-4.7, 0.0, 4.7)


def warn_card(i, f):
    """警告カード（YELLOW → RED の点滅）。カードの中心を返す。"""
    cx = CX[i]
    c_ = K.card("", (cx, CY), CW, CH, K.DARK, z=0.0)
    b.pop(c_, f)
    P.shade(c_, [(f + 6, YEL_D), (f + 20, RED_D), (f + 30, YEL_D), (f + 40, RED_D)], K.DARK)
    K.shake(c_, f + 12, 0.08, 4, 2)
    return cx


# ① 夜中に遅い：重い動きのグラフとカタツムリ
f1 = A(3, 0.4)
cx = warn_card(0, f1)
b.pop(K.text("夜中に遅い", 0.42, (cx, CY + 1.2, 0.2), K.WHITE, 700), f1 + 4)
pts = [(-1.7, -0.2), (-1.0, -0.05), (-0.3, 0.3), (0.3, 0.45), (0.9, 0.95), (1.6, 1.0)]
for j in range(len(pts) - 1):
    sg = P.seg((cx + pts[j][0] * 0.95, CY + pts[j][1] * 0.55 + 0.15), (cx + pts[j + 1][0] * 0.95, CY + pts[j + 1][1] * 0.55 + 0.15),
               0.09, K.WHITE, z=0.2)
    b.pop(sg, f1 + 10 + j * 4, 6)
sn_body = K.card("", (cx - 0.3, CY - 0.95), 1.6, 0.34, K.GRAY, z=0.2)
sn_shell = K.dot((cx - 0.15, CY - 0.55), 0.42, K.ORANGE, None, z=0.3)
sn_in = K.dot((cx - 0.15, CY - 0.55), 0.2, K.hexc("#c4501f"), None, z=0.35)
sn_head = K.dot((cx + 0.5, CY - 0.82), 0.2, K.GRAY, None, z=0.3)
for o in (sn_body, sn_shell, sn_in, sn_head):
    b.pop(o, f1 + 24, 8)
    K.move(o, f1 + 36, (o.location[0] + 0.6, o.location[1]), 70, "SINE", "EASE_IN_OUT")

# ② 投票できない：吹き出しとログの山
f2 = A(4, 0.2)
cx = warn_card(1, f2)
bub = K.card("投票できない", (cx + 0.15, CY + 0.55), 3.4, 0.8, K.WHITE, text_color=INK, size=0.42, z=0.2)
b.pop(bub, f2 + 4)
tail = P.poly((cx - 0.9, CY + 0.08), 0.22, 3, K.WHITE, math.pi, 0.2)
b.pop(tail, f2 + 8, 6)
pp = P.person((cx - 1.4, CY - 0.55), K.ORANGE, 0.62)
b.pop(pp, f2 + 8)
for j in range(4):
    lg_ = K.card("", (cx + 0.9, CY - 0.95 + j * 0.3), 1.9, 0.22, K.GRAY, z=0.2)
    b.pop(lg_, f2 + 18 + j * 4, 6)
q2 = P.qmark((cx + 1.6, CY - 0.1), 0.34, K.WHITE, z=0.5)
b.pop(q2, f2 + 36, 8)
K.pulse(q2, f2 + 48, 1.2, 8)

# ③ DB停止：円筒が灰色になり、アプリが「？」
f3_ = A(5, 0.2)
cx = warn_card(2, f3_)
b.pop(K.text("DB停止", 0.42, (cx, CY + 1.2, 0.2), K.WHITE, 700), f3_ + 4)
db3 = K.card("DB", (cx - 1.2, CY - 0.1), 1.4, 1.6, K.PURPLE, size=0.5, z=0.2)
b.pop(db3, f3_ + 8)
P.shade(db3, [(f3_ + 24, K.GRAY)], K.PURPLE)
app3 = K.card("App", (cx + 1.2, CY - 0.1), 1.4, 1.1, K.BLUE, size=0.46, z=0.2)
b.pop(app3, f3_ + 12)
q3 = P.qmark((cx + 1.65, CY + 0.85), 0.3, K.WHITE, z=0.5)
b.pop(q3, f3_ + 30, 8)
K.pulse(q3, f3_ + 42, 1.2, 8)
dl = K.dashed_line((cx - 0.4, CY - 0.1), (cx + 0.4, CY - 0.1), K.WHITE, 0.07, 0.18, 0.12, z=0.15)
b.pop(dl, f3_ + 16, 6)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---- 第42章：Docker。層の断面、root ✕、メモリ制限
c = I.chapter(42)
dep = K.card("依存の層", (-3.9, 0.45), 5.4, 1.7, P.CUST, size=0.5)
app = K.card("アプリの層", (-3.9, 1.85), 5.4, 0.9, K.BLUE, size=0.46)
c.pop(dep, c.A(0, 0.2))
c.pop(app, c.A(0, 0.55))
K.pulse(app, c.A(0, 1.4), 1.07, 8)
K.pulse(app, c.A(0, 1.9), 1.07, 8)
K.pulse(app, c.A(1, 0.0), 1.07, 8)
pr = P.person((0.9, 1.5), K.ORANGE, 0.85)
c.pop(pr, c.A(1, 0.2))
rc = P.chip("root", (0.9, 0.2), K.GRAY, 0.42, z=0.1)
c.pop(rc, c.A(1, 0.45))
nr = P.nomark((1.75, 2.45), 0.38)
c.pop(nr, c.A(1, 0.7), 8)
K.pulse(nr, c.A(1, 0.7) + 10, 1.2, 8)
mem = P.framed((5.0, 1.2), 3.9, 2.7, K.DARK, K.GRAY, 0.07, 0.3)
c.pop(mem, c.A(1, 1.1))
mt = P.chip("メモリ制限", (0, 0.9), K.GRAY, 0.34, z=0.1)
mt.parent = mem
c.pop(mt, c.A(1, 1.25))
hp = P.framed((0, -0.2), 2.4, 1.2, P.CUST, K.PURPLE, 0.07, 0.25)
hp.parent = mem
K.text("ヒープ", 0.42, (0, 0, 0.1), K.WHITE, 700, parent=hp)
c.pop(hp, c.A(1, 1.4))
fs = c.A(1, 2.0)
K._key(mem, "scale", fs, (1, 1, 1), "SINE", "EASE_IN_OUT")
K._key(mem, "scale", fs + 24, (0.7, 0.7, 0.7), "SINE", "EASE_IN_OUT")

# ---- 第43章：ログ。同じ相関IDの行だけが光る。本文・トークンは出ない
c = I.chapter(43)
lp = K.card("", (-2.3, 1.2), 8.6, 3.7, K.DARK)
c.pop(lp, c.A(0, 0.1))
ci = P.chip("相関ID", (-5.4, 2.75), K.ORANGE, 0.36, INK, z=0.1)
c.pop(ci, c.A(0, 0.4))
TAGC = (K.ORANGE, K.BLUE, K.ORANGE, K.PURPLE, K.ORANGE)
rows = []
for j, (y, tc) in enumerate(zip((2.0, 1.42, 0.84, 0.26, -0.32), TAGC)):
    row = K.card("", (-2.3, y), 7.8, 0.46, K.BG, z=0.05)
    c.pop(row, c.A(0, 0.5) + j * 4, 8)
    tag = K.card("", (-5.9, y), 0.5, 0.3, tc, z=0.1)
    c.pop(tag, c.A(0, 0.55) + j * 4, 8)
    for bx, bw in ((-4.3, 1.5), (-2.3, 2.0), (0.0, 1.3)):
        bar = K.card("", (bx, y), bw, 0.14, K.GRAY, z=0.1)
        c.pop(bar, c.A(0, 0.6) + j * 4, 8)
    rows.append((row, tc))
fl_ = c.A(1, 0.2)
for row, tc in rows:
    if tc == K.ORANGE:
        P.shade(row, [(fl_, K.hexc("#55609c"))], K.BG)
        K.pulse(row, fl_ + 4, 1.04, 8)
    else:
        P.shade(row, [(fl_, K.hexc("#0f1322"))], K.BG)
bw_ = P.chip("本文", (4.6, 2.2), P.CUST, 0.42, z=0.1)
bt_ = P.chip("トークン", (4.6, 1.1), P.CUST, 0.42, z=0.1)
c.pop(bw_, c.A(1, 1.7))
c.pop(bt_, c.A(1, 2.2))
n1 = P.nomark((6.2, 2.2), 0.34)
n2 = P.nomark((6.2, 1.1), 0.34)
c.pop(n1, c.A(1, 2.0), 8)
c.pop(n2, c.A(1, 2.5), 8)
og = P.chip("ログに出さない", (4.6, 0.1), K.GRAY, 0.34, z=0.1)
c.pop(og, c.A(1, 2.9))

# ---- 第44章：メトリクスとヘルス
c = I.chapter(44)
g = c.sub()
gg1 = gauge(g, (-5.3, 1.9), 0.85, c.A(0, 0.2), 2.3, 0.9, c.A(0, 0.9))
gg2 = gauge(g, (-2.9, 1.9), 0.85, c.A(0, 0.4), 2.1, 1.2, c.A(0, 1.2))
c1 = P.chip("プール", (-5.3, 0.55), K.GRAY, 0.36, z=0.1)
c2 = P.chip("HTTP", (-2.9, 0.55), K.GRAY, 0.36, z=0.1)
g.pop(c1, c.A(0, 0.5))
g.pop(c2, c.A(0, 0.7))
db4 = K.card("DB", (0.5, 1.4), 1.5, 1.7, K.PURPLE, size=0.55)
c.pop(db4, c.A(1, 0.0))
# 信号の2灯（readiness は DB が止まると RED の 503。liveness は GREEN のまま）
LAMP_X = 3.2
lr_g = K.dot((LAMP_X, 2.2), 0.5, K.GREEN, None, z=0.2)
lr_r = K.dot((LAMP_X, 2.2), 0.5, K.RED, "503", K.WHITE, z=0.25)
ll = K.dot((LAMP_X, 0.55), 0.5, K.GREEN, None, z=0.2)
lrc = P.chip("readiness", (5.55, 2.2), K.GRAY, 0.36, z=0.1)
llc = P.chip("liveness", (5.4, 0.55), K.GRAY, 0.36, z=0.1)
c.pop(lr_g, c.A(1, 0.3), 8)
c.pop(ll, c.A(1, 0.45), 8)
c.pop(lrc, c.A(1, 0.4))
c.pop(llc, c.A(1, 0.55))
f_stop = c.A(1, 1.5)
P.shade(db4, [(f_stop, K.GRAY)], K.PURPLE)
K.shake(db4, f_stop, 0.12, 6, 2)
P.out(lr_g, f_stop + 4, 4)
c.add(lr_r)
K.pop_in(lr_r, f_stop + 4, 8)
K.pulse(lr_r, f_stop + 14, 1.2, 8)
st_ = P.chip("停止", (0.5, 0.15), P.NGBG, 0.34, z=0.1)
c.pop(st_, f_stop + 2)
K.pulse(ll, f_stop + 20, 1.15, 8)

# ---- 第45章：性能と停止
c = I.chapter(45)
s1 = c.sub()
th = P.chip("タイムアウト", (-3.8, 2.95), K.GRAY, 0.38, z=0.1)
s1.pop(th, c.A(0, 0.2))
slots = []
for j, y in enumerate((2.1, 1.35, 0.6, -0.15)):
    rw = K.card("", (-3.8, y), 5.4, 0.58, K.DARK)
    s1.pop(rw, c.A(0, 0.3) + j * 5, 8)
    lb = K.card("", (-5.2, y), 1.9, 0.18, K.GRAY, z=0.1)
    s1.pop(lb, c.A(0, 0.35) + j * 5, 8)
    sl = K.card("", (-2.3, y), 1.7, 0.4, K.BG, z=0.1)
    s1.pop(sl, c.A(0, 0.4) + j * 5, 8)
    f = c.A(1, 0.1) + j * 12
    pill = K.card("", (-2.3, y), 1.7, 0.4, K.WHITE, z=0.15)
    s1.pop(pill, f, 8)
    tk = P.tick((-1.6, y), 0.22, z=0.4)
    s1.pop(tk, f + 4, 6)
# k6 の山と p95
heights = (0.5, 0.9, 1.5, 2.1, 2.6, 2.1, 1.4, 0.9, 0.5)
kb = P.chip("k6", (3.5, 3.1), K.GRAY, 0.4, z=0.1)
s1.pop(kb, c.A(1, 1.5))
for j, hh in enumerate(heights):
    bx = K.card("", (1.2 + j * 0.62, hh / 2 - 0.1), 0.46, hh, K.ORANGE, z=0.05)
    s1.pop(bx, c.A(1, 1.6) + j * 3, 8)
p95 = K.dashed_line((0.7, 1.9), (6.9, 1.9), K.YELLOW, 0.07, 0.3, 0.2, z=0.3)
s1.pop(p95, c.A(1, 2.2), 8)
pc_ = P.chip("p95", (6.1, 2.55), K.YELLOW, 0.36, INK, z=0.4)
s1.pop(pc_, c.A(1, 2.3))
# 行14：スレッドダンプ（1つの扉の前で全員待っている）
s2 = c.sub()
for o in (th,):
    P.out(o, c.A(2, 0.0) - 6, 6)
for o in list(s1.objs):
    P.out(o, c.A(2, 0.0) - 6, 6)
door = P.framed((3.0, 1.2), 0.55, 2.6, K.GRAY, K.WHITE, 0.06, 0.2)
s2.pop(door, c.A(2, 0.1))
tdc = P.chip("スレッドダンプ", (-1.2, 3.05), K.GRAY, 0.4, z=0.1)
s2.pop(tdc, c.A(2, 0.2))
for j in range(6):
    x = -4.8 + 1.2 * j
    pe = P.person((x, 1.0), K.ORANGE, 0.55)
    s2.pop(pe, c.A(2, 0.3) + j * 3)
    bg_ = P.bang((x, 2.15), 0.26)
    s2.pop(bg_, c.A(2, 1.1) + j * 4, 8)
wt = P.chip("全員 待ち", (-1.8, -0.35), K.YELLOW, 0.4, INK, z=0.1)
s2.pop(wt, c.A(2, 1.9))
K.pulse(wt, c.A(2, 1.9) + 12, 1.12, 8)

# ---- 第46章：リリースと復旧
c = I.chapter(46)
s1 = c.sub()
ci_c = P.chip("CI", (0, 3.3), K.GRAY, 0.36, z=0.1)
s1.pop(ci_c, c.A(0, 0.1))
PX = (-5.4, -1.8, 1.8, 5.4)
for j, (x, lab) in enumerate(zip(PX, ("コミット", "ビルド", "テスト", "イメージ"))):
    pc2 = K.card(lab, (x, 2.3), 2.5, 0.95, P.CUST, size=0.46)
    s1.pop(pc2, c.A(0, 0.25) + j * 5)
    if j < 3:
        ar = K.arrow((x + 1.3, 2.3), (PX[j + 1] - 1.3, 2.3), K.WHITE, 0.09, 0.3)
        K.grow_x(ar, c.A(0, 0.4) + j * 5, 8)
        s1.add(ar)
    tk = P.tick((x + 1.0, 2.75), 0.24, z=0.4)
    s1.pop(tk, c.A(0, 0.9) + j * 12, 8)
bk = K.card("バックアップ", (-4.6, 0.4), 3.2, 1.1, K.PURPLE, size=0.44)
s1.pop(bk, c.A(1, 0.2))
dbr = K.card("DB", (0.7, 0.4), 1.6, 1.1, K.PURPLE, size=0.5)
s1.pop(dbr, c.A(1, 0.4))
P.shade(dbr, [(c.A(1, 1.1), K.GRAY), (c.A(1, 1.9), K.PURPLE)], K.PURPLE)
ar2 = K.arrow((-2.8, 0.4), (-0.3, 0.4), K.BLUE, 0.12, 0.36)
K.grow_x(ar2, c.A(1, 1.3), 12)
s1.add(ar2)
rcv = P.chip("復元", (-1.55, 1.2), K.BLUE, 0.38, z=0.1)
s1.pop(rcv, c.A(1, 1.4))
okr = P.chip("復元できた", (4.6, 0.4), P.OKBG, 0.42, z=0.1)
s1.pop(okr, c.A(1, 2.0))
K.pulse(okr, c.A(1, 2.0) + 12, 1.12, 8)
tkr = P.tick((3.3, 0.4), 0.3, z=0.4)
s1.pop(tkr, c.A(1, 2.1), 8)
# 行17：公開前チェックリスト
s2 = c.sub()
for o in list(s1.objs):
    P.out(o, c.A(2, 0.0) - 6, 6)
cl = K.card("", (0, 1.2), 7.2, 3.5, K.DARK)
s2.pop(cl, c.A(2, 0.1))
clh = P.chip("公開前チェックリスト", (0, 2.7), K.GRAY, 0.4, z=0.1)
s2.pop(clh, c.A(2, 0.25))
for j, y in enumerate((1.85, 1.2, 0.55, -0.1)):
    bx = K.card("", (-0.6, y), 4.6, 0.3, K.GRAY, z=0.1)
    s2.pop(bx, c.A(2, 0.4) + j * 5, 8)
    ck = P.tick((-3.0, y), 0.26, z=0.4)
    s2.pop(ck, c.A(2, 0.9) + j * 14, 8)
    ring = P.chip("", (-3.0, y), K.GRAY, 0.3, w=0.52, h=0.52, z=0.2)
    s2.pop(ring, c.A(2, 0.4) + j * 5, 6)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(18, 0.1))
d.row("before", "夜中に遅い", "ng", A(18, 0.5))
d.row("before", "投票できない", "ng", A(18, 1.0))
d.row("before", "DB停止", "ng", A(18, 1.5))
d.row("after", "公開前チェックリスト", "ok", A(18, 1.8))
d.row("after", "ログとメトリクス", "ok", A(19, 0.3))
# 3つのゲージ（遅い、止まった、原因）
GX = (1.5, 3.2, 5.1)
for gx, lab, a1 in zip(GX, ("遅い", "止まった", "原因"), (0.8, 0.5, 1.0)):
    rg = K.dot((gx, -0.15), 0.3, P.OKBG, None, z=0.15)
    ri = K.dot((gx, -0.15), 0.22, K.BG2, None, z=0.17)
    nd = P.seg((gx, -0.15), (gx + 0.2, -0.15), 0.05, K.WHITE, z=0.2)
    for o in (rg, ri, nd):
        d.g.pop(o, A(19, 1.5), 8)
    K._key(nd, "rotation_euler", A(19, 1.5) + 8, (0, 0, 2.4), "SINE", "EASE_IN_OUT")
    K._key(nd, "rotation_euler", A(19, 1.5) + 30, (0, 0, a1), "SINE", "EASE_IN_OUT")
    lb = P.label(lab, (gx + 0.38, -0.15), 0.34, K.WHITE, 700, "LEFT")
    d.g.pop(lb, A(19, 1.6), 8)

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["動くだけでなく、", "動かし続けられる。"])
I.render()
