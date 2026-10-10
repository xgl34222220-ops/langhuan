"""琅嬛 V96 brand mark: an ink-wash bamboo sprig (墨竹) with a vermilion seal dot.

Procedural sumi-e renderer: every leaf is one brush stroke laid along a curved centre line. Each
pixel gets stroke coordinates (t along the stroke, u across it); the alpha comes from a
press-and-lift width profile, bristle streaks stretched along the stroke, dry-brush (飞白) breaks
towards the tip, ragged edges and a faint wet bleed. Deterministic (fixed seeds), so the shipped
rasters can be regenerated exactly:

    python3 tools/brand/ink_bamboo.py app/src/main/res

Coordinates are in adaptive-icon units (108 x 108, safe-zone circle r = 33 around 54,54).
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import gaussian_filter, map_coordinates
from scipy.spatial import cKDTree

INK = (26, 25, 23)
PAPER_INK = (236, 230, 216)        # leaves on the dark splash
SEAL = (203, 58, 36)               # vermilion 朱砂
PAPER = (244, 239, 228)            # #F4EFE4 rice paper
PAPER_DARK = (21, 20, 18)          # #151412 deep ink


def smooth(a, b, x):
    x = np.clip((x - a) / (b - a), 0, 1)
    return x * x * (3 - 2 * x)


class Stroke:
    def __init__(self, base, angle, length, width, bend=0.08, tone=1.0, seed=0, kind="leaf", dry=1.0):
        self.base = np.array(base, float); self.angle = angle; self.length = length
        self.width = width; self.bend = bend; self.tone = tone; self.seed = seed; self.kind = kind; self.dry = dry

    def centre(self, n=500):
        a = math.radians(self.angle)
        d = np.array([math.cos(a), math.sin(a)]); nrm = np.array([-d[1], d[0]])
        p0 = self.base; p2 = p0 + d * self.length; p1 = (p0 + p2) / 2 + nrm * self.bend * self.length
        t = np.linspace(0, 1, n)[:, None]
        pts = (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2
        tan = 2 * (1 - t) * (p1 - p0) + 2 * t * (p2 - p1)
        tan /= np.linalg.norm(tan, axis=1, keepdims=True)
        return t[:, 0], pts, np.stack([-tan[:, 1], tan[:, 0]], 1)

    def profile(self, t):
        if self.kind == "stem":
            return self.width * (1.0 - 0.35 * t) * (0.55 + 0.45 * smooth(0, 0.12, t))
        press = np.sqrt(smooth(0.0, 0.24, t))
        return self.width * (0.10 + 0.90 * press) * (1 - t) ** 1.05 * (1.0 + 0.6 * t * (1 - t))

    def render(self, size, scale):
        """Return an alpha map (size x size float) for this stroke at `scale` px per unit."""
        rng = np.random.default_rng(self.seed)
        t, pts, nrm = self.centre()
        w = self.profile(t)
        pad = self.width
        lo = np.floor((pts.min(0) - pad) * scale).astype(int).clip(0, size)
        hi = np.ceil((pts.max(0) + pad) * scale).astype(int).clip(0, size)
        ys, xs = np.mgrid[lo[1]:hi[1], lo[0]:hi[0]]
        P = np.stack([xs.ravel(), ys.ravel()], 1) / scale + 0.5 / scale
        _, idx = cKDTree(pts).query(P)
        tt = t[idx]; d = np.einsum('ij,ij->i', P - pts[idx], nrm[idx])
        tan0 = np.array([nrm[0, 1], -nrm[0, 0]])
        behind = ((P - pts[0]) @ tan0) < 0           # no band trailing behind the stroke start
        half = np.maximum(w[idx] / 2, 1e-4)
        u = d / half
        # ragged edge: the boundary wobbles a little along the stroke
        edge_noise = gaussian_filter(rng.standard_normal(400), 6); edge_noise /= np.abs(edge_noise).max() + 1e-6
        e2 = gaussian_filter(rng.standard_normal(400), 6); e2 /= np.abs(e2).max() + 1e-6
        ti = np.clip((tt * 399).astype(int), 0, 399)
        rim = 1 + 0.07 * np.where(u > 0, edge_noise[ti], e2[ti])
        au = np.abs(u) / rim
        # paper absorbs unevenly: a pixel-space grain makes the rim fuzzy rather than vector-clean
        gr = gaussian_filter(rng.standard_normal(ys.shape), max(0.6, scale * 0.06)).ravel()
        gr /= gr.std() + 1e-6
        au = au + 0.022 * gr * smooth(0.75, 1.0, au)
        # bristle streaks: noise stretched along the stroke (high frequency across, low along)
        field = gaussian_filter(rng.standard_normal((96, 24)), (0.9, 2.2))
        field = (field - field.mean()) / (field.std() + 1e-6)
        su = np.clip((u + 1) / 2, 0, 1) * 95; st = tt * 23
        streak = map_coordinates(field, [su, st], order=1, mode='nearest')
        streak = 1 / (1 + np.exp(-1.6 * streak))          # 0..1
        fine = gaussian_filter(rng.standard_normal((220, 40)), (0.6, 1.5)); fine /= fine.std() + 1e-6
        fs = map_coordinates(fine, [np.clip((u + 1) / 2, 0, 1) * 219, tt * 39], order=1, mode='nearest')
        if self.kind == "stem":
            body = smooth(1.05, 0.8, au)
            alpha = body * (0.82 + 0.18 * streak) * (1 - 0.25 * smooth(0.75, 1.0, tt))
        else:
            body = smooth(1.0, 0.82, au)
            # dry brush: towards the tip and at the edges some bristles run out of ink
            dry_start = 0.62 - 0.12 * self.dry + 0.22 * (1 - au) ** 2
            dry = smooth(dry_start, dry_start + 0.32, tt) * self.dry + 0.25 * au ** 4 * self.dry
            keep = np.clip((streak + 0.10 * fs) - dry * 0.95 + 0.42, 0, 1)
            keep = smooth(0.18, 0.62, keep)
            # ink is denser in the belly of the stroke, lighter where the brush lifts
            density = 0.90 + 0.10 * (1 - tt) + 0.06 * (streak - 0.5)
            alpha = body * keep * density
        if self.tone < 0.8:   # pale wash leaves: mottled, softer
            m = gaussian_filter(rng.standard_normal(ys.shape), scale * 0.25).ravel(); m /= m.std() + 1e-6
            alpha = gaussian_filter(alpha.reshape(ys.shape), scale * 0.12).ravel()
            alpha = alpha * (0.9 + 0.1 * m)
        alpha = np.where(behind, 0, alpha)
        alpha = np.clip(alpha * self.tone, 0, 1)
        out = np.zeros((size, size), np.float32)
        out[lo[1]:hi[1], lo[0]:hi[0]] = alpha.reshape(ys.shape)
        return out


def seal(size, scale, centre, r, seed=7):
    """A small hand-pressed vermilion seal dot: slightly irregular rim, uneven pigment."""
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:size, 0:size]
    cx, cy = centre[0] * scale, centre[1] * scale; R = r * scale
    ang = np.arctan2(ys - cy, xs - cx); dist = np.hypot(xs - cx, ys - cy)
    k = np.arange(1, 7)
    amp = rng.uniform(0.004, 0.02, 6); ph = rng.uniform(0, 2 * np.pi, 6)
    wob = 1 + sum(amp[i] * np.cos(k[i] * ang + ph[i]) for i in range(6))
    a = smooth(R * wob * 1.02, R * wob * 0.94, dist)
    grain = gaussian_filter(rng.standard_normal((size, size)), scale * 0.18)
    grain /= grain.std() + 1e-6
    return np.clip(a * (0.95 + 0.035 * grain), 0, 1).astype(np.float32)


# ---------------------------------------------------------------- compositions
def sprig(ox=0.0, oy=0.0, s=1.0):
    """The sprig shared by the icon and the splash, in 108-unit space."""
    def S(**kw):
        b = kw.pop('base'); kw['base'] = (ox + b[0] * s, oy + b[1] * s)
        kw['length'] *= s; kw['width'] *= s
        return Stroke(**kw)
    n1 = (69.6, 32.6); n2 = (58.6, 41.0); n3 = (48.4, 48.4)
    return [
        # twig: a fine stem from the upper right with three nodes
        S(base=(73.4, 29.4), angle=140, length=5.0, width=1.25, bend=0.0, kind="stem", seed=11),
        S(base=(69.7, 32.7), angle=143, length=13.9, width=1.05, bend=-0.04, kind="stem", seed=12),
        S(base=(58.7, 41.1), angle=144, length=12.6, width=0.9, bend=0.05, kind="stem", seed=13),
        # far (pale) leaves for depth
        S(base=n1, angle=134, length=27, width=5.6, bend=0.06, tone=0.30, seed=21, dry=0.6),
        S(base=n3, angle=196, length=17, width=4.0, bend=0.08, tone=0.30, seed=22, dry=0.6),
        # node 1
        S(base=n1, angle=199, length=24.5, width=6.0, bend=-0.10, tone=0.96, seed=31),
        S(base=(69.9, 33.3), angle=103, length=30, width=6.8, bend=-0.08, tone=1.0, seed=34),
        # node 2
        S(base=n2, angle=181, length=27, width=6.6, bend=-0.08, tone=0.9, seed=36),
        S(base=n2, angle=119, length=29, width=7.0, bend=0.08, tone=1.0, seed=33),
        # node 3
        S(base=n3, angle=158, length=19.5, width=5.0, bend=0.08, tone=0.86, seed=35),
        S(base=n3, angle=127, length=16.5, width=4.2, bend=0.10, tone=0.92, seed=37),
    ]


ICON_SCALE = 1.09
SEAL_ICON = ((72.6, 72.2), 2.85)


def icon_strokes():
    # nudge the sprig down 4 units and up-scale it so the painted mass fills the safe zone
    s = ICON_SCALE
    return sprig(ox=54 * (1 - s), oy=54 - 50 * s, s=s)


def paint(strokes, size, unit, seal_at=None, ink=INK, seal_rgb=SEAL):
    """Composite strokes (+ optional seal) into an RGBA image of size x size at `unit` px/unit."""
    sup = 2
    big = size * sup; sc = unit * sup
    a = np.zeros((big, big), np.float32)
    for st in strokes:
        sa = st.render(big, sc)
        a = a + sa * (1 - a)                       # ink layers over ink: darker where they cross
    wet = gaussian_filter(a, sc * 0.22)
    a = np.maximum(a, wet * 0.45)                  # faint wet bleed into the paper
    rgb = np.zeros((big, big, 3), np.float32); rgb[:] = ink
    out_a = a
    if seal_at is not None:
        sa = seal(big, sc, seal_at[0], seal_at[1])
        rgb = rgb * (1 - sa[..., None]) + np.array(seal_rgb, np.float32) * sa[..., None]
        out_a = a + sa * (1 - a)
        # where the seal sits over ink, keep the vermilion on top
    img = np.dstack([rgb, out_a * 255]).clip(0, 255).astype(np.uint8)
    im = Image.fromarray(img, 'RGBA')
    # premultiplied downsample to avoid dark fringes
    pm = np.asarray(im).astype(np.float32); pm[..., :3] *= pm[..., 3:] / 255
    pmi = Image.fromarray(pm.clip(0, 255).astype(np.uint8), 'RGBA').resize((size, size), Image.LANCZOS)
    arr = np.asarray(pmi).astype(np.float32)
    al = np.maximum(arr[..., 3:], 1e-3)
    arr[..., :3] = np.where(arr[..., 3:] > 0, arr[..., :3] * 255 / al, 0)
    return Image.fromarray(arr.clip(0, 255).astype(np.uint8), 'RGBA')


def paper(size, rgb=PAPER, seed=3, strength=1.0):
    """Rice-paper background: soft mottling, scattered random fibres (not a woven grid), fine grain
    and a whisper of warm vignette."""
    rng = np.random.default_rng(seed)
    base = np.ones((size, size, 3), np.float32) * np.array(rgb, np.float32)
    mott = gaussian_filter(rng.standard_normal((size, size)), size / 30); mott /= mott.std() + 1e-6
    mott2 = gaussian_filter(rng.standard_normal((size, size)), size / 120); mott2 /= mott2.std() + 1e-6
    fib = Image.new('L', (size, size), 0); dr = ImageDraw.Draw(fib)
    for _ in range(int(size * 1.4)):
        x, y = rng.uniform(0, size, 2); ang = rng.uniform(0, np.pi); ln = rng.uniform(size / 60, size / 14)
        bend = rng.uniform(-0.25, 0.25); pts = []
        for k in range(6):
            tt = k / 5; aa = ang + bend * tt
            pts.append((x + np.cos(aa) * ln * tt, y + np.sin(aa) * ln * tt))
        dr.line(pts, fill=int(rng.uniform(40, 120)), width=max(1, size // 700))
    fibers = gaussian_filter(np.asarray(fib, np.float32) / 255, max(0.5, size / 1400))
    grain = gaussian_filter(rng.standard_normal((size, size)), 0.6) * 0.8
    ys, xs = np.mgrid[0:size, 0:size] / size - 0.5
    vig = -7.0 * (xs ** 2 + ys ** 2)
    lum = (1.3 * mott + 0.5 * mott2 + grain + 3.2 * fibers + vig) * strength
    base = base + lum[..., None] * np.array([1.0, 0.97, 0.9])
    return Image.fromarray(base.clip(0, 255).astype(np.uint8), 'RGB')


def monochrome_paths(strokes, unit_scale=1.0):
    """Simplified leaf silhouettes for the Android 13 themed icon (solid, no texture)."""
    out = []
    for st in strokes:
        t, pts, nrm = st.centre(60)
        w = st.profile(t) / 2
        if st.kind == "stem":
            w = np.maximum(w, 0.55)
        else:
            w = w * 1.08
        left = pts + nrm * w[:, None]; right = pts - nrm * w[:, None]
        poly = np.concatenate([left, right[::-1]])
        d = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in poly) + " Z"
        out.append(d)
    c, r = SEAL_ICON
    out.append(f"M{c[0]-r:.2f},{c[1]:.2f} A{r},{r} 0 1,0 {c[0]+r:.2f},{c[1]:.2f} A{r},{r} 0 1,0 {c[0]-r:.2f},{c[1]:.2f} Z")
    return out


SPLASH_SCALE = 0.78
SEAL_SPRIG = (((73.0 - 54 * (1 - ICON_SCALE)) / ICON_SCALE, (72.5 - 54 + 50 * ICON_SCALE) / ICON_SCALE), 2.85 / ICON_SCALE)


def mask(im, shape):
    """Crop the 108-unit canvas to the visible 72-unit viewport and apply a launcher mask."""
    n = im.size[0]; k = 4
    m = Image.new('L', (n * k, n * k), 0); dr = ImageDraw.Draw(m)
    i = n * k * 18 / 108; box = [i, i, n * k - i, n * k - i]; w = box[2] - box[0]
    if shape == 'circle': dr.ellipse(box, fill=255)
    elif shape == 'squircle': dr.rounded_rectangle(box, radius=w * 0.32, fill=255)
    elif shape == 'rounded': dr.rounded_rectangle(box, radius=w * 0.16, fill=255)
    elif shape == 'teardrop':
        dr.ellipse(box, fill=255); dr.rectangle([box[0] + w / 2, box[1] + w / 2, box[2], box[3]], fill=255)
    else: dr.rectangle(box, fill=255)
    m = m.resize((n, n), Image.LANCZOS)
    o = Image.new('RGBA', (n, n), (0, 0, 0, 0)); o.paste(im, (0, 0), m)
    c = round(n * 18 / 108)
    return o.crop((c, c, n - c, n - c))


DENS = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}


def build(res):
    strokes = icon_strokes()
    # ---- adaptive foreground / background rasters, per density (108 dp canvas)
    master_fg = paint(strokes, 108 * 8, 8, SEAL_ICON)            # 864 px master
    master_bg = paper(108 * 8)
    for q, f in DENS.items():
        px = int(round(108 * f))
        d = os.path.join(res, f'drawable-{q}'); os.makedirs(d, exist_ok=True)
        master_fg.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher_foreground_v96.png'), optimize=True)
        master_bg.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher_background_v96.png'), optimize=True)
    # ---- monochrome vector
    body = "\n".join(f'    <path android:fillColor="#FF000000" android:pathData="{p}" />' for p in monochrome_paths(strokes))
    with open(os.path.join(res, 'drawable', 'ic_launcher_monochrome_v96.xml'), 'w') as fh:
        fh.write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 琅嬛 V96 themed-icon layer: solid silhouettes of the ink bamboo sprig and the seal dot.
     Generated by tools/brand/ink_bamboo.py; edit there and regenerate. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}
</vector>
''')
    # ---- splash marks: Android 12 icon canvas without an icon background is 288 dp with the
    #      content inside a 192 dp circle. The sprig is kept small (~90 dp of painted mass).
    sz = 960; unit = sz / 108.0; k = SPLASH_SCALE
    cx, cy = 53.5, 47.5          # visual centre of the sprig + seal in sprig space
    ox, oy = 54 - cx * k, 54 - cy * k
    sp = sprig(ox=ox, oy=oy, s=k)
    seal_c = (ox + SEAL_SPRIG[0][0] * k, oy + SEAL_SPRIG[0][1] * k)
    light = paint(sp, sz, unit, (seal_c, SEAL_SPRIG[1] * k))
    dark = paint(sp, sz, unit, (seal_c, SEAL_SPRIG[1] * k), ink=PAPER_INK, seal_rgb=(214, 92, 66))
    for folder, im in (('drawable-nodpi', light), ('drawable-night-nodpi', dark)):
        d = os.path.join(res, folder); os.makedirs(d, exist_ok=True)
        im.save(os.path.join(d, 'splash_bamboo_v96.png'), optimize=True)
    # ---- legacy launcher PNGs (pre-adaptive launchers) + 512 px Play Store icon
    full = master_bg.convert('RGBA'); full.alpha_composite(master_fg)
    for q, px in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
        d = os.path.join(res, f'mipmap-{q}'); os.makedirs(d, exist_ok=True)
        for name, shape in (('ic_launcher', 'rounded'), ('ic_launcher_round', 'circle')):
            mask(full, shape).resize((px, px), Image.LANCZOS).save(os.path.join(d, name + '.png'), optimize=True)
    play = mask(full, 'square').resize((512, 512), Image.LANCZOS).convert('RGB')
    play.save(os.path.join(os.path.dirname(res.rstrip('/')), 'ic_launcher-playstore.png'), optimize=True)
    return master_fg, master_bg


if __name__ == '__main__':
    res = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/res'
    build(res)
    print('ok')
