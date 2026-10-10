"""V96 tab glyphs (书架 / 书城 / 创作 / 我的) on a 24-unit grid, 1.5 stroke, round caps + joins.
Kinds: "s" = stroke only, "fs" = fill (even-odd) + the same 1.5 stroke (so a filled glyph keeps
exactly the outline glyph's silhouette), "f" = fill only.
usage: python3 tools/brand/tab_icons_v96.py [preview.png]   prints the Kotlin path data."""
import math, sys

SW = 1.5


def f(x):
    return ('%.2f' % x).rstrip('0').rstrip('.')


def rrect(x0, y0, x1, y1, r):
    return (f"M{f(x0 + r)},{f(y0)}H{f(x1 - r)}A{f(r)},{f(r)} 0 0 1 {f(x1)},{f(y0 + r)}V{f(y1 - r)}"
            f"A{f(r)},{f(r)} 0 0 1 {f(x1 - r)},{f(y1)}H{f(x0 + r)}A{f(r)},{f(r)} 0 0 1 {f(x0)},{f(y1 - r)}"
            f"V{f(y0 + r)}A{f(r)},{f(r)} 0 0 1 {f(x0 + r)},{f(y0)}Z")


def poly(pts, close=True):
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + ("Z" if close else "")


def rot_rrect(ax, ay, w, h, deg, r):
    """Rounded rect standing on (ax, ay) = bottom-left corner, tilted `deg` clockwise (lean right)."""
    a = math.radians(deg)
    ux, uy = math.sin(a), -math.cos(a)          # up along the spine
    vx, vy = math.cos(a), math.sin(a)           # across the spine
    def P(s, t): return ax + vx * s + ux * t, ay + vy * s + uy * t
    # corners with rounded tops only approximated by small chamfer arcs
    pts = [P(0, 0), P(w, 0), P(w, h - r), P(w - r, h), P(r, h), P(0, h - r)]
    x = [P(0, 0), P(w, 0)]
    d = f"M{f(pts[0][0])},{f(pts[0][1])}L{f(pts[1][0])},{f(pts[1][1])}L{f(pts[2][0])},{f(pts[2][1])}"
    d += f"A{f(r)},{f(r)} 0 0 0 {f(pts[3][0])},{f(pts[3][1])}L{f(pts[4][0])},{f(pts[4][1])}"
    d += f"A{f(r)},{f(r)} 0 0 0 {f(pts[5][0])},{f(pts[5][1])}Z"
    return d


# ------------------------------------------------------------------ 书架: books on a shelf
book1 = rrect(4.25, 4.25, 8.25, 19.75, 1.0)
book2 = rrect(9.75, 6.75, 13.75, 19.75, 1.0)
book3 = rot_rrect(15.0, 19.75, 3.6, 12.4, 17, 1.0)
shelf_base = "M2.75,19.75H21.25"
band1 = "M4.25,8.25H8.25"
band2 = "M9.75,10.25H13.75"
hole1 = rrect(5.0, 7.6, 7.5, 8.9, 0.3)       # knock-outs on the filled spines
hole2 = rrect(10.5, 9.6, 13.0, 10.9, 0.3)

# ------------------------------------------------------------------ 书城: a storefront with an awning
sc = (20.25 - 3.75) / 4 / 2
awning = ("M3.75,9.25L5.25,4.75H18.75L20.25,9.25" + "".join(f"a{f(sc)},{f(sc)} 0 0 1 {f(-2 * sc)},0" for _ in range(4)) + "Z")
store_body = "M5.25,12V19.25A1,1 0 0 0 6.25,20.25H17.75A1,1 0 0 0 18.75,19.25V12"
door = "M10,20.25V16.25A2,2 0 0 1 14,16.25V20.25"
store_body_closed = "M5.25,12.25H18.75V19.25A1,1 0 0 1 17.75,20.25H6.25A1,1 0 0 1 5.25,19.25Z"
door_hole = "M9.4,21V16.25A2.6,2.6 0 0 1 14.6,16.25V21Z"

# ------------------------------------------------------------------ 创作: a calligraphy brush (毛笔)
d = (-1 / math.sqrt(2), 1 / math.sqrt(2)); n = (1 / math.sqrt(2), 1 / math.sqrt(2))
O = (19.6, 4.4)
def A(s, k=0.0): return (O[0] + d[0] * s + n[0] * k, O[1] + d[1] * s + n[1] * k)
handle = f"M{f(A(0)[0])},{f(A(0)[1])}L{f(A(8.2)[0])},{f(A(8.2)[1])}"
fer = poly([A(8.2, -1.55), A(8.2, 1.55), A(10.3, 1.55), A(10.3, -1.55)])
L = 21.8
def C(*p): return " ".join(f"{f(x)},{f(y)}" for x, y in p)
t0l, t0r, tip = A(10.3, -1.55), A(10.3, 1.55), A(L, 0.35)
tuft = (f"M{C(t0l)}C{C(A(12.6, -3.0), A(17.5, -2.3), tip)}"
        f"C{C(A(17.8, 2.6), A(12.8, 3.1), t0r)}Z")

# ------------------------------------------------------------------ 我的: person
head = "M12,4.25A3.75,3.75 0 1 1 11.99,4.25Z"
body = "M4.75,20.25C5.45,16.55 8.35,14.25 12,14.25S18.55,16.55 19.25,20.25"
body_closed = "M4.75,20.25C5.45,16.55 8.35,14.25 12,14.25S18.55,16.55 19.25,20.25Z"

ICONS = {
    "Shelf": {
        "Outline": [("s", book1), ("s", book2), ("s", book3), ("s", shelf_base), ("s", band1), ("s", band2)],
        "Filled": [("fs", book1 + hole1), ("fs", book2 + hole2), ("fs", book3), ("s", shelf_base)],
    },
    "Store": {
        "Outline": [("s", awning), ("s", store_body), ("s", door)],
        "Filled": [("fs", awning), ("fs", store_body_closed + door_hole)],
    },
    "Create": {
        "Outline": [("s", handle), ("s", fer), ("s", tuft)],
        "Filled": [("s", handle), ("fs", fer), ("fs", tuft)],
    },
    "Mine": {
        "Outline": [("s", head), ("s", body)],
        "Filled": [("fs", head), ("fs", body_closed)],
    },
}


def svg(parts, color="#1E6A5A"):
    b = ""
    for k, dd in parts:
        if k in ("f", "fs"):
            b += f'<path d="{dd}" fill="{color}" fill-rule="evenodd"/>'
        if k in ("s", "fs"):
            if k == "fs":
                # stroke only the outer subpath so knock-outs stay open
                dd = dd.split("Z")[0] + "Z"
            b += f'<path d="{dd}" fill="none" stroke="{color}" stroke-width="{SW}" stroke-linecap="round" stroke-linejoin="round"/>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">{b}</svg>'


if __name__ == "__main__":
    for name, v in ICONS.items():
        for st, parts in v.items():
            print(f"// {name}{st}")
            for k, dd in parts:
                print(f'    {k}: "{dd}"')
    if len(sys.argv) > 1:
        import io, cairosvg
        from PIL import Image
        sheet = Image.new("RGB", (4 * 150 + 20, 2 * 150 + 20 + 240), "#F7F5F0")
        for i, (k, v) in enumerate(ICONS.items()):
            for j, st in enumerate(("Outline", "Filled")):
                c = "#6B6A63" if st == "Outline" else "#1E6A5A"
                im = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg(v[st], c).encode(), output_width=140, output_height=140)))
                sheet.paste(im, (10 + i * 150, 10 + j * 150), im)
                for m, px in enumerate((72, 63)):   # 24 dp at xxhdpi / at 420 dpi
                    sm = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg(v[st], c).encode(), output_width=px, output_height=px)))
                    sheet.paste(sm, (10 + i * 150 + j * 74, 330 + m * 90), sm)
        sheet.save(sys.argv[1])
