"""Draws the LanBall logo: a Wi-Fi symbol whose dot is a football. Writes SVGs, then PNGs through Edge headless.
Run: python make_logo.py"""
import math, pathlib, subprocess

here = pathlib.Path(__file__).parent
NAVY, DEEP, GOLD, AMBER, RED, BLUE, MUTED = "#161f44", "#0b1020", "#ffd166", "#f4a52a", "#ff4d5e", "#3fa9ff", "#9fb3d9"


def pentagon(cx, cy, r, turn=-90):
    return " ".join(f"{cx + r * math.cos(math.radians(turn + 72 * k)):.1f},{cy + r * math.sin(math.radians(turn + 72 * k)):.1f}"
                    for k in range(5))


def ball(cx, cy, r, tag):
    """A football: white sphere, a centre pentagon and five edge pentagons clipped to the circle, seams between them."""
    p = r * 0.34
    shapes = [f'<polygon points="{pentagon(cx, cy, p)}" fill="{DEEP}"/>']
    seams = []
    for k in range(5):
        a = math.radians(-90 + 72 * k + 36)  # edge pentagons sit between the centre pentagon's corners
        ex, ey = cx + r * 0.98 * math.cos(a), cy + r * 0.98 * math.sin(a)
        shapes.append(f'<polygon points="{pentagon(ex, ey, p * 0.95, math.degrees(a) + 180)}" fill="{DEEP}"/>')
        c = math.radians(-90 + 72 * k)  # seam from each centre corner out to the edge
        x1, y1 = cx + p * math.cos(c), cy + p * math.sin(c)
        x2, y2 = cx + r * 0.72 * math.cos(c), cy + r * 0.72 * math.sin(c)
        seams.append(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}"/>')
    return f"""
  <defs>
    <radialGradient id="shine{tag}" cx="38%" cy="32%" r="75%">
      <stop offset="0" stop-color="#ffffff"/><stop offset=".7" stop-color="#e9eef7"/><stop offset="1" stop-color="#aab6cc"/>
    </radialGradient>
    <clipPath id="clip{tag}"><circle cx="{cx}" cy="{cy}" r="{r}"/></clipPath>
  </defs>
  <ellipse cx="{cx + r * 0.08}" cy="{cy + r * 1.08}" rx="{r * 0.9}" ry="{r * 0.18}" fill="#000" opacity=".35"/>
  <circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#shine{tag})"/>
  <g clip-path="url(#clip{tag})">{''.join(shapes)}
    <g stroke="{DEEP}" stroke-width="{r * 0.035:.1f}" opacity=".55">{''.join(seams)}</g>
  </g>
  <circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{DEEP}" stroke-width="{r * 0.05:.1f}"/>"""


def wifi(cx, cy, radii, width):
    """Three gold arcs above the ball, like a Wi-Fi signal (90 degrees wide, opening upwards)."""
    arcs = []
    for i, rad in enumerate(radii):
        x1, y1 = cx - rad * math.sin(math.radians(45)), cy - rad * math.cos(math.radians(45))
        x2 = cx + rad * math.sin(math.radians(45))
        arcs.append(f'<path d="M{x1:.1f},{y1:.1f} A{rad},{rad} 0 0 1 {x2:.1f},{y1:.1f}" fill="none" stroke="url(#gold)" '
                    f'stroke-width="{width}" stroke-linecap="round" opacity="{1 - i * 0.18:.2f}"/>')
    return "".join(arcs)


def trail(cx, cy, r):
    """Speed streaks behind the ball (it flies to the right)."""
    out = []
    for i, (dy, length) in enumerate(((-0.45, 1.5), (0.0, 2.1), (0.45, 1.5))):
        y = cy + dy * r
        out.append(f'<line x1="{cx - r * 1.25 - length * r:.1f}" y1="{y:.1f}" x2="{cx - r * 1.25:.1f}" y2="{y:.1f}" '
                   f'stroke="{MUTED}" stroke-width="{r * 0.16:.1f}" stroke-linecap="round" opacity="{0.55 - i % 2 * 0.15:.2f}"/>')
    return "".join(out)


GRADIENTS = f"""
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{NAVY}"/><stop offset="1" stop-color="{DEEP}"/></linearGradient>
    <linearGradient id="gold" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffe08a"/><stop offset="1" stop-color="{AMBER}"/></linearGradient>
    <radialGradient id="flood" cx="50%" cy="38%" r="60%"><stop offset="0" stop-color="#ffffff" stop-opacity=".10"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient>
  </defs>"""


def square():
    s = 1024
    cx, cy, r = 512, 470, 118
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{s}" height="{s}" viewBox="0 0 {s} {s}">{GRADIENTS}
  <rect x="24" y="24" width="976" height="976" rx="200" fill="url(#bg)"/>
  <rect x="24" y="24" width="976" height="976" rx="200" fill="url(#flood)"/>
  <rect x="44" y="44" width="936" height="936" rx="182" fill="none" stroke="{GOLD}" stroke-opacity=".35" stroke-width="6"/>
  <circle cx="512" cy="470" r="250" fill="none" stroke="#ffffff" stroke-opacity=".07" stroke-width="10"/>
  <line x1="512" y1="80" x2="512" y2="680" stroke="#ffffff" stroke-opacity=".05" stroke-width="10"/>
  <circle cx="208" cy="600" r="44" fill="{RED}"/><circle cx="208" cy="600" r="44" fill="none" stroke="#fff" stroke-width="8"/>
  <circle cx="816" cy="600" r="44" fill="{BLUE}"/><circle cx="816" cy="600" r="44" fill="none" stroke="#fff" stroke-width="8"/>
  {wifi(cx, cy - 18, (200, 290, 380), 46)}
  {ball(cx, cy, r, "s")}
  <text x="512" y="826" text-anchor="middle" font-family="Segoe UI Black, Segoe UI, Arial Black, sans-serif" font-weight="900"
        font-size="150" letter-spacing="6" fill="url(#gold)">LANBALL</text>
  <text x="512" y="900" text-anchor="middle" font-family="Segoe UI, Arial, sans-serif" font-weight="600"
        font-size="44" letter-spacing="18" fill="{MUTED}">WI-FI FOOTBALL</text>
</svg>"""


def wide():
    w, h = 1600, 520
    cx, cy, r = 330, 340, 86
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{GRADIENTS}
  <rect width="{w}" height="{h}" rx="60" fill="url(#bg)"/>
  {trail(cx, cy, r)}
  {wifi(cx, cy - 14, (140, 200, 260), 34)}
  {ball(cx, cy, r, "w")}
  <text x="640" y="300" font-family="Segoe UI Black, Segoe UI, Arial Black, sans-serif" font-weight="900"
        font-size="190" letter-spacing="8" fill="url(#gold)">LANBALL</text>
  <text x="648" y="380" font-family="Segoe UI, Arial, sans-serif" font-weight="600" font-size="46" letter-spacing="16"
        fill="{MUTED}">WI-FI FOOTBALL · JAVA</text>
  <rect x="648" y="410" width="120" height="14" rx="7" fill="{RED}"/><rect x="780" y="410" width="120" height="14" rx="7" fill="{BLUE}"/>
</svg>"""


edge = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
profile = pathlib.Path.home() / "AppData/Local/Temp/edge-pdf-profile"
for name, svg, size in (("lanball-logo", square(), (1024, 1024)), ("lanball-logo-wide", wide(), (1600, 520))):
    (here / f"{name}.svg").write_text(svg, encoding="utf-8")
    page = here / f"{name}.html"  # transparent page so the rounded corners stay transparent in the PNG
    page.write_text(f"<!doctype html><html><body style='margin:0;background:transparent'>{svg}</body></html>", encoding="utf-8")
    subprocess.run([edge, "--headless=new", "--disable-gpu", f"--user-data-dir={profile}", "--hide-scrollbars",
                    "--default-background-color=00000000", f"--window-size={size[0]},{size[1]}",
                    f"--screenshot={here / (name + '.png')}", page.as_uri()], check=True, timeout=120)
    page.unlink()
    print("wrote", name + ".svg", name + ".png")
