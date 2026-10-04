"""
Icon Generation Script for Jörmungandr IDE.
Processes master icon art and generates multi-resolution PNGs, ICO, and plugin icons.
"""

import os
import math
from PIL import Image, ImageDraw

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MASTER_ICON = r"C:\Users\RECLUSE\.gemini\antigravity\brain\f4139299-6dbb-4e5d-b98e-37ae60267df4\jormungandr_icon_1791110457743.jpg"

RES_DIR = os.path.join(PROJECT_ROOT, "modules", "platform-shell", "src", "main", "resources", "icons")
ASSETS_DIR = os.path.join(PROJECT_ROOT, "assets", "icons")
META_INF_DIR = os.path.join(PROJECT_ROOT, "modules", "platform-shell", "src", "main", "resources", "META-INF")

os.makedirs(RES_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)
os.makedirs(META_INF_DIR, exist_ok=True)

print(f"Loading master icon from: {MASTER_ICON}")
img = Image.open(MASTER_ICON).convert("RGBA")
w, h = img.size

# Isolate serpent on transparent background via floodfill from corners and center
# Background in image is Solarized Base3 cream (~252, 242, 215)
ImageDraw.floodfill(img, (0, 0), (0, 0, 0, 0), thresh=42)
ImageDraw.floodfill(img, (w - 1, 0), (0, 0, 0, 0), thresh=42)
ImageDraw.floodfill(img, (0, h - 1), (0, 0, 0, 0), thresh=42)
ImageDraw.floodfill(img, (w - 1, h - 1), (0, 0, 0, 0), thresh=42)
ImageDraw.floodfill(img, (w // 2, h // 2), (0, 0, 0, 0), thresh=42)

# Save master transparent PNG
master_transparent_path = os.path.join(RES_DIR, "jormungandr_master.png")
img.save(master_transparent_path, "PNG")
print(f"Saved master transparent PNG: {master_transparent_path}")

# Generate PNG resolutions
sizes = [16, 24, 32, 48, 64, 128, 256, 512]
ico_images = []

for s in sizes:
    resized = img.resize((s, s), Image.Resampling.LANCZOS)
    png_path = os.path.join(RES_DIR, f"jormungandr_{s}.png")
    resized.save(png_path, "PNG")
    print(f"Generated {png_path} ({s}x{s})")
    if s in [16, 24, 32, 48, 64, 128, 256]:
        ico_images.append(resized)

# Save primary jormungandr.png (256x256)
primary_png = os.path.join(RES_DIR, "jormungandr.png")
img.resize((256, 256), Image.Resampling.LANCZOS).save(primary_png, "PNG")
img.resize((256, 256), Image.Resampling.LANCZOS).save(os.path.join(ASSETS_DIR, "jormungandr.png"), "PNG")

# Generate 16@2x for retina / HiDPI
img.resize((32, 32), Image.Resampling.LANCZOS).save(os.path.join(RES_DIR, "jormungandr_16@2x.png"), "PNG")

# Save multi-size Windows .ico
ico_path = os.path.join(RES_DIR, "jormungandr.ico")
ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
img.resize((256, 256), Image.Resampling.LANCZOS).save(
    ico_path,
    format="ICO",
    sizes=ico_sizes
)
print(f"Generated Windows multi-size ICO: {ico_path}")

# Also copy ICO to assets/icons/ for shortcuts / installer packaging
assets_ico_path = os.path.join(ASSETS_DIR, "jormungandr.ico")
img.resize((256, 256), Image.Resampling.LANCZOS).save(
    assets_ico_path,
    format="ICO",
    sizes=ico_sizes
)
print(f"Copied ICO to assets: {assets_ico_path}")

# Also create pluginIcon.svg for IntelliJ Marketplace & Plugin Manager (40x40 standard)
svg_content = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40" width="40" height="40">
  <defs>
    <linearGradient id="serpentGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#2AA198"/>
      <stop offset="50%" stop-color="#268BD2"/>
      <stop offset="100%" stop-color="#073642"/>
    </linearGradient>
    <linearGradient id="circuitGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#B58900"/>
      <stop offset="100%" stop-color="#2AA198"/>
    </linearGradient>
  </defs>
  <!-- Background Disc (Solarized Light Base3) -->
  <rect width="40" height="40" rx="8" fill="#FDF6E3"/>
  <!-- Ouroboros Outer Body -->
  <circle cx="20" cy="20" r="14" fill="none" stroke="url(#serpentGrad)" stroke-width="4.5" stroke-linecap="round"/>
  <!-- Circuit Accent Track -->
  <circle cx="20" cy="20" r="14" fill="none" stroke="url(#circuitGrad)" stroke-width="1.2" stroke-dasharray="3,4"/>
  <!-- Serpent Head -->
  <path d="M 23 7 C 28 6 31 9 32 12 C 30 13 28 13 25 11 Z" fill="#073642"/>
  <circle cx="27" cy="9.5" r="0.8" fill="#FFFFFF"/>
  <!-- Circuit Nodes -->
  <circle cx="8" cy="24" r="1.2" fill="#268BD2"/>
  <circle cx="32" cy="24" r="1.2" fill="#B58900"/>
  <circle cx="20" cy="34" r="1.2" fill="#2AA198"/>
</svg>'''

with open(os.path.join(META_INF_DIR, "pluginIcon.svg"), "w", encoding="utf-8") as f:
    f.write(svg_content)

# Dark version
svg_dark = svg_content.replace('fill="#FDF6E3"', 'fill="#002B36"').replace('fill="#073642"', 'fill="#93A1A1"')
with open(os.path.join(META_INF_DIR, "pluginIcon_dark.svg"), "w", encoding="utf-8") as f:
    f.write(svg_dark)

print("Saved pluginIcon.svg and pluginIcon_dark.svg")
print("Icon generation completed successfully!")
