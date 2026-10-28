"""Noor wallpaper: the Noor app's navy with soft teal/indigo blobs, and its crescent and gold star."""
import sys
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
img = Image.new("RGB", (W, H), "#060a17")
blobs = Image.new("RGB", (W, H), "#060a17")
d = ImageDraw.Draw(blobs)
for (x, y, r, c) in [(260, 120, 520, "#0f8f86"), (1650, 220, 560, "#3f46b8"), (300, 980, 480, "#14506e"),
                     (1150, 700, 520, "#2b2f7a"), (1780, 1040, 380, "#6b4a2e")]:
    d.ellipse((x - r, y - r, x + r, y + r), fill=c)
img = Image.blend(img, blobs.filter(ImageFilter.GaussianBlur(260)), 0.55)

# Crescent and eight-point star, as in the app icon, faint so tiles stay readable.
art = Image.new("RGBA", (W, H), (0, 0, 0, 0))
a = ImageDraw.Draw(art)
cx, cy, r = 1500, 430, 120
a.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 246, 228, 70))
a.ellipse((cx - r + 55, cy - r - 20, cx + r + 55, cy + r - 20), fill=(0, 0, 0, 0))
sx, sy = 1655, 360
for pts in ([(sx, sy - 34), (sx + 9, sy - 9), (sx + 34, sy), (sx + 9, sy + 9), (sx, sy + 34), (sx - 9, sy + 9), (sx - 34, sy), (sx - 9, sy - 9)],
            [(sx + 24, sy - 24), (sx + 6, sy), (sx + 24, sy + 24), (sx, sy + 6), (sx - 24, sy + 24), (sx - 6, sy), (sx - 24, sy - 24), (sx, sy - 6)]):
    a.polygon(pts, fill=(255, 196, 77, 110))
img.paste(art, (0, 0), art)
img.save(sys.argv[1], quality=90)
