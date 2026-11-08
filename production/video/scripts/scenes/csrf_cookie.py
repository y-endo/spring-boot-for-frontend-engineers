"""ブラウザはCookieを自動で添える：CSRFとトークン（第34章）

場面構成（行番号は csrf-cookie.json の lines）
  0      タイトル
  1      左に正規サイト(QuickVote)、右に悪意のあるサイト、上にサーバー、中央にブラウザ
  2-4    ふつうの通信：参加でCookie（入館証）を受け取り、QuickVote宛てには自動で添える
  5-8    攻撃：悪意のあるサイトが「投票を削除」を送らせ、ブラウザはCookieを添え、削除が通る
  9-10   SameSite=Lax：別サイトからのPOSTには付かない／ページ移動のGETには付く
  11-14  対策：CSRFトークン（合言葉）。悪意のあるサイトは読めないので403
  15-17  CORSは別物：応答を読めるかを決めるドアマン。送信は止めない場合もある
  18     まとめ

確認用: TM_LIST=300,600  TM_OUT=出力フォルダ  TM_PCT=50
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

INK = K.BG
EVIL = K.hexc("#b3304f")      # 悪意のあるサイト（暗い赤）
a = [A(i) for i in range(19)]

live = []                      # 場面の入れ替え用：登場させた物をここに記録
gone = set()                   # 退場済み（二重に退場させるとアニメが壊れるので記録する）


def P(o, f, dur=8):
    if o.name in gone:
        return
    gone.add(o.name)
    K.pop_out(o, f, dur)


def L(o):
    live.append(o)
    return o


def clear(f, keep=(), step=1):
    """keep 以外の登場物をまとめて退場"""
    k = 0
    for o in list(live):
        if any(o is x for x in keep):
            continue
        if o.name not in gone:
            P(o, f + min(k, 12) * step)
            k += 1
        live.remove(o)


def tw(label, size):
    """ラベルの幅の見積もり（全角は広く、英数字は狭く数える）"""
    return sum(0.78 if ord(c) > 255 else 0.52 for c in label) * size + 0.9


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None, z=0.0):
    w = w or tw(label, size)
    return L(K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size, z=z))


def hdr(label, color, tc=K.WHITE):
    w = tw(label, 0.4)
    return L(K.card(label, loc=(-6.7 + w / 2, 3.65), w=w, h=0.8, color=color, text_color=tc, size=0.4))


def env(label, color, x, y, w=2.0, tc=K.WHITE):
    return L(K.card(label, loc=(x, y), w=w, h=0.7, color=color, text_color=tc, size=0.32, z=1.0))


def sticker(label, color, x, y, w=1.5, sub=None, tc=INK):
    return L(K.card(label, loc=(x, y), w=w, h=0.8 if sub else 0.64, color=color, text_color=tc,
                    size=0.3, sub=sub, z=1.2))


def shield(loc, s, color, label):
    """盾。本体は名前が body なので recolor できる。"""
    root = bpy.data.objects.new("shield", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], 0.4)
    top = K.rounded_rect("body", 1.3 * s, 1.0 * s, (0, 0.15 * s, 0), color, 0.2 * s)
    top.parent = root
    r = 0.75 * s
    bpy.ops.mesh.primitive_circle_add(vertices=3, radius=r, fill_type="NGON", location=(0, -0.3 * s - r / 2, 0))
    tri = bpy.context.active_object
    tri.rotation_euler = (0, 0, math.pi)
    tri.data.materials.append(K.mat(color))
    tri.parent = root
    K.text(label, 0.5 * s, (0, 0.15 * s, 0.1), INK, 900, parent=root)
    return L(root)


# 位置
SRV = (0, 3.0)
BRW = (0, -0.6)
SITE_L = (-5.6, -0.6)
SITE_R = (5.6, -0.6)
CK_HOME = (0, -1.2)
LANE_Y0, LANE_Y1 = 0.95, 1.8

# ======================================================================
# 0. タイトル
# ======================================================================
K.title_card(T, "CSRFとCookie", "ブラウザは入館証を自動で添える", until_line=0)
stage = K.rounded_rect("stage", 15.2, 7.0, (0, 0.75, -1), K.BG2, 0.5)
K.pop_in(stage, a[1] - 4, 12)


def stage_zoom(f, s, dur=22):
    """カメラを寄せている間だけ舞台パネルを大きくして、パネルの端が画面に写らないようにする"""
    K._key(stage, "scale", f, K._cur(stage, "scale"), "SINE", "EASE_IN_OUT")
    K._key(stage, "scale", f + dur, (s, s, s), "SINE", "EASE_IN_OUT")


# ======================================================================
# 1. 登場人物：サーバー・ブラウザ・左右のサイト
# ======================================================================
server = K.card("サーバー", loc=SRV, w=4.4, h=1.3, color=K.PURPLE, size=0.45, sub="QuickVote")
browser = K.card("", loc=BRW, w=3.2, h=2.2, color=K.BLUE, size=0.45)
K.text("ブラウザ", 0.45, (0, 0.62, 0.05), K.WHITE, 700, parent=browser)
K.text("利用者", 0.25, (0, 0.28, 0.05), K.WHITE, 400, parent=browser)
site_l = K.card("QuickVote", loc=SITE_L, w=3.0, h=1.5, color=K.TEAL, size=0.45, sub="正規サイト")
site_r = K.card("悪意のあるサイト", loc=SITE_R, w=3.4, h=1.5, color=EVIL, size=0.36, sub="攻撃者のページ")
K.pop_in(server, a[1] + 2)
K.pop_in(browser, a[1] + 14)
K.pop_in(site_l, a[1] + 34)
K.pulse(site_l, a[1] + 46, 1.1, 8)
K.pop_in(site_r, a[1] + 66)
K.pulse(site_r, a[1] + 78, 1.1, 8)

# ======================================================================
# 2. 参加してCookie（入館証）を受け取る
# ======================================================================
h1 = hdr("ふつうの通信", K.TEAL)
K.pop_in(h1, a[2])
link_l = L(K.dashed_line((-1.7, -0.6), (-4.0, -0.6), K.GRAY, 0.08))
K.pop_in(link_l, a[2] + 4)
login = chip("参加済み", (-5.6, 0.6), K.GREEN, 0.38, INK)
K.pop_in(login, a[2] + 14)
setck = L(K.arrow((0, 2.3), (0, 0.55), K.YELLOW, 0.1, 0.34))
K.grow_x(setck, a[2] + 36, 14)
ck_home = chip("Cookie（入館証）", CK_HOME, K.YELLOW, 0.34, INK, w=2.8, z=0.3)
K.pop_in(ck_home, a[2] + 56)
K.pulse(ck_home, a[2] + 76, 1.12, 8)

# ======================================================================
# 3. QuickVote宛てのリクエストに入館証を自動で添える
# ======================================================================
lane = L(K.arrow((0, 0.55), (0, 2.3), K.GRAY, 0.06, 0.3))
P(setck, a[3])
K.pop_in(lane, a[3] + 2)


def cookie_copy(f, x, y, w=1.5):
    c = sticker("Cookie", K.YELLOW, CK_HOME[0], CK_HOME[1], w)
    K.pop_in(c, f, 8)
    K.move(c, f + 8, (x, y), 16)
    return c


def send_normal(f, label="投票する", ex=-0.9, cx=0.95):
    """通常の送信：封筒が現れ、Cookie が自動で飛んで来て貼り付き、サーバーへ上る。(封筒, Cookie) を返す"""
    e = env(label, K.TEAL, ex, LANE_Y0)
    K.pop_in(e, f)
    c = cookie_copy(f + 14, cx, LANE_Y0)
    K.pulse(c, f + 40, 1.2, 8)
    K.pulse(browser, f + 40, 1.06, 8)
    K.move(e, f + 54, (ex, LANE_Y1), 20)
    K.move(c, f + 54, (cx, LANE_Y1), 20)
    K.pulse(server, f + 76, 1.06, 8)
    return e, c


e3, c3 = send_normal(a[3] + 2)
ok3 = L(K.dot((-3.0, 2.6), 0.5, K.GREEN, "OK"))
K.pop_in(ok3, a[3] + 80)
K.pulse(ok3, a[3] + 92, 1.2, 8)

# ======================================================================
# 4. ブラウザは正直者：条件が合えば必ず添える（寄り）
# ======================================================================
P(e3, a[4] - 2)
P(c3, a[4] - 2)
P(ok3, a[4] - 2)
P(h1, a[4])
K.camera_move(a[4], to=(0, 0.5), zoom=14, dur=24)
stage_zoom(a[4], 1.8, 24)
auto = chip("自動で添える", (-3.9, 1.6), K.YELLOW, 0.42, INK)
K.pop_in(auto, a[4] + 20)
K.pulse(auto, a[4] + 36, 1.12, 8)
e4, c4 = send_normal(a[4] + 22)
P(e4, a[4] + 108)
P(c4, a[4] + 108)
K.camera_move(a[5] - 6, to=(0, 0), zoom=16, dur=22)
stage_zoom(a[5] - 6, 1.0, 22)

# ======================================================================
# 5. 利用者が悪意のあるサイトを開く
# ======================================================================
h2 = hdr("CSRF攻撃", K.RED)
K.pop_in(h2, a[5] + 4)
P(auto, a[5])
P(login, a[5])
open_arr = L(K.arrow((1.8, -0.6), (3.8, -0.6), EVIL, 0.1, 0.34))
K.grow_x(open_arr, a[5] + 10, 14)
open_chip = chip("ページを開く", (2.8, 0.35), K.YELLOW, 0.36, INK)
K.pop_in(open_chip, a[5] + 24)
K.shake(site_r, a[5] + 40, 0.12, 6, 2)
K.pulse(site_r, a[5] + 40, 1.08, 8)
vote = L(K.card("投票A", loc=(4.2, 3.0), w=2.6, h=1.1, color=K.ORANGE, size=0.42))
K.pop_in(vote, a[5] + 44)

# ======================================================================
# 6. 裏で「投票を削除」を送らせる
# ======================================================================
P(open_chip, a[6])
back = chip("裏で勝手に", (3.0, 0.95), K.YELLOW, 0.4, INK)
K.pop_in(back, a[6] + 8)
K.pulse(back, a[6] + 22, 1.12, 8)
bad = env("投票を削除", EVIL, 5.6, 0.4, 2.3)
K.pop_in(bad, a[6] + 14)
P(back, a[6] + 54)
K.move(bad, a[6] + 56, (-0.9, LANE_Y0), 26)

# ======================================================================
# 7. ブラウザは律儀に Cookie も添えてしまう
# ======================================================================
bad_ck = cookie_copy(a[7] + 4, 0.95, LANE_Y0)
K.pulse(bad_ck, a[7] + 30, 1.25, 8)
K.pulse(browser, a[7] + 30, 1.08, 8)
K.shake(browser, a[7] + 36, 0.1, 6, 2)
K.move(bad, a[7] + 52, (-0.9, LANE_Y1), 20)
K.move(bad_ck, a[7] + 52, (0.95, LANE_Y1), 20)
K.pulse(server, a[7] + 74, 1.06, 8)

# ======================================================================
# 8. Cookie だけで本人と判断 → 削除が通ってしまう（サーバーへ寄る）
# ======================================================================
K.camera_move(a[8], to=(0, 0.5), zoom=14, dur=22)
stage_zoom(a[8], 1.8, 22)
judge = chip("Cookieだけで判断", (-4.9, 1.9), K.YELLOW, 0.36, INK)
K.pop_in(judge, a[8] + 10)
K.recolor(server, a[8] + 46, K.RED)
K.shake(server, a[8] + 46, 0.1, 6, 2)
xv = L(K.cross_mark((6.3, 3.0), 1.0, K.RED, 0.22, z=1.6))
K.recolor(vote, a[8] + 54, K.GRAY)
K.pop_in(xv, a[8] + 54)
K.shake(vote, a[8] + 54, 0.12, 6, 2)
P(vote, a[8] + 78)
P(xv, a[8] + 84)
gone_c = chip("削除が通った", (4.4, 1.9), K.RED, 0.42)
K.pop_in(gone_c, a[8] + 70)
K.pulse(gone_c, a[8] + 84, 1.15, 8)
P(bad, a[8] + 96)
P(bad_ck, a[8] + 96)

# ======================================================================
# 9. SameSite=Lax：別サイトからのPOSTには付かない
# ======================================================================
K.camera_move(a[9] - 2, to=(0, 0), zoom=16, dur=22)
stage_zoom(a[9] - 2, 1.0, 22)
P(h2, a[9])
P(judge, a[9])
P(gone_c, a[9])
K.recolor(server, a[9] + 4, K.PURPLE)
h3 = hdr("SameSite=Lax", K.BLUE)
K.pop_in(h3, a[9] + 6)
vote2 = L(K.card("投票A", loc=(4.2, 3.0), w=2.6, h=1.1, color=K.ORANGE, size=0.42))
K.pop_in(vote2, a[9] + 8)
bad2 = env("投票を削除", EVIL, 5.6, 0.4, 2.3)
K.pop_in(bad2, a[9] + 6)
K.move(bad2, a[9] + 14, (-0.9, LANE_Y0), 22)
lax_ck = sticker("Cookie", K.YELLOW, CK_HOME[0], CK_HOME[1], 1.5)
K.pop_in(lax_ck, a[9] + 40, 8)
K.move(lax_ck, a[9] + 48, (0.95, LANE_Y0), 16)
xlax = L(K.cross_mark((0.95, LANE_Y0), 0.9, K.RED, 0.2, z=1.8))
K.pop_in(xlax, a[9] + 68)
K.shake(lax_ck, a[9] + 68, 0.1, 6, 2)
P(lax_ck, a[9] + 84)
P(xlax, a[9] + 84)
K.move(bad2, a[9] + 88, (-0.9, LANE_Y1), 20)
noauth = chip("ログイン扱いにならない", (-4.9, 1.9), K.GREEN, 0.36, INK)
K.pop_in(noauth, a[9] + 100)
K.pulse(noauth, a[9] + 112, 1.12, 8)
K.pulse(vote2, a[9] + 112, 1.1, 8)

# ======================================================================
# 10. ただしページ移動のGETには付く
# ======================================================================
P(bad2, a[10])
P(noauth, a[10])
get_e = env("ページ移動のGET", K.TEAL, -1.1, LANE_Y0, 3.0)
K.pop_in(get_e, a[10] + 6)
get_ck = cookie_copy(a[10] + 22, 1.5, LANE_Y0)
K.pulse(get_ck, a[10] + 48, 1.2, 8)
K.move(get_e, a[10] + 60, (-1.1, LANE_Y1), 20)
K.move(get_ck, a[10] + 60, (1.5, LANE_Y1), 20)
K.pulse(server, a[10] + 82, 1.06, 8)
only = chip("これだけに頼れない", (-4.9, 1.9), K.YELLOW, 0.36, INK)
K.pop_in(only, a[10] + 86)
K.pulse(only, a[10] + 98, 1.12, 8)

# ======================================================================
# 11. 対策：CSRFトークン（正規のページだけが知る合言葉）
# ======================================================================
clear(a[11] - 2, keep=[server, browser, site_l, site_r, lane, link_l, ck_home, vote2, open_arr])
P(h3, a[11])
h4 = hdr("対策：CSRFトークン", K.GREEN, INK)
K.pop_in(h4, a[11] + 6)
tarr = L(K.arrow((-2.0, 2.5), (-5.0, 0.4), K.GREEN, 0.1, 0.34))
K.grow_x(tarr, a[11] + 12, 16)
token = chip("合言葉", (-5.6, 0.6), K.GREEN, 0.42, INK)
K.pop_in(token, a[11] + 30)
K.pulse(token, a[11] + 44, 1.15, 8)
P(tarr, a[11] + 54)
unk = L(K.dot((5.6, 0.7), 0.5, K.GRAY, "?", K.WHITE))
K.pop_in(unk, a[11] + 74)
unk_t = chip("知らない", (5.6, 1.75), K.GRAY, 0.36)
K.pop_in(unk_t, a[11] + 80)
K.pulse(unk, a[11] + 96, 1.2, 8)

# ======================================================================
# 12. 合言葉をヘッダーに付けて送り、サーバーが照合する
# ======================================================================
EX, CX, TX = -1.7, 0.45, 1.95


def send_token(f):
    e = env("投票する", K.TEAL, EX, LANE_Y0, 1.9)
    K.pop_in(e, f)
    c = cookie_copy(f + 10, CX, LANE_Y0, 1.4)
    K.pulse(c, f + 34, 1.2, 8)
    t = sticker("合言葉", K.GREEN, SITE_L[0], 0.6, 1.5, sub="ヘッダー")
    K.pop_in(t, f + 24, 8)
    K.move(t, f + 32, (TX, LANE_Y0), 18)
    K.pulse(t, f + 52, 1.2, 8)
    K.move(e, f + 60, (EX, LANE_Y1), 18)
    K.move(c, f + 60, (CX, LANE_Y1), 18)
    K.move(t, f + 60, (TX, LANE_Y1), 18)
    return e, c, t


e12, c12, t12 = send_token(a[12] - 8)
ok12 = L(K.dot((-3.0, 2.6), 0.55, K.GREEN, "一致"))
K.pop_in(ok12, a[12] + 70)
K.pulse(ok12, a[12] + 80, 1.2, 8)
K.pulse(server, a[12] + 70, 1.06, 8)

# ======================================================================
# 13. 悪意のあるサイトは合言葉を読めない
# ======================================================================
for o in (e12, c12, t12, ok12):
    P(o, a[13] - 4)
bad3 = env("投票を削除", EVIL, 5.6, 0.4, 1.9)
K.pop_in(bad3, a[13] - 2)
K.move(bad3, a[13] + 2, (EX, LANE_Y0), 18)
bad3_ck = cookie_copy(a[13] + 14, CX, LANE_Y0, 1.4)
q = L(K.dot((TX, LANE_Y0), 0.38, K.GRAY, "?", K.WHITE, z=1.2))
K.pop_in(q, a[13] + 36)
xq = L(K.cross_mark((TX, LANE_Y0), 0.8, K.RED, 0.18, z=1.8))
K.pop_in(xq, a[13] + 48)
K.pulse(unk, a[13] + 40, 1.2, 8)

# ======================================================================
# 14. サーバーは403で拒否（緑の盾）
# ======================================================================
for o in (xq, q):
    P(o, a[14] + 18)
K.move(bad3, a[14], (EX, LANE_Y1), 18)
K.move(bad3_ck, a[14], (CX, LANE_Y1), 18)
sh = shield((-3.4, 2.6), 0.8, K.GREEN, "403")
K.pop_in(sh, a[14] + 22)
K.pulse(sh, a[14] + 34, 1.15, 8)
K.shake(bad3, a[14] + 30, 0.14, 6, 2)
P(bad3, a[14] + 44)
P(bad3_ck, a[14] + 44)
safe = chip("投票は無事", (2.9, 2.0), K.GREEN, 0.4, INK)
K.pop_in(safe, a[14] + 46)
K.pulse(vote2, a[14] + 52, 1.12, 8)

# ======================================================================
# 15. CORSは別物（ドアマン）
# ======================================================================
clear(a[15] - 2, keep=[server, browser, site_r, lane, vote2])
P(site_l, a[15] - 2)
P(h4, a[15])
h5 = hdr("CORSは別物", K.PURPLE)
K.pop_in(h5, a[15] + 4)
door = L(K.person((2.9, -0.6), K.ORANGE, 0.9))
K.pop_in(door, a[15] + 14)
door_t = chip("CORS：ドアマン", (3.7, -1.85), K.ORANGE, 0.34)
K.pop_in(door_t, a[15] + 24)
K.bob(door, a[15] + 30, a[16] + 10, 0.08, 16)
K.pulse(door_t, a[15] + 40, 1.1, 8)

# ======================================================================
# 16. 他サイトのJavaScriptが応答を読めるか
# ======================================================================
js = chip("JavaScript", (5.6, 0.65), K.BLUE, 0.36)
K.pop_in(js, a[16] + 4)
resp = env("応答", K.TEAL, 0.0, 1.8, 1.5)
K.pop_in(resp, a[16] + 24)
K.move(resp, a[16] + 36, (1.0, 0.85), 18)
K.move(resp, a[16] + 60, (2.0, 0.85), 18)
xr = L(K.cross_mark((3.4, 0.85), 0.9, K.RED, 0.2, z=1.8))
K.pop_in(xr, a[16] + 80)
K.shake(resp, a[16] + 80, 0.1, 6, 2)
K.shake(door, a[16] + 82, 0.08, 6, 2)
cant = chip("読ませない", (3.2, 1.85), K.RED, 0.4)
K.pop_in(cant, a[16] + 90)
K.pulse(cant, a[16] + 104, 1.12, 8)
P(resp, a[16] + 112)
P(xr, a[16] + 112)

# ======================================================================
# 17. 送信そのものは止めない場合もある／サーバーを守る仕組みではない
# ======================================================================
P(cant, a[17] - 2)
P(js, a[17] - 2)
req = env("リクエスト", EVIL, 5.6, 0.6, 2.3)
K.pop_in(req, a[17] + 4)
K.move(req, a[17] + 16, (0.0, LANE_Y0 + 0.2), 30)
K.move(req, a[17] + 52, (0.0, LANE_Y1), 18)
K.pulse(server, a[17] + 72, 1.06, 8)
sent = chip("送信は届く場合も", (-4.9, 1.9), K.YELLOW, 0.36, INK)
K.pop_in(sent, a[17] + 40)
nodef = chip("サーバーの防御ではない", (-4.9, 0.9), K.RED, 0.36)
K.pop_in(nodef, a[17] + 86)
K.pulse(nodef, a[17] + 100, 1.12, 8)

# ======================================================================
# 18. まとめ
# ======================================================================
clear(a[18] - 16, keep=[], step=1)
for o in (stage, server, browser, site_l, site_r):
    P(o, a[18] - 14)
P(h5, a[18] - 16)
m1 = K.text("Cookieは自動で付く", 1.1, (0, 2.3, 0.5), K.WHITE, 900)
m2 = K.text("だから、合言葉で確かめる", 1.05, (0, 0.85, 0.5), K.ORANGE, 900)
K.pop_in(m1, a[18] - 6)
K.pop_in(m2, a[18] + 4)
fk = K.card("Cookie", loc=(-2.0, -1.0), w=2.8, h=1.1, color=K.YELLOW, text_color=INK, size=0.45)
ft = K.card("合言葉", loc=(2.0, -1.0), w=2.8, h=1.1, color=K.GREEN, text_color=INK, size=0.45)
K.pop_in(fk, a[18] + 16)
K.pop_in(ft, a[18] + 24)
K.pulse(ft, a[18] + 50, 1.12, 8)

# ======================================================================
# 描画
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
