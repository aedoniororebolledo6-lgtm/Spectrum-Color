#!/usr/bin/env python3
"""Aplica los niveles que el editor de la app publica como aviso (issue) de GitHub.

El cuerpo trae líneas `L<n> <datos>` (datos = zlib + base64 url-safe). Cada nivel se valida línea por línea
y se guarda como app/src/main/assets/levels/<n>.txt. Uso: ISSUE_BODY=... python3 tools/apply_levels.py
"""
import base64, os, re, sys, zlib

NUM = r"-?\d+(?:\.\d+)?(?:E-?\d+)?"
LINE = re.compile(
    r"^(?:S -?\d+ " + NUM + r"|P -?\d+|O(?: " + NUM + r"){12}|W " + NUM + r" \d|Z " + NUM + " " + NUM + r")$"
)


def check(text):
    obs = 0
    fin = None
    for ln in text.split("\n"):
        ln = ln.strip()
        if not ln:
            continue
        if not LINE.match(ln):
            return "línea no válida: " + ln[:60]
        if ln[0] == "O":
            obs += 1
        if ln[0] == "S":
            fin = float(ln.split()[2])
    if obs < 1 or obs > 200:
        return "cantidad de obstáculos no válida"
    if fin is None or fin >= 0:
        return "meta no válida"
    return None


def main():
    body = os.environ.get("ISSUE_BODY", "")
    out = "app/src/main/assets/levels"
    os.makedirs(out, exist_ok=True)
    done = []
    for raw in body.splitlines():
        m = re.match(r"^L(\d{1,3}) ([A-Za-z0-9_-]+)\s*$", raw.strip())
        if not m:
            continue
        n = int(m.group(1))
        if not 1 <= n <= 400:
            print("nivel fuera de rango:", n)
            continue
        b64 = m.group(2)
        try:
            text = zlib.decompress(base64.urlsafe_b64decode(b64 + "=" * (-len(b64) % 4)), 15, 200000).decode("utf-8")
        except Exception as e:  # noqa: BLE001
            print("nivel", n, "no se pudo leer:", e)
            continue
        err = check(text)
        if err:
            print("nivel", n, "descartado:", err)
            continue
        with open(f"{out}/{n}.txt", "w", encoding="utf-8") as f:
            f.write(text if text.endswith("\n") else text + "\n")
        done.append(n)
    print("niveles aplicados:", ",".join(map(str, done)) or "ninguno")
    gh = os.environ.get("GITHUB_OUTPUT")
    if gh:
        with open(gh, "a") as f:
            f.write("levels=" + ",".join(map(str, done)) + "\n")
    sys.exit(0 if done else 1)


main()
