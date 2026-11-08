"""第2部 Spring Bootの心臓部：導入動画（part02-intro）

場面構成（行番号は part02-intro.json の lines）
  0-1    A タイトル → 円「Spring Boot」が心臓のように2回脈打つ
  2-4    B 困りごと : 黒い箱（フタ付き）。java -jar のチップが入ってサーバー起動が出る → 封筒が入って出る。途中は「？」
  5-18   C 第10〜16章（7枚のカード。章ごとのミニ動き）
  19-22  D 前→後 : QuickVote の板に5つの API、その下に YELLOW のチップ3つ
  23     E 最後の1行

確認用: INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import part_intro_kit as P

K = P.K
I = P.Intro(2, "Spring Bootの心臓部", [
    (10, "Boot/全体像"), (11, "DI/Bean"), (12, "リクエスト/の旅"), (13, "DTOと/JSON"),
    (14, "検証と/エラー"), (15, "設定と/秘密"), (16, "APIの/テスト")])
A, E, M = I.A, I.E, I.M
INK = P.INK
BOXC = K.hexc("#0a0d19")          # 黒い箱の色
GOLD = K.hexc("#f2b632")


def envelope(loc, s=1.0, z=0.3):
    """ORANGE の封筒（リクエスト）。"""
    r = K.card("", loc, 0.95 * s, 0.64 * s, K.ORANGE, z=z)
    for sx in (-1, 1):
        sg = P.seg((sx * 0.47 * s, 0.31 * s), (0, 0.0), 0.07 * s, INK, z=0.08)
        sg.parent = r
    return r


def key_icon(loc, s=1.0, z=0.3):
    """鍵のアイコン（YELLOW）。原点は輪の中心。"""
    root = K.dot(loc, 0.36 * s, K.YELLOW, None, z=z)
    hole = K.dot((0, 0), 0.14 * s, P.BG, None, z=0.05)
    hole.parent = root
    for p1, p2, w in (((0.3 * s, 0), (1.15 * s, 0), 0.14 * s), ((0.85 * s, 0), (0.85 * s, -0.3 * s), 0.12 * s),
                      ((1.12 * s, 0), (1.12 * s, -0.24 * s), 0.12 * s)):
        sg = P.seg(p1, p2, w, K.YELLOW, z=0.0)
        sg.parent = root
    return root


# ======================================================================
# A タイトル → 円「Spring Boot」が心臓のように脈打つ
# ======================================================================
I.title_card()
ga = P.Group(I, A(2) - 3)
halo = K.dot((0, 0.5), 2.15, K.hexc("#25356b"), None, z=0.2)
heart = K.dot((0, 0.5), 1.7, K.BLUE, None, z=0.3)
K.text("Spring Boot", 0.52, (0, 0, 0.05), K.WHITE, 900, parent=heart)
ga.pop(halo, A(1, 0.1), 12)
ga.pop(heart, A(1, 0.15), 12)
for t in (0.55, 1.45):                                  # 心臓のように「どくん、どくん」を2回
    f = A(1, t)
    K.pulse(heart, f, 1.2, 8)
    K.pulse(heart, f + 9, 1.12, 8)
    K.pulse(halo, f, 1.16, 10)

# ======================================================================
# B 困りごと：黒い箱
# ======================================================================
b = I.trouble()
me = P.person((-6.5, 0.6), K.ORANGE, 0.8)
b.pop(me, A(2, 0.2))
jv = P.chip("Java", (-6.0, 2.1), P.CUST, 0.4)
b.pop(jv, A(2, 0.5))
jk = P.tick((-4.8, 2.1), 0.26)
b.pop(jk, A(2, 1.4), 8)

# 行3：黒い箱。java -jar が入って、サーバー起動が出る。中身は見えない
BX, BY = 0.4, 1.0
CY = 0.45                     # 箱の中を通る流れの高さ（「？」は上、流れは下）
box = P.framed((BX, BY), 3.8, 2.6, BOXC, K.GRAY, 0.07, 0.3)
lid = K.card("", (BX, BY + 1.4), 4.2, 0.5, K.GRAY, z=0.1)
b.pop(box, A(3, 0.0))
b.pop(lid, A(3, 0.1))
q = P.qmark((BX, BY + 0.6), 0.5, z=0.9)
b.pop(q, A(3, 0.3))
cmd = P.chip("java -jar", (-4.2, CY), P.CUST, 0.46, z=0.5)
b.pop(cmd, A(3, 0.3))
K.move(cmd, A(3, 0.9), (BX, CY), 20)
P.out(cmd, A(3, 0.9) + 18, 6)
K.shake(box, A(3, 0.9) + 20, 0.12, 4, 2)
srv = P.chip("サーバー起動", (BX, CY), K.BLUE, 0.46, z=0.5)
b.pop(srv, A(3, 2.0), 8)
K.move(srv, A(3, 2.15), (5.3, CY), 20)
K.pulse(q, A(3, 3.1), 1.2, 8)

# 行4：封筒が入って出る。途中の道は「？」の点線
P.out(srv, A(4) - 2, 8)
env1 = envelope((-4.2, CY), 1.1, z=0.5)
b.pop(env1, A(4, 0.1))
K.move(env1, A(4, 0.45), (BX - 1.9, CY), 16)
P.out(env1, A(4, 0.45) + 15, 6)
dl = K.dashed_line((BX - 1.3, CY), (BX + 1.3, CY), K.GRAY, 0.09, 0.28, 0.2, z=0.4)
b.pop(dl, A(4, 0.9))
env2 = envelope((BX + 1.9, CY), 1.1, z=0.5)
b.pop(env2, A(4, 1.5))
K.move(env2, A(4, 1.65), (5.4, CY), 20)
K.pulse(q, A(4, 2.3), 1.25, 8)

I.cam(A(3), to=(-0.1, 0.7), zoom=15.2, dur=24)
I.cam(A(5) - 16, to=(0, 0), zoom=16, dur=14)

# ======================================================================
# C 章の道のり
# ======================================================================
I.build_row()

# ---- 第10章：JVM → Spring Framework → Spring Boot の積み木。自動構成は条件付きの設定
c = I.chapter(10)
LX = -3.6
layers = []
for lab, y, col, f in (("JVM", 0.1, K.GRAY, 0.1), ("Spring Framework", 1.0, P.CUST, 0.4), ("Spring Boot", 1.9, K.BLUE, 0.7)):
    lay = K.card(lab, loc=(LX, y), w=6.8, h=0.8, color=col, size=0.44)
    c.pop(lay, c.A(0, f))
    layers.append(lay)
tom = P.chip("Tomcat", (-1.25, 1.9), K.GRAY, 0.32, z=0.1)
c.pop(tom, c.A(0, 1.5))
K.pulse(layers[2], c.A(0, 1.5) + 6, 1.05, 8)
# 行6：条件のひし形「あれば」が点灯し、「設定が入る」札が出る
ar1 = K.arrow((0.0, 1.9), (1.4, 1.9), K.WHITE, 0.1, 0.3)
K.grow_x(ar1, c.A(1, 0.1), 8)
c.add(ar1)
dm0 = P.poly((2.6, 1.9), 1.05, 4, K.GRAY, 0.0, 0.1)
c.pop(dm0, c.A(1, 0.2))
dm1 = P.poly((2.6, 1.9), 1.05, 4, K.GREEN, 0.0, 0.12)
f_on = c.A(1, 1.45)
K.pop_in(dm1, f_on, 8)
c.add(dm1)
K.pulse(dm1, f_on + 8, 1.15, 8)
dl_ = P.label("あれば", (2.6, 1.9), 0.38, INK, 900, z=0.4)
c.pop(dl_, c.A(1, 0.3))
ar2 = K.arrow((3.75, 1.9), (4.6, 1.9), K.WHITE, 0.1, 0.3)
K.grow_x(ar2, c.A(1, 1.8), 8)
c.add(ar2)
tag = P.chip("設定が入る", (5.9, 1.9), K.BLUE, 0.4)
c.pop(tag, c.A(1, 1.95))
K.pulse(tag, c.A(1, 1.95) + 12, 1.1, 8)

# ---- 第11章：Controller → Service → Repository。3つのリクエストが同じ Service へ
c = I.chapter(11)
XS = (-4.9, 0.0, 4.9)
acts = []
for x, nm in zip(XS, ("Controller", "Service", "Repository")):
    t = K.card(nm, loc=(x, 2.0), w=3.4, h=1.1, color=P.CUST, size=0.46)
    acts.append(t)
for i, t in enumerate(acts):
    c.pop(t, c.A(0, 0.05) + i * 12)
for i in range(2):
    a = K.arrow((XS[i] + 1.75, 2.0), (XS[i + 1] - 1.75, 2.0), K.BLUE, 0.11, 0.32)
    K.grow_x(a, c.A(0, 1.2) + i * 10, 8)
    c.add(a)
for i, t in enumerate(acts):                         # 起動時に配線される
    K.pulse(t, c.A(1, 0.6) + i * 9, 1.1, 8)
P.shade(acts[1], [(c.A(1, 1.9), K.BLUE)], P.CUST)
envs = []
for i, (sx, ex) in enumerate(((-2.6, -0.55), (0.0, 0.0), (2.6, 0.55))):
    e = envelope((sx, -0.25), 0.85, z=0.3)
    f = c.A(1, 2.0) + i * 9
    c.pop(e, f, 8)
    K.move(e, f + 4, (ex, 0.95), 16)
    envs.append(e)
K.pulse(acts[1], c.A(1, 2.0) + 40, 1.1, 8)
sh = P.chip("1つを共有", (0, 3.1), K.WHITE, 0.42, INK)
c.pop(sh, c.A(1, 2.9))
K.pulse(sh, c.A(1, 2.9) + 12, 1.1, 8)

# ---- 第12章：封筒とスレッド（人形）が最後まで同じ人で運ぶ
c = I.chapter(12)
XS = (-5.6, -2.8, 0.0, 2.8, 5.6)
nodes = []
for x, nm in zip(XS, ("Filter", "Dispatcher", "Controller", "Service", "Repository")):
    t = K.card(nm, loc=(x, 1.9), w=2.6, h=0.9, color=P.CUST, size=0.34)
    nodes.append(t)
for i, t in enumerate(nodes):
    c.pop(t, c.A(0, 0.1) + i * 6)
road = P.seg((-6.9, 0.5), (6.9, 0.5), 0.12, P.ROAD)
c.pop(road, c.A(0, 0.7))
th = P.person((-6.2, 0.55), K.BLUE, 0.55)
K.text("スレッド", 0.3, (0, -0.95, 0.1), K.WHITE, 700, parent=th)
ev = envelope((-5.2, 0.65), 0.75, z=0.3)
c.pop(th, c.A(0, 1.5))
c.pop(ev, c.A(0, 1.55))
f0 = c.A(0, 1.9)
D = int(round(c.A(1, 2.2) - f0))
x0, x1 = -6.2, 6.0
K.move(th, f0, (x1, 0.55), D, "LINEAR")
K.move(ev, f0, (x1 + 1.0, 0.65), D, "LINEAR")
for x, t in zip(XS, nodes):
    fp = int(f0 + D * (x - 0.4 - x0) / (x1 - x0))
    K.pulse(t, fp, 1.1, 8)
    P.shade(t, [(fp + 2, K.BLUE)], P.CUST)

# ---- 第13章：DTO（record）と JSON の往復。余計な項目は無視される
c = I.chapter(13)
dto = K.card("DTO", loc=(-4.1, 1.7), w=3.6, h=2.0, color=K.BLUE, size=0.62, sub="record")
js = K.card("JSON", loc=(4.1, 1.7), w=3.6, h=2.0, color=K.ORANGE, size=0.62, sub="{ }")
c.pop(dto, c.A(0, 0.1))
c.pop(js, c.A(0, 0.4))
for y, p1, p2 in ((2.1, (-2.1, 2.1), (2.1, 2.1)), (1.3, (2.1, 1.3), (-2.1, 1.3))):
    a = K.arrow(p1, p2, K.WHITE, 0.1, 0.3)
    K.grow_x(a, c.A(0, 1.0) + (0 if y > 2 else 10), 10)
    c.add(a)
ex = P.chip("余計な項目", (4.1, 0.0), P.CUST, 0.4, z=0.1)
c.pop(ex, c.A(1, 0.4))
K.move(ex, c.A(1, 1.0), (-2.0, 0.0), 40)
P.shade(ex, [(c.A(1, 2.4), K.hexc("#252c45"))], P.CUST)
P.out(ex, c.A(1, 2.9), 8)
ig = P.chip("無視される", (-2.0, 0.0), K.YELLOW, 0.4, INK)
c.pop(ig, c.A(1, 3.0))
K.pulse(ig, c.A(1, 3.0) + 12, 1.12, 8)

# ---- 第14章：3つの門で封筒が止められ、同じ形のエラー札が出る
c = I.chapter(14)
GX = (-4.0, 0.0, 4.0)
walls = []
for x, nm in zip(GX, ("形", "業務ルール", "DB制約")):
    w = K.card("", loc=(x, 1.2), w=0.32, h=2.5, color=K.GRAY, z=0.1)
    lb = P.chip(nm, (x, 2.95), P.CUST, 0.38)
    walls.append(w)
    c.pop(w, c.A(0, 0.1) + 10 * len(walls))
    c.pop(lb, c.A(0, 0.1) + 10 * len(walls) + 3)
tags = []
for i, (x, code, st) in enumerate(zip(GX, ("400", "404", "409"), (-6.8, -3.0, 1.0))):
    e = envelope((st, 1.2), 1.0, z=0.3)
    f = c.A(1, 0.05) + i * 26
    c.pop(e, f, 8)
    K.move(e, f + 4, (x - 0.65, 1.2), 14)
    K.shake(walls[i], f + 18, 0.1, 4, 2)
    P.out(e, f + 20, 6)
    tg = P.chip(code, (x, -0.55), P.NGBG, 0.5)
    c.pop(tg, f + 22, 8)
    tags.append(tg)
for tg in tags:
    K.pulse(tg, c.A(1, 2.45), 1.12, 8)

# ---- 第15章：設定の層（環境変数が yaml の上）。足りなければ起動で落ち、秘密は Git へ行かない
c = I.chapter(15)
yaml_ = K.card("yaml", loc=(-5.0, 0.9), w=3.6, h=1.0, color=P.CUST, size=0.46)
env_ = K.card("環境変数", loc=(-4.3, 1.75), w=3.6, h=1.0, color=K.BLUE, size=0.46, z=0.1)
c.pop(yaml_, c.A(0, 0.1))
c.pop(env_, c.A(0, 0.5))
ar = K.arrow((-2.2, 1.3), (-1.0, 1.3), K.WHITE, 0.1, 0.3)
K.grow_x(ar, c.A(0, 1.0), 8)
c.add(ar)
boot = K.card("起動", loc=(0.2, 1.3), w=2.0, h=1.0, color=P.OKBG, size=0.5)
c.pop(boot, c.A(0, 1.2))
P.out(env_, c.A(1, 0.25), 8)                          # 設定が足りない
P.shade(boot, [(c.A(1, 1.2), P.NGBG)], P.OKBG)
fx = P.nomark((1.3, 1.85), 0.3)
c.pop(fx, c.A(1, 1.3), 8)
fall = P.chip("落ちる", (0.2, 0.3), P.NGBG, 0.4)
c.pop(fall, c.A(1, 1.5))
K.shake(boot, c.A(1, 1.3), 0.12, 4, 2)
ky = key_icon((3.0, 1.3), 1.0)
c.pop(ky, c.A(1, 2.1))
ar2 = K.arrow((4.5, 1.3), (5.05, 1.3), K.WHITE, 0.1, 0.25)
K.grow_x(ar2, c.A(1, 2.3), 6)
c.add(ar2)
git = P.chip("Git", (6.1, 1.3), P.CUST, 0.46)
c.pop(git, c.A(1, 2.4))
gx = P.nomark((4.75, 1.3), 0.3)
c.pop(gx, c.A(1, 2.85), 8)
K.shake(git, c.A(1, 2.9), 0.1, 4, 2)

# ---- 第16章：5つの結果が順に点灯。右に「差し替え」
c = I.chapter(16)
codes = ("200", "400", "403", "404", "409")
for i, code in enumerate(codes):
    y = 2.9 - i * 0.8
    r = K.card("", loc=(-3.8, y), w=3.6, h=0.62, color=K.DARK)
    t = P.label(code, (-4.8, y), 0.42, K.WHITE, 900, "LEFT")
    c.pop(r, c.A(0, 0.1) + i * 4)
    c.pop(t, c.A(0, 0.2) + i * 4)
    tk = P.tick((-2.5, y), 0.22, z=0.4)
    f = c.A(0, 0.9) + i * 9
    c.pop(tk, f, 8)
    P.shade(r, [(f, P.OKBG)], K.DARK)
    K.pulse(r, f, 1.05, 8)
wm = P.chip("WebMvcTest", (3.3, 2.3), P.CUST, 0.4)
c.pop(wm, c.A(1, 0.2))
sb = P.chip("SpringBootTest", (3.5, 0.9), K.BLUE, 0.4)
c.pop(sb, c.A(1, 1.3))
mk = P.chip("差し替え", (6.1, 2.3), K.GRAY, 0.36)
c.pop(mk, c.A(1, 0.7))
K.pulse(mk, c.A(1, 0.7) + 12, 1.1, 8)

# ======================================================================
# D 前→後
# ======================================================================
d = I.before_after(A(19, 0.2))
d.row("before", "Javaが読める", "ok", A(19, 0.9))
d.row("before", "仕組みは見えない", "q", A(19, 1.6))
qv = P.chip("QuickVote", (5.6, 2.65), K.GRAY, 0.32, z=0.1)
d.g.pop(qv, A(19, 1.2))
api = (("作る", 1.9, 1.85, 0.2), ("参加", 3.7, 1.85, 0.9), ("選択肢", 5.6, 1.85, 1.5),
       ("投票", 2.8, 0.95, 2.5), ("詳細", 4.8, 0.95, 3.3))
for lab, x, y, t in api:
    ch = P.chip(lab, (x, y), K.BLUE, 0.4, z=0.1)
    d.g.pop(ch, A(20, t - 0.3))
    tk = P.tick((x + ch["w"] / 2 - 0.02, y + 0.32), 0.2, z=0.4)
    d.g.pop(tk, A(20, t + 0.1), 8)
    K.pulse(ch, A(20, t + 0.1), 1.1, 8)
# 行21〜22：まだ守れていないもの（YELLOW のチップ3つ）
pend = ["再起動で消える", "同時実行", "再送"]
ws = [P.text_w(s, 0.36) + 0.36 * 1.7 for s in pend]
tot = sum(ws) + 0.2 * 2
x = 3.0 - tot / 2
for s, w, f in zip(pend, ws, (A(21, 0.9), A(22, 0.1), A(22, 0.9))):
    c_ = P.chip(s, (x + w / 2, -1.12), K.YELLOW, 0.36, INK, w=w)
    d.g.pop(c_, f)
    K.pulse(c_, f + 12, 1.12, 8)
    x += w + 0.2

# ======================================================================
# E 最後の1行
# ======================================================================
I.final(["Spring Bootの動きは、", "ログとテストで追える。"])
I.render()
