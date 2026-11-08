"""Blender用モーショングラフィックス部品集（全動画で共通の見た目にする）。

場面スクリプトの先頭で次のように使う:

    import sys, os
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    import bl_kit as K
    T = K.setup()            # timing.json を読み、シーン・カメラ・背景を準備
    card = K.card("Controller", loc=(-4, 0), color=K.ORANGE)
    K.pop_in(card, K.at(T, 0))  # 0行目のナレーション開始時に登場
    ...
    K.render(T)

座標系: カメラは真上(Z+)から見下ろす。画面の横幅は約16、縦幅は約9（16:9）。
"""
import json
import math
import sys

import bpy
from mathutils import Vector

# ---- 配色（教材サイトと同じ系統） ----
def hexc(h, a=1.0):
    h = h.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    # sRGB → リニア
    c = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return (*c, a)

BG = hexc("#121625")
BG2 = hexc("#1b2136")
ORANGE = hexc("#ff6d3a")
TEAL = hexc("#14b8a6")
YELLOW = hexc("#ffc53d")
BLUE = hexc("#4c8dff")
PURPLE = hexc("#a77bff")
RED = hexc("#ff4d5e")
GREEN = hexc("#3ddc84")
WHITE = hexc("#f4f1ec")
GRAY = hexc("#6b7391")
DARK = hexc("#252c45")

_fonts = {}
TEXT_SCALE = 2.2  # Noto Sans JP の実寸補正
_mats = {}
T_GLOBAL = None


# ---- 時間 ----
def sec(t, s):
    """秒→フレーム番号"""
    return int(round(s * t["fps"])) + 1

def at(t, line_index, offset=0.0):
    """ナレーションの行番号 line_index が始まる瞬間（＋offset秒）のフレーム"""
    return sec(t, t["lines"][line_index]["start"] + offset)

def end_of(t, line_index, offset=0.0):
    return sec(t, t["lines"][line_index]["end"] + offset)


# ---- 初期化 ----
def setup():
    global T_GLOBAL
    argv = sys.argv[sys.argv.index("--") + 1:]
    t = json.load(open(argv[0], encoding="utf-8"))
    t["out"] = argv[1]
    T_GLOBAL = t
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "BLENDER_EEVEE"
    sc.render.resolution_x = t["width"]
    sc.render.resolution_y = t["height"]
    sc.render.fps = t["fps"]
    sc.frame_start = 1
    sc.frame_end = t["frames"]
    sc.render.image_settings.file_format = "JPEG"
    sc.render.image_settings.quality = 93
    sc.render.filepath = t["out"]
    sc.view_settings.view_transform = "Standard"
    try:
        sc.eevee.taa_render_samples = 16
    except Exception:
        pass
    # 背景
    w = bpy.data.worlds.new("World")
    w.use_nodes = True
    bgn = w.node_tree.nodes.get("Background")
    bgn.inputs[0].default_value = BG
    sc.world = w
    # カメラ（正射影、真上から）
    cam_data = bpy.data.cameras.new("Cam")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = 16
    cam = bpy.data.objects.new("Cam", cam_data)
    cam.location = (0, 0, 30)
    sc.collection.objects.link(cam)
    sc.camera = cam
    # 背景の板（うっすらグラデーション風に2枚）
    plane("bg", 60, 40, (0, 0, -2), BG)
    return t


def mat(color, name=None):
    key = name or str(color)
    if key in _mats:
        return _mats[key]
    m = bpy.data.materials.new(key)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = color
    em.inputs["Strength"].default_value = 1.0
    nt.links.new(em.outputs[0], out.inputs[0])
    _mats[key] = m
    return m


def font(weight=700):
    if weight not in _fonts:
        _fonts[weight] = bpy.data.fonts.load(f"{T_GLOBAL['fonts_dir']}/NotoSansJP-{weight}.ttf")
    return _fonts[weight]


# ---- 図形 ----
def plane(name, w, h, loc, color):
    bpy.ops.mesh.primitive_plane_add(size=1, location=loc)
    o = bpy.context.active_object
    o.name = name
    o.scale = (w, h, 1)
    bpy.ops.object.transform_apply(scale=True)
    o.data.materials.append(mat(color))
    return o


def rounded_rect(name, w, h, loc=(0, 0, 0), color=DARK, radius=0.25):
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 0, 0))
    o = bpy.context.active_object
    o.name = name
    o.scale = (w, h, 1)
    bpy.ops.object.transform_apply(scale=True)
    bev = o.modifiers.new("bevel", "BEVEL")
    bev.affect = "VERTICES"
    bev.width = min(radius, w / 2 - 0.01, h / 2 - 0.01)
    bev.segments = 8
    o.data.materials.append(mat(color))
    o.location = loc
    return o


def text(body, size=0.6, loc=(0, 0, 0.1), color=WHITE, weight=700, align="CENTER", parent=None):
    cu = bpy.data.curves.new("txt", "FONT")
    cu.body = body
    cu.font = font(weight)
    cu.size = size * TEXT_SCALE
    cu.align_x = align
    cu.align_y = "CENTER"
    o = bpy.data.objects.new("txt", cu)
    bpy.context.scene.collection.objects.link(o)
    o.data.materials.append(mat(color))
    o.location = loc
    if parent is not None:
        o.parent = parent
    return o


def card(label, loc=(0, 0), w=3.6, h=1.4, color=ORANGE, text_color=WHITE, size=0.5, sub=None, z=0.0):
    """角丸カード＋ラベル。親オブジェクト(Empty)を返す。登場アニメは親に付ける。"""
    root = bpy.data.objects.new(f"card_{label}", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    # 影
    sh = rounded_rect("shadow", w, h, (0.08, -0.1, -0.05), hexc("#000000"), 0.3)
    sh.parent = root
    body = rounded_rect("body", w, h, (0, 0, 0), color, 0.3)
    body.parent = root
    if sub:
        t1 = text(label, size, (0, h * 0.13, 0.05), text_color, 700, parent=root)
        t2 = text(sub, size * 0.55, (0, -h * 0.25, 0.05), text_color, 400, parent=root)
    else:
        text(label, size, (0, 0, 0.05), text_color, 700, parent=root)
    root["w"] = w
    root["h"] = h
    return root


def dot(loc=(0, 0), r=0.25, color=YELLOW, label=None, label_color=None, z=0.3):
    root = bpy.data.objects.new("dot", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    bpy.ops.mesh.primitive_circle_add(vertices=48, radius=r, fill_type="NGON", location=(0, 0, 0))
    c = bpy.context.active_object
    c.data.materials.append(mat(color))
    c.parent = root
    if label:
        text(label, r * 0.9, (0, 0, 0.05), label_color or BG, 900, parent=root)
    return root


def arrow(p1, p2, color=GRAY, width=0.08, head=0.3, z=-0.02):
    """p1→p2 の矢印（平面）。成長アニメ用に root を返す（root.scale.x を 0→1）。"""
    p1, p2 = Vector((p1[0], p1[1], 0)), Vector((p2[0], p2[1], 0))
    d = p2 - p1
    L = d.length
    ang = math.atan2(d.y, d.x)
    root = bpy.data.objects.new("arrow", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (p1.x, p1.y, z)
    root.rotation_euler = (0, 0, ang)
    shaft = plane("shaft", max(L - head, 0.01), width, ((L - head) / 2, 0, 0), color)
    shaft.parent = root
    bpy.ops.mesh.primitive_circle_add(vertices=3, radius=head * 0.62, fill_type="NGON", location=(L - head * 0.45, 0, 0))
    tip = bpy.context.active_object
    tip.rotation_euler = (0, 0, -math.pi / 2)
    tip.data.materials.append(mat(color))
    tip.parent = root
    return root


# ---- アニメーション ----
_last = {}  # (オブジェクト名, プロパティ) -> 最後にキーを打った値


def _cur(o, path):
    """最後にキーを打った値を返す。
    Blender はオペレータ実行のたびにアニメを現フレームで再評価し、o.scale などを上書きしてしまう。
    そのため「今の値」はオブジェクトからではなく、ここで覚えた値から読む。"""
    v = _last.get((o.name, path))
    return v if v is not None else tuple(getattr(o, path))


def _key(o, path, frame, value, interp="BEZIER", easing="AUTO"):
    setattr(o, path, value)
    _last[(o.name, path)] = tuple(value)
    o.keyframe_insert(data_path=path, frame=frame)
    if o.animation_data and o.animation_data.action:
        for fc in _fcurves(o):
            if fc.data_path == path:
                for kp in fc.keyframe_points:
                    if int(kp.co.x) == frame:
                        kp.interpolation = interp
                        kp.easing = easing


def _fcurves(o):
    act = o.animation_data.action
    # Blender 5 系の layered action と旧形式の両方に対応
    try:
        return list(act.fcurves)
    except AttributeError:
        res = []
        for layer in act.layers:
            for strip in layer.strips:
                for cb in strip.channelbags:
                    res.extend(cb.fcurves)
        return res


def hide_until(o, frame):
    """frame より前はスケール0で見えないようにする。"""
    _key(o, "scale", 1, (0, 0, 0), "CONSTANT")
    _key(o, "scale", max(frame - 1, 1), (0, 0, 0), "CONSTANT")


def pop_in(o, frame, dur=10, scale=1.0):
    """ぽよんと登場（少し大きくなってから戻る）"""
    hide_until(o, frame)
    _key(o, "scale", frame, (0.01, 0.01, 0.01), "BACK", "EASE_OUT")
    _key(o, "scale", frame + dur, (scale, scale, scale))


def pop_out(o, frame, dur=8):
    _key(o, "scale", frame, _cur(o, "scale"), "BACK", "EASE_IN")
    _key(o, "scale", frame + dur, (0.0, 0.0, 0.0), "CONSTANT")


def grow_x(o, frame, dur=12):
    """矢印などを左から伸ばす"""
    _key(o, "scale", 1, (0, 1, 1), "CONSTANT")
    _key(o, "scale", max(frame - 1, 1), (0, 1, 1), "CONSTANT")
    _key(o, "scale", frame, (0.0, 1, 1), "SINE", "EASE_OUT")
    _key(o, "scale", frame + dur, (1, 1, 1))


def move(o, frame, to, dur=15, interp="EXPO", easing="EASE_IN_OUT"):
    """現在位置から to へ移動。to は (x, y) か (x, y, z)。"""
    cur = _cur(o, "location")
    if len(to) == 2:
        to = (to[0], to[1], cur[2])
    _key(o, "location", frame, cur, interp, easing)
    _key(o, "location", frame + dur, to, interp, easing)
    o.location = to


def shake(o, frame, amp=0.15, times=4, step=2):
    base = _cur(o, "location")
    for i in range(times):
        _key(o, "location", frame + i * step, (base[0] + (amp if i % 2 == 0 else -amp), base[1], base[2]), "LINEAR")
    _key(o, "location", frame + times * step, base, "LINEAR")


def pulse(o, frame, amount=1.15, dur=8):
    s = _cur(o, "scale")
    _key(o, "scale", frame, s, "SINE", "EASE_IN_OUT")
    _key(o, "scale", frame + dur // 2, tuple(x * amount for x in s), "SINE", "EASE_IN_OUT")
    _key(o, "scale", frame + dur, s, "SINE", "EASE_IN_OUT")


def recolor(obj_root, frame, color):
    """カードの本体色を frame で切り替える（Emission色をキーフレーム）"""
    for ch in obj_root.children:
        if ch.name.startswith("body"):
            m = mat(color, name=f"recolor_{ch.name}_{frame}")
            m2 = ch.data.materials[0].copy()
            ch.data.materials[0] = m2
            em = [n for n in m2.node_tree.nodes if n.type == "EMISSION"][0]
            sock = em.inputs["Color"]
            sock.keyframe_insert("default_value", frame=frame - 1)
            sock.default_value = color
            sock.keyframe_insert("default_value", frame=frame + 4)


def camera_move(frame, to=(0, 0), zoom=16, dur=24):
    """カメラのパン・ズーム（ortho_scaleを小さくすると寄る）"""
    cam = bpy.context.scene.camera
    move(cam, frame, (to[0], to[1], 30), dur, "SINE", "EASE_IN_OUT")
    d = cam.data
    d.ortho_scale = _last.get(("cam_ortho", 0), d.ortho_scale)
    d.keyframe_insert("ortho_scale", frame=frame)
    d.ortho_scale = zoom
    d.keyframe_insert("ortho_scale", frame=frame + dur)
    _last[("cam_ortho", 0)] = zoom


def title_card(t, title, sub=None, until_line=0):
    """冒頭のタイトル（最初のナレーション行の終わりで退場）"""
    g = text(title, 0.95, (0, 0.5, 0.5), WHITE, 900)
    pop_in(g, 2, 12)
    bar = plane("title_bar", 4, 0.12, (0, -0.35, 0.5), ORANGE)
    grow_x(bar, 8, 14)
    objs = [g, bar]
    if sub:
        s = text(sub, 0.42, (0, -0.95, 0.5), GRAY, 400)
        pop_in(s, 10, 10)
        objs.append(s)
    out = end_of(t, until_line, 0.1)
    for o in objs:
        pop_out(o, out)
    return out + 8


# ======================================================================
# 追加部品（thread-model 動画で追加。既存関数のシグネチャは変えない）
# ======================================================================
SKIN = hexc("#ffd9b3")


def person(loc=(0, 0), color=TEAL, s=1.0, z=0.0):
    """人物アイコン（丸い頭＋胴）。親Empty(root)を返す。胴は名前が body なので recolor() で色替えできる。
    s で全体の大きさを変える。root の原点は胴の中心付近（頭は上に約0.6s、胴の下端は約-0.68s）。"""
    root = bpy.data.objects.new("person", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    sh = rounded_rect("shadow", 1.15 * s, 0.95 * s, (0.06 * s, -0.26 * s, -0.05), hexc("#000000"), 0.4 * s)
    sh.parent = root
    body = rounded_rect("body", 1.15 * s, 0.95 * s, (0, -0.2 * s, 0), color, 0.4 * s)
    body.parent = root
    head = dot((0, 0.62 * s), r=0.44 * s, color=SKIN, z=0.1)
    head.parent = root
    for dx in (-0.16, 0.16):
        e = dot((dx * s, 0.66 * s), r=0.065 * s, color=BG, z=0.2)
        e.parent = root
    return root


def show_between(o, f_on, f_off=None):
    """f_on〜f_off の間だけ表示（それ以外はスケール0）。数字の切り替え表示などに使う。"""
    zero, one = (0, 0, 0), (1, 1, 1)
    _key(o, "scale", 1, zero, "CONSTANT")
    if f_on > 2:
        _key(o, "scale", f_on - 1, zero, "CONSTANT")
    if f_off is not None and f_off - f_on > 8:
        # 出現時に少し大きくなってから落ち着く
        _key(o, "scale", f_on, (1.5, 1.5, 1.5), "SINE", "EASE_OUT")
        _key(o, "scale", f_on + 5, one, "CONSTANT")
    else:
        _key(o, "scale", f_on, one, "CONSTANT")
    if f_off is not None:
        _key(o, "scale", f_off - 1, one, "CONSTANT")
        _key(o, "scale", f_off, zero, "CONSTANT")


def number_ticker(seq, loc=(0, 0, 0.1), size=1.6, parent=None, end=None):
    """数字表示の切り替え。seq = [(フレーム, "文字", 色), ...]（色は省略可）。
    各項目を別テキストにして、そのフレームから次の項目の直前まで表示する。テキストのリストを返す。"""
    objs = []
    for i, it in enumerate(seq):
        f_on, body = it[0], it[1]
        color = it[2] if len(it) > 2 else WHITE
        f_off = seq[i + 1][0] if i + 1 < len(seq) else end
        o = text(body, size, loc, color, 900, parent=parent)
        show_between(o, f_on, f_off)
        objs.append(o)
    return objs


def dashed_line(p1, p2, color=GRAY, width=0.07, dash=0.3, gap=0.22, z=-0.02):
    """p1→p2 の点線。root を返す（pop_in / pop_out で出し入れできる）。"""
    a, b = Vector((p1[0], p1[1], 0)), Vector((p2[0], p2[1], 0))
    d = b - a
    L = d.length
    root = bpy.data.objects.new("dashed", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (a.x, a.y, z)
    root.rotation_euler = (0, 0, math.atan2(d.y, d.x))
    x = 0.0
    while x < L - 0.01:
        seg = min(dash, L - x)
        pl = plane("dash", seg, width, (x + seg / 2, 0, 0), color)
        pl.parent = root
        x += dash + gap
    return root


def progress_bar(loc=(0, 0), w=2.4, h=0.22, color=GREEN, track=DARK, z=0.1):
    """進捗バー。(root, fill) を返す。fill.scale.x を bar_to() で動かす（最初は0）。"""
    root = bpy.data.objects.new("bar", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    tr = rounded_rect("track", w, h, (0, 0, 0), track, h / 2)
    tr.parent = root
    fill = bpy.data.objects.new("fill", None)
    bpy.context.scene.collection.objects.link(fill)
    fill.parent = root
    fill.location = (-w / 2, 0, 0.02)
    pl = rounded_rect("fillbar", w, h, (w / 2, 0, 0), color, h / 2)
    pl.parent = fill
    fill.scale = (0.0, 1, 1)
    return root, fill


def bar_to(fill, frame, frac, dur=20):
    """進捗バー fill を現在の長さから frac(0〜1) まで frame から dur フレームで伸ばす。"""
    cur = _cur(fill, "scale")
    _key(fill, "scale", frame, cur, "SINE", "EASE_IN_OUT")
    _key(fill, "scale", frame + dur, (frac, 1, 1), "SINE", "EASE_IN_OUT")


def bob(o, f0, f1, amp=0.12, period=20):
    """f0〜f1 のあいだ上下にぽよぽよ動く（働いている感じ）。終わったら元の位置に戻る。"""
    base = _cur(o, "location")
    f = f0
    _key(o, "location", f, base, "SINE", "EASE_IN_OUT")
    half = max(period // 2, 2)
    while f + period <= f1:
        _key(o, "location", f + half, (base[0], base[1] + amp, base[2]), "SINE", "EASE_IN_OUT")
        _key(o, "location", f + period, base, "SINE", "EASE_IN_OUT")
        f += period
    o.location = base


def cross_mark(loc=(0, 0), size=1.2, color=RED, width=0.22, z=0.6):
    """バツ印（×）。root を返す（pop_in で出す）。"""
    root = bpy.data.objects.new("cross", None)
    bpy.context.scene.collection.objects.link(root)
    root.location = (loc[0], loc[1], z)
    for ang in (math.pi / 4, -math.pi / 4):
        pl = plane("x", size, width, (0, 0, 0), color)
        pl.rotation_euler = (0, 0, ang)
        pl.parent = root
    return root


def render(t):
    bpy.ops.render.render(animation=True)
