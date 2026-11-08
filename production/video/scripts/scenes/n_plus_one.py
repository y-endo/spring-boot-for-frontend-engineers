"""N+1：SQLの滝（第40章）

場面構成（行番号は n-plus-one.json の lines）
  0      タイトル
  1      やりたいこと：投票の一覧と、それぞれの選択肢を表示
  2-4    SQL 1本で投票一覧 → レジのカゴ（永続コンテキスト）→ 選択肢は「あとで聞く」札（Lazy）
  5-7    札を見るたびにSQL。100件なら100本の滝。合計 1＋N ＝ 101本
  8-9    応答時間のバーが伸びる／開発（少ない）では気づきにくい
  10-12  対策：最初から一緒に取る（fetch join / DTO）。SQLは1〜2本、応答も短く
  13-14  注意：トランザクションの外で札を見ると LazyInitializationException
  15     まとめ

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
a = [A(i) for i in range(16)]

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
            P(o, f + min(k, 14) * step)
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


# ---- 位置 ----
ROWS = [1.55, 0.45, -0.65]
POLL_X, OPT_X = -5.4, -2.9
PANEL = (4.3, 0.2)              # SQLログのパネル中心
PIN_Y = 1.55                    # 1本目のSQLを固定表示する高さ
FALL_TOP, FALL_BOT, FALL_DUR = 0.9, -1.75, 24
BAR_LOC, BAR_W = (0.8, -2.3), 9.0


def option_chips(f0, step=8):
    res = []
    for i, y in enumerate(ROWS):
        c = chip("選択肢", (OPT_X, y), K.TEAL, 0.34)
        K.pop_in(c, f0 + i * step)
        res.append(c)
    return res


def lazy_tag(y, f):
    t = chip("あとで聞く", (OPT_X, y), K.YELLOW, 0.32, INK)
    K.pop_in(t, f)
    return t


def drop(f, label=None, color=K.ORANGE):
    """SQLログの滝：上から下へ一定の速さで落ちる。label なしは細い棒。"""
    x = PANEL[0]
    if label:
        o = L(K.card(label, loc=(x, FALL_TOP), w=5.0, h=0.5, color=color, text_color=INK, size=0.26, z=0.2))
    else:
        o = L(K.rounded_rect("sql", 5.0, 0.2, (x, FALL_TOP, 0.2), color, 0.09))
    K.pop_in(o, f, 4)
    K.move(o, f + 4, (x, FALL_BOT), FALL_DUR, "LINEAR", "AUTO")
    P(o, f + 4 + FALL_DUR, 4)


# ======================================================================
# 0. タイトル
# ======================================================================
K.title_card(T, "N+1問題", "SQLの滝", until_line=0)
stage = K.rounded_rect("stage", 15.2, 7.0, (0, 0.75, -1), K.BG2, 0.5)
K.pop_in(stage, a[1] - 4, 12)


def stage_zoom(f, s, dur=22):
    """カメラを寄せている間だけ舞台パネルを大きくして、パネルの端が画面に写らないようにする"""
    K._key(stage, "scale", f, K._cur(stage, "scale"), "SINE", "EASE_IN_OUT")
    K._key(stage, "scale", f + dur, (s, s, s), "SINE", "EASE_IN_OUT")


# ======================================================================
# 1. やりたいこと：投票の一覧と選択肢
# ======================================================================
h1 = hdr("やりたいこと", K.TEAL)
K.pop_in(h1, a[1] + 2)
polls = []
for i, y in enumerate(ROWS):
    c = K.card(f"投票{i + 1}", loc=(POLL_X, y), w=2.0, h=0.85, color=K.BLUE, size=0.4)
    K.pop_in(c, a[1] + 8 + i * 8)
    polls.append(c)
more = K.card("…", loc=(POLL_X, -1.45), w=2.0, h=0.6, color=K.GRAY, size=0.4)
K.pop_in(more, a[1] + 34)
opts1 = option_chips(a[1] + 44)
user = L(K.person((4.6, 0.5), K.ORANGE, 1.5))
K.pop_in(user, a[1] + 30)
want = chip("一覧と選択肢を見たい", (4.6, 3.0), K.YELLOW, 0.4, INK)
K.pop_in(want, a[1] + 52)
K.bob(user, a[1] + 56, a[2] - 4, 0.1, 18)
K.pulse(want, a[1] + 76, 1.1, 8)

# ======================================================================
# 2. まず投票一覧：SQLは1本
# ======================================================================
for o in opts1:
    P(o, a[2])
P(user, a[2])
P(want, a[2])
P(h1, a[2] + 2)
h2 = hdr("まず一覧を取る", K.TEAL)
K.pop_in(h2, a[2] + 8)
db = L(K.card("DB", loc=(6.35, 3.35), w=2.2, h=0.9, color=K.PURPLE, size=0.45))
K.pop_in(db, a[2] + 8)
panel = L(K.rounded_rect("panel", 6.0, 4.3, (PANEL[0], PANEL[1], -0.5), K.DARK, 0.3))
K.pop_in(panel, a[2] + 14)
log_t = L(K.text("SQLログ", 0.32, (PANEL[0], 2.05, 0.1), K.GRAY, 700))
K.pop_in(log_t, a[2] + 20)
pin = L(K.card("SQL #1：投票の一覧", loc=(PANEL[0], PIN_Y), w=5.0, h=0.5, color=K.TEAL, size=0.26, z=0.2))
K.pop_in(pin, a[2] + 38)
tick1 = [(a[2] + 38, "SQL 1本", K.WHITE)]
res_arr = L(K.arrow((1.2, PIN_Y), (-1.6, PIN_Y), K.GRAY, 0.1, 0.32))
K.grow_x(res_arr, a[2] + 52, 14)
for i, c in enumerate(polls):
    K.pulse(c, a[2] + 70 + i * 6, 1.12, 8)
K.pulse(pin, a[2] + 60, 1.06, 8)

# ======================================================================
# 3. 取ったデータはレジのカゴ（永続コンテキスト）
# ======================================================================
P(res_arr, a[3])
P(h2, a[3])
basket = L(K.rounded_rect("basket", 5.8, 4.6, (-4.3, 0.45, -0.5), K.DARK, 0.3))
K.pop_in(basket, a[3] + 4)
bk_t = L(K.text("レジのカゴ（永続コンテキスト）", 0.32, (-4.3, 2.4, 0.1), K.WHITE, 700))
K.pop_in(bk_t, a[3] + 12)
h3 = hdr("レジのカゴ", K.ORANGE)
K.pop_in(h3, a[3] + 6)
for i, c in enumerate(polls):
    K.pulse(c, a[3] + 40 + i * 8, 1.15, 8)
K.pulse(basket, a[3] + 36, 1.03, 10)

# ======================================================================
# 4. 選択肢は「あとで聞く」札（Lazy）
# ======================================================================
P(h3, a[4])
h4 = hdr("Lazy：あとで聞く", K.PURPLE)
K.pop_in(h4, a[4] + 4)
tags = []
for i, y in enumerate(ROWS):
    tags.append(lazy_tag(y, a[4] + 36 + i * 22))
K.pulse(tags[0], a[4] + 104, 1.12, 8)
K.pulse(tags[1], a[4] + 112, 1.12, 8)
K.pulse(tags[2], a[4] + 120, 1.12, 8)

# ======================================================================
# 5. ループで札を見るたびに店員に聞きに行く → SQLが追加される
# ======================================================================
P(h4, a[5])
h5 = hdr("札を見るたびにSQL", K.RED)
K.pop_in(h5, a[5] + 4)
counts = {}
for k, y in enumerate(ROWS):
    t = a[5] + 8 + k * 26
    ar = L(K.arrow((-1.7, y), (1.2, y), K.ORANGE, 0.1, 0.32))
    K.grow_x(ar, t, 10)
    P(ar, t + 24)
    P(tags[k], t + 12)
    oc = chip("選択肢", (OPT_X, y), K.TEAL, 0.34)
    K.pop_in(oc, t + 14)
    drop(t + 10, f"SQL #{k + 2}：選択肢（投票{k + 1}）")
    counts[k + 2] = t + 10
tick1 += [(counts[2], "SQL 2本", K.WHITE), (counts[3], "SQL 3本", K.WHITE), (counts[4], "SQL 4本", K.WHITE)]

# ======================================================================
# 6. 100件なら100本追加：SQLが滝のように流れる
# ======================================================================
P(h5, a[6])
h6 = hdr("100件なら100本", K.RED)
K.pop_in(h6, a[6] + 4)
many = chip("×100件", (OPT_X, -1.45), K.YELLOW, 0.36, INK)
K.pop_in(many, a[6] + 6)
K.pulse(many, a[6] + 22, 1.2, 8)
K.pulse(more, a[6] + 22, 1.2, 8)
# 文字つきのSQLが少し速くなりながら落ち、そのあと細い棒の滝になる
t = a[6] + 14
for n, gap in zip(range(5, 10), (14, 12, 10, 9, 8)):
    drop(t, f"SQL #{n}：選択肢（投票{n - 1}）")
    t += gap
t = max(t, a[6] + 70)
while t < a[8] + 104:
    drop(t, None, K.ORANGE if (t // 3) % 2 else K.RED)
    t += 3
last_drop = t
# 応答時間のバー（SQLが増えるほど伸びる）
bar_lb = L(K.text("応答時間", 0.34, (-5.85, BAR_LOC[1], 0.1), K.GRAY, 700))
K.pop_in(bar_lb, a[6] + 40)
bar1, fill1 = K.progress_bar(BAR_LOC, BAR_W, 0.3, K.RED, K.DARK)
live.append(bar1)
K.pop_in(bar1, a[6] + 40)
K.bar_to(fill1, a[6] + 56, 0.30, 50)
tick1 += [(a[6] + 12 * j + 10, f"SQL {v}本", K.WHITE) for j, v in enumerate((6, 12, 21, 33, 46, 60, 74, 89))]

# ======================================================================
# 7. 合計 101 本（1＋N）
# ======================================================================
tick1.append((a[7] + 18, "SQL 101本", K.RED))
P(h6, a[7])
K.camera_move(a[7] + 4, to=(0.3, -0.1), zoom=14.6, dur=22)
stage_zoom(a[7] + 4, 1.8, 22)
banner = L(K.card("1＋N＝101本", loc=(0.3, 0.7), w=7.4, h=2.7, color=K.PURPLE, size=0.95,
                  sub="一覧1本 ＋ 投票ごとにN本", z=2.0))
K.pop_in(banner, a[7] + 24)
K.pulse(banner, a[7] + 48, 1.06, 8)
K.pulse(banner, a[7] + 88, 1.06, 8)
K.bar_to(fill1, a[7] + 20, 0.55, 90)

# ======================================================================
# 8. SQLが増えるほど応答時間も伸びる
# ======================================================================
P(banner, a[8] - 2)
K.camera_move(a[8] - 2, to=(0, 0), zoom=16, dur=22)
stage_zoom(a[8] - 2, 1.0, 22)
h7 = hdr("応答時間が伸びる", K.RED)
K.pop_in(h7, a[8] + 4)
K.bar_to(fill1, a[8] + 6, 0.96, 70)
slow = chip("遅い", (6.5, BAR_LOC[1]), K.RED, 0.3)
K.pop_in(slow, a[8] + 66)
K.pulse(slow, a[8] + 78, 1.2, 8)

# ======================================================================
# 9. 開発では気づきにくい
# ======================================================================
P(h7, a[9])
h8 = hdr("開発では気づきにくい", K.YELLOW, INK)
K.pop_in(h8, a[9] + 4)
dev = L(K.card("開発：投票3件 → SQL 4本", loc=(0.3, 1.2), w=7.6, h=1.0, color=K.GREEN, text_color=INK, size=0.42, z=2.0))
prod = L(K.card("本番：投票100件 → SQL 101本", loc=(0.3, -0.1), w=8.6, h=1.0, color=K.RED, size=0.42, z=2.0))
K.pop_in(dev, a[9] + 10)
K.pop_in(prod, a[9] + 48)
K.pulse(dev, a[9] + 26, 1.05, 8)
K.pulse(prod, a[9] + 66, 1.05, 8)

# ======================================================================
# 10. 対策：最初から一緒に取ってくる
# ======================================================================
tick1_end = a[10] - 2
for o in (dev, prod):
    P(o, a[10] - 2)
P(h8, a[10])
h9 = hdr("対策：最初から一緒に", K.GREEN, INK)
K.pop_in(h9, a[10] + 4)
P(pin, a[10] + 2)
P(many, a[10] + 2)
for ob in [c for c in live if c.name.startswith("card_選択肢")]:
    P(ob, a[10] + 2)
good_arr = L(K.arrow((1.2, 0.5), (-1.6, 0.5), K.GREEN, 0.14, 0.4))
K.grow_x(good_arr, a[10] + 16, 14)
opts2 = option_chips(a[10] + 24, 5)
for i, c in enumerate(polls):
    K.pulse(c, a[10] + 24 + i * 5, 1.12, 8)
pin2 = L(K.card("SQL #1：投票＋選択肢", loc=(PANEL[0], PIN_Y), w=5.0, h=0.5, color=K.GREEN, text_color=INK, size=0.26, z=0.2))
K.pop_in(pin2, a[10] + 20)
tick2 = [(a[10] + 20, "SQL 1本", K.GREEN)]

# ======================================================================
# 11. fetch join / DTO
# ======================================================================
P(good_arr, a[11])
fj = L(K.card("fetch join", loc=(PANEL[0], 0.45), w=4.8, h=1.1, color=K.GREEN, text_color=INK, size=0.42, sub="一緒に取ってくる"))
dto = L(K.card("DTO", loc=(PANEL[0], -0.95), w=4.8, h=1.1, color=K.TEAL, size=0.42, sub="必要な形だけ取る"))
K.pop_in(fj, a[11] + 6)
K.pop_in(dto, a[11] + 46)
K.pulse(fj, a[11] + 22, 1.08, 8)
K.pulse(dto, a[11] + 62, 1.08, 8)

# ======================================================================
# 12. SQLは1〜2本、応答時間も短く
# ======================================================================
tick2.append((a[12] + 4, "SQL 1〜2本", K.GREEN))
P(bar1, a[12])
P(slow, a[12])
bar2, fill2 = K.progress_bar(BAR_LOC, BAR_W, 0.3, K.GREEN, K.DARK)
live.append(bar2)
K.pop_in(bar2, a[12] + 8)
K.bar_to(fill2, a[12] + 16, 0.08, 24)
fast = chip("速い", (6.5, BAR_LOC[1]), K.GREEN, 0.3, INK)
K.pop_in(fast, a[12] + 44)
K.pulse(fast, a[12] + 56, 1.2, 8)
K.pulse(pin2, a[12] + 30, 1.06, 8)

# ======================================================================
# 13. 注意：トランザクションが終わった後に札を見に行く
# ======================================================================
tick2_end = a[13] - 6
clear(a[13] - 4, keep=[], step=0)
for o in polls + [more]:
    P(o, a[13] - 4)
K.camera_move(a[13], to=(0, 0.3), zoom=14, dur=24)
stage_zoom(a[13], 1.8, 24)
h10 = hdr("注意", K.YELLOW, INK)
K.pop_in(h10, a[13] + 2)
zone1 = L(K.card("", loc=(-3.3, 0.5), w=6.8, h=4.6, color=K.hexc("#17332f"), size=0.4, z=-0.4))
K.pop_in(zone1, a[13] + 6)
z1t = K.text("トランザクション中", 0.38, (0, 1.95, 0.05), K.TEAL, 900, parent=zone1)
zone2 = L(K.card("", loc=(3.8, 0.5), w=6.0, h=4.6, color=K.hexc("#33202c"), size=0.4, z=-0.4))
K.pop_in(zone2, a[13] + 14)
z2t = K.text("終わった後", 0.38, (0, 1.95, 0.05), K.RED, 900, parent=zone2)
bk_open = L(K.card("カゴ：開いている", loc=(-3.3, -0.35), w=4.6, h=1.5, color=K.GREEN, text_color=INK, size=0.42))
K.pop_in(bk_open, a[13] + 20)
poll_x = L(K.card("投票1", loc=(-5.0, 1.55), w=2.0, h=0.85, color=K.BLUE, size=0.4, z=0.3))
tag_x = L(K.card("あとで聞く", loc=(-2.4, 1.55), w=2.4, h=0.7, color=K.YELLOW, text_color=INK, size=0.32, z=0.3))
K.pop_in(poll_x, a[13] + 26)
K.pop_in(tag_x, a[13] + 32)
# トランザクション終了：カゴが閉じ、投票と札は外へ出る
K.recolor(zone1, a[13] + 50, K.hexc("#2a2f45"))
P(bk_open, a[13] + 50)
bk_closed = L(K.card("カゴ：閉じた", loc=(-3.3, -0.35), w=4.6, h=1.5, color=K.GRAY, size=0.42))
K.pop_in(bk_closed, a[13] + 54)
ended = chip("トランザクション終了", (-3.3, -1.45), K.GRAY, 0.34)
K.pop_in(ended, a[13] + 56)
K.move(poll_x, a[13] + 66, (5.4, 1.55), 22)
K.move(tag_x, a[13] + 66, (2.9, 1.55), 22)
# 店員が札を見に行く（カゴは閉じている）
clerk = L(K.person((5.4, -0.3), K.ORANGE, 1.2))
K.pop_in(clerk, a[13] + 70)
clerk_t = chip("店員", (5.4, -1.5), K.ORANGE, 0.3)
K.pop_in(clerk_t, a[13] + 74)
K.pulse(tag_x, a[13] + 92, 1.2, 8)
K.move(clerk, a[13] + 96, (1.9, -0.3), 24)
K.move(clerk_t, a[13] + 96, (1.9, -1.5), 24)
look = L(K.dashed_line((1.2, -0.3), (-0.9, -0.3), K.RED, 0.09))
K.pop_in(look, a[13] + 112)
xm = L(K.cross_mark((0.15, -0.3), 1.0, K.RED, 0.2, z=1.6))
K.pop_in(xm, a[13] + 122)

# ======================================================================
# 14. LazyInitializationException
# ======================================================================
say = chip("聞けない！", (4.4, 0.45), K.RED, 0.38)
K.pop_in(say, a[14] + 2)
K.shake(clerk, a[14] + 4, 0.12, 8, 2)
err = chip("LazyInitializationException", (0.4, 3.35), K.RED, 0.4, z=1.0)
K.pop_in(err, a[14] + 10)
K.shake(err, a[14] + 24, 0.16, 8, 2)
K.pulse(xm, a[14] + 14, 1.3, 8)
K.pulse(err, a[14] + 48, 1.08, 8)
closed_note = chip("カゴはもう閉じている", (-3.3, 1.2), K.RED, 0.34)
K.pop_in(closed_note, a[14] + 32)

# ======================================================================
# 15. まとめ
# ======================================================================
clear(a[15] - 18, keep=[], step=1)
K.camera_move(a[15] - 18, to=(0, 0), zoom=16, dur=20)
for o in (stage, h10, zone1, zone2, poll_x, tag_x):
    P(o, a[15] - 16)
m1 = K.text("SQLが何本流れるか、", 1.1, (0, 2.3, 0.5), K.WHITE, 900)
m2 = K.text("言えるようにする", 1.1, (0, 0.9, 0.5), K.ORANGE, 900)
K.pop_in(m1, a[15] - 8)
K.pop_in(m2, a[15] + 2)
c1 = K.card("1＋N＝101本", loc=(-3.0, -0.9), w=4.4, h=1.1, color=K.RED, size=0.45)
c2 = K.card("1〜2本", loc=(3.0, -0.9), w=3.6, h=1.1, color=K.GREEN, text_color=INK, size=0.45)
K.pop_in(c1, a[15] + 16)
K.pop_in(c2, a[15] + 24)
K.pulse(c2, a[15] + 44, 1.12, 8)

# ======================================================================
# SQL本数の表示（切り替え）
# ======================================================================
K.number_ticker(tick1, (2.3, 3.35, 0.1), 0.75, end=tick1_end)
K.number_ticker(tick2, (2.3, 3.35, 0.1), 0.75, end=tick2_end)

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
