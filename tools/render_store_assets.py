# Compose les visuels de la fiche Play Store depuis les captures brutes de l'appareil.
#
# Entree  : les captures 1080x2400 prises a l'adb (repertoire SHOTS ci-dessous).
# Sortie  : store/screenshots/<langue>/NN-<ecran>.png  (1080x1920, ratio 9:16 exige par la Console)
#           store/feature-graphic/<langue>.png         (1024x500)
#
# Les captures brutes ne sont pas versionnees : regenerer = reprendre les captures (voir
# log/2026.09.21-Visuels Play Store.md), puis relancer ce script.
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = r"C:\DEV\PlantInfo"
SHOTS = os.environ.get(
    "PLANTINFO_SHOTS",
    r"C:\Users\reyna\AppData\Local\Temp\claude\C--DEV-PlantInfo"
    r"\2fd865d6-cee1-4ed7-9daa-a46f9b7fc914\scratchpad\shots",
)
OUT = os.path.join(ROOT, "store")

FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_SEMI = r"C:\Windows\Fonts\seguisb.ttf"

GREEN_DARK = (11, 31, 17)
GREEN_TOP = (18, 58, 30)
ACCENT = (165, 214, 167)
WHITE = (255, 255, 255)

LANGS = ["fr", "en", "de", "it", "es"]

# Une capture brute par ecran et par langue. L'ordre fixe l'ordre d'affichage dans la Console.
SCREENS = [
    ("01-capture", {
        "fr": "20-capture-ready-fr.png", "en": "en-capture2.png", "de": "de-capture.png",
        "it": "it-capture.png", "es": "es-capture.png"}),
    ("02-resultat", {
        "fr": "22-result-fr.png", "en": "en-result.png", "de": "de-result.png",
        "it": "it-result.png", "es": "es-result.png"}),
    ("03-calendrier", {
        "fr": "fr-scroll-2.png", "en": "en-map-final.png", "de": "de-scroll-3.png",
        "it": "it-scroll-2.png", "es": "es-scroll-2.png"}),
    ("04-carte", {
        "fr": "fr-map-final.png", "en": "en-map-final2.png", "de": "de-map-final.png",
        "it": "it-det-4.png", "es": "es-det-4.png"}),
    ("05-questions", {
        "fr": "fr-qa-answer.png", "en": "en-qa.png", "de": "de-qa.png",
        "it": "it-qa.png", "es": "es-qa.png"}),
    ("06-parametres", {
        "fr": "fr-settings-top.png", "en": "en-set-check.png", "de": "de-settings.png",
        "it": "it-settings-top.png", "es": "es-settings-top.png"}),
]

CAPTIONS = {
    "01-capture": {
        "fr": "Une photo suffit",
        "en": "One photo is enough",
        "de": "Ein Foto genügt",
        "it": "Basta una foto",
        "es": "Basta una foto",
    },
    "02-resultat": {
        "fr": "Nom, fiabilité et alerte toxicité",
        "en": "Name, reliability and toxicity alert",
        "de": "Name, Verlässlichkeit und Giftwarnung",
        "it": "Nome, attendibilità e allerta tossicità",
        "es": "Nombre, fiabilidad y alerta de toxicidad",
    },
    "03-calendrier": {
        "fr": "Calendrier, usages et symbolique",
        "en": "Calendar, uses and symbolism",
        "de": "Kalender, Verwendung und Symbolik",
        "it": "Calendario, usi e simbologia",
        "es": "Calendario, usos y simbolismo",
    },
    "04-carte": {
        "fr": "Carte de répartition et lieu de la photo",
        "en": "Range map and where the photo was taken",
        "de": "Verbreitungskarte und Aufnahmeort",
        "it": "Mappa di distribuzione e luogo dello scatto",
        "es": "Mapa de distribución y lugar de la foto",
    },
    "05-questions": {
        "fr": "Posez vos questions à l'IA",
        "en": "Ask the AI your questions",
        "de": "Stellen Sie der KI Ihre Fragen",
        "it": "Fai le tue domande all'IA",
        "es": "Haga sus preguntas a la IA",
    },
    "06-parametres": {
        "fr": "Cinq langues, vos clés, zéro traçage",
        "en": "Five languages, your keys, no tracking",
        "de": "Fünf Sprachen, Ihre Schlüssel, kein Tracking",
        "it": "Cinque lingue, le vostre chiavi, nessun tracciamento",
        "es": "Cinco idiomas, sus claves, sin rastreo",
    },
}

TAGLINES = {
    "fr": "Identifiez plantes, arbres et champignons par photo",
    "en": "Identify plants, trees and mushrooms from a photo",
    "de": "Pflanzen, Bäume und Pilze per Foto bestimmen",
    "it": "Identifica piante, alberi e funghi da una foto",
    "es": "Identifique plantas, árboles y setas por foto",
}

# Coordonnees GPS reelles de la photo d'exemple : floutees avant publication.
# Rectangles (x0, y0, x1, y1) dans la capture brute 1080x2400.
BLUR_COORDS = {
    "fr-map-final.png": (80, 1225, 760, 1300),
    "de-map-final.png": (80, 968, 760, 1043),
    "it-det-4.png": (80, 893, 760, 968),
    "es-det-4.png": (80, 378, 760, 430),
}


def gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGB", (1, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.point((0, y), fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img.resize((w, h), Image.BILINEAR)


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=255)
    return m


def wrap(draw, text, font, max_width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if draw.textlength(trial, font=font) <= max_width or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def bezier(p0, p1, p2, p3, n=60):
    pts = []
    for i in range(n + 1):
        t, u = i / n, 1 - i / n
        pts.append((
            u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
        ))
    return pts


def blur_region(img, box):
    region = img.crop(box).filter(ImageFilter.GaussianBlur(14))
    img.paste(region, box)


def make_screenshot(src_path, caption, out_path, blur_box=None):
    shot = Image.open(src_path).convert("RGB")
    if blur_box:
        blur_region(shot, blur_box)

    canvas = gradient((1080, 1920), GREEN_TOP, GREEN_DARK)
    glow = Image.new("RGB", (1080, 1920), (0, 0, 0))
    ImageDraw.Draw(glow).ellipse([-200, 250, 1280, 1700], fill=(26, 78, 40))
    canvas = Image.blend(canvas, glow.filter(ImageFilter.GaussianBlur(160)), 0.45)

    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(FONT_SEMI, 54)
    lines = wrap(draw, caption, font, 900)
    if len(lines) > 1:                      # une police plus petite plutot qu'un mot orphelin
        font = ImageFont.truetype(FONT_SEMI, 46)
        lines = wrap(draw, caption, font, 960)
    if len(lines) > 1:
        font = ImageFont.truetype(FONT_SEMI, 40)
        lines = wrap(draw, caption, font, 980)
    line_h = 66
    y = 150 - (len(lines) - 1) * line_h // 2
    for line in lines:
        draw.text((540, y), line, font=font, fill=WHITE, anchor="mm")
        y += line_h

    # Filet vert sous le titre
    draw.rounded_rectangle([490, 232, 590, 240], 4, fill=ACCENT)

    pw = 700
    ph = round(shot.height * pw / shot.width)
    phone = shot.resize((pw, ph), Image.LANCZOS)
    mask = rounded_mask((pw, ph), 42)
    px, py = (1080 - pw) // 2, 300

    shadow = Image.new("RGBA", (1080, 1920), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [px - 6, py + 10, px + pw + 6, py + ph + 22], 48, fill=(0, 0, 0, 150))
    canvas = Image.alpha_composite(
        canvas.convert("RGBA"), shadow.filter(ImageFilter.GaussianBlur(24))).convert("RGB")

    canvas.paste(phone, (px, py), mask)
    border = Image.new("RGBA", (1080, 1920), (0, 0, 0, 0))
    ImageDraw.Draw(border).rounded_rectangle(
        [px, py, px + pw - 1, py + ph - 1], 42, outline=ACCENT + (110,), width=3)
    canvas = Image.alpha_composite(canvas.convert("RGBA"), border).convert("RGB")

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    canvas.save(out_path, optimize=True)


def make_feature_graphic(lang, out_path):
    w, h = 1024, 500
    base = gradient((w, h), (27, 94, 32), (46, 125, 50))

    # Motif viseur + feuille, tres discret, a droite : meme geometrie que l'icone
    # (viewport 108 de ic_launcher_foreground.xml), centree sur (54, 54).
    motif = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    md = ImageDraw.Draw(motif)
    s, cx, cy = 5.6, 812.0, 250.0

    def p(x, y):
        return (cx + (x - 54) * s, cy + (y - 54) * s)

    for corner in ([(34, 45), (34, 34), (45, 34)], [(63, 34), (74, 34), (74, 45)],
                   [(74, 63), (74, 74), (63, 74)], [(45, 74), (34, 74), (34, 63)]):
        md.line([p(*q) for q in corner], fill=(255, 255, 255, 36), width=int(7 * s),
                joint="curve")
    leaf = (bezier((43, 63), (40, 50), (50, 41), (65, 41))
            + bezier((65, 41), (67, 54), (57, 66), (43, 63)))
    md.polygon([p(*q) for q in leaf], fill=(255, 255, 255, 32))
    base = Image.alpha_composite(base.convert("RGBA"), motif).convert("RGB")

    icon = Image.open(os.path.join(ROOT, "app", "src", "main", "ic_launcher-playstore.png"))
    icon = icon.convert("RGBA").resize((156, 156), Image.LANCZOS)
    base.paste(icon, (76, 172), rounded_mask((156, 156), 34))

    d = ImageDraw.Draw(base)
    d.text((268, 186), "PlantInfo", font=ImageFont.truetype(FONT_BOLD, 92), fill=WHITE)
    tag_font = ImageFont.truetype(FONT_SEMI, 33)
    for i, line in enumerate(wrap(d, TAGLINES[lang], tag_font, 540)):
        d.text((272, 298 + i * 44), line, font=tag_font, fill=(220, 237, 200))

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    base.save(out_path, optimize=True)


def main():
    for lang in LANGS:
        for name, per_lang in SCREENS:
            src = os.path.join(SHOTS, per_lang[lang])
            make_screenshot(
                src,
                CAPTIONS[name][lang],
                os.path.join(OUT, "screenshots", lang, f"{name}.png"),
                BLUR_COORDS.get(per_lang[lang]),
            )
        make_feature_graphic(lang, os.path.join(OUT, "feature-graphic", f"{lang}.png"))
        print("ok", lang)


if __name__ == "__main__":
    main()
