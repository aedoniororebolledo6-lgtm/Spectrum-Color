#!/usr/bin/env python3
"""Genera los efectos y la música de Spectrum Color (WAV mono 16 bit, 22050 Hz).

Todo se sintetiza aquí, sin samples de terceros. Uso:
    python3 tools/gen_audio.py app/src/main/res/raw
"""
import sys
import wave
import numpy as np
from scipy.signal import lfilter

SR = 22050
OUT = sys.argv[1] if len(sys.argv) > 1 else "."


def note(n):
    """Frecuencia de una nota MIDI."""
    return 440.0 * 2 ** ((n - 69) / 12.0)


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env_ad(dur, a=0.003, tau=0.1):
    t = t_axis(dur)
    e = np.exp(-t / tau)
    att = np.minimum(1.0, t / max(a, 1e-4))
    return e * att


def tone(f, dur, tau=0.1, a=0.003, partials=((1, 1.0),)):
    t = t_axis(dur)
    s = np.zeros_like(t)
    for mult, amp in partials:
        s += amp * np.sin(2 * np.pi * f * mult * t)
    return s * env_ad(dur, a, tau)


def sweep(f0, f1, dur, tau=0.1, a=0.003, curve=1.0):
    t = t_axis(dur)
    x = (t / dur) ** curve
    f = f0 + (f1 - f0) * x
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * env_ad(dur, a, tau)


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    return lfilter([1 - a], [1, -a], x)


def place(buf, start, sig, g=1.0, wrap=False):
    n = len(buf)
    if not wrap:
        end = min(n, start + len(sig))
        if end > start:
            buf[start:end] += sig[: end - start] * g
        return
    idx = (start + np.arange(len(sig))) % n
    first = n - (start % n)
    if len(sig) <= first:
        s0 = start % n
        buf[s0:s0 + len(sig)] += sig * g
    else:
        s0 = start % n
        buf[s0:] += sig[:first] * g
        rest = len(sig) - first
        k = 0
        while rest > 0:
            m = min(rest, n)
            buf[:m] += sig[first + k * n: first + k * n + m] * g
            rest -= m
            k += 1


def save(name, x, peak=0.85):
    x = np.asarray(x, dtype=np.float64)
    m = np.max(np.abs(x))
    if m > 0:
        x = x / m * peak
    pcm = (x * 32767).astype(np.int16)
    with wave.open(f"{OUT}/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(name, f"{len(pcm) / SR:.2f}s", f"{len(pcm) * 2 / 1024:.0f} KB")


GLASS = ((1, 1.0), (2, 0.22), (3.01, 0.08))
BELL = ((1, 1.0), (2.76, 0.28), (5.4, 0.10))


# ------------------------------------------------------------------ efectos
def sfx():
    # toque: "pip" de cristal
    save("sfx_tap", sweep(620, 980, 0.09, tau=0.03) + 0.25 * sweep(1240, 1960, 0.09, tau=0.02), 0.7)

    # estrella: dos notas brillantes
    d = np.zeros(int(SR * 0.45))
    place(d, 0, tone(note(88), 0.3, tau=0.09, partials=BELL))
    place(d, int(SR * 0.07), tone(note(93), 0.35, tau=0.11, partials=BELL))
    save("sfx_star", d, 0.8)

    # cambiador de color: arpegio ascendente
    d = np.zeros(int(SR * 0.55))
    for i, n in enumerate([76, 80, 83, 88]):
        place(d, int(SR * 0.045 * i), tone(note(n), 0.3, tau=0.07, partials=GLASS))
    save("sfx_switch", d, 0.75)

    # choque: vidrio que se rompe
    rng = np.random.default_rng(7)
    n = int(SR * 0.6)
    t = np.arange(n) / SR
    thud = sweep(380, 70, 0.5, tau=0.14, curve=0.6)
    noise = lowpass(rng.standard_normal(int(SR * 0.3)), 5200) * np.exp(-np.arange(int(SR * 0.3)) / SR / 0.06)
    d = np.zeros(n)
    place(d, 0, thud, 0.9)
    place(d, 0, noise, 0.55)
    for k in range(6):  # esquirlas
        f = rng.uniform(1800, 4200)
        place(d, int(SR * rng.uniform(0.02, 0.18)), tone(f, 0.12, tau=0.03, partials=GLASS), 0.18)
    save("sfx_die", d, 0.85)

    # nivel completado: fanfarria
    d = np.zeros(int(SR * 1.5))
    for i, n_ in enumerate([72, 76, 79, 84, 88]):
        place(d, int(SR * 0.09 * i), tone(note(n_), 0.7, tau=0.22, partials=BELL), 0.8)
    for n_ in (72, 79, 84, 88):
        place(d, int(SR * 0.5), tone(note(n_), 0.95, tau=0.35, a=0.01, partials=GLASS), 0.5)
    save("sfx_win", d, 0.85)

    # menú
    save("sfx_click", sweep(1900, 1250, 0.045, tau=0.012, a=0.001), 0.6)
    save("sfx_back", sweep(1300, 800, 0.06, tau=0.018, a=0.001), 0.55)

    # cuenta atrás
    save("sfx_count", tone(note(76), 0.16, tau=0.07, a=0.004, partials=GLASS), 0.7)
    d = np.zeros(int(SR * 0.5))
    place(d, 0, tone(note(83), 0.45, tau=0.16, a=0.004, partials=GLASS))
    place(d, 0, tone(note(88), 0.45, tau=0.16, a=0.004, partials=GLASS), 0.6)
    save("sfx_go", d, 0.8)


# ------------------------------------------------------------------ música
def pad(freqs, dur, vol=1.0):
    t = t_axis(dur)
    s = np.zeros_like(t)
    for f in freqs:
        for det in (-0.12, 0.0, 0.12):
            s += np.sin(2 * np.pi * (f + det) * t + det * 7)
    a = np.minimum(1.0, t / 1.2)
    r = np.minimum(1.0, (dur - t) / 1.4)
    s = lowpass(s, 1400)
    return s * a * r * vol / (len(freqs) * 3)


def pluck(f, dur=0.5, tau=0.16):
    return tone(f, dur, tau=tau, a=0.004, partials=BELL)


def echo(x, bpm, taps=((0.75, 0.38), (1.5, 0.22), (2.25, 0.12))):
    out = x.copy()
    beat = int(SR * 60 / bpm)
    for beats, g in taps:
        out += np.roll(x, int(beat * beats)) * g
    return out


def track(name, bpm, chords, arp_pattern, scale, drums, vol_arp, seed):
    rng = np.random.default_rng(seed)
    beat = SR * 60 / bpm
    bar = int(round(beat * 4))
    bars = len(chords) * 2
    n = bar * bars
    pads = np.zeros(n)
    bass = np.zeros(n)
    arp = np.zeros(n)
    kick = np.zeros(n)
    hat = np.zeros(n)
    for ci, ch in enumerate(chords):
        start = ci * 2 * bar
        place(pads, start, pad([note(m) for m in ch["pad"]], 2 * bar / SR + 1.0, 1.0), wrap=True)
        for b in range(2):
            place(bass, start + b * bar, tone(note(ch["bass"]), bar / SR * 0.95, tau=0.9, a=0.02), 0.9, wrap=True)
        # arpegio sobre las notas del acorde y la escala
        notes = ch["arp"]
        step = int(beat / 2)
        for i in range(16):
            p = arp_pattern[i % len(arp_pattern)]
            if p is None:
                continue
            m = notes[p % len(notes)] + (12 if p >= len(notes) else 0)
            place(arp, start + i * step, pluck(note(m), 0.55, 0.15), 0.6 + 0.4 * rng.random(), wrap=True)
    if drums:
        for b in range(bars):
            for q in (0, 2):
                k = sweep(130, 45, 0.22, tau=0.07, a=0.001)
                place(kick, int(b * bar + q * beat), k, 1.0, wrap=True)
            for e in range(8):
                if e % 2 == 1:
                    h = lowpass(rng.standard_normal(int(SR * 0.05)), 9000) * np.exp(-np.arange(int(SR * 0.05)) / SR / 0.012)
                    place(hat, int(b * bar + e * beat / 2), h, 0.5, wrap=True)
    mix = pads * 0.55 + bass * 0.45 + echo(arp, bpm) * vol_arp
    if drums:
        mix += kick * 0.5 + hat * 0.16
    # suavizado suave para evitar picos
    mix = np.tanh(mix * 1.1)
    save(name, mix, 0.55)


def music():
    am = {"pad": [57, 60, 64, 67], "bass": 33, "arp": [69, 72, 76, 79]}
    f_ = {"pad": [53, 57, 60, 64], "bass": 29, "arp": [65, 69, 72, 76]}
    c_ = {"pad": [55, 60, 64, 67], "bass": 36, "arp": [67, 72, 76, 79]}
    g_ = {"pad": [55, 59, 62, 66], "bass": 31, "arp": [67, 71, 74, 79]}
    track(
        "music_menu", 76, [am, f_, c_, g_],
        [0, None, 2, None, 4, None, 2, None, 1, None, 3, None, 5, None, 3, None],
        None, False, 0.55, 11,
    )
    track(
        "music_game", 112, [am, f_, c_, g_],
        [0, 2, 4, 2, 1, 3, 5, 3, 0, 2, 4, 6, 5, 3, 2, 1],
        None, True, 0.5, 23,
    )


if __name__ == "__main__":
    sfx()
    music()
