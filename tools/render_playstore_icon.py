# Rend l'icone X3 (viseur + feuille) en PNG 512x512 pour la fiche Play Store.
# Meme geometrie que app/src/main/res/drawable/ic_launcher_foreground.xml (viewport 108).
from PIL import Image, ImageDraw

OUT = r"C:\DEV\PlantInfo\app\src\main\ic_launcher-playstore.png"
SIZE = 512
SS = 4                      # supersampling
S = SIZE * SS / 108.0       # viewport 108 -> pixels

GREEN = (46, 125, 50, 255)      # #2E7D32
WHITE = (255, 255, 255, 255)
ACCENT = (165, 214, 167, 255)   # #A5D6A7


def p(x, y):
    return (x * S, y * S)


def bezier(p0, p1, p2, p3, n=80):
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        x = u**3 * p0[0] + 3 * u*u*t * p1[0] + 3 * u*t*t * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u*u*t * p1[1] + 3 * u*t*t * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def stroke(draw, pts, width, color):
    """Polyligne avec extremites et jointures arrondies."""
    w = width * S
    draw.line([p(*q) for q in pts], fill=color, width=int(round(w)))
    r = w / 2.0
    for x, y in pts:
        cx, cy = p(x, y)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)


img = Image.new("RGBA", (SIZE * SS, SIZE * SS), GREEN)
d = ImageDraw.Draw(img)

# Viseur : quatre coins
for corner in [[(34, 45), (34, 34), (45, 34)],
               [(63, 34), (74, 34), (74, 45)],
               [(74, 63), (74, 74), (63, 74)],
               [(45, 74), (34, 74), (34, 63)]]:
    stroke(d, corner, 7, WHITE)

# Feuille
leaf = bezier((43, 63), (40, 50), (50, 41), (65, 41)) + \
       bezier((65, 41), (67, 54), (57, 66), (43, 63))
d.polygon([p(*q) for q in leaf], fill=ACCENT)

# Nervure, couleur du fond
vein = bezier((43, 63), (51, 58), (59, 51), (65, 41))
stroke(d, vein, 3, GREEN)

img.resize((SIZE, SIZE), Image.LANCZOS).save(OUT)
print("ecrit", OUT)
