"""部の導入動画（part00-intro 〜 part10-intro）の共通部品。

各部の場面スクリプト（scenes/partNN_intro.py）は、この部品に「部ごとの動き」
（困りごとの絵、章ごとのミニ動き、前→後の中身）を足すだけで作れる。
型・色・配置の約束は production/video/PART_INTRO_STORYBOARDS.md を参照。

======================================================================
使い方（場面スクリプトの骨組み）
======================================================================
    import os, sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # この部品を読めるように
    import part_intro_kit as P
    K = P.K                                                          # bl_kit（K.TEAL などの色もここから）

    I = P.Intro(part=0, title="はじめに",
                chapters=[(0, "歩き方"), (1, "環境を整える")])        # 章番号とカードのラベル
    A, E, M = I.A, I.E, I.M     # A(i)=行iの開始フレーム  E(i)=終了  M(i, 0.5)=行iの中ほど

    I.title_card()              # 場面A：「第N部」のチップ＋題名（行0の間だけ）。終わると、チップが左上のバッジになる
    b = I.trouble()             # 場面B：困りごと用のグループ（章の最初の行の少し前に自動で退場）
    b.pop(P.person((-5, 0.3)), A(1))       # b.pop(o, f) = pop_in して b に登録（退場は自動）
    b.add(o)                    # 自分で pop_in した物を登録だけする
    I.build_row()               # 場面C：章カードの列。章の頭の行で光り、前の章は済みになる
    c = I.chapter(0)            # 第0章のミニ動き用グループ（次の章の頭の5フレーム前に自動で退場）
    c.A(0), c.A(2, 0.3)         # 章の中の k 行目の開始フレーム（c.L(k) は行番号）
    s = c.sub()                 # 章の中で場面を入れ替えたいとき：s.pop(...)、s.out(f) で自分で退場
    d = I.before_after(A(16))   # 場面D：左「前」・右「後」の2枚パネル（章カードは全点灯して脈打つ）
    d.row("before", "空の机", "q", A(16, 0.5))      # パネルに項目を1行足す。mark: "ok" ✓ / "ng" ✕ / "q" ？ / None
    d.row("after", "Hello", "ok", A(17, 1.0))
    d.pending(["再起動で消える"], A(17))             # 「後」の下の YELLOW のチップ（まだ守れていないもの）
    I.final("最初に、進め方と環境をそろえる。")      # 場面E：他をすべて退場し、一言まとめを大きく出す
    I.render()                  # 描画（INTRO_SEC / INTRO_LIST があれば静止画だけ出す）

cue（台本の cue）は自動で読み取る。title / trouble / ch:N / after / final の行が無いと、起動時に止まる。
章のミニ動きは、c.A(k, 秒) / c.M(k, 割合) で「その行を読み上げているあいだ」に収める
（行の長さは build/<id>/timing.json で分かる。短い行は 2 秒前後しかない）。

----------------------------------------------------------------------
つまずきやすい点（part00_intro.py / part01_intro.py は動く見本）
----------------------------------------------------------------------
* カメラは真上から見る。面（パネル・カード）の上に物を重ねるときは z を上げる
  （K.card(..., z=0.1)、P.chip(..., z=0.1)、P.qmark(..., z=0.9) など）。同じ z だと下に隠れる。
* 退場は自動。Group に登録（pop / add）した物は、場面の終わりに自動で pop_out される。
  途中で自分で P.out(o, f) した物を登録したままにしても、二重には退場しない。
* K.recolor は同じカードに1回しか使えない。色を何度も替えるなら P.shade（文字が読める暗めの色は
  P.OKBG / P.WAITBG / P.NGBG）。
* P.poly の最初の頂点は真上（三角は ▲）。▶ にするには rot=-pi/2、▼ は rot=pi。
* 章カードが映っている間（C と D）は、カメラを寄せない（8枚の列が切れ、字幕と重なる）。
  寄るのは B（章カードが出る前）と E だけ。I.final() は、ごくゆっくり寄る余韻を自動で付ける。
* 図は y > -1.2 に置く（章カード y=-2.15、字幕 y < -2.8）。静止画の確認では y=-2.8 に桃色の線が出る。
* 画面の文字は「ラベル」だけ。コードは映さない。フォントに無い字（✕ ↻ など）は図形で描く
  （✓ ？ ！ は使える）。

----------------------------------------------------------------------
部品の座標（BeforeAfter）
----------------------------------------------------------------------
  パネル：中心 (±3.85, 1.2)、幅 6.1、高さ 3.9。行の y は ROWS_Y = (1.65, 0.75, -0.15)（3行まで）。
  d.pending() のチップは y=-1.12（章カードの上端 -1.6 との間に収める）。
  パネルの中に独自の絵を置くときは、パネルの外に出ない範囲（x が ±0.8〜±6.9、y が -0.7〜3.1）に収める。

======================================================================
主な部品
======================================================================
  I.title_card(sub=None)          タイトル（場面A）。長い題名は自動で縮める。
  I.badge                         左上の「第N部」バッジ（タイトルのチップが縮んで移る）
  I.trouble() / I.chapter(n) / I.before_after(f) / I.final(text)
  I.build_row()                   章カードの列。n>=6 なら、ラベルの "/" で2行に折り返す
  I.pulse_row(f)                  章カードを全部同時に脈打たせる（第6部の行19など）
  （D の開始 cue:after で、章カードは自動で全点灯して脈打ち、D の終わりに退場）
  I.cam(f, to, zoom)              カメラの寄り引き（章カードが映る間は zoom 16 のまま。寄るのは B・E など章カードの無い場面だけ）
  Group.pop(o, f) / add(o) / out(f)   場面ごとの出し入れ。退場の二重掛けは自動で防ぐ。
                                  任意の場面は P.Group(I, 退場フレーム) で作れる（part01 の A 場面を参照）
  P.chip / P.tile / P.framed / P.qmark / P.tick / P.nomark / P.seg / P.poly / P.flag / P.person / P.text_w   小さな図形部品
  P.shade / P.show / P.spin / P.out   色の切り替え・区間表示・回転・二重退場の防止
  P.show(o, [(f0, f1), ...])      その区間だけ表示（f1=None は最後まで）。状態ごとの切り替え用
  P.shade(card, [(f, color), ...], base)   カードの本体色を何度でも切り替える（K.recolor は1回きり）

静止画で確認：INTRO_SEC=12.5,30  INTRO_OUT=出力フォルダ  INTRO_PCT=50  （秒指定。INTRO_LIST=フレーム番号 でも可）
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
import bpy
import bl_kit as K
from mathutils import Vector

# ---- 色の再エクスポート ----
TEAL, BLUE, ORANGE, PURPLE = K.TEAL, K.BLUE, K.ORANGE, K.PURPLE
RED, GREEN, YELLOW = K.RED, K.GREEN, K.YELLOW
WHITE, GRAY, DARK, BG, BG2 = K.WHITE, K.GRAY, K.DARK, K.BG, K.BG2
INK = K.BG                       # 明るい地の上の文字色
CUST = K.hexc("#34406b")         # 中立のカードの色（thread-model の客テーブルと同じ）
FOG = K.hexc("#5a6492")          # 霧・もや（見えない物を隠す）
ROAD = K.hexc("#303a5e")         # 道・コンベア・机などの地面
OKBG = K.hexc("#1e8a52")         # 白い文字を載せられる暗めの緑（成功の状態）
WAITBG = K.hexc("#9a7412")       # 同、暗めの黄（待ち・詰まり）
NGBG = K.hexc("#b4343f")         # 同、暗めの赤（失敗）

CARD_Y = -2.15                   # 章カードの列の y
CARD_H = 1.1
BADGE_Y = 3.65
BADGE_X0 = -6.7                  # バッジの左端

_gone = set()                    # 退場済みのオブジェクト名（二重に pop_out しない）


# ======================================================================
# 小さな部品
# ======================================================================
def text_w(s, size=1.0):
    """文字列の幅の見積もり（Noto Sans JP 700）。全角 0.84、半角 0.56。"""
    return sum(0.84 if ord(c) > 0x2FF else 0.56 for c in s) * size


def out(o, f, dur=8):
    """退場。同じ物を二重に退場させるとアニメが壊れるので、記録して防ぐ。"""
    if o.name in _gone:
        return
    _gone.add(o.name)
    K.pop_out(o, f, dur)


def chip(label, loc, color, size=0.4, tc=WHITE, w=None, h=None, z=0.0):
    """ラベル用の小さなカード。幅は文字数から見積もる。他の面に重ねるときは z を上げる。"""
    w = w or (text_w(label, size) + size * 1.7)
    return K.card(label, loc=loc, w=w, h=h or size * 2.0, color=color, text_color=tc, size=size, z=z)


def tile(label, loc, w, h, color=CUST, size=0.5, tc=WHITE):
    return K.card(label, loc=loc, w=w, h=h, color=color, text_color=tc, size=size)


def label(body, loc, size=0.4, color=WHITE, weight=700, align="CENTER", z=0.2):
    return K.text(body, size, (loc[0], loc[1], z), color, weight, align)


def qmark(loc, r=0.45, color=YELLOW, z=0.3):
    """黄色の「？」の丸。重ねるときは z を大きく（カメラは上から見る）。"""
    root = K.dot(loc, r, color, None, z=z)
    K.text("？", r * 1.5, (0, 0, 0.05), BG, 900, parent=root)
    return root


def tick(loc, r=0.32, color=GREEN, z=0.3):
    """緑の ✓ の丸。"""
    root = K.dot(loc, r, color, None, z=z)
    K.text("✓", r * 1.4, (0, 0, 0.05), BG, 900, parent=root)
    return root


def bang(loc, r=0.32, color=YELLOW, z=0.3):
    """黄色の「！」の丸（詰まり・注意）。"""
    root = K.dot(loc, r, color, None, z=z)
    K.text("！", r * 1.5, (0, 0, 0.05), BG, 900, parent=root)
    return root


def nomark(loc, r=0.32, color=RED):
    """赤い ✕ の丸（白いバツ）。"""
    root = K.dot(loc, r, color, None)
    x = K.cross_mark((0, 0), r * 1.0, WHITE, r * 0.26, z=0.1)
    x.parent = root
    return root


def seg(p1, p2, width=0.1, color=GRAY, z=-0.03):
    """p1→p2 の太い線分（道・コンベアなど）。root を返す。"""
    a, b = Vector((p1[0], p1[1], 0)), Vector((p2[0], p2[1], 0))
    d = b - a
    root = bpy.data.objects.new("seg", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (a.x, a.y, z)
    root.rotation_euler = (0, 0, math.atan2(d.y, d.x))
    pl = K.plane("segbar", d.length, width, (d.length / 2, 0, 0), color)
    pl.parent = root
    return root


def poly(loc, r=0.5, n=6, color=GRAY, rot=0.0, z=0.1):
    """正 n 角形。最初の頂点は真上（n=3 なら ▲。rot=-pi/2 で ▶、rot=pi で ▼）。n=4 は ◆（rot=pi/4 で正方形）。"""
    root = bpy.data.objects.new("poly", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    root.rotation_euler = (0, 0, rot)
    bpy.ops.mesh.primitive_circle_add(vertices=n, radius=r, fill_type="NGON", location=(0, 0, 0))
    o = bpy.context.active_object
    o.data.materials.append(K.mat(color))
    o.parent = root
    return root


def flag(loc, s=1.0, color=GREEN):
    """旗（ゴールの目印）。原点は旗竿の根もと。"""
    root = bpy.data.objects.new("flag", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], 0.1)
    pole = K.plane("pole", 0.09 * s, 1.7 * s, (0, 0.85 * s, 0), WHITE)
    pole.parent = root
    cloth = poly((0.36 * s + 0.04, 1.3 * s), 0.62 * s, 3, color, -math.pi / 2, 0.01)
    cloth.parent = root
    return root


def framed(loc, w, h, fill=DARK, border=GRAY, bw=0.08, radius=0.3, z=0.0):
    """枠線つきのカード（K.card の子に枠を足したもの）。root を返す。"""
    root = K.card("", loc, w, h, fill, z=z)
    b = K.rounded_rect("border", w + 2 * bw, h + 2 * bw, (0, 0, -0.02), border, radius + bw)
    b.parent = root
    b.location = (0, 0, -0.02)
    return root


def person(loc, color=ORANGE, s=1.0):
    return K.person(loc, color, s)


def show(o, intervals):
    """intervals=[(f0, f1), ...] の区間だけ表示する（f1=None は最後まで）。スケール0/1の切り替え。"""
    K._key(o, "scale", 1, (0, 0, 0), "CONSTANT")
    for f0, f1 in intervals:
        K._key(o, "scale", max(f0, 2), (1, 1, 1), "CONSTANT")
        if f1 is not None:
            K._key(o, "scale", f1, (0, 0, 0), "CONSTANT")


def shade(root, changes, base, dur=4):
    """カード本体の色を何度でも切り替える。changes=[(frame, color), ...]、base=最初の色。
    （K.recolor は同じカードに2回使うと壊れることがあるので、複数回の切り替えはこちらを使う）"""
    for ch in root.children:
        if ch.name.startswith("body"):
            m2 = ch.data.materials[0].copy()
            ch.data.materials[0] = m2
            em = [n for n in m2.node_tree.nodes if n.type == "EMISSION"][0]
            sock = em.inputs["Color"]
            cur = base
            for f, c in changes:
                sock.default_value = cur
                sock.keyframe_insert("default_value", frame=f - 1)
                sock.default_value = c
                sock.keyframe_insert("default_value", frame=f + dur)
                cur = c
            sock.default_value = base


def spin(o, f0, f1, turns=1.0, axis=2):
    """f0〜f1 の間に z 軸まわりで等速回転（時計の針など）。"""
    r0 = list(o.rotation_euler)
    K._key(o, "rotation_euler", f0, tuple(r0), "LINEAR")
    r1 = list(r0)
    r1[axis] = r0[axis] - turns * 2 * math.pi
    K._key(o, "rotation_euler", f1, tuple(r1), "LINEAR")


def slide(o, f0, to, dur=15, interp="SINE", easing="EASE_IN_OUT"):
    K.move(o, f0, to, dur, interp, easing)


# ======================================================================
# 場面ごとの出し入れ
# ======================================================================
class Group:
    """場面の要素の束。close/out で、登録した物をまとめて pop_out する。"""

    def __init__(self, intro, out_frame=None):
        self.intro = intro
        self.objs = []
        self.closed = False
        self.out_frame = out_frame
        intro.groups.append(self)

    def add(self, *objs):
        self.objs.extend(objs)
        return objs[0] if len(objs) == 1 else objs

    def pop(self, o, f, dur=10, scale=1.0):
        """pop_in して登録する。"""
        K.pop_in(o, f, dur, scale)
        self.objs.append(o)
        return o

    def out(self, f=None, step=1, dur=8):
        f = self.out_frame if f is None else f
        for i, o in enumerate(self.objs):
            out(o, f + min(i, 4) * step, dur)
        self.closed = True


class Chapter(Group):
    """章ごとのグループ。行は「章の中の k 行目」で数えられる。"""

    def __init__(self, intro, n, idx, first, last, out_frame):
        super().__init__(intro, out_frame)
        self.n, self.idx, self.first, self.last = n, idx, first, last
        self.subs = []

    def L(self, k=0):
        return self.first + k

    def A(self, k=0, off=0.0):
        return self.intro.A(self.first + k, off)

    def E(self, k=0, off=0.0):
        return self.intro.E(self.first + k, off)

    def M(self, k=0, frac=0.5):
        return self.intro.M(self.first + k, frac)

    def sub(self):
        """章の中で場面を入れ替えるための入れ物。s.out(f) で退場、しなければ章の終わりに自動で退場。"""
        g = Group(self.intro, self.out_frame)
        self.subs.append(g)
        return g


class BeforeAfter:
    """場面D：左「前」・右「後」の2枚パネル。"""
    W, H, CY = 6.1, 3.9, 1.2
    LX, RX = -3.85, 3.85
    ROWS_Y = (1.65, 0.75, -0.15)
    ROW_H = 0.8

    def __init__(self, intro, f, grp):
        self.intro, self.g = intro, grp
        self.n = {"before": 0, "after": 0}
        W, H, CY = self.W, self.H, self.CY
        self.left = K.card("", (self.LX, CY), W, H, DARK)
        grp.pop(self.left, f)
        self.right = framed((self.RX, CY), W, H, BG2, TEAL, 0.07, 0.3)
        grp.pop(self.right, f + 6)
        self.t_left = label("前", (self.LX, CY + H / 2 - 0.5), 0.5, GRAY, 900)
        self.t_right = label("後", (self.RX, CY + H / 2 - 0.5), 0.5, TEAL, 900)
        grp.pop(self.t_left, f + 4)
        grp.pop(self.t_right, f + 10)
        self.arrow = K.arrow((-0.6, CY), (0.6, CY), WHITE, 0.12, 0.38)
        K.grow_x(self.arrow, f + 12, 10)
        grp.add(self.arrow)

    def row(self, side, body, mark="ok", f=0, k=None, size=0.42, color=None, tc=WHITE):
        """side: "before"/"after"。mark: "ok"/"ng"/"q"/None。k: 行の位置（省略で上から順）。"""
        if k is None:
            k = self.n[side]
        self.n[side] = k + 1
        cx = self.LX if side == "before" else self.RX
        y = self.ROWS_Y[k]
        w = self.W - 0.6
        base = color or (BG if side == "before" else DARK)
        r = K.card("", (cx, y), w, self.ROW_H, base, size=0.3, z=0.1)
        self.g.pop(r, f, 8)
        t = label(body, (cx - w / 2 + 1.0, y), size, tc, 700, "LEFT")
        self.g.pop(t, f + 2, 8)
        m = None
        mloc = (cx - w / 2 + 0.5, y)
        if mark == "ok":
            m = tick(mloc)
        elif mark == "ng":
            m = nomark(mloc)
        elif mark == "q":
            m = qmark(mloc, 0.32)
        if m is not None:
            self.g.pop(m, f + 5, 8)
        return r, t, m

    def pending(self, labels, f, step=10, size=0.36, y=-1.12):
        """「後」の下の YELLOW のチップ（まだ守れていないもの）。"""
        ws = [text_w(s, size) + size * 1.7 for s in labels]
        gap = 0.2
        total = sum(ws) + gap * (len(ws) - 1)
        x = self.RX - total / 2
        x = max(x, -0.3)
        res = []
        for i, (s, w) in enumerate(zip(labels, ws)):
            c = chip(s, (x + w / 2, y), YELLOW, size, INK, w=w)
            self.g.pop(c, f + i * step)
            K.pulse(c, f + i * step + 12, 1.12, 8)
            res.append(c)
            x += w + gap
        return res


# ======================================================================
# 本体
# ======================================================================
class Intro:
    def __init__(self, part, title, chapters):
        self.T = K.setup()
        T = self.T
        self.part, self.title, self.chapters_def = part, title, list(chapters)
        self.groups = []
        self.A = lambda i, off=0.0: K.at(T, i, off)
        self.E = lambda i, off=0.0: K.end_of(T, i, off)
        self.row_cards = []
        self.row_built = False
        self.badge = None
        self.final_done = False

        lines = T["lines"]
        self.cue = {}
        self.ch_line = {}
        for i, ln in enumerate(lines):
            c = ln.get("cue", "")
            if c.startswith("ch:"):
                self.ch_line[int(c[3:])] = i
            elif c:
                self.cue[c] = i
        for need in ("title", "trouble", "after", "final"):
            if need not in self.cue:
                raise SystemExit(f"台本に cue '{need}' がありません")
        for n, _ in self.chapters_def:
            if n not in self.ch_line:
                raise SystemExit(f"台本に cue 'ch:{n}' がありません")
        self.ch_order = [n for n, _ in self.chapters_def]
        self.ch_lines = [self.ch_line[n] for n in self.ch_order]
        if self.ch_lines != sorted(self.ch_lines):
            raise SystemExit("章の順序と台本の cue の順序が合いません")

        self.line_title = self.cue["title"]
        self.line_trouble = self.cue["trouble"]
        self.line_after = self.cue["after"]
        self.line_final = self.cue["final"]
        self.trouble_out = self.A(self.ch_lines[0]) - 5
        self.d_end = self.A(self.line_final) - 12

        self.chs = {}
        for idx, n in enumerate(self.ch_order):
            first = self.ch_lines[idx]
            nxt = self.ch_lines[idx + 1] if idx + 1 < len(self.ch_lines) else self.line_after
            self.chs[n] = Chapter(self, n, idx, first, nxt - 1, self.A(nxt) - 5)

    # ---- 時間 ----
    def M(self, i, frac=0.5):
        ln = self.T["lines"][i]
        return K.sec(self.T, ln["start"] + (ln["end"] - ln["start"]) * frac)

    def dur(self, i):
        ln = self.T["lines"][i]
        return ln["end"] - ln["start"]

    # ---- A：タイトル ----
    def title_card(self, sub=None, line=0):
        """「第N部」のチップ＋題名。行 line（既定0）の間だけ出し、終わるとチップが左上のバッジへ縮んで移る。"""
        T = self.T
        tsize = min(1.05, 13.6 / text_w(self.title, 1.0))
        g = K.text(self.title, tsize, (0, 0.2, 0.5), WHITE, 900)
        K.pop_in(g, 2, 12)
        bar = K.plane("title_bar", 4, 0.12, (0, -0.55, 0.5), ORANGE)
        K.grow_x(bar, 8, 14)
        objs = [g, bar]
        if sub:
            s = K.text(sub, 0.42, (0, -1.15, 0.5), GRAY, 400)
            K.pop_in(s, 10, 10)
            objs.append(s)
        # バッジ（タイトルでは大きく中央、のちに左上へ）
        bw = text_w(f"第{self.part}部", 0.4) + 0.4 * 1.7
        bx, by = BADGE_X0 + bw / 2, BADGE_Y
        self.badge = chip(f"第{self.part}部", (0, 1.75), TEAL, 0.4, WHITE, w=bw)
        K.pop_in(self.badge, 2, 12, 1.9)
        fo = K.end_of(T, line, 0.1)
        for o in objs:
            out(o, fo)
        K._key(self.badge, "scale", fo, (1.9, 1.9, 1.9), "SINE", "EASE_IN_OUT")
        K._key(self.badge, "scale", fo + 22, (1, 1, 1), "SINE", "EASE_IN_OUT")
        K.move(self.badge, fo, (bx, by), 22, "SINE", "EASE_IN_OUT")
        return fo + 22

    # ---- B：困りごと ----
    def trouble(self):
        return Group(self, self.trouble_out)

    # ---- C：章 ----
    def chapter(self, n):
        return self.chs[n]

    def _card_w(self):
        n = len(self.ch_order)
        return min(3.6, (14.6 - 0.2 * (n - 1)) / n)

    def build_row(self):
        """章カードの列（y=-2.15）。章の頭の行で光り、前の章は済み、D で全点灯、D の終わりで退場。"""
        if self.row_built:
            return
        self.row_built = True
        n = len(self.ch_order)
        w = self._card_w()
        total = n * w + 0.2 * (n - 1)
        x0 = -total / 2 + w / 2
        start = self.A(self.ch_lines[0])
        f_after = self.A(self.line_after)
        f_end = self.d_end
        for k, (num, lab) in enumerate(self.chapters_def):
            x = x0 + k * (w + 0.2)
            s_k = self.A(self.ch_lines[k])
            s_next = self.A(self.ch_lines[k + 1]) if k + 1 < n else None
            root = self._chapter_card(num, lab, x, w, n, s_k, s_next, f_after)
            K.pop_in(root, start + 2 * k, 10)
            if k == 0:
                K.pulse(root, start + 14, 1.12, 8)
            else:
                K.pulse(root, s_k, 1.12, 8)
            self.row_cards.append(root)
        # D：全点灯して一度脈打つ
        for r in self.row_cards:
            K.pulse(r, f_after + 4, 1.1, 8)
            out(r, f_end)

    def _chapter_card(self, num, lab, x, w, n, s_k, s_next, f_after):
        two = n >= 6
        root = bpy.data.objects.new(f"chcard_{num}", None)
        bpy.context.scene.collection.objects.link(root)
        root.location = (x, CARD_Y, 0)
        sh = K.rounded_rect("shadow", w, CARD_H, (0.08, -0.1, -0.05), K.hexc("#000000"), 0.3)
        sh.parent = root
        bodies = {}
        for st, col in (("dark", DARK), ("teal", TEAL), ("done", BG2)):
            b = K.rounded_rect(f"body_{st}", w, CARD_H, (0, 0, 0), col, 0.28)
            b.parent = root
            bodies[st] = b
        brd = K.rounded_rect("border_done", w + 0.1, CARD_H + 0.1, (0, 0, -0.02), TEAL, 0.3)
        brd.parent = root
        # 状態ごとの区間
        dark = [(2, s_k)] if s_k > 2 else []
        teal = [(s_k, s_next)] if s_next else [(s_k, None)]
        if s_next:
            teal.append((f_after, None))
            done = [(s_next, f_after)]
        else:
            done = []
        # 文字
        parts = lab.split("/") if "/" in lab else [lab]
        tag = f"第{num}章"
        if two:
            tag_y, tag_s = 0.38, 0.27
            ys = [0.06, -0.28] if len(parts) == 2 else [-0.12]
            lsz = 0.33
        else:
            tag_y, tag_s = 0.3, 0.3
            ys = [-0.12]
            lsz = 0.52
        mw = max(text_w(p, 1.0) for p in parts)
        lsz = min(lsz, (w - 0.25) / mw)
        for st, col, ivs in (("dark", GRAY, dark), ("teal", WHITE, teal), ("done", TEAL, done)):
            if not ivs:
                continue
            tobjs = [K.text(tag, tag_s, (0, tag_y, 0.05), col, 700, parent=root)]
            for p, y in zip(parts, ys):
                tobjs.append(K.text(p, lsz, (0, y, 0.05), col, 700, parent=root))
            for o in tobjs:
                show(o, ivs)
            show(bodies[st], ivs)
        if done:
            show(brd, done)
            ck = K.text("✓", 0.34, (w / 2 - 0.27, CARD_H / 2 - 0.24, 0.06), TEAL, 900, parent=root)
            show(ck, done)
        else:
            K._key(brd, "scale", 1, (0, 0, 0), "CONSTANT")
        # 点灯のとき、dark の本体が空区間なら見えないままにする
        if not dark:
            K._key(bodies["dark"], "scale", 1, (0, 0, 0), "CONSTANT")
        if not done:
            K._key(bodies["done"], "scale", 1, (0, 0, 0), "CONSTANT")
        return root

    def pulse_row(self, f, amount=1.12):
        for r in self.row_cards:
            K.pulse(r, f, amount, 8)

    # ---- D：前→後 ----
    def before_after(self, f=None):
        f = self.A(self.line_after, 0.2) if f is None else f
        g = Group(self, self.d_end)
        return BeforeAfter(self, f, g)

    # ---- カメラ ----
    def cam(self, f, to=(0, 0), zoom=16, dur=24):
        """寄り引き。章カード・バッジが映る間は zoom 14.6 以上に保つ（端が切れる）。"""
        K.camera_move(f, to=to, zoom=zoom, dur=dur)

    # ---- E：最後の1行 ----
    def final(self, text, size=0.95, sub=True, split=None, push=True):
        """すべて退場して、一言まとめを大きく出す。text は文字列、または行の配列。
        文字列が13字を超えるときは、読点の近くで2行に分ける。"""
        fa = self.A(self.line_final)
        self.finish()
        if self.badge is not None:
            out(self.badge, fa - 6)
        if isinstance(text, (list, tuple)):
            lines = list(text)
        elif len(text) <= 13 and split is None:
            lines = [text]
        else:
            if split is None:
                mid = len(text) / 2
                cands = [i + 1 for i, c in enumerate(text) if c in "、，"]
                split = min(cands, key=lambda i: abs(i - mid)) if cands else len(text) // 2
            lines = [text[:split], text[split:]]
        lw = max(text_w(s, 1.0) for s in lines)
        size = min(size, 14.6 / lw)
        ys = [0.95] if len(lines) == 1 else [1.55, 0.3]
        objs = []
        for i, (s, y) in enumerate(zip(lines, ys)):
            o = K.text(s, size, (0, y, 0.5), WHITE, 900)
            K.pop_in(o, fa + 4 + i * 8, 12)
            objs.append(o)
        bar = K.plane("final_bar", 3.0, 0.1, (0, ys[-1] - 0.85, 0.5), ORANGE)
        K.grow_x(bar, fa + 16, 14)
        if sub:
            s = K.text(f"第{self.part}部", 0.42, (0, ys[-1] - 1.45, 0.5), GRAY, 700)
            K.pop_in(s, fa + 22, 10)
        if push:    # ごくゆっくり寄る（余韻）
            K.camera_move(fa + 6, to=(0, 0.25), zoom=15.0, dur=int(self.dur(self.line_final) * 30 + 40))
        self.final_done = True
        return objs

    def finish(self):
        """まだ退場していない場面を、それぞれの退場フレームで退場させる。final() が呼ぶ。"""
        for g in self.groups:
            if g.closed:
                continue
            g.out()
            for s in getattr(g, "subs", []):
                if not s.closed:
                    s.out()

    # ---- 描画 ----
    def render(self):
        sc = bpy.context.scene
        secs = os.environ.get("INTRO_SEC")
        lst = os.environ.get("INTRO_LIST")
        if secs or lst:
            frames = ([K.sec(self.T, float(s)) for s in secs.split(",")] if secs
                      else [int(s) for s in lst.split(",")])
            sc.render.resolution_percentage = int(os.environ.get("INTRO_PCT", "50"))
            outdir = os.environ["INTRO_OUT"]
            os.makedirs(outdir, exist_ok=True)
            # 字幕の領域（y < -2.8）の目印を静止画だけに出す
            guide = K.plane("sub_guide", 16, 0.03, (0, -2.8, 3), K.hexc("#ff00ff"))
            for fr in frames:
                sc.frame_set(fr)
                sc.render.filepath = os.path.join(outdir, f"s_{fr:04d}.png")
                sc.render.image_settings.file_format = "PNG"
                bpy.ops.render.render(write_still=True)
        else:
            K.render(self.T)
