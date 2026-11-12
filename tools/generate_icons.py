#!/usr/bin/env python3
"""Generates DairyFlow launcher icons (emerald gradient + milk droplet)."""
from PIL import Image, ImageDraw, ImageFilter
import math, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')

EMERALD_DARK = (6, 78, 59)      # #064E3B
EMERALD      = (5, 150, 105)    # #059669
EMERALD_LIGHT= (16, 185, 129)   # #10B981
MINT         = (52, 211, 153)   # #34D399
WHITE        = (255, 255, 255)

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def gradient_rounded(size, radius_ratio=0.225):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    grad = Image.new('RGBA', (size, size))
    d = ImageDraw.Draw(grad)
    for y in range(size):
        t = y / max(1, size - 1)
        if t < 0.5:
            col = lerp(EMERALD_LIGHT, EMERALD, t / 0.5)
        else:
            col = lerp(EMERALD, EMERALD_DARK, (t - 0.5) / 0.5)
        d.line([(0, y), (size, y)], fill=col + (255,))
    # diagonal sheen
    sheen = Image.new('L', (size, size), 0)
    ds = ImageDraw.Draw(sheen)
    ds.ellipse([-size * 0.55, -size * 0.75, size * 0.75, size * 0.35], fill=70)
    grad.putalpha(255)
    r = int(size * radius_ratio)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    img.paste(grad, (0, 0), mask)
    return img, mask

def draw_droplet(img, size):
    d = ImageDraw.Draw(img)
    cx = size / 2
    top = size * 0.16
    bottom = size * 0.86
    w = size * 0.30  # half width at belly
    cy = size * 0.60  # belly center
    r = size * 0.255  # belly radius
    pts = []
    # teardrop: circle bottom + quadratic-ish side curves to apex
    steps = 40
    for i in range(steps + 1):
        t = i / steps
        ang = math.pi + t * math.pi  # from left (pi) to right (2pi) through top? we want sides
        # parametric bezier from apex to belly via control points
        # left side: apex -> left belly
        pass
    # simpler: build polygon: apex at top, bezier down both sides to circle
    def bez(p0, p1, p2, n=48):
        out = []
        for i in range(n + 1):
            t = i / n
            x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
            y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
            out.append((x, y))
        return out
    apex = (cx, top)
    belly_left = (cx - r, cy)
    belly_right = (cx + r, cy)
    ctrl_l = (cx - w * 0.9, size * 0.34)
    ctrl_r = (cx + w * 0.9, size * 0.34)
    poly = bez(apex, ctrl_l, belly_left) + bez(belly_left, (cx - r, size * 0.78), (cx, bottom))[1:] \
         + bez((cx, bottom), (cx + r, size * 0.78), belly_right)[1:] \
         + bez(belly_right, ctrl_r, apex)[1:]
    d.polygon(poly, fill=WHITE + (255,))
    # inner shine
    shine = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ds = ImageDraw.Draw(shine)
    ds.ellipse([cx - r * 0.55, cy - r * 0.30, cx - r * 0.05, cy + r * 0.45], fill=(255, 255, 255, 90))
    shine = shine.filter(ImageFilter.GaussianBlur(size * 0.02))
    img.alpha_composite(shine)
    # small wave inside droplet (milk line)
    dw = ImageDraw.Draw(img)
    wy = size * 0.665
    amp = size * 0.028
    wl = size * 0.46
    pts2 = []
    for i in range(101):
        x = cx - wl / 2 + wl * i / 100
        y = wy + math.sin(i / 100 * math.pi * 2) * amp
        pts2.append((x, y))
    dw.polygon(pts2 + [(cx + wl / 2, bottom), (cx - wl / 2, bottom)], fill=(219, 234, 254, 235))

def icon(size):
    img, _ = gradient_rounded(size)
    draw_droplet(img, size)
    return img

sizes = {'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96, 'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192}
for folder, px in sizes.items():
    img = icon(px)
    d = os.path.join(OUT, folder)
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, 'ic_launcher.png'))
    img.save(os.path.join(d, 'ic_launcher_round.png'))
print('icons written')
