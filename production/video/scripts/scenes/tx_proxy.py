"""@Transactionalと秘書（プロキシ）（第23章）

場面構成（行番号は tx-proxy.json の lines）
  0      タイトル
  1-4    社長（Service本体）の前に秘書（プロキシ）／Controller が頼むのは秘書／Spring が注入したのは秘書
  5-8    BEGIN → INSERT 2回（秘書は待つ）→ COMMIT
  9-10   社長が RuntimeException → 秘書が ROLLBACK
  11-13  検査例外は既定でコミット（ルール表）／rollbackFor で変えられる
  14-17  自己呼び出し・private は秘書を通らない → @Transactional は効かない
  18-20  例外を握りつぶすと秘書は気づけず COMMIT／例外は秘書に届いて意味を持つ
  21     まとめ
"""
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
DEEP = K.hexc("#6c5ce7")
SUBC = K.hexc("#b8bfd8")
BOXC = K.hexc("#232b47")


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None):
    w = w or (len(label) * 0.78 * size + 0.9)
    return K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size)


def hdr(label, color, tc=K.WHITE):
    w = len(label) * 0.78 * 0.4 + 0.9
    return K.card(label, loc=(-6.7 + w / 2, 3.65), w=w, h=0.8, color=color, text_color=tc, size=0.4)


def phase(label, color, f_in, f_out, tc=K.WHITE):
    h = hdr(label, color, tc)
    K.pop_in(h, f_in)
    K.pop_out(h, f_out)
    return h


def out(objs, f, step=2):
    for i, o in enumerate(objs):
        K.pop_out(o, f + i * step)


def actor(loc, color, label, sub=None, s=1.25):
    p = K.person(loc, color, s)
    K.text(label, 0.4, (0, -0.68 * s - 0.4, 0.2), K.WHITE, 900, parent=p)
    if sub:
        K.text(sub, 0.27, (0, -0.68 * s - 0.82, 0.2), SUBC, 700, parent=p)
    return p


def row(i, f_in, done):
    """DB の行カード。未確定=黄、確定=緑（色と文字の両方で区別）。"""
    y = 1.5 - i * 0.95
    if done:
        c = K.card(f"投票{i + 1}", loc=(5.2, y), w=2.0, h=0.85, color=K.GREEN, text_color=INK, size=0.36, sub="確定")
    else:
        c = K.card(f"投票{i + 1}", loc=(5.2, y), w=2.0, h=0.85, color=K.YELLOW, text_color=INK, size=0.36, sub="未確定")
    K.pop_in(c, f_in)
    return c


# 配置（Controller / 秘書 / 社長 / DB）
CX, SX, BX = -5.6, -2.4, 1.4
SEC_Y = 2.8


def build_cast(f_ctrl, f_sec, f_boss, f_db=None):
    """Controller・秘書・社長（・DB）と、つなぐ矢印を作る。dict で返す。"""
    c = {}
    c["boss"] = actor((BX, 0.9), K.ORANGE, "社長", "Serviceの本体")
    K.pop_in(c["boss"], f_boss)
    c["sec"] = actor((SX, 0.9), K.TEAL, "秘書", "プロキシ")
    K.pop_in(c["sec"], f_sec)
    c["ctrl"] = K.card("Controller", loc=(CX, 1.0), w=2.2, h=1.2, color=K.BLUE, size=0.36)
    K.pop_in(c["ctrl"], f_ctrl)
    c["a1"] = K.arrow((CX + 1.2, 1.0), (SX - 0.95, 1.0), K.BLUE, 0.1, 0.3)
    K.grow_x(c["a1"], f_ctrl + 12, 10)
    c["a2"] = K.arrow((SX + 0.8, 1.0), (BX - 0.8, 1.0), K.TEAL, 0.1, 0.3)
    K.grow_x(c["a2"], f_sec + 18, 10)
    c["objs"] = [c["boss"], c["sec"], c["ctrl"], c["a1"], c["a2"]]
    if f_db is not None:
        c["db"] = K.card("DB", loc=(5.2, 2.7), w=2.0, h=0.8, color=DEEP, size=0.4)
        K.pop_in(c["db"], f_db)
        c["a3"] = K.arrow((BX + 0.85, 1.1), (4.1, 1.1), K.ORANGE, 0.1, 0.3)
        K.grow_x(c["a3"], f_db + 10, 10)
        c["objs"] += [c["db"], c["a3"]]
    return c


a = [A(i) for i in range(22)]

# ======================================================================
# 0. タイトル
# ======================================================================
title_out = K.title_card(T, "@Transactional", "社長と秘書（プロキシ）のはなし", until_line=0)
stage = K.rounded_rect("stage", 15.2, 6.7, (0, 0.85, -1), K.BG2, 0.5)
K.pop_in(stage, a[1] - 4, 12)


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
# 1-4. 社長の前に秘書／Controller が頼むのは秘書／Spring が注入したのは秘書
# ======================================================================
phase("社長と秘書", K.TEAL, a[1] + 4, a[5] - 2)
C1 = build_cast(a[2] + 4, a[1] + 34, a[1] + 8, f_db=a[4] + 4)
K.pulse(C1["boss"], a[1] + 24, 1.1, 8)
K.pulse(C1["sec"], a[1] + 56, 1.12, 8)
tag_s = chip("Serviceの前に立つ", (-0.5, 2.9), K.YELLOW, 0.36, INK)
K.pop_in(tag_s, a[1] + 66)
K.pop_out(tag_s, a[2] - 4)

# 2: Controller が頼むのは秘書（社長へは直接つながない）
req = K.dot((CX + 0.4, 1.0), 0.42, K.BLUE, "依頼")
K.pop_in(req, a[2] + 24)
K.move(req, a[2] + 34, (SX - 0.6, 1.0), 20)
K.pop_out(req, a[2] + 58)
K.pulse(C1["sec"], a[2] + 56, 1.12, 8)
bypass = K.dashed_line((CX + 1.2, 0.35), (BX - 0.8, 0.35), K.GRAY, 0.08)
K.pop_in(bypass, a[2] + 66)
byx = K.cross_mark((-1.0, 0.35), 0.8, K.RED, 0.18)
K.pop_in(byx, a[2] + 76)
nodirect = chip("社長には直接つながない", (-1.0, -1.45), K.RED, 0.38)
K.pop_in(nodirect, a[2] + 82)

# 3: Spring が注入したのは秘書
out([bypass, byx, nodirect], a[3] - 2, 1)
inj = chip("Springが注入", (-4.4, 2.8), K.PURPLE, 0.4)
K.pop_in(inj, a[3] + 6)
injar = K.arrow((-4.4, 2.35), (-3.0, 1.5), K.PURPLE, 0.1, 0.3)
K.grow_x(injar, a[3] + 14, 10)
K.pulse(C1["sec"], a[3] + 28, 1.15, 8)
K.pulse(C1["sec"], a[3] + 52, 1.15, 8)
real = chip("注入されたのは秘書", (-0.2, -1.45), K.YELLOW, 0.4, INK)
K.pop_in(real, a[3] + 34)

# 4: 開始と終了は秘書の仕事
out([inj, injar, real], a[4] - 2, 1)
cb1 = chip("BEGIN", (SX - 0.9, SEC_Y), K.TEAL, 0.36, w=2.2)
cb2 = chip("COMMIT", (SX + 1.6, SEC_Y), K.GREEN, 0.36, INK, w=2.5)
K.pop_in(cb1, a[4] + 10)
K.pop_in(cb2, a[4] + 24)
K.pulse(C1["sec"], a[4] + 40, 1.12, 8)
K.pulse(C1["sec"], a[4] + 70, 1.12, 8)
out([cb1, cb2], a[5] - 4, 1)

# ======================================================================
# 5-8. BEGIN → INSERT 2回 → COMMIT
# ======================================================================
cam(a[5], to=(-0.25, 0.5), zoom=14, dur=22)
begin = chip("BEGIN", (SX, SEC_Y), K.TEAL, 0.4, w=2.4)
K.pop_in(begin, a[5] + 8)
K.pulse(C1["sec"], a[5] + 12, 1.12, 8)
K.pulse(C1["a2"], a[5] + 40, 1.3, 8)
tx_on = chip("取引中", (SX, -1.45), K.TEAL, 0.38, w=2.4)
K.pop_in(tx_on, a[5] + 30)

# 6: 社長が INSERT を2回
K.pulse(C1["a2"], a[6] + 2, 1.3, 8)
ins1 = chip("INSERT 1", (BX, SEC_Y), K.ORANGE, 0.36, w=2.8)
K.pop_in(ins1, a[6] + 6)
K.pulse(C1["a3"], a[6] + 14, 1.3, 8)
r1 = row(0, a[6] + 20, False)
K.pop_out(ins1, a[6] + 40)
ins2 = chip("INSERT 2", (BX, SEC_Y), K.ORANGE, 0.36, w=2.8)
K.pop_in(ins2, a[6] + 44)
K.pulse(C1["a3"], a[6] + 52, 1.3, 8)
r2 = row(1, a[6] + 58, False)
K.bob(C1["boss"], a[6] + 6, a[7] - 6, 0.12, 14)
K.pop_out(ins2, a[7] + 2)

# 7: 秘書は待つ
wd = K.dot((SX + 1.15, 2.2), 0.45, K.YELLOW, "待ち")
K.pop_in(wd, a[7] + 4)
K.pulse(wd, a[7] + 20, 1.2, 8)
K.pulse(wd, a[7] + 50, 1.2, 8)
K.recolor(C1["sec"], a[7] + 6, K.YELLOW)

# 8: COMMIT
K.pop_out(begin, a[8])
K.pop_out(wd, a[8])
K.pop_out(tx_on, a[8])
K.recolor(C1["sec"], a[8] + 2, K.TEAL)
commit = chip("COMMIT", (SX, SEC_Y), K.GREEN, 0.4, INK, w=2.7)
K.pop_in(commit, a[8] + 6)
K.pulse(C1["sec"], a[8] + 8, 1.12, 8)
K.pop_out(r1, a[8] + 28)
K.pop_out(r2, a[8] + 30)
g1 = row(0, a[8] + 32, True)
g2 = row(1, a[8] + 36, True)
okc = K.dot((SX + 1.9, 2.0), 0.4, K.GREEN, "OK")
K.pop_in(okc, a[8] + 40)
K.pulse(okc, a[8] + 52, 1.2, 8)

# ======================================================================
# 9-10. RuntimeException → ROLLBACK
# ======================================================================
cam(a[9], to=(0, 0), zoom=16, dur=18)
out([commit, okc, g1, g2], a[9] - 2, 1)
phase("例外が起きたら", K.RED, a[9] + 2, a[11] - 2)
begin2 = chip("BEGIN", (SX, SEC_Y), K.TEAL, 0.4, w=2.4)
K.pop_in(begin2, a[9] + 8)
y1 = row(0, a[9] + 18, False)
K.recolor(C1["boss"], a[9] + 30, K.RED)
K.shake(C1["boss"], a[9] + 30, 0.1, 6, 2)
ex1 = chip("RuntimeException", (BX, SEC_Y), K.RED, 0.3, w=3.2)
K.pop_in(ex1, a[9] + 34)
K.move(ex1, a[9] + 42, (0.3, SEC_Y), 22)
K.shake(C1["sec"], a[9] + 66, 0.1, 6, 2)

# 10: 秘書が ROLLBACK
K.pop_out(begin2, a[10])
rb = chip("ROLLBACK", (SX, SEC_Y), K.RED, 0.4, w=3.0)
K.pop_in(rb, a[10] + 6)
K.pulse(C1["sec"], a[10] + 8, 1.12, 8)
K.shake(y1, a[10] + 20, 0.12, 6, 2)
K.pop_out(y1, a[10] + 36)
K.pop_out(ex1, a[10] + 2)
none = chip("なかったことに", (5.2, 1.95), K.RED, 0.34, w=3.0)
K.pop_in(none, a[10] + 44)
K.recolor(C1["boss"], a[10] + 70, K.ORANGE)

# ======================================================================
# 11. 検査例外はロールバックしない
# ======================================================================
out([rb, none], a[11] - 2, 1)
begin3 = chip("BEGIN", (SX, SEC_Y), K.TEAL, 0.4, w=2.4)
K.pop_in(begin3, a[11] + 4)
y2 = row(0, a[11] + 14, False)
ex2 = K.card("IOException", loc=(BX, SEC_Y), w=3.0, h=1.0, color=K.YELLOW, text_color=INK, size=0.38, sub="検査例外")
K.pop_in(ex2, a[11] + 26)
K.move(ex2, a[11] + 34, (0.3, SEC_Y), 22)
K.pop_out(begin3, a[11] + 58)
cm3 = chip("COMMIT", (SX, SEC_Y), K.GREEN, 0.4, INK, w=2.7)
K.pop_in(cm3, a[11] + 62)
K.pulse(C1["sec"], a[11] + 64, 1.12, 8)
K.pop_out(y2, a[11] + 74)
y2g = row(0, a[11] + 78, True)
K.pop_out(ex2, a[11] + 58)
half = chip("失敗したのに確定", (5.2, 0.0), K.RED, 0.34, w=3.4)
K.pop_in(half, a[11] + 86)

# 12: ルール表
out(C1["objs"] + [cm3, y2g, half], a[12] - 2, 1)
rl = K.card("非検査例外", loc=(-3.8, 2.0), w=6.4, h=1.8, color=CUST, size=0.5, sub="RuntimeException と Error")
rr = K.card("検査例外", loc=(3.8, 2.0), w=6.4, h=1.8, color=CUST, size=0.5, sub="Exception など（チェック例外）")
K.pop_in(rl, a[12] + 4)
K.pop_in(rr, a[12] + 14)
ral = K.arrow((-3.8, 1.0), (-3.8, 0.35), K.GRAY, 0.12, 0.34)
rar = K.arrow((3.8, 1.0), (3.8, 0.35), K.GRAY, 0.12, 0.34)
K.grow_x(ral, a[12] + 20, 8)
K.grow_x(rar, a[12] + 44, 8)
rres_l = chip("ROLLBACK", (-3.8, -0.4), K.RED, 0.5, w=4.6)
rres_r = chip("既定は COMMIT", (3.8, -0.4), K.YELLOW, 0.5, INK, w=4.6)
K.pop_in(rres_l, a[12] + 26)
K.pop_in(rres_r, a[12] + 50)
K.pulse(rres_r, a[12] + 70, 1.1, 8)
K.pulse(rres_r, a[12] + 94, 1.1, 8)

# 13: rollbackFor で変えられる
rf = chip("変えたいときは rollbackFor", (3.8, -1.65), K.TEAL, 0.38)
K.pop_in(rf, a[13] + 6)
K.pulse(rf, a[13] + 28, 1.1, 8)

# ======================================================================
# 14-17. 自己呼び出し・private は秘書を通らない
# ======================================================================
out([rl, rr, ral, rar, rres_l, rres_r, rf], a[14] - 2, 1)
phase("自己呼び出し", K.ORANGE, a[14] + 4, a[15] - 2)
sc_ctrl = K.card("Controller", loc=(-6.0, 1.0), w=1.8, h=1.2, color=K.BLUE, size=0.28)
sc_sec = actor((-3.6, 0.9), K.TEAL, "秘書", "プロキシ", s=1.15)
sbox = K.rounded_rect("sbox", 8.6, 4.0, (3.0, 1.2, -0.3), BOXC, 0.4)
sbox_t = K.text("社長（Serviceの本体）", 0.36, (3.0, 2.85, 0.1), K.WHITE, 700)
mA = K.card("メソッドA", loc=(1.2, 2.0), w=3.0, h=0.9, color=K.ORANGE, size=0.36)
mB = K.card("メソッドB", loc=(1.2, 0.1), w=3.0, h=0.9, color=K.ORANGE, size=0.36)
tagB = chip("@Transactional", (4.9, 0.1), K.YELLOW, 0.3, INK, w=3.2)
for k, o in enumerate((sc_ctrl, sc_sec, sbox, sbox_t, mA, mB, tagB)):
    K.pop_in(o, a[14] + 8 + k * 5)
sa1 = K.arrow((-5.0, 1.0), (-4.4, 1.0), K.BLUE, 0.1, 0.28)
K.grow_x(sa1, a[14] + 24, 8)
sa2 = K.arrow((-3.0, 1.2), (-0.4, 1.9), K.TEAL, 0.1, 0.3)
K.grow_x(sa2, a[14] + 30, 10)
K.pulse(mA, a[14] + 46, 1.1, 8)
# 社長が自分の別メソッドを直接呼ぶ
sa3 = K.arrow((1.2, 1.5), (1.2, 0.65), K.ORANGE, 0.12, 0.34)
K.grow_x(sa3, a[14] + 66, 10)
direct = chip("自分で直接呼ぶ", (3.7, 1.05), K.ORANGE, 0.34, w=3.3)
K.pop_in(direct, a[14] + 74)
K.pulse(mB, a[14] + 82, 1.1, 8)

# 15: 秘書を通らない → @Transactional は効かない
cam(a[15], to=(0.2, 0.9), zoom=14.2, dur=22)
ghostb = chip("BEGIN", (-3.6, 2.6), CUST, 0.34, SUBC, w=2.2)
K.pop_in(ghostb, a[15] + 8)
gbx = K.cross_mark((-2.1, 2.6), 0.8, K.RED, 0.18)
K.pop_in(gbx, a[15] + 20)
skip = chip("秘書を通らない", (1.5, -0.95), K.RED, 0.38)
K.pop_in(skip, a[15] + 30)
tagx = K.cross_mark((6.85, 0.1), 0.7, K.RED, 0.16)
K.recolor(tagB, a[15] + 54, CUST)
K.pop_in(tagx, a[15] + 56)
K.pulse(tagx, a[15] + 66, 1.2, 8)
K.shake(tagB, a[15] + 60, 0.1, 6, 2)

# 16: private も同じ
K.pop_out(direct, a[16])
priv = chip("private", (-0.35, 1.0), K.PURPLE, 0.3, w=1.9)
K.pop_in(priv, a[16] + 8)
K.pulse(priv, a[16] + 24, 1.15, 8)
same = chip("同じ理由で効かない", (4.4, -1.75), K.RED, 0.34, w=4.2)
K.pop_in(same, a[16] + 30)

# 17: 通らない呼び出しはすべて対象外
cam(a[17] - 2, to=(0, 0), zoom=16, dur=18)
K.pop_out(priv, a[17])
K.pop_out(same, a[17])
allno = chip("秘書を通らない呼び出し ＝ 対象外", (0.5, -1.95), K.RED, 0.42)
K.pop_in(allno, a[17] + 14)
K.pulse(allno, a[17] + 30, 1.08, 8)
K.pulse(sc_sec, a[17] + 56, 1.12, 8)

# ======================================================================
# 18-20. 例外を握りつぶすと秘書は気づけない
# ======================================================================
out([sc_ctrl, sc_sec, sbox, sbox_t, mA, mB, tagB, sa1, sa2, sa3, ghostb, gbx, skip, tagx, allno], a[18] - 2, 1)
phase("例外を握りつぶす", K.PURPLE, a[18] + 4, a[21] - 2)
C2 = build_cast(a[18] + 4, a[18] + 8, a[18] + 12, f_db=a[18] + 16)
begin4 = chip("BEGIN", (SX, SEC_Y), K.TEAL, 0.4, w=2.4)
K.pop_in(begin4, a[18] + 24)
z1 = row(0, a[18] + 30, False)
ex3 = chip("例外", (BX, SEC_Y), K.RED, 0.38, w=1.8)
K.pop_in(ex3, a[18] + 40)
K.shake(C2["boss"], a[18] + 40, 0.1, 6, 2)

# 19: 社長が握りつぶす → 秘書は気づけず COMMIT
swal = chip("握りつぶす", (BX, SEC_Y), K.PURPLE, 0.36, w=2.8)
K.move(ex3, a[19], (BX, 0.9), 12)
K.pop_out(ex3, a[19] + 12)
K.pop_in(swal, a[19] + 14)
K.pulse(C2["boss"], a[19] + 14, 1.12, 8)
okr = K.arrow((BX - 0.8, 0.75), (SX + 0.8, 0.75), K.GREEN, 0.1, 0.3)
K.grow_x(okr, a[19] + 24, 10)
q = K.dot((SX + 1.15, 2.2), 0.45, K.YELLOW, "？")
K.pop_in(q, a[19] + 30)
K.pop_out(begin4, a[19] + 44)
cm4 = chip("COMMIT", (SX, SEC_Y), K.GREEN, 0.4, INK, w=2.7)
K.pop_in(cm4, a[19] + 48)
K.pop_out(z1, a[19] + 52)
z1g = row(0, a[19] + 56, True)
bad2 = chip("失敗したのに確定", (5.2, 0.0), K.RED, 0.34, w=3.4)
K.pop_in(bad2, a[19] + 64)

# 20: 例外は秘書に届いて意味を持つ
out([swal, okr, q, cm4, z1g, bad2], a[20] - 2, 1)
begin5 = chip("BEGIN", (SX, SEC_Y), K.TEAL, 0.4, w=2.4)
K.pop_in(begin5, a[20] + 4)
z2 = row(0, a[20] + 10, False)
ex4 = chip("RuntimeException", (BX, SEC_Y), K.RED, 0.3, w=3.2)
K.pop_in(ex4, a[20] + 20)
K.move(ex4, a[20] + 28, (0.3, SEC_Y), 22)
K.pop_out(begin5, a[20] + 54)
rb5 = chip("ROLLBACK", (SX, SEC_Y), K.RED, 0.4, w=3.0)
K.pop_in(rb5, a[20] + 58)
K.pulse(C2["sec"], a[20] + 60, 1.12, 8)
K.pop_out(z2, a[20] + 66)
K.pop_out(ex4, a[20] + 52)
reach = chip("秘書まで届いた", (-0.3, -1.45), K.GREEN, 0.4, INK)
K.pop_in(reach, a[20] + 72)

# ======================================================================
# 21. まとめ
# ======================================================================
out(C2["objs"] + [rb5, reach], a[21] - 14, 0)
m1 = K.text("@Transactional は、", 0.95, (0, 2.55, 0.5), K.WHITE, 900)
m2 = K.text("秘書を通ったときだけ効く", 0.95, (0, 1.2, 0.5), K.ORANGE, 900)
K.pop_in(m1, a[21] + 4)
K.pop_in(m2, a[21] + 14)
fc = K.card("Controller", loc=(-5.6, -0.7), w=2.2, h=1.2, color=K.BLUE, size=0.36)
fs = actor((-1.2, -0.7), K.TEAL, "秘書", None, s=1.1)
fb = actor((3.2, -0.7), K.ORANGE, "社長", None, s=1.1)
K.pop_in(fc, a[21] + 24)
K.pop_in(fs, a[21] + 30)
K.pop_in(fb, a[21] + 36)
fa1 = K.arrow((-4.4, -0.7), (-2.2, -0.7), K.BLUE, 0.1, 0.3)
fa2 = K.arrow((-0.2, -0.7), (2.2, -0.7), K.TEAL, 0.1, 0.3)
K.grow_x(fa1, a[21] + 40, 10)
K.grow_x(fa2, a[21] + 50, 10)
K.bob(fs, a[21] + 60, a[21] + 200, 0.12, 20)
K.bob(fb, a[21] + 66, a[21] + 200, 0.12, 20)
fok = K.dot((1.0, 0.2), 0.45, K.GREEN, "OK")
K.pop_in(fok, a[21] + 62)
K.pulse(fok, a[21] + 76, 1.2, 8)

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
