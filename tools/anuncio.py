#!/usr/bin/env python3
"""Arma el anuncio de Color Flow (1920x1080, 30 cuadros/s, ~32 s) con las escenas filmadas cuadro a cuadro por la app
(modo filmación; ci/filmar.txt → film.zip del release "diagnostico"), títulos, intro, cierre y la música del juego.

Uso: python3 tools/anuncio.py <carpeta_film> <carpeta_fuentes_roboto> <esfera.png> <salida.mp4> [es|en]
  <carpeta_film>: subcarpetas menu, n5, n33, n75, n112, skins, adv0, adv4, jefe2, taller con 0000.jpg, 0001.jpg…
  <esfera.png>: la esfera Aurora boreal sobre fondo oscuro (captura 00_icono_a recortada al centro)
Necesita Pillow y ffmpeg.
"""
import math
import os
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 30
FILM, FONTS, ORB, OUT = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
LANG = sys.argv[5] if len(sys.argv) > 5 else "es"
MUSIC = "app/src/main/res/raw/music_game.wav"


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, "Roboto-%s.ttf" % name), size)


T = {
    "es": {
        "sub": "Una gran aventura de color", "tap": "Toca para saltar", "glass": "200 obstáculos\nde vidrio líquido",
        "lvl": "120 niveles\nen 12 mundos", "refl": "Reflejos\nal límite", "adv": "MODO AVENTURA", "advs": "Explora 12 mundos",
        "boss": "Vence a 12 jefes", "skins": "99 esferas\ny 41 estelas", "maker": "Crea tus propias salas",
        "avail": "Disponible en Google Play", "noads": "Sin anuncios · Pago único",
    },
    "en": {
        "sub": "A colorful adventure", "tap": "Tap to jump", "glass": "200 liquid-glass\nobstacles",
        "lvl": "120 levels\nacross 12 worlds", "refl": "Reflexes\nto the limit", "adv": "ADVENTURE MODE", "advs": "Explore 12 worlds",
        "boss": "Defeat 12 bosses", "skins": "99 balls\nand 41 trails", "maker": "Build your own rooms",
        "avail": "Available on Google Play", "noads": "No ads · One-time purchase",
    },
}[LANG]

# escenas: (tipo, carpeta, cuadros a usar, desde, texto)
SCENES = [
    ("intro", None, 90, 0, None),
    ("vert", "n5", 78, 0, T["tap"]),
    ("vert", "n75", 78, 10, T["glass"]),
    ("vert", "n112", 78, 10, T["lvl"]),
    ("vert", "n33", 72, 10, T["refl"]),
    ("horiz", "adv0", 105, 20, (T["adv"], T["advs"])),
    ("horiz", "adv4", 90, 20, (T["adv"], T["advs"])),
    ("horiz", "jefe2", 120, 0, (T["boss"], None)),
    ("vert", "skins", 78, 5, T["skins"]),
    ("horiz", "taller", 72, 0, (T["maker"], None)),
    ("outro", None, 120, 0, None),
]
XF = 8  # cuadros de fundido entre escenas


def ease(x):
    x = max(0.0, min(1.0, x))
    return 1 - (1 - x) ** 3


def frame_path(folder, i):
    d = os.path.join(FILM, folder)
    files = sorted(f for f in os.listdir(d) if f.endswith(".jpg"))
    return os.path.join(d, files[min(i, len(files) - 1)])


_bg_cache = {}


def dark_bg(seed=0):
    if seed in _bg_cache:
        return _bg_cache[seed].copy()
    img = Image.new("RGB", (W // 4, H // 4))
    px = img.load()
    for y in range(H // 4):
        for x in range(W // 4):
            u, v = x / (W / 4), y / (H / 4)
            px[x, y] = (int(10 + 30 * u * (1 - v)), int(12 + 10 * v), int(36 + 40 * (1 - u) + 18 * v))
    img = img.resize((W, H), Image.BILINEAR).convert("RGBA")
    g = Image.new("RGBA", (W // 4, H // 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    for cx, cy, r, c in [(120, 60, 90, (53, 232, 160, 90)), (380, 200, 110, (140, 80, 255, 80)), (300, 40, 70, (255, 62, 170, 60))]:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=c)
    g = g.filter(ImageFilter.GaussianBlur(30)).resize((W, H), Image.BILINEAR)
    img = Image.alpha_composite(img, g)
    _bg_cache[seed] = img
    return img.copy()


def blurred_cover(src):
    s = src.resize((W // 6, int(W // 6 * src.height / src.width)), Image.BILINEAR)
    top = (s.height - H // 6) // 2
    s = s.crop((0, max(0, top), W // 6, max(0, top) + H // 6)).filter(ImageFilter.GaussianBlur(6))
    s = s.resize((W, H), Image.BILINEAR).convert("RGBA")
    return Image.alpha_composite(s, Image.new("RGBA", (W, H), (6, 8, 26, 150)))


def rounded(img, r):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, img.width - 1, img.height - 1), r, fill=255)
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


def glow_text(base, xy, text, f, fill, glow=(120, 220, 255), anchor="la", spacing=10, alpha=1.0):
    if alpha <= 0:
        return base
    g = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(g).multiline_text(xy, text, font=f, fill=glow + (int(200 * alpha),), anchor=anchor, spacing=spacing)
    base = Image.alpha_composite(base, g.filter(ImageFilter.GaussianBlur(16)))
    t = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(t).multiline_text(xy, text, font=f, fill=fill[:3] + (int(255 * alpha),), anchor=anchor, spacing=spacing)
    return Image.alpha_composite(base, t)


ORB_IMG = Image.open(ORB).convert("RGBA")


def orb(size):
    o = ORB_IMG.resize((size, size), Image.LANCZOS)
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse((0, 0, size - 1, size - 1), fill=255)
    m = m.filter(ImageFilter.GaussianBlur(max(1, size // 60)))
    o.putalpha(m)
    return o


def intro(i, n):
    img = dark_bg(0)
    a = ease(i / 30)
    size = int(300 + 160 * ease(i / 50))
    ob = orb(size)
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    r = size * 0.75
    ImageDraw.Draw(gl).ellipse((W / 2 - r, 430 - r, W / 2 + r, 430 + r), fill=(53, 232, 160, int(110 * a)))
    img = Image.alpha_composite(img, gl.filter(ImageFilter.GaussianBlur(60)))
    ob.putalpha(ob.getchannel("A").point(lambda v: int(v * a)))
    img.alpha_composite(ob, (W // 2 - size // 2, 430 - size // 2))
    ta = ease((i - 30) / 18)
    img = glow_text(img, (W // 2, 800 - int(20 * (1 - ta))), "Color Flow", font("Bold", 150), (255, 255, 255), anchor="mm", alpha=ta)
    sa = ease((i - 50) / 18)
    img = glow_text(img, (W // 2, 925), T["sub"], font("Medium", 58), (140, 255, 200), glow=(53, 232, 160), anchor="mm", alpha=sa)
    return img


def outro(i, n):
    img = dark_bg(1)
    a = ease(i / 18)
    size = 380
    ob = orb(size)
    ob.putalpha(ob.getchannel("A").point(lambda v: int(v * a)))
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).ellipse((330, 250, 830, 750), fill=(53, 232, 160, int(110 * a)))
    img = Image.alpha_composite(img, gl.filter(ImageFilter.GaussianBlur(60)))
    bob = int(10 * math.sin(i / 12))
    img.alpha_composite(ob, (580 - size // 2, 500 - size // 2 + bob))
    img = glow_text(img, (860, 330), "Color Flow", font("Bold", 140), (255, 255, 255), alpha=ease((i - 6) / 15))
    img = glow_text(img, (866, 520), T["avail"], font("Medium", 60), (140, 255, 200), glow=(53, 232, 160), alpha=ease((i - 16) / 15))
    img = glow_text(img, (866, 610), T["noads"], font("Regular", 44), (220, 230, 255), glow=(140, 80, 255), alpha=ease((i - 26) / 15))
    return img


def vert(folder, i, n, start, text, side):
    src = Image.open(frame_path(folder, start + i)).convert("RGB")
    img = blurred_cover(src)
    ph = 1000
    pw = int(ph * src.width / src.height)
    fr = rounded(src.resize((pw, ph), Image.LANCZOS), 40)
    x = int(W * (0.66 if side == 0 else 0.34)) - pw // 2
    y = (H - ph) // 2
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((x, y + 16, x + pw, y + ph + 16), 40, fill=(0, 0, 0, 170))
    img = Image.alpha_composite(img, sh.filter(ImageFilter.GaussianBlur(24)))
    img.alpha_composite(fr, (x, y))
    ImageDraw.Draw(img).rounded_rectangle((x, y, x + pw - 1, y + ph - 1), 40, outline=(255, 255, 255, 120), width=4)
    a = ease((i - 3) / 12)
    tx = int(W * (0.30 if side == 0 else 0.70))
    off = int(40 * (1 - a)) * (1 if side == 0 else -1)
    img = glow_text(img, (tx - off, H // 2), text, font("Bold", 96), (255, 255, 255), anchor="mm", alpha=a, spacing=16)
    return img


def horiz(folder, i, n, start, text):
    src = Image.open(frame_path(folder, start + i)).convert("RGB")
    # cubrir 1920x1080: escalar al alto y recortar los lados (también esconde los botones táctiles de las esquinas)
    s = 1.0 + 0.04 * (i / max(1, n))  # acercamiento lento
    hh = int(H * s)
    ww = int(src.width * hh / src.height)
    big = src.resize((ww, hh), Image.LANCZOS)
    left = (ww - W) // 2
    img = big.crop((left, (hh - H) // 2, left + W, (hh - H) // 2 + H)).convert("RGBA")
    title, sub = text
    a = ease((i - 3) / 12)
    pnl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(pnl)
    pw = 820 if sub else 760
    d.rounded_rectangle((70 - int(60 * (1 - a)), 790, 70 + pw - int(60 * (1 - a)), 1010), 40, fill=(8, 10, 30, int(170 * a)), outline=(255, 255, 255, int(110 * a)), width=3)
    img = Image.alpha_composite(img, pnl)
    xo = 110 - int(60 * (1 - a))
    if sub:
        img = glow_text(img, (xo, 815), title, font("Bold", 84), (255, 255, 255), alpha=a)
        img = glow_text(img, (xo + 4, 925), sub, font("Medium", 50), (140, 255, 200), glow=(53, 232, 160), alpha=a)
    else:
        img = glow_text(img, (xo, 852), title, font("Bold", 84), (255, 255, 255), alpha=a)
    return img


def render_scene(k, sc, i):
    kind, folder, n, start, text = sc
    if kind == "intro":
        return intro(i, n)
    if kind == "outro":
        return outro(i, n)
    if kind == "vert":
        side = sum(1 for s in SCENES[:k] if s[0] == "vert") % 2
        return vert(folder, i, n, start, text, side)
    return horiz(folder, i, n, start, text)


def main():
    tmp = tempfile.mkdtemp(prefix="anuncio_")
    idx = 0
    prev_tail = []
    for k, sc in enumerate(SCENES):
        n = sc[2]
        frames = [render_scene(k, sc, i) for i in range(n)]
        # fundido con el final de la escena anterior
        for j in range(min(XF, len(prev_tail))):
            t = (j + 1) / (XF + 1)
            frames[j] = Image.blend(prev_tail[j].convert("RGB"), frames[j].convert("RGB"), t).convert("RGBA")
        cut = n - XF if k < len(SCENES) - 1 else n
        for f in frames[:cut]:
            f.convert("RGB").save(os.path.join(tmp, "%05d.jpg" % idx), quality=94)
            idx += 1
        prev_tail = frames[cut:]
        print("escena", k + 1, sc[0], sc[1] or "", "listo", flush=True)
    dur = idx / FPS
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", os.path.join(tmp, "%05d.jpg"),
        "-stream_loop", "-1", "-i", MUSIC,
        "-filter_complex", "[1:a]atrim=0:%.3f,asetpts=N/SR/TB,aresample=48000,loudnorm=I=-14:TP=-1.5:LRA=11,"
        "afade=t=in:st=0:d=0.6,afade=t=out:st=%.3f:d=2.0[a]" % (dur, dur - 2.0),
        "-map", "0:v", "-map", "[a]", "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p",
        "-r", str(FPS), "-c:a", "aac", "-b:a", "192k", "-ac", "2", "-movflags", "+faststart", "-shortest", OUT,
    ], check=True)
    shutil.rmtree(tmp)
    print("ok", OUT, "%.1f s" % dur)


if __name__ == "__main__":
    main()
