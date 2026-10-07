"""
High-Definition Splash Screen Generator for Jörmungandr IDE.
Generates:
  1. splash@2x.png (1280x820, 32-bit RGBA) - High-Definition Retina master
  2. splash.png    (640x410, 32-bit RGBA)  - High-Definition standard DPI
  3. splash.gif    (1280x820, 40 frames @ 75ms = 3000ms loop) - High-Definition animated GIF89a
"""

import os
import math
from PIL import Image, ImageDraw, ImageFont

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SPLASH_DIR = os.path.join(PROJECT_ROOT, "modules", "platform-shell", "src", "main", "resources", "splash")
ASSETS_DIR = os.path.join(PROJECT_ROOT, "assets", "splash")
ARTIFACTS_DIR = r"C:\Users\RECLUSE\.gemini\antigravity\brain\da496499-6d63-4486-afff-f012f0e27664"

os.makedirs(SPLASH_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)
os.makedirs(ARTIFACTS_DIR, exist_ok=True)

# Master serpent transparent icon (1024x1024)
SERPENT_PATH = os.path.join(PROJECT_ROOT, "modules", "platform-shell", "src", "main", "resources", "icons", "jormungandr_master.png")
serpent_master = Image.open(SERPENT_PATH).convert("RGBA")

# 2X High-Definition dimensions
WIDTH_2X = 1280
HEIGHT_2X = 820
WIDTH_1X = 640
HEIGHT_1X = 410

# 40 frames @ 75ms = 3000ms (3.0 seconds loop)
TOTAL_FRAMES = 40
FRAME_DURATION = 75

def get_font(name, size, bold=False):
    win_fonts = r"C:\Windows\Fonts"
    if bold:
        candidates = [os.path.join(win_fonts, "segoeuib.ttf"), os.path.join(win_fonts, "arialbd.ttf")]
    else:
        candidates = [os.path.join(win_fonts, "segoeui.ttf"), os.path.join(win_fonts, "arial.ttf")]
    for c in candidates:
        if os.path.exists(c):
            try:
                return ImageFont.truetype(c, size)
            except Exception:
                pass
    return ImageFont.load_default()

font_title_2x = get_font("segoeui", 52, bold=True)
font_subtitle_2x = get_font("segoeui", 22, bold=True)
font_tech_2x = get_font("segoeui", 20, bold=False)
font_badge_2x = get_font("segoeui", 17, bold=True)
font_status_2x = get_font("segoeui", 18, bold=False)

# Solarized Palette
COLOR_BG_START = (253, 246, 227)   # Base3 #FDF6E3
COLOR_BG_END = (238, 232, 213)     # Base2 #EEE8D5
COLOR_BORDER = (203, 197, 178)     # Accent border
COLOR_TEXT_TITLE = (7, 54, 66)     # Base02 #073642
COLOR_CYAN = (42, 161, 152)        # Cyan #2AA198
COLOR_BLUE = (38, 139, 210)        # Blue #268BD2
COLOR_YELLOW = (181, 137, 0)       # Yellow #B58900
COLOR_TEXT_MUTED = (101, 123, 131) # Base00 #657B83
COLOR_STATUS = (130, 145, 145)     # Base1 #829191

# Serpent placement at 2X HD
SERPENT_SIZE_2X = 330
serpent_scaled_2x = serpent_master.resize((SERPENT_SIZE_2X, SERPENT_SIZE_2X), Image.Resampling.LANCZOS)
serpent_cx_2x = WIDTH_2X // 2
serpent_cy_2x = 290
serpent_x_2x = serpent_cx_2x - (SERPENT_SIZE_2X // 2)
serpent_y_2x = serpent_cy_2x - (SERPENT_SIZE_2X // 2)

ORBIT_RADIUS_2X = 132

def render_frame_2x(f, total_frames=TOTAL_FRAMES):
    t = f / total_frames
    angle = 2.0 * math.pi * t

    # 1. Base smooth vertical gradient background
    img = Image.new("RGBA", (WIDTH_2X, HEIGHT_2X), (253, 246, 227, 255))
    draw = ImageDraw.Draw(img)

    for y in range(HEIGHT_2X):
        ratio = y / HEIGHT_2X
        r = int(COLOR_BG_START[0] * (1 - ratio) + COLOR_BG_END[0] * ratio)
        g = int(COLOR_BG_START[1] * (1 - ratio) + COLOR_BG_END[1] * ratio)
        b = int(COLOR_BG_START[2] * (1 - ratio) + COLOR_BG_END[2] * ratio)
        draw.line([(0, y), (WIDTH_2X, y)], fill=(r, g, b, 255))

    # Outer decorative card border with rounded corners
    draw.rounded_rectangle([12, 12, WIDTH_2X - 13, HEIGHT_2X - 13], radius=24, outline=COLOR_BORDER, width=3)
    draw.rounded_rectangle([18, 18, WIDTH_2X - 19, HEIGHT_2X - 19], radius=20, outline=(245, 240, 225), width=2)

    # Crosshair markers in corners
    marker_color = (212, 204, 184)
    for mx, my in [(50, 50), (WIDTH_2X - 50, 50), (50, HEIGHT_2X - 50), (WIDTH_2X - 50, HEIGHT_2X - 50)]:
        draw.line([(mx - 12, my), (mx + 12, my)], fill=marker_color, width=2)
        draw.line([(mx, my - 12), (mx, my + 12)], fill=marker_color, width=2)

    # Top right version badge
    badge_box = [WIDTH_2X - 250, 36, WIDTH_2X - 44, 76]
    draw.rounded_rectangle(badge_box, radius=10, fill=(235, 228, 208, 200), outline=(210, 202, 180), width=1)
    badge_text = "v2026.1 Enterprise"
    bbox_b = draw.textbbox((0, 0), badge_text, font=font_badge_2x)
    bw = bbox_b[2] - bbox_b[0]
    bh = bbox_b[3] - bbox_b[1]
    draw.text((badge_box[0] + (badge_box[2] - badge_box[0] - bw) // 2, badge_box[1] + (badge_box[3] - badge_box[1] - bh) // 2 - 2),
              badge_text, font=font_badge_2x, fill=COLOR_CYAN)

    # 2. Ambient glowing energy ring behind serpent
    ring_pulse = 0.5 + 0.5 * math.sin(angle * 2.0)
    for r_offset in range(12, 0, -2):
        alpha = int((8 + 12 * ring_pulse) * (1.0 - r_offset / 12.0))
        draw.ellipse(
            [serpent_cx_2x - ORBIT_RADIUS_2X - r_offset, serpent_cy_2x - ORBIT_RADIUS_2X - r_offset,
             serpent_cx_2x + ORBIT_RADIUS_2X + r_offset, serpent_cy_2x + ORBIT_RADIUS_2X + r_offset],
            outline=(42, 161, 152, alpha),
            width=2
        )

    # 3. Composite Jörmungandr Serpent Emblem (High-Res 330x330)
    img.alpha_composite(serpent_scaled_2x, (serpent_x_2x, serpent_y_2x))

    # 4. Orbiting energy stream along the serpent ouroboros body
    head_x = serpent_cx_2x + ORBIT_RADIUS_2X * math.cos(angle)
    head_y = serpent_cy_2x + ORBIT_RADIUS_2X * math.sin(angle)

    # Comet glow layers
    draw.ellipse([head_x - 14, head_y - 14, head_x + 14, head_y + 14], fill=(42, 161, 152, 90))
    draw.ellipse([head_x - 8, head_y - 8, head_x + 8, head_y + 8], fill=(38, 139, 210, 180))
    draw.ellipse([head_x - 4, head_y - 4, head_x + 4, head_y + 4], fill=(255, 255, 255, 240))

    # Comet tail particles
    for i in range(1, 10):
        trail_angle = angle - (i * 0.11)
        tx = serpent_cx_2x + ORBIT_RADIUS_2X * math.cos(trail_angle)
        ty = serpent_cy_2x + ORBIT_RADIUS_2X * math.sin(trail_angle)
        fade = 1.0 - (i / 10.0)
        radius = max(2, int(7 * fade))
        trail_alpha = int(210 * fade)
        draw.ellipse([tx - radius, ty - radius, tx + radius, ty + radius], fill=(181, 137, 0, trail_alpha))

    # Secondary trailing sparkles
    for i in range(4):
        sparkle_angle = angle + math.pi + (i * 0.7)
        sx = serpent_cx_2x + (ORBIT_RADIUS_2X + 8 * math.sin(angle * 4 + i)) * math.cos(sparkle_angle)
        sy = serpent_cy_2x + (ORBIT_RADIUS_2X + 8 * math.sin(angle * 4 + i)) * math.sin(sparkle_angle)
        s_pulse = 0.5 + 0.5 * math.sin(angle * 3.0 + i)
        draw.ellipse([sx - 4, sy - 4, sx + 4, sy + 4], fill=(42, 161, 152, int(160 * s_pulse)))

    # 5. Typography: "J Ö R M U N G A N D R"
    title_text = "J Ö R M U N G A N D R"
    bbox_title = draw.textbbox((0, 0), title_text, font=font_title_2x)
    w_title = bbox_title[2] - bbox_title[0]
    draw.text(((WIDTH_2X - w_title) // 2, 500), title_text, font=font_title_2x, fill=COLOR_TEXT_TITLE)

    # Subtitle: "DATA SCIENCE & ANALYTICS STUDIO"
    sub_text = "DATA SCIENCE & ANALYTICS STUDIO"
    bbox_sub = draw.textbbox((0, 0), sub_text, font=font_subtitle_2x)
    w_sub = bbox_sub[2] - bbox_sub[0]
    draw.text(((WIDTH_2X - w_sub) // 2, 574), sub_text, font=font_subtitle_2x, fill=COLOR_CYAN)

    # Tech stack tag: "Python · Jupyter · Apache Arrow · DuckDB · PyTorch · Polars"
    tech_text = "Python   ·   Jupyter   ·   Apache Arrow   ·   DuckDB   ·   PyTorch   ·   Polars"
    bbox_tech = draw.textbbox((0, 0), tech_text, font=font_tech_2x)
    w_tech = bbox_tech[2] - bbox_tech[0]
    draw.text(((WIDTH_2X - w_tech) // 2, 620), tech_text, font=font_tech_2x, fill=COLOR_TEXT_MUTED)

    # 6. High-Definition Progress Bar
    track_x1, track_x2 = 240, WIDTH_2X - 240
    track_y = 690
    track_w = track_x2 - track_x1
    track_h = 8

    # Background track
    draw.rounded_rectangle([track_x1, track_y, track_x2, track_y + track_h], radius=4, fill=(225, 218, 200, 255))

    # Shimmering segment sweeping across track
    shimmer_len = 220
    shimmer_center = track_x1 + int(t * track_w)
    s_start = max(track_x1, shimmer_center - shimmer_len // 2)
    s_end = min(track_x2, shimmer_center + shimmer_len // 2)
    if s_end > s_start:
        draw.rounded_rectangle([s_start, track_y, s_end, track_y + track_h], radius=4, fill=COLOR_BLUE)

    # Extra sweeping lead highlight
    lead_x = min(track_x2 - 8, max(track_x1, shimmer_center + 20))
    draw.ellipse([lead_x - 6, track_y - 4, lead_x + 10, track_y + track_h + 4], fill=COLOR_YELLOW)

    # 7. Status Text (Cycling with progress)
    if f < 13:
        status_text = "Initializing Modular Extension Core..."
    elif f < 27:
        status_text = "Starting Jupyter Kernel Protocols & Python Engine..."
    else:
        status_text = "Loading Apache Arrow Virtual Grid & Model Checkpoint Inspector..."

    dots = "." * ((f // 3) % 4)
    status_full = f"{status_text}{dots}"
    bbox_status = draw.textbbox((0, 0), status_full, font=font_status_2x)
    w_status = bbox_status[2] - bbox_status[0]
    draw.text(((WIDTH_2X - w_status) // 2, 725), status_full, font=font_status_2x, fill=COLOR_STATUS)

    return img

print("Generating High-Definition Splash Screen assets...")

# 1. Master splash@2x.png (Frame 15 has balanced energy and progress)
master_hd_frame = render_frame_2x(15)
splash_2x_path = os.path.join(SPLASH_DIR, "splash@2x.png")
master_hd_frame.save(splash_2x_path, "PNG", optimize=True)
master_hd_frame.save(os.path.join(ASSETS_DIR, "splash@2x.png"), "PNG", optimize=True)
master_hd_frame.save(os.path.join(ARTIFACTS_DIR, "splash@2x.png"), "PNG", optimize=True)
print(f"Generated splash@2x.png: {splash_2x_path} (1280x820)")

# 2. High-quality downscaled 1x splash.png (640x410)
splash_1x = master_hd_frame.resize((WIDTH_1X, HEIGHT_1X), Image.Resampling.LANCZOS)
splash_1x_path = os.path.join(SPLASH_DIR, "splash.png")
splash_1x.save(splash_1x_path, "PNG", optimize=True)
splash_1x.save(os.path.join(ASSETS_DIR, "splash.png"), "PNG", optimize=True)
splash_1x.save(os.path.join(ARTIFACTS_DIR, "splash.png"), "PNG", optimize=True)
print(f"Generated splash.png: {splash_1x_path} (640x410)")

# 3. High-Definition animated GIF89a (1280x820, 40 frames @ 75ms)
print(f"Rendering {TOTAL_FRAMES} frames for 2X High-Definition animated GIF...")
gif_frames = []
for f in range(TOTAL_FRAMES):
    hd_frame = render_frame_2x(f)
    gif_frames.append(hd_frame.convert("RGB"))

splash_gif_path = os.path.join(SPLASH_DIR, "splash.gif")
gif_frames[0].save(
    splash_gif_path,
    save_all=True,
    append_images=gif_frames[1:],
    duration=FRAME_DURATION,
    loop=0,
    optimize=True
)
gif_frames[0].save(
    os.path.join(ASSETS_DIR, "splash.gif"),
    save_all=True,
    append_images=gif_frames[1:],
    duration=FRAME_DURATION,
    loop=0,
    optimize=True
)
gif_frames[0].save(
    os.path.join(ARTIFACTS_DIR, "splash.gif"),
    save_all=True,
    append_images=gif_frames[1:],
    duration=FRAME_DURATION,
    loop=0,
    optimize=True
)
print(f"Generated High-Definition splash.gif: {splash_gif_path} ({os.path.getsize(splash_gif_path)} bytes)")

print("All high-definition splash screen assets generated successfully!")
