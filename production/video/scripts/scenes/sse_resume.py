"""SSEの切断とLast-Event-ID（第37章）

場面構成（行番号は sse-resume.json の lines）
  0      タイトル
  1-2    ラジオ局(サーバー)とラジオ(ブラウザ)。イベント #41 #42 が届き、得票バーが伸びる
  3-6    トンネルで切断。その間に #43〜#45 がサーバーで起き、DBの履歴台帳に積まれる。ブラウザは知らない
  7-8    EventSource が自動で再接続し、Last-Event-ID: 42 を伝える
  9-11   台帳から #43〜#45 を先に送り、ライブ配信に合流（#46…）。順番どおりに取り戻せる
  12-14  重複：#45 が2回届く（少し重ねて送るため）。ブラウザは番号を見て2回目を無視
  15-17  台帳の保存期間が過ぎていたら、差分ではなく現在の状態をまるごと取り直す
  18     まとめ

確認用: TM_LIST=300,600  TM_OUT=出力フォルダ  TM_PCT=50
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
import bpy
import bl_kit as K

T = K.setup()
A = lambda i, off=0.0: K.at(T, i, off)
INK = K.BG
a = [A(i) for i in range(19)]

live = []
gone = set()


def L(o):
    live.append(o)
    return o


def P(o, f, dur=8):
    """退場（二重に退場させるとアニメが壊れるので記録する）"""
    if o.name in gone:
        return
    gone.add(o.name)
    K.pop_out(o, f, dur)


def clear(f, keep=(), step=1):
    k = 0
    for o in list(live):
        if any(o is x for x in keep):
            continue
        if o.name not in gone:
            P(o, f + min(k, 12) * step)
            k += 1
        live.remove(o)


def tw(label, size):
    return sum(0.78 if ord(c) > 255 else 0.52 for c in label) * size + 0.9


def chip(label, loc, color, size=0.4, tc=K.WHITE, w=None, z=0.0):
    w = w or tw(label, size)
    return L(K.card(label, loc=loc, w=w, h=size * 2.0, color=color, text_color=tc, size=size, z=z))


def hdr(label, color, tc=K.WHITE):
    w = tw(label, 0.4)
    return L(K.card(label, loc=(-6.7 + w / 2, 3.65), w=w, h=0.8, color=color, text_color=tc, size=0.4))


def ev(label, x, y, color=K.TEAL, z=1.0, tc=K.WHITE, w=0.98):
    """番号付きイベントの小さなカード"""
    return L(K.card(label, loc=(x, y), w=w, h=0.62, color=color, text_color=tc, size=0.32, z=z))


# 位置
SRV = (-5.4, 2.0)
BRW = (5.3, 2.0)
PIPE_Y = 2.0
X0, X1 = -3.05, 3.1            # イベントが飛ぶ範囲
SLOT = {n: (-6.1 + (n - 41) * 0.95, -1.25) for n in range(41, 47)}   # 台帳の枠 #41〜#46
BAR_Y = (0.5, -0.1, -0.7)
BAR_C = (K.ORANGE, K.TEAL, K.PURPLE)


def fly(label, f0, dur=26, color=K.TEAL, back=False, tc=K.WHITE):
    """サーバー→ブラウザへイベントが飛ぶ。到着フレームを返す。"""
    c = ev(label, X0, PIPE_Y, color, tc=tc)
    K.pop_in(c, f0, 6)
    K.move(c, f0 + 6, (X1, PIPE_Y), dur, "SINE", "EASE_IN_OUT")
    P(c, f0 + 6 + dur)
    return f0 + 6 + dur


# ======================================================================
# 0. タイトル
# ======================================================================
K.title_card(T, "SSEの再接続", "切れても、続きから受け取る", until_line=0)
stage = K.rounded_rect("stage", 15.2, 7.0, (0, 0.75, -1), K.BG2, 0.5)
K.pop_in(stage, a[1] - 4, 12)


def stage_zoom(f, s, dur=22):
    """カメラを寄せている間だけ舞台パネルを大きくして、パネルの端が画面に写らないようにする"""
    K._key(stage, "scale", f, K._cur(stage, "scale"), "SINE", "EASE_IN_OUT")
    K._key(stage, "scale", f + dur, (s, s, s), "SINE", "EASE_IN_OUT")


# ======================================================================
# 1. SSE：サーバーからブラウザへ流し続ける
# ======================================================================
h1 = hdr("SSE：流し続ける", K.TEAL)
K.pop_in(h1, a[1] + 2)
server = K.card("サーバー", loc=SRV, w=3.0, h=1.5, color=K.ORANGE, size=0.45, sub="ラジオ局")
browser = K.card("ブラウザ", loc=BRW, w=3.0, h=1.5, color=K.BLUE, size=0.45, sub="ラジオ")
K.pop_in(server, a[1] - 2)
K.pop_in(browser, a[1] + 2)
pipe = L(K.arrow((-3.7, PIPE_Y), (3.7, PIPE_Y), K.GRAY, 0.1, 0.32))
K.grow_x(pipe, a[1] + 8, 16)
for k in range(3):       # 番号なしの流れ（流し続けるイメージ）
    d = L(K.dot((X0, PIPE_Y), 0.22, K.TEAL, z=1.0))
    f0 = a[1] + 26 + k * 16
    K.pop_in(d, f0, 6)
    K.move(d, f0 + 6, (X1, PIPE_Y), 22, "SINE", "EASE_IN_OUT")
    P(d, f0 + 28)

# 得票バー（ブラウザの画面）
panel = L(K.rounded_rect("panel", 3.4, 3.0, (5.3, -0.35, -0.5), K.DARK, 0.3))
K.pop_in(panel, a[1] + 76)
vt = L(K.text("得票", 0.3, (5.3, 0.95, 0.1), K.GRAY, 700))
K.pop_in(vt, a[1] + 80)
fills = []
for i, (y, c) in enumerate(zip(BAR_Y, BAR_C)):
    lb = L(K.text("ABC"[i], 0.4, (4.1, y, 0.1), K.WHITE, 900))
    K.pop_in(lb, a[1] + 84 + i * 4)
    bar, fill = K.progress_bar((5.8, y), 2.2, 0.26, c, K.BG2)
    live.append(bar)
    K.pop_in(bar, a[1] + 84 + i * 4)
    fills.append(fill)
# 最後に受け取った番号
last_lb = L(K.text("最後に受け取った番号", 0.28, (5.3, -1.1, 0.1), K.GRAY, 700))
K.pop_in(last_lb, a[1] + 90)

# 受け取った番号（ticker）の切り替え時刻
arr = {}

# ======================================================================
# 2. イベント #41 #42：届くたびに得票バーが伸びる
# ======================================================================
arr[41] = fly("#41", a[2] + 6)
K.bar_to(fills[0], arr[41], 0.30, 14)
K.pulse(browser, arr[41], 1.06, 8)
arr[42] = fly("#42", a[2] + 52)
K.bar_to(fills[1], arr[42], 0.30, 14)
K.pulse(browser, arr[42], 1.06, 8)

# ======================================================================
# 3. トンネルで切断
# ======================================================================
K.camera_move(a[3], to=(0, 0.7), zoom=14, dur=22)
stage_zoom(a[3], 1.8, 22)
P(h1, a[3])
h2 = hdr("切断", K.RED)
K.pop_in(h2, a[3] + 10)
P(pipe, a[3] + 4)
tunnel = L(K.card("", loc=(0, PIPE_Y), w=3.0, h=1.5, color=K.hexc("#0b0e1a"), text_color=K.GRAY, size=0.42, z=0.3))
K.pop_in(tunnel, a[3] + 8)
tun_t = L(K.text("トンネル", 0.34, (0, PIPE_Y - 1.1, 0.3), K.GRAY, 700))
K.pop_in(tun_t, a[3] + 12)
cut = L(K.cross_mark((0, PIPE_Y), 1.3, K.RED, 0.24, z=1.6))
K.pop_in(cut, a[3] + 26)
K.shake(tunnel, a[3] + 26, 0.1, 6, 2)
K.recolor(browser, a[3] + 26, K.GRAY)
nosig = chip("電波が切れた", (0, 3.4), K.RED, 0.4)
K.pop_in(nosig, a[3] + 40)
K.pulse(nosig, a[3] + 54, 1.12, 8)

# ======================================================================
# 4. 切断中に #43 #44 #45 がサーバーで起きる
# ======================================================================
nothing = chip("何も届かない", (5.3, 3.5), K.YELLOW, 0.36, INK)
K.pop_in(nothing, a[4] + 8)
pending = {}
for n, d in ((43, 46), (44, 72), (45, 98)):
    c = ev(f"#{n}", -6.2 + (n - 43) * 1.05, 0.78, K.PURPLE, w=0.9)
    K.pop_in(c, a[4] + d)
    K.pulse(c, a[4] + d + 12, 1.2, 8)
    pending[n] = c

# ======================================================================
# 5. DBの履歴台帳に積まれる
# ======================================================================
K.camera_move(a[5] + 6, to=(0, 0), zoom=16, dur=22)
stage_zoom(a[5] + 6, 1.0, 22)
ledger = L(K.rounded_rect("ledger", 6.2, 2.3, (-3.9, -1.05, -0.5), K.DARK, 0.3))
K.pop_in(ledger, a[5] + 2)
ledger_t = L(K.text("履歴台帳（DB）", 0.34, (-3.9, -0.15, 0.1), K.GRAY, 700))
K.pop_in(ledger_t, a[5] + 6)
slot = {}
for n in (41, 42):
    c = ev(f"#{n}", *SLOT[n], K.PURPLE, w=0.9)
    K.pop_in(c, a[5] + 10 + (n - 41) * 4)
    slot[n] = c
for k, n in enumerate((43, 44, 45)):
    K.move(pending[n], a[5] + 24 + k * 10, SLOT[n], 16)
    live.remove(pending[n])
    slot[n] = pending[n]
    K.pulse(pending[n], a[5] + 40 + k * 10, 1.2, 8)

# ======================================================================
# 6. ブラウザは、その間に起きたことを知らない
# ======================================================================
P(nothing, a[6])
P(nosig, a[6])
P(h2, a[6])
h3 = hdr("ブラウザは知らない", K.GRAY)
K.pop_in(h3, a[6] + 4)
unk = L(K.dot((5.3, 3.55), 0.5, K.GRAY, "?", K.WHITE))
K.pop_in(unk, a[6] + 14)
K.pulse(unk, a[6] + 40, 1.25, 8)
K.pulse(unk, a[6] + 62, 1.25, 8)

# ======================================================================
# 7. EventSource が自動で再接続
# ======================================================================
P(unk, a[7])
P(h3, a[7])
P(tunnel, a[7] + 2)
P(tun_t, a[7] + 2)
P(cut, a[7] + 2)
h4 = hdr("再接続", K.BLUE)
K.pop_in(h4, a[7] + 6)
K.recolor(browser, a[7] + 8, K.BLUE)
es = chip("EventSource", (5.3, 3.5), K.BLUE, 0.38)
K.pop_in(es, a[7] + 14)
K.pulse(es, a[7] + 30, 1.12, 8)
auto = chip("自動でつなぎ直す", (0, 3.4), K.GREEN, 0.4, INK)
K.pop_in(auto, a[7] + 30)
back_arr = L(K.arrow((3.7, PIPE_Y), (-3.7, PIPE_Y), K.BLUE, 0.1, 0.32))
K.grow_x(back_arr, a[7] + 36, 18)

# ======================================================================
# 8. 「最後に受け取ったのは42」= Last-Event-ID（寄り）
# ======================================================================
P(es, a[8] - 2)
P(auto, a[8] - 2)
P(h4, a[8])
h5 = hdr("Last-Event-ID", K.ORANGE)
K.pop_in(h5, a[8] + 6)
K.camera_move(a[8] + 4, to=(0, 0.7), zoom=14, dur=22)
stage_zoom(a[8] + 4, 1.8, 22)
req = L(K.card("Last-Event-ID: 42", loc=(1.5, PIPE_Y), w=tw("Last-Event-ID: 42", 0.38) + 0.2, h=0.8,
               color=K.YELLOW, text_color=INK, size=0.38, z=1.2))
K.pop_in(req, a[8] + 16)
K.pulse(req, a[8] + 36, 1.12, 8)
K.move(req, a[8] + 54, (-1.55, PIPE_Y), 26)
K.pulse(server, a[8] + 82, 1.06, 8)
K.pulse(last_lb, a[8] + 36, 1.15, 8)

# ======================================================================
# 9. 台帳から #43〜#45 を先に送る
# ======================================================================
K.camera_move(a[9] - 4, to=(0, 0), zoom=16, dur=22)
stage_zoom(a[9] - 4, 1.0, 22)
P(req, a[9])
P(back_arr, a[9])
pipe2 = L(K.arrow((-3.7, PIPE_Y), (3.7, PIPE_Y), K.GRAY, 0.1, 0.32))
K.grow_x(pipe2, a[9] + 4, 14)
up = L(K.arrow((-4.6, 0.05), (-4.6, 1.2), K.PURPLE, 0.12, 0.34))
K.grow_x(up, a[9] + 8, 12)
P(up, a[9] + 100)
h6 = hdr("台帳から先に送る", K.PURPLE)
P(h5, a[9])
K.pop_in(h6, a[9] + 6)
for k, n in enumerate((43, 44, 45)):
    K.pulse(slot[n], a[9] + 26 + k * 6, 1.25, 8)
fr = {43: (0.48, 0), 44: (0.25, 2), 45: (0.62, 0)}
for k, n in enumerate((43, 44, 45)):
    arr[n] = fly(f"#{n}", a[9] + 42 + k * 13, 24, K.PURPLE)
    K.bar_to(fills[fr[n][1]], arr[n], fr[n][0], 12)
    K.pulse(browser, arr[n], 1.05, 8)

# ======================================================================
# 10. ライブ配信に合流（#46…）
# ======================================================================
P(h6, a[10])
h7 = hdr("ライブ配信に合流", K.GREEN, INK)
K.pop_in(h7, a[10] + 4)
live_tag = chip("ライブ", (0, 3.4), K.GREEN, 0.4, INK)
K.pop_in(live_tag, a[10] + 10)
K.pulse(server, a[10] + 26, 1.06, 8)
s46 = ev("#46", *SLOT[46], K.PURPLE, w=0.9)
K.pop_in(s46, a[10] + 34)
arr[46] = fly("#46", a[10] + 38, 26, K.GREEN, tc=INK)
K.bar_to(fills[1], arr[46], 0.45, 12)
K.pulse(browser, arr[46], 1.05, 8)
for k in range(2):
    d = L(K.dot((X0, PIPE_Y), 0.22, K.GREEN, z=1.0))
    f0 = a[10] + 80 + k * 16
    K.pop_in(d, f0, 6)
    K.move(d, f0 + 6, (X1, PIPE_Y), 22, "SINE", "EASE_IN_OUT")
    P(d, f0 + 28)
P(live_tag, a[11] - 2)

# ======================================================================
# 11. 切れていた間の分も、順番に取り戻せる
# ======================================================================
P(h7, a[11])
h8 = hdr("順番どおり", K.GREEN, INK)
K.pop_in(h8, a[11] + 4)
ROW_Y = 0.95
rowc = {}
for k, n in enumerate(range(43, 48)):
    c = ev(f"#{n}", -2.2 + k * 1.1, ROW_Y, K.PURPLE if n <= 45 else K.GREEN, z=0.5, tc=K.WHITE if n <= 45 else INK)
    K.pop_in(c, a[11] + 12 + k * 12)
    rowc[n] = c

# ======================================================================
# 12. 同じ番号が重複して届く（#45）
# ======================================================================
P(h8, a[12])
h9 = hdr("重複", K.YELLOW, INK)
K.pop_in(h9, a[12] + 4)
dup = ev("#45", 0.0, -0.1, K.ORANGE, z=0.6)
K.pop_in(dup, a[12] + 22)
K.shake(dup, a[12] + 36, 0.12, 6, 2)
K.pulse(rowc[45], a[12] + 36, 1.25, 8)
twice = chip("#45が2回", (1.2, -0.8), K.ORANGE, 0.4)
K.pop_in(twice, a[12] + 46)
K.pulse(twice, a[12] + 62, 1.12, 8)

# ======================================================================
# 13. 少し重ねて送る（台帳の範囲とライブの範囲が #45 で重なる）
# ======================================================================
P(twice, a[13] - 2)
brk_a = L(K.plane("bracketA", 3.5, 0.12, (-1.0, 0.55, 0.1), K.PURPLE))
brk_b = L(K.plane("bracketB", 3.5, 0.12, (1.0, 0.25, 0.1), K.GREEN))
K.pop_in(brk_a, a[13] + 6)
K.pop_in(brk_b, a[13] + 20)
bl_a = chip("台帳から", (-3.95, 0.55), K.PURPLE, 0.3)
bl_b = chip("ライブ", (1.8, -0.2), K.GREEN, 0.3, INK)
K.pop_in(bl_a, a[13] + 10)
K.pop_in(bl_b, a[13] + 24)
K.pulse(rowc[45], a[13] + 46, 1.3, 8)
ovl = chip("重なる", (0.6, -0.95), K.YELLOW, 0.36, INK)
K.pop_in(ovl, a[13] + 50)
K.pulse(ovl, a[13] + 64, 1.12, 8)

# ======================================================================
# 14. ブラウザは番号を見て、2回目を無視
# ======================================================================
P(ovl, a[14] - 2)
K.move(dup, a[14] + 2, (2.6, -0.9), 22)
xd = L(K.cross_mark((2.6, -0.9), 0.9, K.RED, 0.2, z=1.8))
K.pop_in(xd, a[14] + 28)
K.shake(dup, a[14] + 28, 0.1, 6, 2)
P(dup, a[14] + 44)
P(xd, a[14] + 44)
ign = chip("2回目は無視", (0.6, -1.3), K.GRAY, 0.36)
K.pop_in(ign, a[14] + 40)
K.pulse(ign, a[14] + 54, 1.12, 8)

# ======================================================================
# 15. 台帳の保存期間が過ぎて、消えている
# ======================================================================
for o in (brk_a, brk_b, bl_a, bl_b, ign, h9):
    P(o, a[15] - 2)
for n in rowc:
    P(rowc[n], a[15] - 2)
h10 = hdr("保存期間が過ぎた", K.RED)
K.pop_in(h10, a[15] + 4)
clock = chip("保存期間", (-4.0, 0.55), K.YELLOW, 0.36, INK)
K.pop_in(clock, a[15] + 10)
K.pulse(clock, a[15] + 24, 1.15, 8)
for k, n in enumerate((41, 42, 43, 44, 45)):
    K.recolor(slot[n], a[15] + 34 + k * 8, K.GRAY)
    P(slot[n], a[15] + 52 + k * 5)
gone_t = chip("消えている", (-4.0, -1.2), K.RED, 0.4)
K.pop_in(gone_t, a[15] + 80)
K.pulse(gone_t, a[15] + 92, 1.12, 8)

# ======================================================================
# 16. 差分ではなく、現在の状態をまるごと取り直す
# ======================================================================
P(clock, a[16] - 2)
P(gone_t, a[16] - 2)
P(h10, a[16])
h11 = hdr("まるごと取り直す", K.GREEN, INK)
K.pop_in(h11, a[16] + 4)
nodiff = chip("差分は作れない", (-0.2, 3.4), K.RED, 0.4)
K.pop_in(nodiff, a[16] + 8)
snap = L(K.card("現在の状態", loc=(X0 + 0.4, PIPE_Y), w=2.9, h=0.9, color=K.GREEN, text_color=INK, size=0.4, z=1.2))
P(nodiff, a[16] + 40)
K.pop_in(snap, a[16] + 44)
K.move(snap, a[16] + 52, (X1 - 0.6, PIPE_Y), 28, "SINE", "EASE_IN_OUT")
arr[52] = a[16] + 80
K.bar_to(fills[0], arr[52], 0.7, 14)
K.bar_to(fills[1], arr[52], 0.55, 14)
K.bar_to(fills[2], arr[52], 0.4, 14)
K.pulse(browser, arr[52], 1.06, 8)
P(snap, a[16] + 80)

# ======================================================================
# 17. 画面は最新に追いつく
# ======================================================================
P(h11, a[17])
okd = L(K.dot((2.5, 0.2), 0.55, K.GREEN, "最新"))
K.pop_in(okd, a[17] + 8)
K.pulse(okd, a[17] + 22, 1.2, 8)
K.pulse(panel, a[17] + 18, 1.05, 8)

# 受け取った番号の表示切り替え（枠はずっと出しておく）
seq = [(a[1] + 90, "—", K.GRAY),
       (arr[41], "#41", K.YELLOW), (arr[42], "#42", K.YELLOW),
       (arr[43], "#43", K.YELLOW), (arr[44], "#44", K.YELLOW), (arr[45], "#45", K.YELLOW),
       (arr[46], "#46", K.YELLOW), (arr[52], "#52", K.GREEN)]
K.number_ticker(seq, (5.3, -1.5, 0.1), 0.5, end=a[18] - 14)

# ======================================================================
# 18. まとめ
# ======================================================================
clear(a[18] - 18, keep=[], step=1)
for o in (stage, server, browser):
    P(o, a[18] - 16)
P(okd, a[18] - 16)
m1 = K.text("切れても、", 1.15, (0, 2.5, 0.5), K.WHITE, 900)
m2 = K.text("台帳から続きを届ける", 1.05, (0, 1.15, 0.5), K.ORANGE, 900)
K.pop_in(m1, a[18] - 8)
K.pop_in(m2, a[18] + 2)
for k, n in enumerate((43, 44, 45)):
    c = K.card(f"#{n}", loc=(-2.2 + k * 2.2, -0.9), w=1.8, h=1.0, color=K.PURPLE, size=0.45)
    K.pop_in(c, a[18] + 14 + k * 6)
    K.pulse(c, a[18] + 40 + k * 6, 1.12, 8)

# ======================================================================
# 描画
# ======================================================================
sc = bpy.context.scene
if os.environ.get("TM_LIST"):
    sc.render.resolution_percentage = int(os.environ.get("TM_PCT", "50"))
    for fr_ in os.environ["TM_LIST"].split(","):
        sc.frame_set(int(fr_))
        sc.render.filepath = os.path.join(os.environ["TM_OUT"], f"s_{int(fr_):04d}.png")
        sc.render.image_settings.file_format = "PNG"
        bpy.ops.render.render(write_still=True)
else:
    K.render(T)
