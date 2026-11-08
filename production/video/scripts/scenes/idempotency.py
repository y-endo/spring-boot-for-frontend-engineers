"""応答が消えても二重登録しない：Idempotency-Key（第27章）

例え：宅配の伝票番号。行番号は idempotency.json の lines。
  0      タイトル
  1-3    スマホ→サーバー→DB(1行)。戻りの応答がトンネルで消える
  4-6    スマホは失敗と思い、もう一度送る → DBは2行（赤）＝二重登録
  7-9    対策：伝票番号（Idempotency-Key）。べき等＝何度送っても結果が同じ
  10-12  番号簿を見る → 初めての番号なら処理して結果を記録（同じトランザクション）
  13-14  同じ番号の再送 → 処理せず保存した結果を返す → DBは1行のまま
  15     同じ番号で中身が違う → 422
  16     番号簿は例えば24時間で掃除
  17     保証の範囲（期間内・同じ利用者のキー）
  18     まとめ
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
import bpy
import bl_kit as K

T = K.setup()
A = lambda i, off=0.0: K.at(T, i, off)

INK = K.BG
PANEL = K.hexc("#2b3350")
CUST = K.hexc("#34406b")
TUNNEL = K.hexc("#0a0d18")
REQ_Y, RES_Y = 1.75, -0.25          # 行き・戻りの道の高さ
PH_X, SV_X, DB_X, TN_X = -6.4, 0.1, 5.5, -3.3
TICKET = "No.7F3A"


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None):
    w = w or (len(label) * 0.78 * size + 0.9)
    return K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size)


def hdr(label, color, tc=K.WHITE):
    w = len(label) * 0.78 * 0.4 + 0.9
    return K.card(label, loc=(-6.7 + w / 2, 3.65), w=w, h=0.8, color=color, text_color=tc, size=0.4)


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


def quad(p0, p1, width, color, z=0.0):
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


def envelope(loc, body, label, label_color=INK, ticket=False, z=0.1, s=1.3):
    """封筒（リクエスト／応答）。ticket=True で伝票番号の札が付く。"""
    root = bpy.data.objects.new("env", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    sh = K.rounded_rect("shadow", 1.0 * s, 0.74 * s, (0.05 * s, -0.07 * s, -0.02), K.hexc("#000000"), 0.1 * s)
    sh.parent = root
    b = K.rounded_rect("body", 1.0 * s, 0.74 * s, (0, 0, 0), body, 0.1 * s)
    b.parent = root
    for p in (quad((-0.5 * s, 0.37 * s), (0, 0.02 * s), 0.045 * s, K.GRAY, 0.03),
              quad((0.5 * s, 0.37 * s), (0, 0.02 * s), 0.045 * s, K.GRAY, 0.03)):
        p.parent = root
    K.text(label, 0.25 * s, (0, -0.19 * s, 0.05), label_color, 900, parent=root)
    if ticket:
        t = K.card(TICKET, loc=(0, 0), w=1.5 * s, h=0.46 * s, color=K.YELLOW, text_color=INK, size=0.25 * s)
        t.parent = root
        t.location = (0.0, 0.84 * s, 0.05)
    return root


def clock(loc, r=0.55, z=0.3):
    root = bpy.data.objects.new("clock", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    face = K.dot((0, 0), r, K.WHITE, z=0.0)
    face.parent = root
    inner = K.dot((0, 0), r * 0.86, K.DARK, z=0.02)
    inner.parent = root
    hand = bpy.data.objects.new("hand", None)
    bpy.context.scene.collection.objects.link(hand)
    hand.parent = root
    hand.location = (0, 0, 0.05)
    quad((0, 0), (0, r * 0.7), 0.09, K.YELLOW).parent = hand
    return root, hand


def db_card(f_in, counts):
    """DB（選択肢テーブル）。counts=[(フレーム,"1行",色)...] は件数表示。"""
    db = K.card("", loc=(DB_X, 1.85), w=3.8, h=2.9, color=K.PURPLE)
    K.text("DB：選択肢", 0.38, (-0.7, 1.1, 0.05), K.WHITE, 700, parent=db)
    if counts:
        K.number_ticker(counts, (1.2, 1.1, 0.05), 0.42, parent=db)
    K.pop_in(db, f_in)
    return db


def db_row(db, y, f, color, tc, label="ラーメン"):
    r = K.card(label, loc=(0, 0), w=3.1, h=0.7, color=color, text_color=tc, size=0.4)
    r.parent = db
    r.location = (0, y, 0.05)
    K.pop_in(r, f)
    return r


def ledger_card(f_in):
    lg = K.card("", loc=(DB_X, -1.0), w=3.8, h=1.7, color=K.BLUE)
    K.text("番号簿", 0.4, (0, 0.46, 0.05), K.WHITE, 700, parent=lg)
    K.pop_in(lg, f_in)
    return lg


def ledger_row(lg, f):
    r = K.card(f"{TICKET} → 結果", loc=(0, 0), w=3.3, h=0.58, color=K.YELLOW, text_color=INK, size=0.3)
    r.parent = lg
    r.location = (0, -0.25, 0.05)
    K.pop_in(r, f)
    return r


def send(env, f, x0, x1, y, dur=30):
    """封筒を出して移動させ、着いたら消す。"""
    K.pop_in(env, f, 8)
    K.move(env, f + 8, (x1, y), dur)
    K.pop_out(env, f + 8 + dur)


# ======================================================================
# 0. タイトル
# ======================================================================
K.title_card(T, "二重登録を防ぐ", "応答が消えても、効果は1回", until_line=0)
stage = K.rounded_rect("stage", 15.2, 6.7, (0, 0.85, -1), K.BG2, 0.5)
K.pop_in(stage, A(1) - 4, 12)
a = {i: A(i) for i in range(1, 19)}
ext_stage([(a[3], a[4] + 24), (a[14], a[15] + 24)])

# ======================================================================
# 1. スマホがリクエストを送る
# ======================================================================
h1 = hdr("選択肢を追加", K.TEAL)
K.pop_in(h1, a[1] + 2)
phone = K.card("", loc=(PH_X, 0.75), w=2.0, h=3.4, color=CUST)
screen = K.rounded_rect("screen", 1.6, 2.2, (0, 0.4, 0.04), K.BG2, 0.2)
screen.parent = phone
K.text("スマホ", 0.34, (0, -1.3, 0.05), K.WHITE, 700, parent=phone)
K.pop_in(phone, a[1])
server = K.card("サーバー", loc=(SV_X, 0.75), w=2.8, h=3.0, color=K.ORANGE, size=0.5)
K.pop_in(server, a[1] + 8)
db1 = db_card(a[1] + 14, [(a[2] + 24, "1行", K.GREEN), (a[6] + 22, "2行", K.RED)])
road_q = K.arrow((-5.2, REQ_Y), (-1.5, REQ_Y), K.GRAY, 0.08, 0.3)
road_r = K.arrow((-1.5, RES_Y), (-5.2, RES_Y), K.GRAY, 0.08, 0.3)
K.grow_x(road_q, a[1] + 20, 14)
K.grow_x(road_r, a[1] + 26, 14)
e1 = envelope((-5.0, REQ_Y), K.WHITE, "追加")
send(e1, a[1] + 34, -5.0, -1.6, REQ_Y, 28)
K.pulse(server, a[1] + 72, 1.06, 8)

# 2. サーバーは保存に成功（DBに1行）
w_db1 = K.arrow((1.6, 1.45), (3.5, 1.45), K.ORANGE, 0.1, 0.3)
K.grow_x(w_db1, a[2] + 4, 12)
K.pulse(db1, a[2] + 22, 1.05, 8)
row1 = db_row(db1, 0.3, a[2] + 22, K.GREEN, INK)
tunnel = K.card("", loc=(TN_X, RES_Y), w=1.9, h=1.5, color=TUNNEL, z=0.3)
K.text("トンネル", 0.32, (0, 0.5, 0.05), K.WHITE, 700, parent=tunnel)
K.pop_in(tunnel, a[2] + 40)
r1 = envelope((-1.5, RES_Y), K.GREEN, "OK")
K.pop_in(r1, a[2] + 60, 8)

# 3. 戻りの応答がトンネルで消える
K.camera_move(a[3], to=(-3.0, -0.1), zoom=12, dur=20)
K.move(r1, a[3] + 4, (TN_X + 0.2, RES_Y), 28)
K.pop_out(r1, a[3] + 32)
lost = K.cross_mark((TN_X, RES_Y - 0.2), 0.7, K.RED, 0.16, z=0.8)
K.pop_in(lost, a[3] + 36)
K.pulse(lost, a[3] + 50, 1.2, 8)
lostc = chip("応答が消えた", (TN_X, -1.5), K.RED, 0.4)
K.pop_in(lostc, a[3] + 44)
K.shake(lostc, a[3] + 60, 0.1, 6, 2)
K.pop_out(h1, a[3])
h3 = hdr("応答が消える", K.RED)
K.pop_in(h3, a[4] + 24)

# 4. スマホは失敗と思い込む
K.camera_move(a[4], to=(0, 0), zoom=16, dur=20)
K.shake(phone, a[4] + 12, 0.12, 8, 2)
sure = chip("保存済みだとは知らない", (-4.6, -1.85), K.RED, 0.4)
K.pop_in(sure, a[4] + 50)
K.pulse(sure, a[4] + 66, 1.08, 8)

# 5. もう一度送る
K.pop_out(lostc, a[5])
K.pop_out(lost, a[5])
e2 = envelope((-5.0, REQ_Y), K.WHITE, "追加")
send(e2, a[5] + 4, -5.0, -1.6, REQ_Y, 28)
K.pulse(server, a[5] + 40, 1.06, 8)

# 6. また保存 → 2行（二重登録）
K.pop_out(sure, a[6])
K.pulse(w_db1, a[6] + 6, 1.2, 8)
K.pulse(db1, a[6] + 20, 1.05, 8)
row2 = db_row(db1, -0.5, a[6] + 20, K.RED, K.WHITE)
K.shake(row2, a[6] + 34, 0.1, 6, 2)
dup = chip("二重登録", (DB_X, -0.25), K.RED, 0.45, w=2.6)
K.pop_in(dup, a[6] + 44)
K.pulse(dup, a[6] + 60, 1.15, 8)
K.pulse(db1, a[6] + 70, 1.05, 8)

# ======================================================================
# 7-9. 対策：伝票番号（Idempotency-Key）
# ======================================================================
f7 = a[7]
out([h3, db1, row1, row2, dup, tunnel, w_db1], f7 - 2, 1)
h7 = hdr("対策：伝票番号", K.GREEN, INK)
K.pop_in(h7, f7 + 4)
db2 = db_card(f7 + 10, [(a[11] + 34, "1行", K.GREEN)])
ledger = ledger_card(f7 + 16)
e3 = envelope((-4.4, REQ_Y), K.WHITE, "追加", ticket=True)
K.pop_in(e3, f7 + 30, 8)
K.pulse(e3, f7 + 54, 1.15, 8)
K.pulse(e3, f7 + 76, 1.15, 8)

# 8. Idempotency-Key
keyc = chip("Idempotency-Key", (0.1, 3.3), K.ORANGE, 0.4, INK, w=4.6)
K.pop_in(keyc, a[8] + 4)
K.pulse(keyc, a[8] + 20, 1.1, 8)
K.move(e3, a[8] + 30, (-2.4, REQ_Y), 28)
K.pop_out(e3, a[8] + 60)
K.pulse(server, a[8] + 60, 1.06, 8)

# 9. べき等：何度送っても結果は同じ
bekitou = chip("べき等：何度送っても、結果は同じ", (0.4, -2.3), K.YELLOW, 0.4, INK)
K.pop_in(bekitou, a[9] + 6)
K.pulse(bekitou, a[9] + 30, 1.08, 8)

# ======================================================================
# 10-12. 番号簿を見る → 初めてなら処理して記録（同じトランザクション）
# ======================================================================
K.pop_out(keyc, a[10])
K.pop_out(bekitou, a[10])
w_lg = K.arrow((1.6, 0.25), (3.5, -0.55), K.ORANGE, 0.1, 0.3)
K.grow_x(w_lg, a[10] + 4, 12)
K.pulse(ledger, a[10] + 18, 1.06, 8)
q1 = K.dot((7.0, -0.35), 0.42, K.YELLOW, "?")
K.pop_in(q1, a[10] + 26)
K.pulse(q1, a[10] + 44, 1.2, 8)

# 11. 初めての番号 → 処理して結果を番号簿に残す
K.pop_out(q1, a[11])
first = chip("初めての番号", (-1.0, -1.6), K.GREEN, 0.42, INK)
K.pop_in(first, a[11] + 4)
w_db2 = K.arrow((1.6, 1.45), (3.5, 1.45), K.ORANGE, 0.1, 0.3)
K.grow_x(w_db2, a[11] + 26, 12)
K.pulse(db2, a[11] + 34, 1.05, 8)
rowA = db_row(db2, 0.3, a[11] + 34, K.GREEN, INK)
K.pulse(w_lg, a[11] + 62, 1.2, 8)
rowL = ledger_row(ledger, a[11] + 70)
K.pulse(ledger, a[11] + 72, 1.06, 8)

# 12. 同じトランザクション
K.pop_out(first, a[12])
txo = K.rounded_rect("tx_o", 4.3, 5.5, (DB_X, 0.8, -0.3), K.TEAL, 0.3)
txi = K.rounded_rect("tx_i", 4.1, 5.3, (DB_X, 0.8, -0.29), K.BG2, 0.25)
K.pop_in(txo, a[12] + 6)
K.pop_in(txi, a[12] + 6)
txc = chip("同じトランザクション", (DB_X, -2.4), K.TEAL, 0.36)
K.pop_in(txc, a[12] + 16)
K.pulse(txc, a[12] + 50, 1.1, 8)
K.pulse(db2, a[12] + 60, 1.04, 8)
K.pulse(ledger, a[12] + 60, 1.04, 8)

# ======================================================================
# 13-14. 同じ番号の再送 → 保存した結果をそのまま返す（DBは1行のまま）
# ======================================================================
K.pop_out(txc, a[13])
e4 = envelope((-4.4, REQ_Y), K.WHITE, "追加", ticket=True)
send(e4, a[13] + 4, -4.4, -2.4, REQ_Y, 28)
K.pulse(server, a[13] + 40, 1.06, 8)
K.pulse(w_lg, a[13] + 46, 1.2, 8)
K.pulse(rowL, a[13] + 52, 1.15, 8)
same = chip("同じ番号", (-3.4, -1.7), K.YELLOW, 0.42, INK, w=2.8)
K.pop_in(same, a[13] + 50)
ret = chip("保存済みの結果を返す", (0.5, -1.7), K.GREEN, 0.4, INK)
K.pop_in(ret, a[13] + 80)
r3 = envelope((-1.5, RES_Y), K.GREEN, "OK")
K.pop_in(r3, a[13] + 84, 8)
K.move(r3, a[13] + 92, (-5.0, RES_Y), 40)
K.pop_out(r3, a[13] + 132)

# 14. DBは1行のまま
K.camera_move(a[14], to=(4.0, 0.2), zoom=12, dur=20)
K.pop_out(same, a[14])
K.pop_out(ret, a[14])
K.pulse(db2, a[14] + 24, 1.06, 8)
K.pulse(rowA, a[14] + 28, 1.12, 8)
keep = chip("1行のまま", (1.4, -1.5), K.GREEN, 0.45, INK, w=3.0)
K.pop_in(keep, a[14] + 26)

# ======================================================================
# 15. 同じ番号で中身が違う → 422
# ======================================================================
K.camera_move(a[15], to=(0, 0), zoom=16, dur=20)
K.pop_out(keep, a[15])
e5 = envelope((-4.4, REQ_Y), K.ORANGE, "別内容", ticket=True)
send(e5, a[15] + 4, -4.4, -2.4, REQ_Y, 28)
K.pulse(server, a[15] + 40, 1.06, 8)
K.pulse(rowL, a[15] + 48, 1.15, 8)
diff = chip("同じ番号で、別の中身", (-3.0, -1.7), K.YELLOW, 0.42, INK)
K.pop_in(diff, a[15] + 50)
r4 = envelope((-1.5, RES_Y), K.RED, "422", K.WHITE)
K.pop_in(r4, a[15] + 68, 8)
K.move(r4, a[15] + 76, (-5.0, RES_Y), 36)
K.pop_out(r4, a[15] + 112)
no422 = chip("422：断る", (1.5, -1.7), K.RED, 0.42, w=3.2)
K.pop_in(no422, a[15] + 80)
K.shake(no422, a[15] + 96, 0.12, 6, 2)
K.shake(phone, a[15] + 100, 0.12, 8, 2)

# ======================================================================
# 16. 番号簿は例えば24時間で掃除
# ======================================================================
f16 = a[16]
out([diff, no422], f16, 1)
clk, hand = clock((1.0, -1.7))
K.pop_in(clk, f16 + 6)
K._key(hand, "rotation_euler", f16 + 10, (0, 0, 0), "LINEAR")
K._key(hand, "rotation_euler", f16 + 86, (0, 0, -2 * math.pi), "LINEAR")
c24 = chip("24時間（例）", (-2.4, -1.7), K.GRAY, 0.4, w=3.4)
K.pop_in(c24, f16 + 10)
K.pop_out(rowL, f16 + 92)
swp = chip("掃除", (DB_X, -2.4), K.TEAL, 0.36, w=2.0)
K.pop_in(swp, f16 + 94)
K.pulse(ledger, f16 + 94, 1.06, 8)

# ======================================================================
# 17. 保証の範囲
# ======================================================================
f17 = a[17]
objs17 = [h7, phone, server, db2, ledger, rowA, txo, txi, road_q, road_r, w_db2, w_lg, clk, c24, swp]
out(objs17, f17 - 2, 1)
h17 = hdr("保証の範囲", K.YELLOW, INK)
K.pop_in(h17, f17 + 6)
g1 = K.card("期間内", loc=(-5.0, 1.0), w=4.2, h=1.8, color=K.BLUE, size=0.6, sub="番号簿が覚えている間")
g2 = K.card("同じ利用者", loc=(0.0, 1.0), w=4.2, h=1.8, color=K.PURPLE, size=0.6, sub="同じキーの範囲")
g3 = K.card("効果の重複なし", loc=(5.0, 1.0), w=4.2, h=1.8, color=K.GREEN, text_color=INK, size=0.5,
            sub="二重登録を防ぐ")
K.pop_in(g1, f17 + 14)
K.pop_in(g2, f17 + 40)
pl = K.text("＋", 0.9, (-2.5, 1.0, 0.2), K.WHITE, 900)
eq = K.text("＝", 0.9, (2.5, 1.0, 0.2), K.WHITE, 900)
K.pop_in(pl, f17 + 40)
K.pop_in(eq, f17 + 66)
K.pop_in(g3, f17 + 70)
K.pulse(g3, f17 + 84, 1.08, 8)

# ======================================================================
# 18. まとめ
# ======================================================================
f18 = a[18]
out([h17, g1, g2, g3, pl, eq], f18 - 12, 1)
m1 = K.text("再送されても、", 0.9, (0, 2.6, 0.5), K.WHITE, 900)
m2 = K.text("効果は1回", 1.0, (0, 1.25, 0.5), K.ORANGE, 900)
K.pop_in(m1, f18 + 4)
K.pop_in(m2, f18 + 14)
fin_e = []
for i in range(3):
    ev = envelope((-5.4 + i * 2.1, -0.7), K.WHITE, "追加", ticket=True)
    K.pop_in(ev, f18 + 26 + i * 6)
    fin_e.append(ev)
fin_a = K.arrow((0.0, -0.7), (1.9, -0.7), K.GRAY, 0.1, 0.34)
K.grow_x(fin_a, f18 + 48, 12)
fin_r = K.card("1行", loc=(4.4, -0.7), w=3.0, h=1.1, color=K.GREEN, text_color=INK, size=0.55)
K.pop_in(fin_r, f18 + 58)
K.pulse(fin_r, f18 + 74, 1.1, 8)

# ======================================================================
# スマホの画面表示（状態が切り替わる）
# ======================================================================
K.number_ticker([(a[1] + 12, "待機", K.GRAY), (a[1] + 36, "送信中", K.WHITE), (a[4] + 12, "失敗？", K.RED),
                 (a[5] + 8, "再送中", K.YELLOW), (a[7] + 12, "待機", K.GRAY), (a[7] + 34, "送信中", K.WHITE),
                 (a[13] + 6, "再送中", K.YELLOW), (a[13] + 130, "OK", K.GREEN), (a[15] + 6, "別内容", K.ORANGE),
                 (a[15] + 110, "422", K.RED)], (0, 0.4, 0.1), 0.45, parent=phone)

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
