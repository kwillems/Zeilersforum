#!/usr/bin/env python3
"""
Maakt de app-iconen van de Zeilersforum-app vanuit één beschrijving:
- art/iconen/icoon-<sleutel>.svg      (ontwerp, te openen in elk tekenprogramma)
- app/src/main/res/drawable/ic_app_<sleutel>_background.xml / _foreground.xml
- app/src/main/res/mipmap-anydpi/ic_app_<sleutel>.xml (adaptive icon)
- app/src/main/res/drawable/ic_app_monochrome.xml en ic_splash_sails.xml

Gebruik (vanuit de hoofdmap van het project):  python3 art/iconen/maak_iconen.py
Pas na wijzigen ook AppIcon in ui/AppIcons.kt en de activity-aliases in AndroidManifest.xml aan
als je een variant toevoegt of weghaalt.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art" / "iconen"
RES = ROOT / "app" / "src" / "main" / "res"

# De twee gestileerde zeilen, in een vlak van 108 x 108 (middelpunt ongeveer 68,56).
SAILS = [
    "M75,16.5 C55,30 42,60 40.3,97.5 L70.3,76 Q60,48 75,16.5 Z",
    "M96.5,10.3 Q70,42 82.5,78 L46,102 Q72,95 94.7,85 Q86,50 96.5,10.3 Z",
]
SAIL_CENTER = (68, 56)
BUBBLE = "M34,28 H74 A9,9 0 0 1 83,37 V66 A9,9 0 0 1 74,75 H50 L37,85 L40,75 H34 A9,9 0 0 1 25,66 V37 A9,9 0 0 1 34,28 Z"
FULL = "M0,0 H108 V108 H0 Z"
LIGHT_BG = "#EAF2FA"


def wave(y, amp=5):
    """Golf over de hele breedte op hoogte y, met water eronder."""
    return f"M0,{y} Q13.5,{y - amp} 27,{y} T54,{y} T81,{y} T108,{y} V108 H0 Z"


def wave_small(y):
    return f"M0,{y} Q10,{y - 3} 20,{y} T40,{y} T60,{y} T80,{y} T100,{y} T120,{y} V108 H0 Z"


# Een laag: (pad, vulling, doorzichtigheid). Vulling is "#RRGGBB" of ("grad", boven, onder, y1, y2).
def grad(top, bottom, y1=0, y2=108):
    return ("grad", top, bottom, y1, y2)


# sleutel: naam, achtergrondlagen, ballon (None of lagen binnen de ballon), zeilen (schaal, dx, dy)
VARIANTS = {
    "a": ("Klassiek", [(FULL, grad("#78A8D8", "#3A6BA3"), 1)], None, (0.58, 0, 0)),
    "b": ("Diepzee", [(FULL, grad("#145A9A", "#072A4C"), 1), (wave(80), "#2B86C8", 0.55)], None, (0.54, 0, -4)),
    "c": ("Avond", [(FULL, grad("#F6A04D", "#2F5F98"), 1),
                            ("M29,42 A11,11 0 1 0 51,42 A11,11 0 1 0 29,42 Z", "#FFD27A", 0.9)], None, (0.58, 3, 0)),
    "d": ("Zee", [(FULL, "#8EC0EC", 1), (wave(68, 4), "#1E5D9C", 1)], None, (0.56, 0, -3)),
    "e": ("Ballon", [(FULL, LIGHT_BG, 1)], [(BUBBLE, grad("#3F7FC0", "#1D4F86", 28, 85), 1)], (0.42, 1, -2)),
    "f": ("Zeegroen", [(FULL, grad("#22A3B0", "#0D5868"), 1)], None, (0.58, 0, 0)),
    "ab": ("Ballon licht", [(FULL, LIGHT_BG, 1)], [(BUBBLE, grad("#78A8D8", "#3A6BA3", 28, 85), 1)], (0.42, 1, -2)),
    "db": ("Ballon zee", [(FULL, LIGHT_BG, 1)],
           [(FULL, "#8EC0EC", 1), (wave_small(60), "#1E5D9C", 1)], (0.42, 1, -2)),
}
# Ballonnen iets kleiner, zodat de punt ook in een rond icoon past.
BALLOON_SCALE = 0.95


# ---------- SVG ----------

def svg_fill(fill, gid):
    if isinstance(fill, tuple):
        _, a, b, y1, y2 = fill
        return (f'url(#{gid})',
                f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="0" y1="{y1}" x2="0" y2="{y2}">'
                f'<stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>')
    return fill, ""


def svg_layers(layers, prefix):
    out, defs = [], []
    for i, (d, fill, alpha) in enumerate(layers):
        f, g = svg_fill(fill, f"{prefix}{i}")
        defs.append(g)
        op = f' fill-opacity="{alpha}"' if alpha != 1 else ""
        out.append(f'<path d="{d}" fill="{f}"{op}/>')
    return "".join(out), "".join(defs)


def svg_sails(s, dx, dy):
    cx, cy = SAIL_CENTER
    paths = "".join(f'<path d="{p}"/>' for p in SAILS)
    return f'<g fill="#fff" transform="translate({54 + dx} {54 + dy}) scale({s}) translate({-cx} {-cy})">{paths}</g>'


def make_svg(key):
    name, bg, balloon, (s, dx, dy) = VARIANTS[key]
    body, defs = svg_layers(bg, f"{key}bg")
    fg = ""
    if balloon is not None:
        inner, d2 = svg_layers(balloon, f"{key}fg")
        defs += d2 + f'<clipPath id="{key}clip"><path d="{BUBBLE}"/></clipPath>'
        fg = f'<g clip-path="url(#{key}clip)">{inner}</g>'
    fg += svg_sails(s, dx, dy)
    if balloon is not None:
        fg = f'<g transform="translate(54 54) scale({BALLOON_SCALE}) translate(-54 -54)">{fg}</g>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">'
            f'<title>Zeilersforum-app: {name}</title><defs>{defs}</defs>{body}{fg}</svg>\n')


# ---------- Android VectorDrawable ----------

def argb(c, alpha=1.0):
    return f"#{round(alpha * 255):02X}{c.lstrip('#').upper()}"


def vd_path(d, fill, alpha=1):
    if isinstance(fill, tuple):
        _, a, b, y1, y2 = fill
        return (f'<path android:pathData="{d}"'
                + (f' android:fillAlpha="{alpha}"' if alpha != 1 else "") + '>'
                f'<aapt:attr name="android:fillColor"><gradient android:type="linear" '
                f'android:startX="0" android:startY="{y1}" android:endX="0" android:endY="{y2}" '
                f'android:startColor="{argb(a)}" android:endColor="{argb(b)}"/></aapt:attr></path>')
    return f'<path android:fillColor="{argb(fill, alpha)}" android:pathData="{d}"/>'


def vd_sails(s, dx, dy, color="#FFFFFF"):
    cx, cy = SAIL_CENTER
    paths = "".join(f'<path android:fillColor="{argb(color)}" android:pathData="{p}"/>' for p in SAILS)
    return (f'<group android:translateX="{54 + dx}" android:translateY="{54 + dy}">'
            f'<group android:scaleX="{s}" android:scaleY="{s}">'
            f'<group android:translateX="{-cx}" android:translateY="{-cy}">{paths}</group></group></group>')


def vector(body, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} Gemaakt met art/iconen/maak_iconen.py; niet met de hand aanpassen. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            f'    {body}\n</vector>\n')


def make_android(key):
    name, bg, balloon, (s, dx, dy) = VARIANTS[key]
    background = "".join(vd_path(d, f, a) for d, f, a in bg)
    fg = ""
    if balloon is not None:
        inner = "".join(vd_path(d, f, a) for d, f, a in balloon)
        fg = f'<group><clip-path android:pathData="{BUBBLE}"/>{inner}</group>'
    fg += vd_sails(s, dx, dy)
    if balloon is not None:
        fg = (f'<group android:scaleX="{BALLOON_SCALE}" android:scaleY="{BALLOON_SCALE}" '
              f'android:pivotX="54" android:pivotY="54">{fg}</group>')
    (RES / "drawable" / f"ic_app_{key}_background.xml").write_text(vector(background, f"App-icoon '{name}', achtergrond."))
    (RES / "drawable" / f"ic_app_{key}_foreground.xml").write_text(vector(fg, f"App-icoon '{name}', voorgrond."))
    (RES / "mipmap-anydpi" / f"ic_app_{key}.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<!-- App-icoon \'{name}\'. Gemaakt met art/iconen/maak_iconen.py. -->\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@drawable/ic_app_{key}_background" />\n'
        f'    <foreground android:drawable="@drawable/ic_app_{key}_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_app_monochrome" />\n'
        '</adaptive-icon>\n')


def main():
    ART.mkdir(parents=True, exist_ok=True)
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi").mkdir(parents=True, exist_ok=True)
    for key in VARIANTS:
        (ART / f"icoon-{key}.svg").write_text(make_svg(key))
        make_android(key)
    # Eenkleurig (themed icons, Android 13+) en opstartscherm: alleen de zeilen.
    (RES / "drawable" / "ic_app_monochrome.xml").write_text(
        vector(vd_sails(0.58, 0, 0, "#000000"), "Eenkleurige versie van het app-icoon (themed icons)."))
    (RES / "drawable" / "ic_splash_sails.xml").write_text(
        vector(vd_sails(0.5, 0, 0), "Zeilen op het opstartscherm."))
    print("Gemaakt:", ", ".join(VARIANTS))


if __name__ == "__main__":
    main()
