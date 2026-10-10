"""Legacy (pre-adaptive) launcher PNGs + 512px Play Store icon, rendered from the V95 vectors."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import vector_preview as v
res = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/res'
for q, px in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
    d = os.path.join(res, f'mipmap-{q}'); os.makedirs(d, exist_ok=True)
    big = v.adaptive(res, px * 3 * 108 // 72)
    for name, shape in (('ic_launcher', 'rounded'), ('ic_launcher_round', 'circle')):
        v.mask(big, shape).resize((px, px), v.Image.LANCZOS).save(os.path.join(d, name + '.png'), optimize=True)
play = v.mask(v.adaptive(res, 512 * 108 // 72 * 2), 'square').resize((512, 512), v.Image.LANCZOS).convert('RGB')
play.save(os.path.join(os.path.dirname(res.rstrip('/')), 'ic_launcher-playstore.png'), optimize=True)
print('ok')
