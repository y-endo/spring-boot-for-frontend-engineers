"""コネクションプール：傘が足りない（第24章）

例え：傘立て（傘＝DB接続、例として10本）。行番号は connection-pool.json の lines。
  0      タイトル
  1-2    傘を借りてDBへ行き、返す
  3      傘を作るのは時間がかかる（接続確立は高コスト）
  4-6    先に用意して使い回す＝プール／例えば10本（HikariCP既定）／順番に使い回す
  7-9    遅いSQL・外部API呼び出しで傘が戻らない → 残り0 → 行列
  10     待ちが30秒を超える → 接続取得タイムアウト
  11-12  店員（仮想スレッドでも）を増やしても傘は増えない → 行列が長くなるだけ
  13-15  対策：持つ時間を短く／SQLを直す・外部通信しない／本数は測って決める
  16     まとめ
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
import bpy
import bl_kit as K

T = K.setup()
A = lambda i, off=0.0: K.at(T, i, off)
E = lambda i, off=0.0: K.end_of(T, i, off)

INK = K.BG
PANEL = K.hexc("#2b3350")
SLOTBG = K.hexc("#1d233a")
UMB_COLS = [K.ORANGE, K.TEAL, K.BLUE, K.PURPLE]
S = 0.7                       # 人物・傘の大きさ
LANE = -1.2                   # 人物が歩く高さ
HOLD = 1.17                   # 傘を持つとき、人物の中心より上にずらす量
DBX = 5.9                     # DBの真下の x


def dim(c, f=0.55):
    return (c[0] * f, c[1] * f, c[2] * f, 1.0)


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None):
    w = w or (len(label) * 0.78 * size + 0.9)
    return K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size)


def hdr(label, color, tc=K.WHITE, cam=(0.0, 0.0, 16.0)):
    """左上の見出しチップ。カメラを寄せている間は cam=(中心x, 中心y, zoom) を渡す。"""
    w = len(label) * 0.78 * 0.4 + 0.9
    cx, cy, z = cam
    return K.card(label, loc=(cx - z / 2 + 0.7 + w / 2, cy + z * 9 / 32 - 0.85), w=w, h=0.8,
                  color=color, text_color=tc, size=0.4)


def ext_stage(windows):
    """寄りの間だけ、舞台の板を画面いっぱいに広げる（板の縁が映り込むのを防ぐ）。
    背景と同じ色から板の色へなめらかに変え、寄りが終わると元に戻す。windows=[(開始, 終了), ...]"""
    o = K.rounded_rect("stage_ext", 40, 30, (0, 0.85, -1.05), K.BG, 0.5)
    m = K.mat(K.BG, name="ext_stage_mat")
    o.data.materials[0] = m
    sock = [n for n in m.node_tree.nodes if n.type == "EMISSION"][0].inputs["Color"]
    sock.default_value = K.BG
    sock.keyframe_insert("default_value", frame=1)
    for a, b in windows:
        for f, c in ((a, K.BG), (a + 12, K.BG2), (b - 12, K.BG2), (b, K.BG)):
            sock.default_value = c
            sock.keyframe_insert("default_value", frame=f)
    return o


def out(objs, f, step=2):
    for i, o in enumerate(objs):
        K.pop_out(o, f + i * step)


def arc_part(r, a0, a1, color, loc=(0, 0, 0), n=28):
    verts = [(0, 0, 0)] + [(r * math.cos(a0 + (a1 - a0) * i / n), r * math.sin(a0 + (a1 - a0) * i / n), 0)
                           for i in range(n + 1)]
    me = bpy.data.meshes.new("arc")
    me.from_pydata(verts, [], [list(range(len(verts)))])
    me.update()
    o = bpy.data.objects.new("arc", me)
    bpy.context.scene.collection.objects.link(o)
    o.data.materials.append(K.mat(color))
    o.location = loc
    return o


def quad(p0, p1, width, color, z=0.0):
    """p0→p1 を結ぶ細長い四角形（線の代わり）。"""
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy)
    nx, ny = -dy / L * width / 2, dx / L * width / 2
    verts = [(p0[0] + nx, p0[1] + ny, z), (p1[0] + nx, p1[1] + ny, z),
             (p1[0] - nx, p1[1] - ny, z), (p0[0] - nx, p0[1] - ny, z)]
    me = bpy.data.meshes.new("quad")
    me.from_pydata(verts, [], [[0, 1, 2, 3]])
    me.update()
    o = bpy.data.objects.new("quad", me)
    bpy.context.scene.collection.objects.link(o)
    o.data.materials.append(K.mat(color))
    return o


def umbrella(loc, color, s=S, z=0.2, parent=None, rib=None):
    """傘のアイコン。原点は傘の骨の付け根（開いた部分が上、持ち手が下）。"""
    root = bpy.data.objects.new("umbrella", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    r = 0.5 * s
    parts = [arc_part(r, 0, math.pi, color)]
    drk = rib or dim(color, 0.6)
    for ang in (math.radians(50), math.radians(90), math.radians(130)):
        parts.append(quad((0, 0), (r * 0.98 * math.cos(ang), r * 0.98 * math.sin(ang)), 0.035 * s, drk, 0.02))
    handle = K.WHITE if rib is None else dim(color, 0.8)
    parts.append(quad((0, 0), (0, -0.58 * s), 0.07 * s, handle, 0.01))
    parts.append(quad((0, -0.58 * s), (-0.2 * s, -0.58 * s), 0.07 * s, handle, 0.01))
    for p in parts:
        p.parent = root
    if parent is not None:
        root.parent = parent
    return root


def check_mark(loc, size=0.7, color=K.GREEN, width=0.14, z=0.6):
    root = bpy.data.objects.new("check", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    for p in (quad((-size * 0.38, 0.0), (-size * 0.12, -size * 0.3), width, color),
              quad((-size * 0.12, -size * 0.3), (size * 0.4, size * 0.34), width, color)):
        p.parent = root
    return root


SLOTS = [(-3.4 + (c - 2) * 1.05, y) for y in (2.5, 1.2) for c in range(5)]


def build_rack(f0):
    """傘立てと10本の傘。(グループ, 傘のリスト) を返す。グループごと pop_out できる。"""
    grp = bpy.data.objects.new("rack_grp", None)
    bpy.context.scene.collection.objects.link(grp)
    card = K.card("", loc=(-3.4, 1.8), w=5.6, h=2.9, color=PANEL)
    card.parent = grp
    for p in SLOTS:
        d = umbrella(p, SLOTBG, z=0.05, parent=grp, rib=SLOTBG)
    umbs = []
    for i, p in enumerate(SLOTS):
        u = umbrella(p, UMB_COLS[i % 4], parent=grp)
        K.pop_in(u, f0 + 8 + i * 2)
        umbs.append(u)
    K.pop_in(grp, f0, 10)
    return grp, umbs


def counter(f_in, f_out, events, loc=(2.1, 2.4), label="残りの傘"):
    """「残りの傘」の数字。events=[(フレーム, 本数), ...]（時間順）。"""
    cd = K.card("", loc=loc, w=2.6, h=1.9, color=PANEL)
    K.text(label, 0.32, (0, 0.62, 0.05), K.WHITE, 700, parent=cd)
    seq = []
    for f, v in events:
        col = K.RED if v == 0 else (K.YELLOW if v <= 3 else K.WHITE)
        seq.append((f, str(v), col))
    K.number_ticker(seq, (0, -0.22, 0.05), 1.15, parent=cd)
    K.pop_in(cd, f_in)
    if f_out:
        K.pop_out(cd, f_out)
    return cd


def go(p, u, f, xy, dur, hold=True):
    """人物 p（と持っている傘 u）を xy へ歩かせる。"""
    K.move(p, f, xy, dur)
    if u is not None:
        K.move(u, f, (xy[0], xy[1] + HOLD), dur)


# ======================================================================
# 0. タイトル
# ======================================================================
K.title_card(T, "コネクションプール", "傘が足りない", until_line=0)
stage = K.rounded_rect("stage", 15.2, 6.7, (0, 0.85, -1), K.BG2, 0.5)
K.pop_in(stage, A(1) - 4, 12)

a = {i: A(i) for i in range(1, 17)}
ext_stage([(a[3], a[4] + 24), (a[10], a[11] + 24)])

# ======================================================================
# 1-2. 傘を借りてDBへ行き、返す
# ======================================================================
h1 = hdr("傘を借りて、返す", K.TEAL)
K.pop_in(h1, a[1] + 2)
db = K.card("DB", loc=(DBX, 2.2), w=2.0, h=1.3, color=K.PURPLE, size=0.5)
K.pop_in(db, a[1] + 10)
rack1, u1 = build_rack(a[1])
ev1 = [(a[1] + 14, 10)]

pA = K.person((-6.8, LANE), K.TEAL, S)
K.pop_in(pA, a[1] + 30)
K.move(pA, a[1] + 34, (SLOTS[9][0], LANE), 14)                 # 傘立ての下へ
K.move(u1[9], a[1] + 52, (SLOTS[9][0], LANE + HOLD), 10)       # 傘を取る
ev1.append((a[1] + 52, 9))
go(pA, u1[9], a[1] + 64, (DBX, LANE), 22)                       # DBへ
sql = K.dot((DBX, 0.95), 0.42, K.YELLOW, "SQL")
K.pop_in(sql, a[2] + 2)
K.pulse(db, a[2] + 4, 1.12, 8)
K.pulse(sql, a[2] + 14, 1.25, 8)
K.pop_out(sql, a[2] + 30)

# 2: 用が済んだら返す → 次の人
go(pA, u1[9], a[2] + 32, (SLOTS[9][0], LANE), 22)
K.move(u1[9], a[2] + 56, SLOTS[9], 10)                          # 返却
ev1.append((a[2] + 60, 10))
back = K.dot((SLOTS[9][0] + 1.1, LANE + 0.55), 0.4, K.GREEN, "返却")
K.pop_in(back, a[2] + 62)
K.pulse(rack1, a[2] + 62, 1.04, 8)
K.pop_out(back, a[2] + 82)
K.pop_out(pA, a[2] + 60)
pB = K.person((-6.8, LANE), K.TEAL, S)
K.pop_in(pB, a[2] + 54)
K.move(pB, a[2] + 60, (SLOTS[9][0], LANE), 14)
K.move(u1[9], a[2] + 76, (SLOTS[9][0], LANE + HOLD), 10)        # 次の人が使う
ev1.append((a[2] + 76, 9))
counter(a[1] + 14, a[3] - 4, ev1)

# ======================================================================
# 3. 傘を作るのは時間がかかる（接続確立は高コスト）
# ======================================================================
K.pop_out(h1, a[3])
out([rack1, pB, db], a[3], 1)
h3 = hdr("傘を作るのは大変", K.YELLOW, INK, cam=(1.0, 1.0, 13.0))
K.pop_in(h3, a[3] + 26)
K.camera_move(a[3] + 2, to=(1.0, 1.0), zoom=13, dur=22)
db3 = K.card("DB", loc=(DBX, 2.2), w=2.0, h=1.3, color=K.PURPLE, size=0.5)
K.pop_in(db3, a[3] + 12)
big = umbrella((-2.8, 1.5), K.ORANGE, 2.1)
K.pop_in(big, a[3] + 10)
K.bob(big, a[3] + 24, a[3] + 120, 0.08, 20)
bar3, fill3 = K.progress_bar((-2.8, -0.15), 3.6, 0.32, K.YELLOW, K.DARK)
K.pop_in(bar3, a[3] + 16)
K.bar_to(fill3, a[3] + 24, 1.0, 100)
link3 = K.dashed_line((-1.0, 1.9), (4.8, 2.2), K.GRAY, 0.08)
K.pop_in(link3, a[3] + 28)
c3a = chip("接続の確立", (2.0, 3.15), K.YELLOW, 0.42, INK)
K.pop_in(c3a, a[3] + 34)
c3b = chip("作るのに時間がかかる", (-2.8, -0.95), K.RED, 0.42)
K.pop_in(c3b, a[3] + 70)
K.shake(c3b, a[3] + 88, 0.1, 6, 2)

# ======================================================================
# 4. 先に用意して使い回す＝コネクションプール
# ======================================================================
f4 = a[4]
K.camera_move(f4 - 2, to=(0, 0), zoom=16, dur=20)
out([h3, big, bar3, link3, c3a, c3b, db3], f4 - 4, 1)
h4 = hdr("コネクションプール", K.TEAL)
K.pop_in(h4, f4 + 4)
db4 = K.card("DB", loc=(DBX, 2.2), w=2.0, h=1.3, color=K.PURPLE, size=0.5)
K.pop_in(db4, f4 + 6)
rack2, u2 = build_rack(f4 + 6)
ev2 = [(f4 + 22, 10)]
pre = chip("先に用意しておく", (-3.4, -1.7), K.GREEN, 0.42, INK)
K.pop_in(pre, f4 + 40)
K.pulse(pre, f4 + 54, 1.1, 8)
K.pulse(rack2, f4 + 60, 1.04, 8)

# 5: 例えば10本（HikariCP の既定の最大数）
K.pop_out(pre, a[5])
hk = chip("HikariCP 既定：最大10本", (1.2, 3.95), K.ORANGE, 0.4, INK)
K.pop_in(hk, a[5] + 4)
ten = K.dashed_line((-6.1, 0.1), (-0.7, 0.1), K.YELLOW, 0.08)
K.pop_in(ten, a[5] + 20)
tenc = chip("10本", (-3.4, 0.1), K.YELLOW, 0.42, INK)
K.pop_in(tenc, a[5] + 24)
for i, u in enumerate(u2):
    K.pulse(u, a[5] + 30 + i * 3, 1.25, 8)

# 6: 順番に使い回す（2人が借りて返す）
out([ten, tenc, hk], a[6], 1)
for slot, off, n_take in [(8, 0, 9), (7, 16, 8)]:
    p = K.person((-6.8, LANE), K.TEAL, S)
    t0 = a[6] + off
    sx = SLOTS[slot][0]
    K.pop_in(p, t0)
    K.move(p, t0 + 2, (sx, LANE), 14)
    K.move(u2[slot], t0 + 18, (sx, LANE + HOLD), 10)
    go(p, u2[slot], t0 + 30, (DBX, LANE), 18)
    K.pulse(db4, t0 + 48, 1.12, 8)
    go(p, u2[slot], t0 + 54, (sx, LANE), 16)
    K.move(u2[slot], t0 + 72, SLOTS[slot], 8)
    K.pop_out(p, t0 + 78)
EV = [(f4 + 22, 10), (a[6] + 18, 9), (a[6] + 34, 8), (a[6] + 76, 9), (a[6] + 92, 10)]

# ======================================================================
# 7. 遅いSQLで傘を持ったまま戻らない
# ======================================================================
HX = [-0.9 + 0.9 * k for k in range(10)]        # 傘を持ったまま動かない人の位置
TAKE = list(range(9, -1, -1))                    # 傘立ての取り出し順（右下から）
holders = []
hum = {}


def holder(k, f):
    """k番目の「傘を持ったまま」の人。人が現れ、傘が傘立てから飛んでくる。"""
    p = K.person((HX[k], LANE), K.TEAL, S)
    K.pop_in(p, f)
    u = u2[TAKE[k]]
    K.move(u, f + 6, (HX[k], LANE + HOLD), 12)
    K.recolor(p, f + 22, K.RED)
    holders.append(p)
    hum[k] = u
    return p


K.pop_out(h4, a[7])
h7 = hdr("傘が戻ってこない", K.RED)
K.pop_in(h7, a[7] + 4)
holder(0, a[7] + 14)
EV.append((a[7] + 20, 9))
slow1 = chip("遅いSQL", (3.8, 0.95), K.RED, 0.42)
K.pop_in(slow1, a[7] + 30)
K.shake(slow1, a[7] + 46, 0.12, 6, 2)
slowbar, slowfill = K.progress_bar((3.8, 0.38), 2.6, 0.2, K.RED, K.DARK)
K.pop_in(slowbar, a[7] + 34)
K.bar_to(slowfill, a[7] + 38, 0.45, 80)
K.pulse(db4, a[7] + 70, 1.1, 8)

# 8: 外部API呼び出しでも同じ
K.pop_out(slow1, a[8])
K.pop_out(slowbar, a[8])
slow2 = chip("TX中の外部API", (3.8, 0.95), K.RED, 0.42)
K.pop_in(slow2, a[8] + 6)
K.shake(slow2, a[8] + 22, 0.12, 6, 2)
holder(1, a[8] + 10)
EV.append((a[8] + 16, 8))
holder(2, a[8] + 44)
EV.append((a[8] + 50, 7))

# 9: 傘が減って行列
K.pop_out(slow2, a[9])
n_left = 7
for k in range(3, 10):
    f = a[9] + 2 + (k - 3) * 6
    holder(k, f)
    n_left -= 1
    EV.append((f + 6, n_left))
QX = [-7.0 + 0.75 * k for k in range(5)]
queue = []
for k in range(5):
    q = K.person((QX[k], -1.3), K.YELLOW, 0.5)
    K.pop_in(q, a[9] + 48 + k * 4)
    queue.append(q)
qc = chip("行列", (-2.4, -1.3), K.YELLOW, 0.42, INK)
K.pop_in(qc, a[9] + 52)
K.pulse(qc, a[9] + 68, 1.15, 8)
qwait = K.dot((QX[4], -0.5), 0.34, K.YELLOW, "待ち")
K.pop_in(qwait, a[9] + 66)
counter(f4 + 22, a[10] - 1, EV)

# ======================================================================
# 10. 待ちが30秒を超える → 接続取得タイムアウト
# ======================================================================
K.pop_out(h7, a[10])
K.camera_move(a[10] + 2, to=(-3.4, 0.2), zoom=12, dur=22)
tcard = K.card("", loc=(-3.4, 1.8), w=5.6, h=2.9, color=PANEL, z=0.5)
K.text("待ち時間（例）", 0.4, (0, 0.85, 0.05), K.WHITE, 700, parent=tcard)
seq = []
for i, v in enumerate([0, 5, 10, 15, 20, 25, 30]):
    col = K.WHITE if v < 20 else (K.YELLOW if v < 30 else K.RED)
    seq.append((a[10] + 18 + i * 8, f"{v}秒", col))
K.number_ticker(seq, (0, -0.3, 0.05), 1.3, parent=tcard)
K.pop_in(tcard, a[10] + 12)
K.pop_out(tcard, a[10] + 80)
err = K.card("接続取得タイムアウト", loc=(-3.4, 1.8), w=5.6, h=2.9, color=K.RED, size=0.5, z=0.5, sub="30秒を超えて、エラー")
K.pop_in(err, a[10] + 84)
K.shake(err, a[10] + 96, 0.14, 8, 2)
K.recolor(queue[4], a[10] + 88, K.RED)
K.shake(queue[4], a[10] + 92, 0.1, 8, 2)

# ======================================================================
# 11. 店員を増やしても、傘は増えない
# ======================================================================
K.camera_move(a[11], to=(0, 0), zoom=16, dur=22)
K.pop_out(err, a[11])
K.pop_out(qwait, a[11])
h11 = hdr("傘の本数は変わらない", K.RED)
K.pop_in(h11, a[11] + 6)
mk = chip("店員を増やす", (3.8, 0.95), K.TEAL, 0.42)
K.pop_in(mk, a[11] + 8)
rows2 = []
for r_i, y in enumerate((-0.5, -2.1)):
    for k in range(5):
        q = K.person((QX[k], y), K.YELLOW, 0.5)
        K.pop_in(q, a[11] + 10 + (r_i * 5 + k) * 3)
        rows2.append(q)
K.pop_out(mk, a[11] + 52)
fixed10 = chip("傘は10本のまま", (3.8, 0.95), K.YELLOW, 0.42, INK)
K.pop_in(fixed10, a[11] + 56)
K.pulse(fixed10, a[11] + 68, 1.12, 8)

# ======================================================================
# 12. 仮想スレッドで安く大量に雇っても、行列が長くなるだけ
# ======================================================================
vt = chip("仮想スレッド", (3.8, 0.95), K.PURPLE, 0.42)
K.pop_out(fixed10, a[12])
K.pop_in(vt, a[12] + 6)
out(queue + rows2 + [qc], a[12], 0)
crowd = []
n = 0
for y in (-0.35, -0.95, -1.55, -2.15):
    for k in range(11):
        q = K.person((-7.25 + 0.5 * k, y), K.YELLOW, 0.3)
        K.pop_in(q, a[12] + 10 + n)
        crowd.append(q)
        n += 1
longq = chip("行列が長くなるだけ", (3.6, -2.3), K.RED, 0.42)
K.pop_in(longq, a[12] + 70)
K.shake(longq, a[12] + 84, 0.12, 6, 2)

# ======================================================================
# 13. 対策：傘を持つ時間を短くする
# ======================================================================
f13 = a[13]
out([h11, vt, longq] + crowd + holders, f13, 0)
h13 = hdr("対策", K.GREEN, INK)
K.pop_in(h13, f13 + 6)
ev3 = [(a[11] + 6, 0)]
for k in range(10):
    K.move(hum[k], f13 + 4 + k * 3, SLOTS[TAKE[k]], 10)     # 傘が傘立てへ戻る
    ev3.append((f13 + 14 + k * 3, k + 1))
counter(a[11] + 4, a[14], ev3)
l1 = K.text("いま", 0.42, (-5.6, -0.6, 0.2), K.WHITE, 700)
l2 = K.text("目標", 0.42, (-5.6, -1.7, 0.2), K.WHITE, 700)
K.pop_in(l1, f13 + 36)
K.pop_in(l2, f13 + 54)
b1, fl1 = K.progress_bar((0.2, -0.6), 8.4, 0.42, K.RED, K.DARK)
b2, fl2 = K.progress_bar((0.2, -1.7), 8.4, 0.42, K.GREEN, K.DARK)
K.pop_in(b1, f13 + 38)
K.bar_to(fl1, f13 + 44, 1.0, 36)
K.pop_in(b2, f13 + 56)
K.bar_to(fl2, f13 + 62, 0.2, 8)
r1 = K.text("長い", 0.42, (6.4, -0.6, 0.2), K.RED, 900)
r2 = K.text("短い", 0.42, (6.4, -1.7, 0.2), K.GREEN, 900)
K.pop_in(r1, f13 + 80)
K.pop_in(r2, f13 + 70)
hold_t = chip("傘を持つ時間", (3.4, 0.5), K.TEAL, 0.4)
K.pop_in(hold_t, f13 + 30)

# 14: 遅いSQLを直す／トランザクション中に外部通信しない
out([l1, l2, b1, b2, r1, r2, hold_t], a[14], 1)
cd1 = K.card("遅いSQLを直す", loc=(-3.6, -0.9), w=5.2, h=1.4, color=K.GREEN, text_color=INK, size=0.5)
K.pop_in(cd1, a[14] + 6)
ck1 = check_mark((-5.8, -0.9), 0.8, K.WHITE)
K.pop_in(ck1, a[14] + 14)
K.pulse(cd1, a[14] + 18, 1.08, 8)
cd2 = K.card("トランザクション中は外部通信しない", loc=(2.9, -0.9), w=7.2, h=1.4, color=K.GREEN,
             text_color=INK, size=0.42)
K.pop_in(cd2, a[14] + 52)
ck2 = check_mark((-0.1, -0.9), 0.8, K.WHITE)
K.pop_in(ck2, a[14] + 60)
K.pulse(cd2, a[14] + 64, 1.06, 8)

# 15: 傘の本数は測って決める
out([cd1, cd2, ck1, ck2, h13], a[15], 1)
h15 = hdr("測って決める", K.TEAL)
K.pop_in(h15, a[15] + 6)
gc = K.card("", loc=(0, -0.95), w=9.0, h=2.0, color=PANEL)
K.text("使用中の接続", 0.4, (-2.9, 0.5, 0.05), K.WHITE, 700, parent=gc)
K.number_ticker([(a[15] + 14, "5 / 10", K.WHITE), (a[15] + 30, "9 / 10", K.YELLOW),
                 (a[15] + 46, "6 / 10", K.WHITE)], (3.0, 0.45, 0.05), 0.6, parent=gc)
K.pop_in(gc, a[15] + 4)
gb, gf = K.progress_bar((0, -1.4), 8.0, 0.36, K.GREEN, K.DARK)
K.pop_in(gb, a[15] + 8)
K.bar_to(gf, a[15] + 14, 0.5, 12)
K.bar_to(gf, a[15] + 30, 0.9, 12)
K.bar_to(gf, a[15] + 46, 0.6, 12)

# ======================================================================
# 16. まとめ
# ======================================================================
f16 = a[16]
out([gc, gb, h15, rack2, db4], f16 - 14, 1)
m1 = K.text("ボトルネックは、", 0.9, (0, 2.5, 0.5), K.WHITE, 900)
m2 = K.text("店員ではなく傘の本数", 0.85, (0, 1.15, 0.5), K.ORANGE, 900)
K.pop_in(m1, f16 - 2, 6)
K.pop_in(m2, f16 + 6, 6)
fin = []
for i in range(10):
    u = umbrella((-5.4 + i * 1.2, -0.7), UMB_COLS[i % 4], 0.9)
    K.pop_in(u, f16 + 26 + i * 3)
    K.bob(u, f16 + 60 + i * 2, f16 + 200, 0.12, 20)
    fin.append(u)

# ======================================================================
# 描画（確認用に環境変数で静止画だけ出せる）  TM_LIST=300,600  TM_OUT=出力フォルダ  TM_PCT=50
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
