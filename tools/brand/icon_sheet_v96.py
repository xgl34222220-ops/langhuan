"""Preview sheet for the V96 ink-bamboo icon + splash, rendered from the shipped resources.
usage: python3 tools/brand/icon_sheet_v96.py <res_dir> <out.png> <font.ttf>"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import vector_preview as v
from ink_bamboo import mask

res, out, font = sys.argv[1], sys.argv[2], sys.argv[3]


def F(size, w=500):
    f = ImageFont.truetype(font, size)
    try: f.set_variation_by_axes([w])
    except Exception: pass
    return f


bg = Image.open(os.path.join(res, 'drawable-xxxhdpi', 'ic_launcher_background_v96.png')).convert('RGBA')
fg = Image.open(os.path.join(res, 'drawable-xxxhdpi', 'ic_launcher_foreground_v96.png')).convert('RGBA')
full = bg.copy(); full.alpha_composite(fg)                 # 432 px = 108 dp canvas


def icon(shape, px):
    return mask(full.resize((px * 108 // 72 * 2,) * 2, Image.LANCZOS), shape).resize((px, px), Image.LANCZOS)


def themed(px, tint, back):
    m = v.render(os.path.join(res, 'drawable', 'ic_launcher_monochrome_v96.xml'), res, px * 108 // 72 * 2)
    base = Image.new('RGBA', m.size, back + (255,)); solid = Image.new('RGBA', m.size, tint + (255,))
    base.paste(solid, (0, 0), m.split()[3])
    return mask(base, 'circle').resize((px, px), Image.LANCZOS)


def shadow(sheet, im, xy, r=10, a=60):
    sh = Image.new('RGBA', (im.width + 4 * r, im.height + 4 * r), (0, 0, 0, 0))
    sh.paste(Image.new('RGBA', im.size, (0, 0, 0, a)), (2 * r, 2 * r + r // 2), im.split()[3])
    sh = sh.filter(ImageFilter.GaussianBlur(r))
    sheet.alpha_composite(sh, (xy[0] - 2 * r, xy[1] - 2 * r))
    sheet.alpha_composite(im, xy)


W, H = 2000, 1900
sheet = Image.new('RGBA', (W, H), '#ECE9E2'); d = ImageDraw.Draw(sheet)
d.text((40, 26), "琅嬛 · 应用图标 V96（墨竹 · 宣纸 · 朱砂印）", fill='#22221F', font=F(40, 700))
x = 40
for shape, lab in [('circle', '圆形'), ('squircle', '超椭圆'), ('rounded', '圆角方形'), ('teardrop', '水滴')]:
    shadow(sheet, icon(shape, 330), (x, 110)); d.text((x, 462), lab, fill='#4B4B46', font=F(28)); x += 380
for i, (tint, back, lab) in enumerate([((28, 68, 121), (207, 222, 244), '主题 · 浅色'), ((183, 230, 212), (38, 48, 44), '主题 · 深色')]):
    im = themed(170, tint, back); sheet.alpha_composite(im, (x + i * 200, 150)); d.text((x + i * 200 + 18, 340), lab, fill='#4B4B46', font=F(24))
# raw layers
d.text((x, 400), "前景 / 背景层", fill='#4B4B46', font=F(24))
lay = Image.new('RGBA', (432, 432), (200, 200, 200, 255))
for yy in range(0, 432, 24):
    for xx in range(0, 432, 24):
        if (xx // 24 + yy // 24) % 2: ImageDraw.Draw(lay).rectangle([xx, yy, xx + 23, yy + 23], fill=(230, 230, 230, 255))
lay.alpha_composite(fg); lay = lay.resize((150, 150), Image.LANCZOS); sheet.alpha_composite(lay, (x, 440))
sheet.alpha_composite(bg.resize((150, 150), Image.LANCZOS), (x + 170, 440))

y = 640; d.text((40, y), "实际尺寸（xxhdpi 192 / 144 / 96 / 72 / 48 / 36 / 24 px）", fill='#22221F', font=F(30, 600)); x = 40
for px in (192, 144, 96, 72, 48, 36, 24):
    im = icon('circle', px); sheet.alpha_composite(im, (x, 700 + (192 - px))); d.text((x, 905), f"{px}", fill='#666760', font=F(22)); x += px + 34
# launcher mocks: light + dark wallpaper
for j, (wall, txt) in enumerate([('#6E8F86', 'white'), ('#1D2321', '#E8E4DA')]):
    wx, wy = 1080 + j * 0, 690 + j * 0
    if j == 1: wx, wy = 1080, 990
    box = Image.new('RGBA', (860, 270), wall); g = ImageDraw.Draw(box)
    for k in range(270): g.line([(0, k), (860, k)], fill=tuple(int(c) for c in np.array(Image.new('RGB', (1, 1), wall).getpixel((0, 0))) * (1 - 0.18 * k / 270)) + (255,))
    m = Image.new('L', box.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, 859, 269], radius=30, fill=255)
    sheet.paste(box, (wx, wy), m)
    others = [((233, 84, 61), '相机'), ((70, 130, 220), '天气'), None, ((245, 245, 245), '日历')]
    for i, o in enumerate(others):
        ix = wx + 50 + i * 205
        if o is None:
            sheet.alpha_composite(icon('squircle' if j == 0 else 'circle', 132), (ix, wy + 40)); lab = '琅嬛'
        else:
            ph = Image.new('RGBA', (132, 132), o[0] + (255,)); pm = Image.new('L', (132, 132), 0)
            if j == 0: ImageDraw.Draw(pm).rounded_rectangle([0, 0, 131, 131], radius=42, fill=255)
            else: ImageDraw.Draw(pm).ellipse([0, 0, 131, 131], fill=255)
            sheet.paste(ph, (ix, wy + 40), pm); lab = o[1]
        d.text((ix + 66, wy + 212), lab, fill=txt, font=F(26), anchor='mm')
d.text((1080, 640), "桌面效果（浅色 / 深色壁纸）", fill='#22221F', font=F(30, 600))

# splash
y0 = 1290
d.text((40, y0 - 50), "启动页（Android 12+ SplashScreen · 宣纸 / 浓墨）", fill='#22221F', font=F(30, 600))
for i, (night, paper, lab) in enumerate([(False, '#F4EFE4', '浅色'), (True, '#151412', '深色')]):
    px = 40 + i * 330; ph = Image.new('RGBA', (300, 600), paper)
    mk = Image.open(os.path.join(res, 'drawable-night-nodpi' if night else 'drawable-nodpi', 'splash_bamboo_v96.png')).convert('RGBA')
    s = int(300 * 288 / 411)                                # phone width 411 dp
    ph.alpha_composite(mk.resize((s, s), Image.LANCZOS), ((300 - s) // 2, (600 - s) // 2))
    pm = Image.new('L', ph.size, 0); ImageDraw.Draw(pm).rounded_rectangle([0, 0, 299, 599], radius=34, fill=255)
    sheet.paste(ph, (px, y0), pm); d.rounded_rectangle([px, y0, px + 299, y0 + 599], radius=34, outline='#BDB8AE', width=3)
    d.text((px + 150, y0 + 570), lab, fill='#8E897F' if night else '#666760', font=F(22), anchor='mm')
# splash mark close-up
for i, night in enumerate((False, True)):
    mk = Image.open(os.path.join(res, 'drawable-night-nodpi' if night else 'drawable-nodpi', 'splash_bamboo_v96.png')).convert('RGBA')
    tile = Image.new('RGBA', (300, 300), '#151412' if night else '#F4EFE4'); tile.alpha_composite(mk.resize((300, 300), Image.LANCZOS))
    sheet.alpha_composite(tile, (720 + i * 320, y0))
d.text((720, y0 + 320), "启动图标特写（288 dp 画布 · 192 dp 圆内）", fill='#4B4B46', font=F(22))
# play store
ps = Image.open(os.path.join(os.path.dirname(res.rstrip('/')), 'ic_launcher-playstore.png')).convert('RGBA').resize((300, 300), Image.LANCZOS)
pm = Image.new('L', (300, 300), 0); ImageDraw.Draw(pm).rounded_rectangle([0, 0, 299, 299], radius=60, fill=255)
sheet.paste(ps, (1400, y0), pm); d.text((1400, y0 + 320), "Play 商店 512 px", fill='#4B4B46', font=F(22))
sheet.convert('RGB').save(out)
print('wrote', out)
