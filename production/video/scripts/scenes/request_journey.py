"""リクエストの旅（第12章）

場面構成（行番号は request-journey.json の lines）
  0      タイトル
  1-2    ブラウザが fetch → 封筒が Tomcat（入口）へ
  3-5    スレッドプールの店員が1人、封筒を受け取る／他の店員も同時／専任
  6      Filter の列でスタンプ
  7      DispatcherServlet が宛先表（HandlerMapping）で Controller を選ぶ
  8-9    Jackson が JSON を record に／@Valid の検問
  10-11  Controller → Service → Repository → DB／店員はその場で待つ
  12-14  結果が戻る／Jackson が JSON に／Filter を逆順に通ってブラウザへ
  15-16  同じ店員が最初から最後まで／1リクエスト＝1スレッド
  17     店員はプールに戻る
  18     まとめ
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
import bpy
import bl_kit as K

T = K.setup()
A = lambda i, off=0.0: K.at(T, i, off)       # 行 i の開始フレーム
E = lambda i, off=0.0: K.end_of(T, i, off)   # 行 i の終了フレーム

CUST = K.hexc("#34406b")
INK = K.BG
SERVER = K.hexc("#232b47")
POOL = K.hexc("#2f3a5f")
FLAP = K.hexc("#8b93b0")
DEEP = K.hexc("#6c5ce7")
OTHER_ENV = K.hexc("#9db7ff")


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None):
    w = w or (len(label) * 0.78 * size + 0.9)
    return K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size)


def hdr(label, color, tc=K.WHITE):
    w = len(label) * 0.78 * 0.4 + 0.9
    return K.card(label, loc=(-6.7 + w / 2, 3.65), w=w, h=0.8, color=color, text_color=tc, size=0.4)


def out(objs, f, step=2):
    for i, o in enumerate(objs):
        K.pop_out(o, f + i * step)


def phase(label, color, f_in, f_out, tc=K.WHITE):
    h = hdr(label, color, tc)
    K.pop_in(h, f_in)
    K.pop_out(h, f_out)
    return h


def actor(loc, color, label, s=0.9, lab=0.32):
    p = K.person(loc, color, s)
    K.text(label, lab, (0, -0.68 * s - 0.42, 0.2), K.WHITE, 700, parent=p)
    return p


def envelope(loc, body=K.WHITE, z=0.5):
    """封筒。ふたの V は2本の細い板。"""
    root = bpy.data.objects.new("env", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    b = K.rounded_rect("ebody", 1.5, 1.0, (0, 0, 0), body, 0.12)
    b.parent = root
    for sgn in (-1, 1):
        p = K.plane("flap", 0.86, 0.07, (sgn * 0.375, 0.24, 0.02), FLAP)
        p.rotation_euler = (0, 0, -sgn * math.radians(28))
        p.parent = root
    return root


def station(label, x, y=1.9, color=CUST, w=2.6, h=1.4, sub=None, size=0.38, tc=K.WHITE):
    return K.card(label, loc=(x, y), w=w, h=h, color=color, size=size, sub=sub, text_color=tc)


def stamp(env, local, color, f):
    s = K.dot((0, 0), 0.2, color)
    s.parent = env
    s.location = (local[0], local[1], 0.2)
    K.pop_in(s, f, 8)
    return s


SLOT = {"browser": -6.0, "tomcat": -3.0, "log": 0.0, "sec": 3.0, "disp": 6.0}
WY = -0.6          # 店員が立つ高さ
HOLD = (1.35, 0.45)  # 店員が封筒を持つ位置


# ======================================================================
# 0. タイトル
# ======================================================================
title_out = K.title_card(T, "リクエストの旅", "1つのリクエストが運ばれる道", until_line=0)
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

a = [A(i) for i in range(19)]

# ======================================================================
# 1-2. ブラウザ → 封筒 → Tomcat
# ======================================================================
phase("fetch で送る", K.BLUE, a[1] + 4, a[3] - 4)
browser = station("ブラウザ", SLOT["browser"], color=K.BLUE, size=0.4)
K.pop_in(browser, a[1] + 2)
K.pulse(browser, a[1] + 30, 1.12, 8)
env1 = envelope((SLOT["browser"], 0.3))
K.pop_in(env1, a[1] + 18)
lab_http = chip("HTTPリクエスト", (SLOT["browser"], -0.75), K.YELLOW, 0.36, INK)
K.pop_in(lab_http, a[1] + 28)
K.pulse(env1, a[1] + 56, 1.15, 8)

# 2: サーバーの入口へ
server1 = K.rounded_rect("server1", 12.0, 5.2, (1.5, 0.6, -0.5), SERVER, 0.4)
K.pop_in(server1, a[1] + 56)
srv_t = K.text("サーバー", 0.34, (5.9, 2.95, 0.1), K.GRAY, 700)
K.pop_in(srv_t, a[1] + 62)
tomcat = station("Tomcat", SLOT["tomcat"], color=K.PURPLE, sub="入口", size=0.42)
K.pop_in(tomcat, a[2] + 8)
path = K.dashed_line((-5.1, 0.3), (-3.9, 0.3), K.GRAY, 0.08)
K.pop_in(path, a[2] + 12)
K.pop_out(lab_http, a[2] + 14)
K.pop_out(path, a[3] + 2)
K.move(env1, a[2] + 20, (SLOT["tomcat"], 0.3), 26)
K.pulse(tomcat, a[2] + 48, 1.12, 8)

# ======================================================================
# 3-5. スレッドプール：店員が封筒を受け取る
# ======================================================================
POOLX = [0.6, 2.4, 4.2, 6.0]
pool = K.rounded_rect("pool", 8.6, 3.3, (3.0, 1.3, -0.3), POOL, 0.35)
pool_t = K.text("スレッドプール", 0.36, (3.0, 2.5, 0.1), K.WHITE, 700)
K.pop_in(pool, a[3] + 2)
K.pop_in(pool_t, a[3] + 8)
workers = []
for i, x in enumerate(POOLX):
    w = actor((x, 1.0), K.ORANGE, f"スレッド{i + 1}")
    K.pop_in(w, a[3] + 12 + i * 5)
    workers.append(w)
W = workers[2]
K.pulse(W, a[3] + 44, 1.15, 8)
# 店員3が出てきて封筒を受け取る
K.move(W, a[3] + 56, (4.2, -1.35), 12)
K.move(W, a[3] + 70, (SLOT["tomcat"], WY), 26)
K.pop_out(env1, a[3] + 94)
held = envelope(HOLD)
held.parent = W
K.pop_in(held, a[3] + 94)
K.pulse(W, a[3] + 98, 1.12, 8)
K.pulse(tomcat, a[3] + 98, 1.1, 8)

# 4: ほかの店員も別の封筒を同時に運ぶ
others = []
for k, i in enumerate((0, 1, 3)):
    oe = envelope((0, 0), OTHER_ENV)
    oe.parent = workers[i]
    oe.location = (0.85, 0.4, 0.5)
    K.pop_in(oe, a[4] + 6 + k * 8, 10, 0.55)
    K.bob(workers[i], a[4] + 14 + k * 4, a[5] - 6, 0.16, 18)
    others.append(oe)
K.pulse(W, a[4] + 60, 1.12, 8)

# 5: この店員が、この封筒の担当
cam(a[5], to=(-2.0, 0.2), zoom=12, dur=22)
out(others + workers[0:2] + [workers[3]] + [pool, pool_t], a[5], 1)
phase("入口（Tomcat）", K.PURPLE, a[3] + 4, a[5] - 2)
mine = chip("この封筒の担当", (1.5, WY), K.YELLOW, 0.42, INK)
K.pop_in(mine, a[5] + 24)
K.pulse(mine, a[5] + 44, 1.1, 8)
K.pulse(W, a[5] + 50, 1.1, 8)

# ======================================================================
# 6. Filter の列：通るたびにスタンプ
# ======================================================================
cam(a[6], to=(0.8, 0.4), zoom=13.5, dur=20)
K.pop_out(mine, a[6])
K.pop_out(browser, a[6])
flog = station("ログ", SLOT["log"], color=K.TEAL, sub="Filter", size=0.42)
fsec = station("Security", SLOT["sec"], color=K.TEAL, sub="Filter", size=0.36)
disp = station("Dispatcher", SLOT["disp"], color=K.ORANGE, sub="Servlet", size=0.34)
K.pop_in(flog, a[6] + 4)
K.pop_in(fsec, a[6] + 10)
K.pop_in(disp, a[6] + 16)
K.move(W, a[6] + 24, (SLOT["log"], WY), 14)
K.pulse(flog, a[6] + 38, 1.12, 8)
st1 = stamp(held, (-0.38, -0.22), K.YELLOW, a[6] + 42)
K.move(W, a[6] + 62, (SLOT["sec"], WY), 14)
K.pulse(fsec, a[6] + 76, 1.12, 8)
st2 = stamp(held, (0.38, -0.22), K.BLUE, a[6] + 80)
sp_chip = chip("スタンプ", (1.5, 3.2), K.YELLOW, 0.36, INK)
K.pop_in(sp_chip, a[6] + 44)
K.pulse(sp_chip, a[6] + 82, 1.15, 8)

# ======================================================================
# 7. DispatcherServlet が宛先表で Controller を選ぶ
# ======================================================================
cam(a[7], to=(0, 0), zoom=16, dur=20)
out([browser, tomcat, flog, fsec, server1, srv_t, sp_chip], a[7], 1)
K.move(disp, a[7] + 2, (-5.4, 1.9), 16)
K.move(W, a[7] + 2, (-5.4, WY), 16)
phase("宛先表（HandlerMapping）", K.ORANGE, a[7] + 4, a[8] - 2)
table = K.card("", loc=(0.4, 1.9), w=5.6, h=3.0, color=K.WHITE, size=0.4)
K.text("宛先表", 0.4, (0, 1.15, 0.05), INK, 900, parent=table)
K.pop_in(table, a[7] + 16)
ROWS = ["GET /polls → list()", "POST /polls → create()", "GET /polls/{id} → get()"]
rows = []
for i, r in enumerate(ROWS):
    rc = K.card(r, loc=(0.4, 2.3 - i * 0.7 + 0.0 - 0.1), w=4.9, h=0.55, color=CUST, size=0.27)
    K.pop_in(rc, a[7] + 26 + i * 6)
    rows.append(rc)
arr_dt = K.arrow((-4.0, 1.9), (-2.5, 1.9), K.GRAY, 0.1, 0.32)
K.grow_x(arr_dt, a[7] + 22, 10)
K.recolor(rows[1], a[7] + 66, K.YELLOW)
K.pulse(rows[1], a[7] + 66, 1.08, 8)
ctrl = station("Controller", 5.7, color=K.ORANGE, sub="create()", w=2.8, size=0.4)
K.pop_in(ctrl, a[7] + 88)
arr_c = K.arrow((3.2, 1.5), (4.2, 1.8), K.YELLOW, 0.1, 0.32)
K.grow_x(arr_c, a[7] + 80, 10)
K.pulse(ctrl, a[7] + 104, 1.12, 8)
K.move(W, a[7] + 112, (4.9, WY), 22)

# ======================================================================
# 8-9. Jackson が JSON → record／@Valid の検問
# ======================================================================
out([disp, table, arr_dt, arr_c, ctrl] + rows, a[8] - 2, 1)
phase("JSON → Java", K.TEAL, a[8] + 4, a[9] - 2)
phase("@Valid の検問", K.PURPLE, a[9] + 2, a[10] - 2)
K.move(W, a[8] + 2, (-5.6, WY), 18)
K.pop_out(held, a[8] + 20)
jcard = K.card("", loc=(-5.6, 1.9), w=2.8, h=1.7, color=K.WHITE, size=0.4)
K.text("JSON", 0.42, (0, 0.45, 0.05), INK, 900, parent=jcard)
for k, bw in enumerate((1.9, 1.5, 1.7)):
    br = K.plane("jline", bw, 0.13, (-(1.9 - bw) / 2, 0.0 - k * 0.3, 0.05), K.GRAY)
    br.parent = jcard
K.pop_in(jcard, a[8] + 22)
jack = station("Jackson", -2.0, color=K.TEAL, sub="変換", w=2.6, size=0.42)
K.pop_in(jack, a[8] + 34)
ja1 = K.arrow((-4.1, 1.9), (-3.4, 1.9), K.GRAY, 0.1, 0.3)
K.grow_x(ja1, a[8] + 30, 8)
K.move(W, a[8] + 38, (-2.0, WY), 14)
K.pulse(jack, a[8] + 52, 1.12, 8)
ja2 = K.arrow((-0.6, 1.9), (0.3, 1.9), K.GRAY, 0.1, 0.3)
K.grow_x(ja2, a[8] + 60, 8)
rec = K.card("PollRequest", loc=(1.7, 1.9), w=2.4, h=1.4, color=K.ORANGE, size=0.3, sub="record")
K.pop_in(rec, a[8] + 68)
K.move(W, a[8] + 72, (1.7, WY), 14)
K.pulse(rec, a[8] + 88, 1.12, 8)

# 9: @Valid の検問
pil1 = K.plane("pil1", 0.3, 2.4, (4.3, 1.9, 0.0), K.YELLOW)
pil2 = K.plane("pil2", 0.3, 2.4, (6.9, 1.9, 0.0), K.YELLOW)
topb = K.plane("topb", 2.9, 0.2, (5.6, 3.0, 0.0), K.YELLOW)
for o in (pil1, pil2, topb):
    K.pop_in(o, a[9] + 4)
vch = chip("@Valid", (5.6, 3.55), K.PURPLE, 0.4, w=2.4)
K.pop_in(vch, a[9] + 10)
K.move(rec, a[9] + 16, (5.6, 1.9), 22)
K.move(W, a[9] + 16, (0.6, WY), 22)
K.recolor(rec, a[9] + 40, K.GREEN)
okd = K.dot((5.6, 0.7), 0.45, K.GREEN, "OK")
K.pop_in(okd, a[9] + 42)
K.pulse(okd, a[9] + 54, 1.2, 8)
bad = K.card("PollRequest", loc=(0.6, 1.9), w=2.4, h=1.4, color=K.RED, size=0.3, sub="title が空")
K.pop_in(bad, a[9] + 56)
K.move(bad, a[9] + 66, (2.6, 1.9), 18)
K.shake(bad, a[9] + 86, 0.14, 6, 2)
badx = K.cross_mark((2.6, 1.9), 1.2, K.RED, 0.22)
K.pop_in(badx, a[9] + 88)
stopc = chip("ここで止める", (2.6, 0.5), K.RED, 0.36)
K.pop_in(stopc, a[9] + 94)

# ======================================================================
# 10-11. Controller → Service → Repository → DB、店員はその場で待つ
# ======================================================================
out([jcard, jack, ja1, ja2, rec, pil1, pil2, topb, vch, okd, bad, badx, stopc], a[10] - 2, 1)
phase("アプリの本体", K.ORANGE, a[10] + 4, a[11] - 2)
LX = [-5.4, -1.8, 1.8, 5.4]
LN = ["Controller", "Service", "Repository", "DB"]
layers = []
for i, (x, n) in enumerate(zip(LX, LN)):
    c = station(n, x, color=(DEEP if n == "DB" else K.ORANGE), w=2.6, size=0.4)
    K.pop_in(c, a[10] + 6 + i * 5)
    layers.append(c)
larr = []
for i in range(3):
    ar = K.arrow((LX[i] + 1.4, 1.9), (LX[i + 1] - 1.4, 1.9), K.GRAY, 0.1, 0.3)
    K.grow_x(ar, a[10] + 30 + i * 30, 8)
    larr.append(ar)
K.move(W, a[10] + 4, (LX[0], WY), 16)
for i in range(4):
    fk = a[10] + 22 + i * 32
    if i > 0:
        K.move(W, fk, (LX[i], WY), 14)
    K.pulse(layers[i], fk + 14, 1.12, 8)

# 11: DB の返事を待つ間、店員はその場で待つ
cam(a[11], to=(3.4, 0.5), zoom=12, dur=20)
K.recolor(W, a[11] + 8, K.YELLOW)
wdot = K.dot((6.3, 0.55), 0.5, K.YELLOW, "待ち")
K.pop_in(wdot, a[11] + 10)
K.pulse(wdot, a[11] + 26, 1.2, 8)
K.pulse(wdot, a[11] + 56, 1.2, 8)
wchip = chip("その場で待つ", (1.7, WY), K.YELLOW, 0.4, INK)
K.pop_in(wchip, a[11] + 22)
K.pulse(layers[3], a[11] + 40, 1.1, 8)
K.pulse(layers[3], a[11] + 70, 1.1, 8)

# ======================================================================
# 12. 結果が同じ道を戻る
# ======================================================================
cam(a[12], to=(0, 0), zoom=16, dur=18)
K.pop_out(wdot, a[12])
K.pop_out(wchip, a[12])
K.recolor(W, a[12] + 2, K.ORANGE)
res = chip("結果", (0, 0), K.GREEN, 0.4, INK, w=1.5)
res.parent = W
res.location = (1.3, 0.6, 0.5)
K.pop_in(res, a[12] + 4)
rarr = []
for i in range(3):
    ar = K.arrow((LX[i + 1] - 1.4, 1.35), (LX[i] + 1.4, 1.35), K.GREEN, 0.1, 0.3)
    K.grow_x(ar, a[12] + 16 + (2 - i) * 22, 8)
    rarr.append(ar)
for k, i in enumerate((2, 1, 0)):
    fk = a[12] + 8 + k * 22
    K.move(W, fk, (LX[i], WY), 14)
    K.pulse(layers[i], fk + 14, 1.12, 8)

# ======================================================================
# 13. Jackson が JSON に変換
# ======================================================================
K.pop_out(res, a[13])
out(layers + larr + rarr, a[13] - 2, 1)
phase("戻り値 → JSON", K.TEAL, a[13] + 4, a[14] - 2)
rsp = K.card("PollResponse", loc=(-5.0, 1.9), w=2.6, h=1.4, color=K.ORANGE, size=0.3, sub="record")
K.pop_in(rsp, a[13] + 6)
K.move(W, a[13] + 2, (-5.0, WY), 12)
jack2 = station("Jackson", -1.6, color=K.TEAL, sub="変換", w=2.6, size=0.42)
K.pop_in(jack2, a[13] + 16)
rb1 = K.arrow((-3.5, 1.9), (-2.95, 1.9), K.GRAY, 0.1, 0.28)
K.grow_x(rb1, a[13] + 14, 8)
K.move(W, a[13] + 24, (-1.6, WY), 12)
K.pulse(jack2, a[13] + 34, 1.12, 8)
rb2 = K.arrow((-0.2, 1.9), (0.45, 1.9), K.GRAY, 0.1, 0.28)
K.grow_x(rb2, a[13] + 40, 8)
jcard2 = K.card("", loc=(2.0, 1.9), w=2.8, h=1.7, color=K.WHITE, size=0.4)
K.text("JSON", 0.42, (0, 0.45, 0.05), INK, 900, parent=jcard2)
for k, bw in enumerate((1.9, 1.5, 1.7)):
    br = K.plane("jline", bw, 0.13, (-(1.9 - bw) / 2, 0.0 - k * 0.3, 0.05), K.GRAY)
    br.parent = jcard2
K.pop_in(jcard2, a[13] + 46)
K.move(W, a[13] + 48, (2.0, WY), 12)
K.pulse(jcard2, a[13] + 60, 1.12, 8)

# ======================================================================
# 14. Filter を逆順に通ってブラウザへ
# ======================================================================
out([rsp, jack2, rb1, rb2, jcard2], a[14] - 2, 1)
phase("逆順で戻る", K.GREEN, a[14] + 2, a[15] - 2, INK)
server2 = K.rounded_rect("server2", 12.0, 5.2, (1.5, 0.6, -0.5), SERVER, 0.4)
K.pop_in(server2, a[14] - 2)
b2 = station("ブラウザ", SLOT["browser"], color=K.BLUE, size=0.4)
t2 = station("Tomcat", SLOT["tomcat"], color=K.PURPLE, sub="入口", size=0.42)
l2 = station("ログ", SLOT["log"], color=K.TEAL, sub="Filter", size=0.42)
s2 = station("Security", SLOT["sec"], color=K.TEAL, sub="Filter", size=0.36)
d2 = station("Dispatcher", SLOT["disp"], color=K.ORANGE, sub="Servlet", size=0.34)
for i, o in enumerate((b2, t2, l2, s2, d2)):
    K.pop_in(o, a[14] - 2 + i * 3)
K.move(W, a[14] - 2, (SLOT["disp"], WY), 12)
held2 = envelope(HOLD, K.GREEN)
held2.parent = W
K.pop_in(held2, a[14] + 8)
for k, key in enumerate(("sec", "log", "tomcat")):
    fk = a[14] + 14 + k * 20
    K.move(W, fk, (SLOT[key], WY), 12)
    K.pulse({"sec": s2, "log": l2, "tomcat": t2}[key], fk + 12, 1.12, 8)
K.pop_out(held2, a[14] + 74)
fly = envelope((SLOT["tomcat"] + 1.35, WY + 0.45), K.GREEN)
K.pop_in(fly, a[14] + 74)
K.move(fly, a[14] + 76, (SLOT["browser"], 0.3), 20)
K.pulse(b2, a[14] + 98, 1.15, 8)
ok200 = K.dot((SLOT["browser"] + 1.5, 2.6), 0.45, K.GREEN, "OK")
K.pop_in(ok200, a[14] + 100)

# ======================================================================
# 15. 同じ店員が最初から最後まで
# ======================================================================
f0 = a[15]
out([b2, t2, l2, s2, d2, server2, fly, ok200], f0 - 2, 1)
phase("1リクエストの旅", K.BLUE, f0 + 2, a[16] - 2)
bar, fill = K.progress_bar((0, 0.5), 12.0, 0.3, K.GREEN, K.DARK)
K.pop_in(bar, f0 + 6)
tks = []
for x, nm in ((-6.0, "受け取り"), (0.0, "DB"), (6.0, "返却")):
    td = K.dot((x, 0.5), 0.26, K.WHITE)
    K.pop_in(td, f0 + 8)
    tl = K.text(nm, 0.38, (x, -0.35, 0.2), K.WHITE, 700)
    K.pop_in(tl, f0 + 10)
    tks += [td, tl]
K.move(W, f0 + 4, (-6.0, 2.0), 14)
K.move(W, f0 + 24, (6.0, 2.0), 60)
K.bar_to(fill, f0 + 24, 1.0, 60)
same = chip("ずっと、同じスレッド3", (0, 3.6), K.TEAL, 0.42)
K.pop_in(same, f0 + 14)
K.pulse(same, f0 + 70, 1.1, 8)

# ======================================================================
# 16. 1リクエスト = 1スレッド
# ======================================================================
out([bar, W, same] + tks, a[16] - 2, 1)
PX = [-4.8, 0.0, 4.8]
pairs = []
for i, x in enumerate(PX):
    lt = K.text(f"リクエスト{'ABC'[i]}", 0.34, (x, 3.55, 0.2), K.WHITE, 700)
    ev = envelope((x, 2.55))
    ar = K.arrow((x, 1.95), (x, 1.4), K.GRAY, 0.1, 0.3)
    wk = actor((x, 0.35), K.ORANGE, f"スレッド{i + 1}")
    fk = a[16] + 6 + i * 22
    K.pop_in(lt, fk)
    K.pop_in(ev, fk + 2)
    K.grow_x(ar, fk + 8, 8)
    K.pop_in(wk, fk + 12)
    K.bob(wk, fk + 30, a[17] - 8, 0.14, 18)
    pairs += [lt, ev, ar, wk]
one = chip("1リクエスト ＝ 1スレッド", (0, -1.95), K.GREEN, 0.44, INK)
K.pop_in(one, a[16] + 80)
K.pulse(one, a[16] + 96, 1.1, 8)

# ======================================================================
# 17. 店員はプールに戻る
# ======================================================================
out(pairs + [one], a[17] - 2, 1)
phase("終わったら", K.GREEN, a[17] + 2, a[18] - 2, INK)
pool2 = K.rounded_rect("pool2", 10.6, 3.3, (0, 1.3, -0.3), POOL, 0.35)
pool2t = K.text("スレッドプール", 0.36, (0, 2.5, 0.1), K.WHITE, 700)
K.pop_in(pool2, a[17] + 2)
K.pop_in(pool2t, a[17] + 8)
pw = []
for i, x in zip((1, 2, 4), (-3.9, -1.3, 1.3)):
    w = actor((x, 1.0), K.ORANGE, f"スレッド{i}")
    K.pop_in(w, a[17] + 10 + i * 4)
    K.bob(w, a[17] + 24 + i * 3, a[18] - 6, 0.14, 18)
    pw.append(w)
w3 = actor((3.9, -1.35), K.ORANGE, "スレッド3")
K.pop_in(w3, a[17] + 18)
K.move(w3, a[17] + 40, (3.9, 1.0), 20)
K.pulse(w3, a[17] + 62, 1.12, 8)
back = chip("プールに戻る", (-2.0, -1.6), K.GREEN, 0.42, INK)
K.pop_in(back, a[17] + 44)
K.pulse(back, a[17] + 64, 1.1, 8)

# ======================================================================
# 18. まとめ
# ======================================================================
out([pool2, pool2t, back, w3] + pw, a[18] - 12, 0)
m1 = K.text("1つのリクエストを、", 0.95, (0, 2.6, 0.5), K.WHITE, 900)
m2 = K.text("1人のスレッドが最後まで運ぶ", 0.88, (0, 1.25, 0.5), K.ORANGE, 900)
K.pop_in(m1, a[18] + 4)
K.pop_in(m2, a[18] + 14)
bL = station("ブラウザ", -5.8, y=-0.8, color=K.BLUE, w=2.4, h=1.1, size=0.34)
dR = station("DB", 5.8, y=-0.8, color=DEEP, w=2.0, h=1.1, size=0.34)
K.pop_in(bL, a[18] + 16)
K.pop_in(dR, a[18] + 18)
w4 = actor((-3.4, -0.8), K.ORANGE, "スレッド3")
K.pop_in(w4, a[18] + 20)
he4 = envelope(HOLD)
he4.parent = w4
K.pop_in(he4, a[18] + 26)
K.move(w4, a[18] + 32, (2.6, -0.8), 80)

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
