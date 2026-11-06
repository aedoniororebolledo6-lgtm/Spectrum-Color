#!/usr/bin/env python3
"""Arma las capturas de la ficha de Google Play (1080x1920, sin transparencia) a partir de las fotos del Android
virtual (pasos p_es_* y p_en_* de ci/smoke.sh): título arriba, captura enmarcada en cristal y fondo del juego.

Uso: python3 tools/ficha_capturas.py <carpeta_capturas> <carpeta_fuentes_roboto> [carpeta_salida=docs/play]
Las capturas se bajan con tools/capturas.sh <carpeta> p ; Roboto-*.ttf de github.com/googlefonts/roboto-3-classic.
"""
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1080, 1920

# (archivo(s) de la captura sin p_<idioma>_, título ES, subtítulo ES, título EN, subtítulo EN)
CARDS = [
    (["01_menu"], "Un toque. Un color.", "Cruza cada obstáculo solo por tu color",
     "One tap. One color.", "Pass every obstacle only through your color"),
    (["02_nivel5"], "Toca para saltar", "Calcula el momento justo",
     "Tap to jump", "Time it just right"),
    (["04_nivel75"], "200 obstáculos", "De vidrio líquido, nuevos en cada mundo",
     "200 obstacles", "Liquid glass, new ones in every world"),
    (["05_nivel112"], "120 niveles", "12 mundos llenos de desafíos",
     "120 levels", "12 worlds full of challenges"),
    (["03_nivel33"], "Reflejos al límite", "Cada nivel es un desafío nuevo",
     "Reflexes to the limit", "Every level is a new challenge"),
    (["07_apariencias"], "99 esferas y 41 estelas", "Personaliza tu estilo",
     "99 balls and 41 trails", "Make it your style"),
    (["10_aventura", "12_taller"], "Aventura y Taller", "12 mundos, 12 jefes y tus propias salas",
     "Adventure & Maker", "12 worlds, 12 bosses and your own rooms"),
    (["08_ganar"], "Consigue las 3 estrellas", "Sin anuncios · Pago único",
     "Earn all 3 stars", "No ads · One-time purchase"),
]


def background(seed):
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        u = y / H
        r = int(10 + 28 * u)
        g = int(14 + 6 * u)
        b = int(42 + 40 * u)
        for x in range(W):
            px[x, y] = (r, g, b)
    img = img.convert("RGBA")
    rnd = random.Random(seed)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    cols = [(61, 209, 255), (255, 62, 170), (255, 200, 40), (140, 80, 255), (53, 232, 160)]
    for i in range(5):
        x, y, r = rnd.randint(0, W), rnd.randint(0, H), rnd.randint(260, 460)
        d.ellipse((x - r, y - r, x + r, y + r), fill=cols[(i + seed) % len(cols)] + (70,))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(120)))
    bub = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(bub)
    for i in range(16):
        x, y, r = rnd.randint(0, W), rnd.randint(0, H), rnd.randint(14, 70)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, 10), outline=(255, 255, 255, 60), width=2)
        d.arc((x - r * 0.7, y - r * 0.7, x + r * 0.7, y + r * 0.7), 200, 250, fill=(255, 255, 255, 120), width=2)
    return Image.alpha_composite(img, bub)


def framed(shot, w, h, radius):
    """Captura escalada a w x h con esquinas redondeadas, borde de luz y sombra suave (devuelve RGBA con margen)."""
    m = 60
    out = Image.new("RGBA", (w + 2 * m, h + 2 * m), (0, 0, 0, 0))
    sh = Image.new("RGBA", out.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((m, m + 18, m + w, m + h + 18), radius, fill=(0, 0, 0, 150))
    out = Image.alpha_composite(out, sh.filter(ImageFilter.GaussianBlur(26)))
    s = shot.convert("RGBA").resize((w, h), Image.LANCZOS)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius, fill=255)
    out.paste(s, (m, m), mask)
    ImageDraw.Draw(out).rounded_rectangle((m, m, m + w - 1, m + h - 1), radius, outline=(255, 255, 255, 110), width=4)
    return out, m


def text_center(d, y, s, font, fill, maxw):
    # achica la letra si no cabe en una línea
    f = font
    while d.textlength(s, font=f) > maxw and f.size > 30:
        f = ImageFont.truetype(f.path, f.size - 4)
    d.text(((W - d.textlength(s, font=f)) / 2, y), s, font=f, fill=fill)


def card(shots, title, sub, seed, fonts):
    img = background(seed)
    # título con brillo
    tf = ImageFont.truetype(os.path.join(fonts, "Roboto-Bold.ttf"), 96)
    sf = ImageFont.truetype(os.path.join(fonts, "Roboto-Regular.ttf"), 46)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    text_center(ImageDraw.Draw(glow), 120, title, tf, (120, 220, 255, 230), 980)
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(18)))
    d = ImageDraw.Draw(img)
    text_center(d, 120, title, tf, (255, 255, 255, 255), 980)
    text_center(d, 250, sub, sf, (205, 225, 255, 255), 980)
    top, bottom = 360, H - 50
    if len(shots) == 1 and shots[0].width < shots[0].height:
        h = bottom - top
        w = int(h * shots[0].width / shots[0].height)
        f, m = framed(shots[0], w, h, 46)
        img.alpha_composite(f, ((W - w) // 2 - m, top - m))
    else:
        n = len(shots)
        gap = 34
        w = 960
        h = int(w * shots[0].height / shots[0].width)
        total = n * h + (n - 1) * gap
        y = top + (bottom - top - total) // 2
        for s in shots:
            f, m = framed(s, w, h, 30)
            img.alpha_composite(f, ((W - w) // 2 - m, y - m))
            y += h + gap
    return img.convert("RGB")


def main():
    src, fonts = sys.argv[1], sys.argv[2]
    out = sys.argv[3] if len(sys.argv) > 3 else "docs/play"
    for lang in ("es", "en"):
        dest = os.path.join(out, "capturas_" + lang)
        os.makedirs(dest, exist_ok=True)
        for i, (names, tes, ses, ten, sen) in enumerate(CARDS):
            files = [os.path.join(src, "p_%s_%s.png" % (lang, n)) for n in names]
            if not all(os.path.exists(f) for f in files):
                print("falta captura para la tarjeta", i + 1, lang)
                continue
            shots = [Image.open(f).convert("RGB") for f in files]
            # sin la franja de abajo (ahí a veces aparece la barra de navegación del Android virtual)
            shots = [im.crop((0, 0, im.width, im.height - 110)) if im.height > im.width else im for im in shots]
            t, s = (tes, ses) if lang == "es" else (ten, sen)
            p = os.path.join(dest, "%d.png" % (i + 1))
            card(shots, t, s, i, fonts).save(p, optimize=True)
            print("ok", p)


if __name__ == "__main__":
    main()
