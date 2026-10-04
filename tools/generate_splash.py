"""
Splash Screen Generator for Jörmungandr IDE.
Generates an animated, seamlessly looping 3.0-second GIF in Solarized Light theme.
"""

import os
import math
from PIL import Image, ImageDraw, ImageFont

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SPLASH_DIR = os.path.join(PROJECT_ROOT, "modules", "platform-shell", "src", "main", "resources", "splash")
ASSETS_DIR = os.path.join(PROJECT_ROOT, "assets", "splash")
ARTIFACTS_DIR = r"C:\Users\RECLUSE\.gemini\antigravity\brain\f4139299-6dbb-4e5d-b98e-37ae60267df4"

os.makedirs(SPLASH_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)

# Master serpent transparent icon
SERPENT_PATH = os.path.join(PROJECT_ROOT, "modules", "platform-shell", "src", "main", "resources", "icons", "jormungandr_master.png")
serpent_img = Image.open(SERPENT_PATH).convert("RGBA")

# Target dimensions
WIDTH = 640
HEIGHT = 410

# Animation specs: 60 frames @ 50ms = 3000ms (3.0s loop)
TOTAL_FRAMES = 60
FRAME_DURATION = 50

# Fonts
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

font_title = get_font("segoeui", 26, bold=True)
font_subtitle = get_font("segoeui", 11, bold=True)
font_tech = get_font("segoeui", 10, bold=False)
font_status = get_font("segoeui", 9, bold=False)

# Solarized Light Palette
COLOR_BG_START = (253, 246, 227)   # Base3 #FDF6E3
COLOR_BG_END = (238, 232, 213)     # Base2 #EEE8D5
COLOR_BORDER = (203, 197, 178)     # Accent border
COLOR_TEXT_TITLE = (7, 54, 66)     # Base02 #073642
COLOR_CYAN = (42, 161, 152)        # Cyan #2AA198
COLOR_BLUE = (38, 139, 210)        # Blue #268BD2
COLOR_YELLOW = (181, 137, 0)       # Yellow #B58900
COLOR_TEXT_MUTED = (101, 123, 131) # Base00 #657B83
COLOR_STATUS = (147, 161, 161)     # Base1 #93A1A1

# Resize serpent to 170x170 for center placement
serpent_size = 170
serpent_scaled = serpent_img.resize((serpent_size, serpent_size), Image.Resampling.LANCZOS)
serpent_cx = WIDTH // 2
serpent_cy = 150
serpent_x = serpent_cx - (serpent_size // 2)
serpent_y = serpent_cy - (serpent_size // 2)

# Radius of the ouroboros ring path for particles
ORBIT_RADIUS = 68

frames = []

print(f"Rendering {TOTAL_FRAMES} frames for 3.0s animated splash GIF...")

for f in range(TOTAL_FRAMES):
    t = f / TOTAL_FRAMES
    angle = 2.0 * math.pi * t

    # 1. Base gradient background
    img = Image.new("RGBA", (WIDTH, HEIGHT), (253, 246, 227, 255))
    draw = ImageDraw.Draw(img)

    # Vertical subtle gradient
    for y in range(HEIGHT):
        ratio = y / HEIGHT
        r = int(COLOR_BG_START[0] * (1 - ratio) + COLOR_BG_END[0] * ratio)
        g = int(COLOR_BG_START[1] * (1 - ratio) + COLOR_BG_END[1] * ratio)
        b = int(COLOR_BG_START[2] * (1 - ratio) + COLOR_BG_END[2] * ratio)
        draw.line([(0, y), (WIDTH, y)], fill=(r, g, b, 255))

    # Outer decorative card border
    draw.rounded_rectangle([6, 6, WIDTH - 7, HEIGHT - 7], radius=12, outline=COLOR_BORDER, width=2)
    draw.rounded_rectangle([9, 9, WIDTH - 10, HEIGHT - 10], radius=10, outline=(245, 240, 225), width=1)

    # Subtle tech grid / crosshair markers in corners
    marker_color = (215, 208, 190)
    for mx, my in [(25, 25), (WIDTH - 25, 25), (25, HEIGHT - 25), (WIDTH - 25, HEIGHT - 25)]:
        draw.line([(mx - 6, my), (mx + 6, my)], fill=marker_color, width=1)
        draw.line([(mx, my - 6), (mx, my + 6)], fill=marker_color, width=1)

    # 2. Draw subtle pulsating orbit ring behind serpent
    ring_pulse = 0.5 + 0.5 * math.sin(angle * 2.0)
    ring_color = (42, 161, 152, int(40 + 35 * ring_pulse))
    draw.ellipse(
        [serpent_cx - ORBIT_RADIUS - 6, serpent_cy - ORBIT_RADIUS - 6,
         serpent_cx + ORBIT_RADIUS + 6, serpent_cy + ORBIT_RADIUS + 6],
        outline=(38, 139, 210, 50),
        width=1
    )

    # 3. Composite Jörmungandr Serpent Emblem
    img.alpha_composite(serpent_scaled, (serpent_x, serpent_y))

    # 4. Draw orbiting luminous energy stream along the serpent ouroboros body
    # Main comet head
    head_x = serpent_cx + ORBIT_RADIUS * math.cos(angle)
    head_y = serpent_cy + ORBIT_RADIUS * math.sin(angle)

    # Comet glow layers
    draw.ellipse([head_x - 7, head_y - 7, head_x + 7, head_y + 7], fill=(42, 161, 152, 90))
    draw.ellipse([head_x - 4, head_y - 4, head_x + 4, head_y + 4], fill=(38, 139, 210, 180))
    draw.ellipse([head_x - 2, head_y - 2, head_x + 2, head_y + 2], fill=(255, 255, 255, 240))

    # Comet tail particles
    for i in range(1, 8):
        trail_angle = angle - (i * 0.12)
        tx = serpent_cx + ORBIT_RADIUS * math.cos(trail_angle)
        ty = serpent_cy + ORBIT_RADIUS * math.sin(trail_angle)
        fade = 1.0 - (i / 8.0)
        radius = max(1, int(3.5 * fade))
        trail_alpha = int(190 * fade)
        draw.ellipse([tx - radius, ty - radius, tx + radius, ty + radius], fill=(181, 137, 0, trail_alpha))

    # Secondary trailing sparkles
    for i in range(3):
        sparkle_angle = angle + math.pi + (i * 0.8)
        sx = serpent_cx + (ORBIT_RADIUS + 4 * math.sin(angle * 4 + i)) * math.cos(sparkle_angle)
        sy = serpent_cy + (ORBIT_RADIUS + 4 * math.sin(angle * 4 + i)) * math.sin(sparkle_angle)
        s_pulse = 0.5 + 0.5 * math.sin(angle * 3.0 + i)
        draw.ellipse([sx - 2, sy - 2, sx + 2, sy + 2], fill=(42, 161, 152, int(150 * s_pulse)))

    # 5. Typography: "J Ö R M U N G A N D R"
    title_text = "J Ö R M U N G A N D R"
    bbox_title = draw.textbbox((0, 0), title_text, font=font_title)
    w_title = bbox_title[2] - bbox_title[0]
    draw.text(((WIDTH - w_title) // 2, 260), title_text, font=font_title, fill=COLOR_TEXT_TITLE)

    # Subtitle: "DATA SCIENCE & ANALYTICS STUDIO"
    sub_text = "DATA SCIENCE & ANALYTICS STUDIO"
    bbox_sub = draw.textbbox((0, 0), sub_text, font=font_subtitle)
    w_sub = bbox_sub[2] - bbox_sub[0]
    draw.text(((WIDTH - w_sub) // 2, 302), sub_text, font=font_subtitle, fill=COLOR_CYAN)

    # Tech stack tag: "Python · Jupyter · Apache Arrow · DuckDB"
    tech_text = "Python  ·  Jupyter  ·  Apache Arrow  ·  DuckDB"
    bbox_tech = draw.textbbox((0, 0), tech_text, font=font_tech)
    w_tech = bbox_tech[2] - bbox_tech[0]
    draw.text(((WIDTH - w_tech) // 2, 326), tech_text, font=font_tech, fill=COLOR_TEXT_MUTED)

    # 6. Animated Progress Bar (Track & Shimmer)
    track_x1, track_x2 = 120, WIDTH - 120
    track_y = 358
    track_w = track_x2 - track_x1
    track_h = 4

    # Background track
    draw.rounded_rectangle([track_x1, track_y, track_x2, track_y + track_h], radius=2, fill=(225, 218, 200, 255))

    # Shimmering segment sweeping across track
    shimmer_len = 110
    shimmer_center = track_x1 + int(t * track_w)
    s_start = max(track_x1, shimmer_center - shimmer_len // 2)
    s_end = min(track_x2, shimmer_center + shimmer_len // 2)
    if s_end > s_start:
        draw.rounded_rectangle([s_start, track_y, s_end, track_y + track_h], radius=2, fill=COLOR_BLUE)

    # Extra sweeping lead highlight
    lead_x = min(track_x2 - 4, max(track_x1, shimmer_center + 10))
    draw.ellipse([lead_x - 3, track_y - 2, lead_x + 5, track_y + track_h + 2], fill=COLOR_YELLOW)

    # 7. Status Text (Cycling with progress)
    if f < 20:
        status_text = "Initializing Modular Extension Core..."
    elif f < 40:
        status_text = "Starting Jupyter Kernel Protocols & Python Engine..."
    else:
        status_text = "Loading Apache Arrow Virtual Grid & Database Suite..."

    # Dots animation
    dots = "." * ((f // 5) % 4)
    status_full = f"{status_text}{dots}"
    bbox_status = draw.textbbox((0, 0), status_full, font=font_status)
    w_status = bbox_status[2] - bbox_status[0]
    draw.text(((WIDTH - w_status) // 2, 375), status_full, font=font_status, fill=COLOR_STATUS)

    # Convert to RGB (required for GIF palette quantization)
    frames.append(img.convert("RGB"))

# Save animated GIF
splash_gif_path = os.path.join(SPLASH_DIR, "splash.gif")
frames[0].save(
    splash_gif_path,
    save_all=True,
    append_images=frames[1:],
    duration=FRAME_DURATION,
    loop=0,
    optimize=True
)
print(f"Generated animated splash GIF: {splash_gif_path} (size: {os.path.getsize(splash_gif_path)} bytes)")

# Also save static PNG fallback
splash_png_path = os.path.join(SPLASH_DIR, "splash.png")
frames[20].save(splash_png_path, "PNG")

# Also copy to assets/splash/ and artifacts
frames[0].save(os.path.join(ASSETS_DIR, "splash.gif"), save_all=True, append_images=frames[1:], duration=FRAME_DURATION, loop=0, optimize=True)
frames[20].save(os.path.join(ASSETS_DIR, "splash.png"), "PNG")
frames[0].save(os.path.join(ARTIFACTS_DIR, "splash.gif"), save_all=True, append_images=frames[1:], duration=FRAME_DURATION, loop=0, optimize=True)

print("Splash screen generation completed successfully!")
