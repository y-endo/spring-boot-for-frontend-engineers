"""DIコンテナ：配役表で役者をつなぐ（第11章）

場面構成（行番号は di-container.json の lines）
  0      タイトル
  1-2    台本    : 台本には「相手役が必要」とだけ書いてある／自分では連れてこない
  3-5    スキャン: 演出家がフォルダを巡回 → @Component の札 → 名簿に載せる
  6-10   グラフ  : 依存の矢印 → 依存される側から生成 → コンストラクタで手渡し → 幕が上がる
  11-12  singleton: 1人の役者が全公演（全リクエスト）に出続ける
  13-14  対比    : React の Provider（木）と Spring（グラフ・一括）
  15-16  失敗    : 自分で new した役者は名簿にいない → 相手役が null
  17     まとめ
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
import bpy
import bl_kit as K

T = K.setup()
A = lambda i, off=0.0: K.at(T, i, off)       # 行 i の開始フレーム
E = lambda i, off=0.0: K.end_of(T, i, off)   # 行 i の終了フレーム

CUST = K.hexc("#34406b")     # 未生成の役者（カード）の色
INK = K.BG                   # 明るい地の上の文字色
CURTAIN = K.hexc("#8f2442")
CURTAIN_D = K.hexc("#6d1a33")


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None):
    w = w or (len(label) * 0.78 * size + 0.9)
    return K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size)


def hdr(label, color, tc=K.WHITE):
    w = len(label) * 0.78 * 0.4 + 0.9
    return K.card(label, loc=(-6.7 + w / 2, 3.65), w=w, h=0.8, color=color, text_color=tc, size=0.4)


def out(objs, f, step=2):
    for i, o in enumerate(objs):
        K.pop_out(o, f + i * step)


def actor(loc, color, label, s=1.2, lab=0.36):
    """ラベル付きの役者。ラベルは親子にしてあるので一緒に動く。"""
    p = K.person(loc, color, s)
    K.text(label, lab, (0, -0.68 * s - 0.42, 0.2), K.WHITE, 700, parent=p)
    return p


def curtain_panel(w, h, loc):
    """幕。赤い板に縦のひだ。root を返す。"""
    root = bpy.data.objects.new("curtain", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], 0.7)
    body = K.rounded_rect("cbody", w, h, (0, 0, 0), CURTAIN, 0.2)
    body.parent = root
    n = 9
    for i in range(n):
        x = -w / 2 + (i + 0.5) * w / n
        st = K.plane("fold", w / n * 0.42, h, (x, 0, 0.02), CURTAIN_D)
        st.parent = root
    return root


# ======================================================================
# 0. タイトル
# ======================================================================
title_out = K.title_card(T, "DIコンテナ", "配役表で役者をつなぐ", until_line=0)
stage = K.rounded_rect("stage", 15.2, 6.7, (0, 0.85, -1), K.BG2, 0.5)
K.pop_in(stage, A(1) - 4, 12)


def cam(f, to=(0, 0), zoom=16, dur=24):
    """カメラの寄り・引き。舞台パネルも同じ動きで拡大・移動し、寄った場面でも端が画面に入らないようにする。"""
    K.camera_move(f, to=to, zoom=zoom, dur=dur)
    if zoom >= 15.9:
        loc, sc_ = (0, 0.85), (1, 1, 1)
    else:
        loc, sc_ = to, ((zoom + 1.5) / 15.2, (zoom * 0.5625 + 1.5) / 6.7, 1)
    K.move(stage, f, loc, dur, "SINE", "EASE_IN_OUT")
    K._key(stage, "scale", f, K._cur(stage, "scale"), "SINE", "EASE_IN_OUT")
    K._key(stage, "scale", f + dur, sc_, "SINE", "EASE_IN_OUT")

# ======================================================================
# 1-2. 台本：相手役が必要、とだけ書いてある
# ======================================================================
a1, a2, a3, a4, a5, a6 = (A(i) for i in range(1, 7))
a7, a8, a9, a10, a11, a12 = (A(i) for i in range(7, 13))
a13, a14, a15, a16, a17 = (A(i) for i in range(13, 18))

h1 = hdr("台本（クラス）", K.TEAL)
K.pop_in(h1, a1 + 1)

act = actor((-4.6, 0.7), K.ORANGE, "PollService", 1.3, 0.38)
K.pop_in(act, a1 + 2)
K.pulse(act, a1 + 20, 1.1, 8)

script = K.card("", loc=(1.7, 1.5), w=7.0, h=3.0, color=K.WHITE, size=0.4)
K.text("台本", 0.44, (0, 0.85, 0.05), INK, 900, parent=script)
K.text("相手役：PollRepository", 0.5, (0, -0.1, 0.05), INK, 700, parent=script)
K.text("が必要", 0.5, (0, -0.85, 0.05), K.RED, 700, parent=script)
K.pop_in(script, a1 + 8)
K.pulse(script, a1 + 60, 1.05, 8)

# 2: 自分では相手を連れてこない
K.pop_out(script, a2)
ghost = K.card("PollRepository", loc=(3.6, 0.7), w=4.4, h=1.3, color=K.DARK, text_color=K.GRAY, size=0.42)
K.pop_in(ghost, a2 + 6)
dl = K.dashed_line((-3.2, 0.7), (1.1, 0.7), K.GRAY, 0.09)
K.pop_in(dl, a2 + 12)
xm = K.cross_mark((-1.0, 0.7), 1.0, K.RED, 0.2)
K.pop_in(xm, a2 + 26)
K.pulse(xm, a2 + 36, 1.2, 8)
no_self = chip("自分では連れてこない", (0.3, 2.7), K.RED, 0.46)
K.pop_in(no_self, a2 + 34)
K.shake(act, a2 + 40, 0.1, 6, 2)

# ======================================================================
# 3-5. 起動時：フォルダを巡回 → 札 → 名簿
# ======================================================================
out([h1, ghost, dl, xm, no_self, act], a3 - 2, 1)
h2 = hdr("起動時", K.PURPLE)
K.pop_in(h2, a3 + 4)

FX = [-5.2, -1.73, 1.73, 5.2]
FN = ["PollController", "PollService", "PollRepository", "Util"]
folders = []
for i, (x, n) in enumerate(zip(FX, FN)):
    c = K.card(n, loc=(x, 1.5), w=3.2, h=1.1, color=CUST, size=0.38)
    K.pop_in(c, a3 + 6 + i * 6)
    folders.append(c)

boss = actor((-5.2, -0.5), K.PURPLE, "演出家", 1.1, 0.38)
K.pop_in(boss, a3 + 14)
tags = []
f = a3 + 40
for i in range(4):
    K.move(boss, f, (FX[i], -0.5), 9)
    K.pulse(folders[i], f + 9, 1.1, 6)
    if i < 3:
        tg = chip("@Component", (FX[i], 2.7), K.YELLOW, 0.3, INK, w=2.5)
        K.pop_in(tg, f + 11)
        tags.append(tg)
    else:
        nt = K.dot((FX[i], 2.6), 0.4, K.GRAY, "－")
        K.pop_in(nt, f + 11)
        tags.append(nt)
    f += 26

# 4: 札が付いた役者だけ名簿へ
cam(a4, to=(0, 0.5), zoom=14, dur=22)
roster = K.card("", loc=(-0.4, -1.15), w=9.8, h=1.9, color=K.WHITE, size=0.4)
K.text("名簿", 0.44, (-4.45, 0, 0.05), INK, 900, parent=roster)
K.pop_in(roster, a4 + 4)
K.move(boss, a4, (5.9, -0.9), 14)
minis = []
for i, (x, n) in enumerate(zip((-2.9, -0.1, 2.7), FN[:3])):
    m = K.card(n, loc=(x, -1.15), w=2.7, h=0.8, color=K.ORANGE, size=0.28)
    K.pop_in(m, a4 + 22 + i * 18)
    K.pulse(folders[i], a4 + 20 + i * 18, 1.12, 6)
    minis.append(m)
K.shake(folders[3], a4 + 80, 0.1, 6, 2)

# 5: コンポーネントスキャン
scan = chip("コンポーネントスキャン", (1.8, 3.55), K.YELLOW, 0.45, INK)
K.pop_in(scan, a5 + 4)
K.pulse(scan, a5 + 24, 1.12, 8)
for i, m in enumerate(minis):
    K.pulse(m, a5 + 30 + i * 6, 1.1, 8)

# ======================================================================
# 6-10. 依存グラフ → 生成順 → 手渡し → 幕が上がる
# ======================================================================
cam(a6 - 4, to=(0, 0), zoom=16, dur=20)
out([h2, scan, roster, boss] + folders + tags + minis, a6 - 4, 1)
h3 = hdr("依存グラフ", K.BLUE)
K.pop_in(h3, a6 + 4)

NY = 0.9      # グラフの行のy
NX = [-4.9, 0.0, 4.9]
NN = ["Controller", "Service", "Repository"]
nodes = []
for i, (x, n) in enumerate(zip(NX, NN)):
    c = K.card(n, loc=(x, NY), w=3.4, h=1.3, color=CUST, size=0.46)
    K.pop_in(c, a6 + 10 + i * 10)
    nodes.append(c)
arrs = []
for i in range(2):
    ar = K.arrow((NX[i] + 1.75, NY), (NX[i + 1] - 1.75, NY), K.GRAY, 0.1, 0.34)
    K.grow_x(ar, a6 + 46 + i * 8, 12)
    arrs.append(ar)
needs = []
for i in range(2):
    nd = chip("必要", ((NX[i] + NX[i + 1]) / 2, NY + 0.95), K.GRAY, 0.3, K.WHITE, w=1.2)
    K.pop_in(nd, a7 + 4 + i * 56)
    K.pulse(nd, a7 + 14 + i * 56, 1.2, 8)
    K.pulse(arrs[i], a7 + 10 + i * 56, 1.3, 8)
    needs.append(nd)

# 8: 依存される側から順に生成（番号と色）
ORDER = [2, 1, 0]
nums = []
for k, ni in enumerate(ORDER):
    fk = a8 + 4 + k * 34
    K.recolor(nodes[ni], fk + 6, K.ORANGE)
    K.pulse(nodes[ni], fk + 6, 1.12, 8)
    nm = K.dot((NX[ni] + 1.45, NY + 0.8), 0.4, K.TEAL, str(k + 1))
    K.pop_in(nm, fk)
    nums.append(nm)
orderc = chip("依存される側から作る", (0, NY - 2.0), K.TEAL, 0.42)
K.pop_in(orderc, a8 + 10)

# 9: コンストラクタで相手役を手渡す
cam(a9, to=(0, 0.9), zoom=14, dur=20)
K.pop_out(orderc, a9)
hand = chip("コンストラクタで手渡す", (0, NY - 2.0), K.YELLOW, 0.42, INK)
K.pop_in(hand, a9 + 8)
toks = []
for k, (src, dst, nm) in enumerate([(2, 1, "Repository"), (1, 0, "Service")]):
    fk = a9 + 24 + k * 36
    sx0 = NX[src] - (1.2 if src == 1 else 0.0)     # 手前に置かれたトークンと重ならない位置から出す
    dx1 = NX[dst] + (1.15 if dst == 1 else 0.0)
    tk = K.card(nm, loc=(sx0, NY - 1.1), w=2.3, h=0.62, color=K.ORANGE, size=0.28)
    K.pop_in(tk, fk)
    K.move(tk, fk + 10, (dx1, NY - 1.1), 14)
    K.pulse(nodes[dst], fk + 26, 1.1, 8)
    toks.append(tk)

# 10: 幕が上がる＝起動完了
cam(a10 - 2, to=(0, 0), zoom=16, dur=16)
curt = curtain_panel(15.2, 7.2, (0, 9.5))
K.move(curt, a10, (0, 1.0), 12)
K.move(curt, a10 + 46, (0, 10.5), 20)
okc = chip("起動完了", (0, NY + 2.0), K.GREEN, 0.6, INK)
K.pop_in(okc, a10 + 62)
K.pulse(okc, a10 + 76, 1.12, 8)
for i, n in enumerate(nodes):
    K.pulse(n, a10 + 70 + i * 5, 1.1, 8)
K.pop_out(h3, a10 - 2)
out([hand] + toks, a10 - 2, 1)
for k in nums:
    K.pop_out(k, a10 - 2)

# ======================================================================
# 11-12. singleton：1人の役者が全公演に出る
# ======================================================================
f_out = a11 - 6
out(nodes + arrs + needs + [okc], f_out, 1)
h4 = hdr("シングルトン", K.PURPLE)
K.pop_in(h4, a11 + 2)
sing = actor((1.4, 0.8), K.ORANGE, "PollService", 1.4, 0.38)
K.pop_in(sing, a11 + 8)
reqs = []
for i, y in enumerate((2.2, 0.8, -0.6)):
    r = K.card(f"リクエスト{i + 1}", loc=(-5.0, y), w=3.0, h=1.0, color=K.BLUE, size=0.4)
    K.pop_in(r, a11 + 26 + i * 22)
    ay = 0.8 + (y - 0.8) * 0.3
    ar = K.arrow((-3.45, y), (0.55, ay), K.BLUE, 0.09, 0.3)
    K.grow_x(ar, a11 + 36 + i * 22, 12)
    K.pulse(sing, a11 + 52 + i * 22, 1.1, 8)
    reqs += [r, ar]
one = chip("アプリ全体で1つ", (1.4, 3.1), K.PURPLE, 0.46)
K.pop_in(one, a11 + 30)
K.pulse(one, a11 + 100, 1.12, 8)

# 12: リクエストごとに作り直さない
g1 = K.person((4.7, 0.8), K.GRAY, 1.0)
g2 = K.person((6.5, 0.8), K.GRAY, 1.0)
K.pop_in(g1, a12 + 4)
K.pop_in(g2, a12 + 12)
gx1 = K.cross_mark((4.7, 0.8), 1.1, K.RED, 0.2)
gx2 = K.cross_mark((6.5, 0.8), 1.1, K.RED, 0.2)
K.pop_in(gx1, a12 + 24)
K.pop_in(gx2, a12 + 32)
nomk = chip("毎回は作らない", (5.6, -1.2), K.RED, 0.4)
K.pop_in(nomk, a12 + 40)
K.pulse(sing, a12 + 60, 1.12, 8)

# ======================================================================
# 13-14. React Provider（木）と Spring（グラフ）
# ======================================================================
f_out = a13 - 6
out([h4, sing, one, g1, g2, gx1, gx2, nomk] + reqs, f_out, 1)
grp = bpy.data.objects.new("reactgrp", None)
bpy.context.scene.collection.objects.link(grp)
grp.location = (3.9, 0, 0)     # 最初は中央（あとで左へ寄せる）
div = K.dashed_line((-0.15, 3.9), (-0.15, -2.4), K.GRAY, 0.07)
K.pop_in(div, a14 + 2)
rc = chip("React", (-3.9, 3.55), K.BLUE, 0.4, w=2.1)
K.pop_in(rc, a13 + 4)
prov = K.card("Provider", loc=(-3.9, 2.3), w=2.8, h=0.9, color=K.BLUE, size=0.36)
K.pop_in(prov, a13 + 12)
cx = (-6.0, -3.9, -1.8)
kids = []
vals = []
for i, x in enumerate(cx):
    kd = K.card("子", loc=(x, 0.3), w=1.7, h=0.9, color=CUST, size=0.4)
    K.pop_in(kd, a13 + 22 + i * 6)
    kids.append(kd)
    ar = K.arrow((-3.9 + (x + 3.9) * 0.2, 1.8), (x, 0.8), K.GRAY, 0.08, 0.26)
    K.grow_x(ar, a13 + 30 + i * 6, 10)
    kids.append(ar)
    v = K.dot((-3.9, 1.7), 0.32, K.YELLOW, "値")
    K.pop_in(v, a13 + 52 + i * 4)
    K.move(v, a13 + 62 + i * 4, (x, 0.3), 12)
    K.pop_out(v, a13 + 84)
    vals.append(v)
K.pulse(prov, a13 + 56, 1.1, 8)
tree = chip("木の中で渡す", (-3.9, -1.5), K.BLUE, 0.38, w=3.4)
for _o in [rc, prov, tree] + kids + vals:
    _o.parent = grp
K.move(grp, a14 + 2, (0, 0, 0), 18)
K.pop_in(tree, a13 + 80)
K.pulse(tree, a13 + 94, 1.1, 8)

# 14: Spring はグラフを起動時に一括で
sc_ = chip("Spring", (3.9, 3.55), K.ORANGE, 0.4, w=2.1)
K.pop_in(sc_, a14 + 2)
SP = {"C": (3.9, 2.3), "S": (1.5, 0.7), "V": (6.3, 0.7), "R": (3.9, -0.95)}
SPN = {"C": "Controller", "S": "Service", "V": "Validator", "R": "Repository"}
sp_nodes = {}
for k, (key, p) in enumerate(SP.items()):
    c = K.card(SPN[key], loc=p, w=2.7, h=0.9, color=CUST, size=0.34)
    K.pop_in(c, a14 + 8 + k * 5)
    sp_nodes[key] = c
sp_arrs = []
for (s_, d_), (p1, p2) in {("C", "S"): ((3.3, 1.85), (2.2, 1.2)), ("C", "V"): ((4.5, 1.85), (5.6, 1.2)),
                           ("S", "R"): ((2.2, 0.25), (3.3, -0.5)), ("V", "R"): ((5.6, 0.25), (4.5, -0.5))}.items():
    ar = K.arrow(p1, p2, K.GRAY, 0.08, 0.26)
    K.grow_x(ar, a14 + 34, 10)
    sp_arrs.append(ar)
for key, c in sp_nodes.items():
    K.recolor(c, a14 + 62, K.ORANGE)
    K.pulse(c, a14 + 62, 1.12, 8)
once = chip("起動時に一括", (1.8, -2.0), K.ORANGE, 0.34, w=2.7)
K.pop_in(once, a14 + 56)
graph = chip("木ではなくグラフ", (5.6, -2.0), K.TEAL, 0.34, w=3.2)
K.pop_in(graph, a14 + 74)
K.pulse(graph, a14 + 90, 1.1, 8)
K.pulse(sp_nodes["R"], a14 + 96, 1.2, 8)

# ======================================================================
# 15-16. 失敗：自分で new した役者は名簿にいない
# ======================================================================
f_out = a15 - 6
out([div, rc, prov, tree, sc_, once, graph] + kids + list(sp_nodes.values()) + sp_arrs, f_out, 1)
h5 = hdr("失敗例", K.RED)
K.pop_in(h5, a15 + 2)
K.pop_out(h5, a16 - 2)
rost2 = K.card("", loc=(-4.2, 1.5), w=5.6, h=3.3, color=K.WHITE, size=0.4)
K.text("名簿", 0.42, (0, 1.3, 0.05), INK, 900, parent=rost2)
K.pop_in(rost2, a15 + 4)
mm = []
for i, n in enumerate(FN[:3]):
    m = K.card(n, loc=(-4.2, 2.0 - i * 0.8 + 0.0), w=3.6, h=0.65, color=K.ORANGE, size=0.28)
    K.pop_in(m, a15 + 12 + i * 5)
    mm.append(m)
rogue = actor((3.4, 1.5), K.ORANGE, "自分でnewした", 1.3, 0.34)
K.pop_in(rogue, a15 + 20)
newc = chip("new", (4.7, 2.9), K.PURPLE, 0.4, w=1.4)
K.pop_in(newc, a15 + 28)
rdl = K.dashed_line((-1.2, 1.5), (2.4, 1.5), K.GRAY, 0.09)
K.pop_in(rdl, a15 + 34)
rx = K.cross_mark((0.6, 1.5), 1.0, K.RED, 0.2)
K.pop_in(rx, a15 + 44)
notin = chip("名簿にいない", (0.6, 2.8), K.RED, 0.42)
K.pop_in(notin, a15 + 50)
K.pulse(notin, a15 + 66, 1.12, 8)

# 16: 相手役がいない → null
cam(a16, to=(1.2, 0.6), zoom=13.5, dur=20)
nullc = K.card("相手役：null", loc=(3.4, -0.9), w=4.2, h=1.2, color=K.RED, size=0.46)
K.pop_in(nullc, a16 + 14)
K.shake(nullc, a16 + 28, 0.15, 6, 2)
K.recolor(rogue, a16 + 24, K.RED)
K.shake(rogue, a16 + 34, 0.12, 6, 2)

# ======================================================================
# 17. まとめ
# ======================================================================
cam(a17 - 14, to=(0, 0), zoom=16, dur=18)
out([h5, rost2, rogue, newc, rdl, rx, notin, nullc] + mm, a17 - 14, 0)
m1 = K.text("必要なものは、", 0.95, (0, 2.5, 0.5), K.WHITE, 900)
m2 = K.text("自分で作らず、受け取る", 0.95, (0, 1.15, 0.5), K.ORANGE, 900)
K.pop_in(m1, a17 + 4)
K.pop_in(m2, a17 + 14)
giver = K.person((-4.2, -0.8), K.PURPLE, 1.1)
taker = K.person((4.2, -0.8), K.ORANGE, 1.1)
K.pop_in(giver, a17 + 24)
K.pop_in(taker, a17 + 30)
K.bob(giver, a17 + 40, a17 + 220, 0.12, 20)
K.bob(taker, a17 + 46, a17 + 220, 0.12, 20)
for k in range(4):
    tkn = K.card("Repository", loc=(-2.1, -0.5), w=2.4, h=0.62, color=K.TEAL, size=0.28)
    fk = a17 + 40 + k * 44
    K.pop_in(tkn, fk)
    K.move(tkn, fk + 8, (2.1, -0.5), 18)
    K.pulse(taker, fk + 26, 1.1, 8)
    K.pop_out(tkn, fk + 30)

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
