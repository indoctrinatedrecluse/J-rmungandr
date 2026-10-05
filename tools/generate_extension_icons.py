#!/usr/bin/env python3
#
# Copyright 2025–2026 indoctrinatedrecluse
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
Generate bespoke icons for Jörmungandr extensions:
- Jupyter Integration (Notebooks + Scientific Plots)
- DataFrame Viewer & Studio
- Database Analytics Suite
- Platform Shell
Generates SVGs and multi-resolution PNGs (16x16, 16@2x/32x32, 24x24, 40x40, 80x80)
for tool windows, left UI pane cell renderers, and IntelliJ Plugin Manager.
"""

import os
from PIL import Image, ImageDraw

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

# Directories
JUPYTER_RES = os.path.join(PROJECT_ROOT, "modules", "jupyter-integration", "src", "main", "resources")
DATAFRAME_RES = os.path.join(PROJECT_ROOT, "modules", "dataframe-viewer", "src", "main", "resources")
DATABASE_RES = os.path.join(PROJECT_ROOT, "modules", "database-suite", "src", "main", "resources")
SHELL_RES = os.path.join(PROJECT_ROOT, "modules", "platform-shell", "src", "main", "resources")

for base in [JUPYTER_RES, DATAFRAME_RES, DATABASE_RES, SHELL_RES]:
    os.makedirs(os.path.join(base, "icons"), exist_ok=True)
    os.makedirs(os.path.join(base, "META-INF"), exist_ok=True)

# Solarized Colors
BASE03 = (0, 43, 54, 255)       # #002B36
BASE02 = (7, 54, 66, 255)       # #073642
BASE01 = (88, 110, 117, 255)    # #586E75
BASE00 = (101, 123, 131, 255)   # #657B83
BASE0 = (131, 148, 150, 255)    # #839496
BASE1 = (147, 161, 161, 255)    # #93A1A1
BASE2 = (238, 232, 213, 255)    # #EEE8D5
BASE3 = (253, 246, 227, 255)    # #FDF6E3
YELLOW = (181, 137, 0, 255)     # #B58900
ORANGE = (203, 75, 22, 255)     # #CB4B16
RED = (220, 50, 47, 255)        # #DC322F
MAGENTA = (211, 54, 130, 255)   # #D33682
VIOLET = (108, 113, 196, 255)   # #6C71C4
BLUE = (38, 139, 210, 255)      # #268BD2
CYAN = (42, 161, 152, 255)      # #2AA198
GREEN = (133, 153, 0, 255)      # #859900
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)

# ==============================================================================
# 1. JUPYTER NOTEBOOK ICONS
# ==============================================================================
def draw_jupyter(size):
    """Draws Jupyter planetary body with orbital ring and moon satellites"""
    img = Image.new("RGBA", (size, size), CLEAR)
    draw = ImageDraw.Draw(img)
    scale = size / 40.0

    # Planetary ring ellipse (back arc)
    rx = 16.0 * scale
    ry = 6.0 * scale
    cx = size / 2.0
    cy = size / 2.0
    ring_bbox = [cx - rx, cy - ry, cx + rx, cy + ry]
    draw.arc(ring_bbox, start=190, end=350, fill=ORANGE, width=max(1, int(2.5 * scale)))

    # Central planet body
    pr = 7.5 * scale
    planet_bbox = [cx - pr, cy - pr, cx + pr, cy + pr]
    draw.ellipse(planet_bbox, fill=MAGENTA, outline=VIOLET, width=max(1, int(1.0 * scale)))

    # Planet highlight
    hr = 2.2 * scale
    hx = cx - 2.0 * scale
    hy = cy - 2.0 * scale
    draw.ellipse([hx - hr, hy - hr, hx + hr, hy + hr], fill=(255, 255, 255, 140))

    # Planetary ring ellipse (front arc)
    draw.arc(ring_bbox, start=10, end=170, fill=ORANGE, width=max(1, int(2.5 * scale)))

    # Satellite moons
    dot1_r = 1.8 * scale
    draw.ellipse([cx - 11 * scale - dot1_r, cy - 6 * scale - dot1_r,
                  cx - 11 * scale + dot1_r, cy - 6 * scale + dot1_r], fill=ORANGE)
    draw.ellipse([cx + 10 * scale - dot1_r, cy + 6 * scale - dot1_r,
                  cx + 10 * scale + dot1_r, cy + 6 * scale + dot1_r], fill=CYAN)
    draw.ellipse([cx - dot1_r, cy + 11 * scale - dot1_r,
                  cx + dot1_r, cy + 11 * scale + dot1_r], fill=YELLOW)
    return img

def draw_jupyter_card(size, dark=False):
    bg = BASE03 if dark else BASE3
    img = Image.new("RGBA", (size, size), CLEAR)
    draw = ImageDraw.Draw(img)
    r = int(size * 0.2)
    draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=bg)
    icon = draw_jupyter(int(size * 0.85))
    offset = int(size * 0.075)
    img.alpha_composite(icon, (offset, offset))
    return img

# ==============================================================================
# 2. SCIENTIFIC PLOTS ICONS
# ==============================================================================
def draw_plots(size):
    """Draws Cartesian chart axes with analytical curves and scatter nodes"""
    img = Image.new("RGBA", (size, size), CLEAR)
    draw = ImageDraw.Draw(img)
    scale = size / 40.0

    # L-frame axes
    ax_x0 = 6.0 * scale
    ax_y0 = 6.0 * scale
    ax_x1 = 34.0 * scale
    ax_y1 = 34.0 * scale
    lw = max(1, int(2.0 * scale))

    # Y-axis and X-axis
    draw.line([(ax_x0, ax_y0), (ax_x0, ax_y1)], fill=BASE00, width=lw)
    draw.line([(ax_x0, ax_y1), (ax_x1, ax_y1)], fill=BASE00, width=lw)

    # Grid tick dashes
    for i in range(1, 4):
        gy = ax_y1 - i * 7.0 * scale
        draw.line([(ax_x0 - 1.5 * scale, gy), (ax_x0, gy)], fill=BASE1, width=1)
        gx = ax_x0 + i * 7.0 * scale
        draw.line([(gx, ax_y1), (gx, ax_y1 + 1.5 * scale)], fill=BASE1, width=1)

    # Trend curve line
    points = [
        (ax_x0 + 3.0 * scale, ax_y1 - 5.0 * scale),
        (ax_x0 + 9.0 * scale, ax_y1 - 16.0 * scale),
        (ax_x0 + 16.0 * scale, ax_y1 - 11.0 * scale),
        (ax_x0 + 23.0 * scale, ax_y1 - 24.0 * scale),
    ]
    for i in range(len(points) - 1):
        draw.line([points[i], points[i+1]], fill=CYAN, width=max(1, int(2.5 * scale)))

    # Secondary scatter points
    dot_r = 2.0 * scale
    for pt in points:
        draw.ellipse([pt[0] - dot_r, pt[1] - dot_r, pt[0] + dot_r, pt[1] + dot_r], fill=YELLOW, outline=ORANGE)
    return img

# ==============================================================================
# 3. DATAFRAME VIEWER & STUDIO ICONS
# ==============================================================================
def draw_dataframe(size):
    """Draws Tabular Grid Matrix with analytical histogram bars and trendline"""
    img = Image.new("RGBA", (size, size), CLEAR)
    draw = ImageDraw.Draw(img)
    scale = size / 40.0

    # Outer table border
    x0, y0 = 6.0 * scale, 7.0 * scale
    x1, y1 = 34.0 * scale, 33.0 * scale
    lw = max(1, int(1.8 * scale))
    draw.rounded_rectangle([x0, y0, x1, y1], radius=max(1, int(2 * scale)), outline=BASE00, width=lw)

    # Header horizontal line
    hy = y0 + 7.0 * scale
    draw.line([(x0, hy), (x1, hy)], fill=BASE00, width=lw)

    # Column vertical dividers
    c1 = x0 + 9.0 * scale
    c2 = x0 + 19.0 * scale
    draw.line([(c1, y0), (c1, y1)], fill=BASE1, width=max(1, int(1.0 * scale)))
    draw.line([(c2, y0), (c2, y1)], fill=BASE1, width=max(1, int(1.0 * scale)))

    # Analytical Histogram Bars in cell areas
    # Bar 1 (Column 1)
    b1_w = 4.5 * scale
    b1_h = 7.0 * scale
    draw.rounded_rectangle([c1 - 6.5 * scale, y1 - b1_h - 2 * scale, c1 - 2.0 * scale, y1 - 2 * scale],
                           radius=max(1, int(1 * scale)), fill=CYAN)
    # Bar 2 (Column 2)
    b2_h = 12.0 * scale
    draw.rounded_rectangle([c2 - 6.5 * scale, y1 - b2_h - 2 * scale, c2 - 2.0 * scale, y1 - 2 * scale],
                           radius=max(1, int(1 * scale)), fill=BLUE)
    # Bar 3 (Column 3)
    b3_h = 16.0 * scale
    draw.rounded_rectangle([x1 - 6.5 * scale, y1 - b3_h - 2 * scale, x1 - 2.0 * scale, y1 - 2 * scale],
                           radius=max(1, int(1 * scale)), fill=GREEN)

    # Trend sparkline overlay
    sp_pts = [
        (c1 - 4.0 * scale, y1 - b1_h - 4 * scale),
        (c2 - 4.0 * scale, y1 - b2_h - 4 * scale),
        (x1 - 4.0 * scale, y1 - b3_h - 4 * scale),
    ]
    for i in range(len(sp_pts) - 1):
        draw.line([sp_pts[i], sp_pts[i+1]], fill=YELLOW, width=max(1, int(2.0 * scale)))
    pr = 1.5 * scale
    draw.ellipse([sp_pts[-1][0] - pr, sp_pts[-1][1] - pr, sp_pts[-1][0] + pr, sp_pts[-1][1] + pr], fill=ORANGE)
    return img

def draw_dataframe_card(size, dark=False):
    bg = BASE03 if dark else BASE3
    img = Image.new("RGBA", (size, size), CLEAR)
    draw = ImageDraw.Draw(img)
    r = int(size * 0.2)
    draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=bg)
    icon = draw_dataframe(int(size * 0.85))
    offset = int(size * 0.075)
    img.alpha_composite(icon, (offset, offset))
    return img

# ==============================================================================
# 4. DATABASE ANALYTICS STUDIO ICONS
# ==============================================================================
def draw_database(size):
    """Draws Multi-tier Database Cylinder Storage with SQL Query Spark"""
    img = Image.new("RGBA", (size, size), CLEAR)
    draw = ImageDraw.Draw(img)
    scale = size / 40.0

    # Cylinder parameters
    cx = size / 2.0 - 2.0 * scale
    rx = 11.0 * scale
    ry = 3.6 * scale
    h_step = 7.0 * scale
    top_y = 10.0 * scale

    # Tier 3 (Bottom)
    y3 = top_y + 2 * h_step
    draw.chord([cx - rx, y3 - ry, cx + rx, y3 + ry], start=0, end=180, fill=BASE02, outline=BLUE, width=max(1, int(1.2 * scale)))
    draw.rectangle([cx - rx, y3, cx + rx, y3 + 4 * scale], fill=BASE02)
    draw.ellipse([cx - rx, y3 + 4 * scale - ry, cx + rx, y3 + 4 * scale + ry], fill=BASE02, outline=BLUE, width=max(1, int(1.2 * scale)))

    # Tier 2 (Middle)
    y2 = top_y + h_step
    draw.rectangle([cx - rx, y2, cx + rx, y2 + 4 * scale], fill=BLUE)
    draw.chord([cx - rx, y2 - ry, cx + rx, y2 + ry], start=0, end=180, fill=BLUE, outline=CYAN, width=max(1, int(1.2 * scale)))
    draw.ellipse([cx - rx, y2 + 4 * scale - ry, cx + rx, y2 + 4 * scale + ry], fill=BLUE, outline=CYAN, width=max(1, int(1.2 * scale)))

    # Tier 1 (Top)
    draw.rectangle([cx - rx, top_y, cx + rx, top_y + 4 * scale], fill=CYAN)
    draw.chord([cx - rx, top_y - ry, cx + rx, top_y + ry], start=0, end=180, fill=CYAN, outline=BASE1, width=max(1, int(1.2 * scale)))
    draw.ellipse([cx - rx, top_y - ry, cx + rx, top_y + ry], fill=BASE1, outline=BASE2, width=max(1, int(1.2 * scale)))

    # SQL Lightning Flash Query Indicator
    bolt_pts = [
        (cx + 9 * scale, 5 * scale),
        (cx + 3 * scale, 15 * scale),
        (cx + 7 * scale, 15 * scale),
        (cx + 1 * scale, 25 * scale),
        (cx + 11 * scale, 12 * scale),
        (cx + 6 * scale, 12 * scale)
    ]
    draw.polygon(bolt_pts, fill=YELLOW, outline=ORANGE)
    return img

def draw_database_card(size, dark=False):
    bg = BASE03 if dark else BASE3
    img = Image.new("RGBA", (size, size), CLEAR)
    draw = ImageDraw.Draw(img)
    r = int(size * 0.2)
    draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=bg)
    icon = draw_database(int(size * 0.85))
    offset = int(size * 0.075)
    img.alpha_composite(icon, (offset, offset))
    return img

# ==============================================================================
# 5. PLATFORM SHELL PLUGIN ICONS
# ==============================================================================
def draw_shell_card(size, dark=False):
    bg = BASE03 if dark else BASE3
    img = Image.new("RGBA", (size, size), CLEAR)
    draw = ImageDraw.Draw(img)
    r = int(size * 0.2)
    draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=bg)
    scale = size / 40.0
    cx, cy = size / 2.0, size / 2.0

    # Ouroboros ring
    ring_r = 13.5 * scale
    draw.ellipse([cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r], outline=CYAN, width=max(1, int(4.5 * scale)))
    # Inner track
    draw.ellipse([cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r], outline=YELLOW, width=max(1, int(1.2 * scale)))

    # Head
    head_pts = [
        (cx + 3 * scale, cy - 13 * scale),
        (cx + 11 * scale, cy - 14 * scale),
        (cx + 12 * scale, cy - 7 * scale),
        (cx + 5 * scale, cy - 8 * scale)
    ]
    draw.polygon(head_pts, fill=BASE02)
    draw.ellipse([cx + 8 * scale, cy - 11 * scale, cx + 10 * scale, cy - 9 * scale], fill=WHITE)

    # Orbit nodes
    dr = 1.4 * scale
    draw.ellipse([cx - 12 * scale - dr, cy + 4 * scale - dr, cx - 12 * scale + dr, cy + 4 * scale + dr], fill=BLUE)
    draw.ellipse([cx + 12 * scale - dr, cy + 4 * scale - dr, cx + 12 * scale + dr, cy + 4 * scale + dr], fill=YELLOW)
    draw.ellipse([cx - dr, cy + 13 * scale - dr, cx + dr, cy + 13 * scale + dr], fill=CYAN)
    return img

# ==============================================================================
# 6. SVG TEMPLATES (Clean, No-Raster Standard 16x16 SVGs)
# ==============================================================================
SVG_JUPYTER_16 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16">
  <!-- Back Orbital Ring Arc -->
  <path d="M 2 7.5 C 3 4, 13 4, 14 7.5" fill="none" stroke="#CB4B16" stroke-width="1.2" stroke-linecap="round"/>
  <!-- Planet Core -->
  <circle cx="8" cy="8" r="3.2" fill="#D33682" stroke="#6C71C4" stroke-width="0.6"/>
  <circle cx="7" cy="7" r="1" fill="#FFFFFF" opacity="0.6"/>
  <!-- Front Orbital Ring Arc -->
  <path d="M 14 8.5 C 13 12, 3 12, 2 8.5" fill="none" stroke="#CB4B16" stroke-width="1.2" stroke-linecap="round"/>
  <!-- Moons -->
  <circle cx="4" cy="5.5" r="0.8" fill="#CB4B16"/>
  <circle cx="12" cy="10.5" r="0.8" fill="#2AA198"/>
  <circle cx="8" cy="12.5" r="0.6" fill="#B58900"/>
</svg>"""

SVG_PLOTS_16 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16">
  <!-- Axes Frame -->
  <line x1="2.5" y1="2" x2="2.5" y2="13.5" stroke="#657B83" stroke-width="1.2"/>
  <line x1="2.5" y1="13.5" x2="14" y2="13.5" stroke="#657B83" stroke-width="1.2"/>
  <!-- Trendline Path -->
  <path d="M 3.5 11.5 L 6 7 L 9 9 L 12 4" fill="none" stroke="#2AA198" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Data Points -->
  <circle cx="6" cy="7" r="1" fill="#B58900"/>
  <circle cx="9" cy="9" r="1" fill="#B58900"/>
  <circle cx="12" cy="4" r="1.2" fill="#CB4B16"/>
</svg>"""

SVG_DATAFRAME_16 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16">
  <!-- Grid Matrix Frame -->
  <rect x="2" y="2" width="12" height="12" rx="1.5" fill="none" stroke="#657B83" stroke-width="1"/>
  <!-- Header Line -->
  <line x1="2" y1="5.5" x2="14" y2="5.5" stroke="#657B83" stroke-width="1"/>
  <!-- Columns -->
  <line x1="6" y1="2" x2="6" y2="14" stroke="#93A1A1" stroke-width="0.8"/>
  <line x1="10" y1="2" x2="10" y2="14" stroke="#93A1A1" stroke-width="0.8"/>
  <!-- Histogram Bars -->
  <rect x="3.2" y="10" width="1.6" height="3" rx="0.4" fill="#2AA198"/>
  <rect x="7.2" y="8" width="1.6" height="5" rx="0.4" fill="#268BD2"/>
  <rect x="11.2" y="6" width="1.6" height="7" rx="0.4" fill="#859900"/>
</svg>"""

SVG_DATABASE_16 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16">
  <!-- Bottom Disk -->
  <path d="M 3 10 C 3 11.5, 11 11.5, 11 10 L 11 12 C 11 13.5, 3 13.5, 3 12 Z" fill="#073642" stroke="#268BD2" stroke-width="0.8"/>
  <ellipse cx="7" cy="10" rx="4" ry="1.4" fill="#268BD2"/>
  <!-- Top Disk -->
  <path d="M 3 5 C 3 6.5, 11 6.5, 11 5 L 11 7 C 11 8.5, 3 8.5, 3 7 Z" fill="#268BD2" stroke="#2AA198" stroke-width="0.8"/>
  <ellipse cx="7" cy="5" rx="4" ry="1.4" fill="#2AA198"/>
  <!-- Golden Lightning Query Bolt -->
  <polygon points="13,2 9.5,7 11.5,7 9,12 14,6 12,6" fill="#B58900" stroke="#CB4B16" stroke-width="0.4"/>
</svg>"""

def main():
    print("Generating icons for Jörmungandr extensions...")

    # 1. Jupyter Notebooks
    jup_icon_dir = os.path.join(JUPYTER_RES, "icons")
    draw_jupyter(16).save(os.path.join(jup_icon_dir, "jupyter_16.png"), "PNG")
    draw_jupyter(32).save(os.path.join(jup_icon_dir, "jupyter_16@2x.png"), "PNG")
    draw_jupyter(24).save(os.path.join(jup_icon_dir, "jupyter_24.png"), "PNG")
    draw_jupyter(32).save(os.path.join(jup_icon_dir, "jupyter_32.png"), "PNG")
    with open(os.path.join(jup_icon_dir, "jupyter_16.svg"), "w", encoding="utf-8") as f:
        f.write(SVG_JUPYTER_16)

    # Scientific Plots
    draw_plots(16).save(os.path.join(jup_icon_dir, "plots_16.png"), "PNG")
    draw_plots(32).save(os.path.join(jup_icon_dir, "plots_16@2x.png"), "PNG")
    draw_plots(24).save(os.path.join(jup_icon_dir, "plots_24.png"), "PNG")
    draw_plots(32).save(os.path.join(jup_icon_dir, "plots_32.png"), "PNG")
    with open(os.path.join(jup_icon_dir, "plots_16.svg"), "w", encoding="utf-8") as f:
        f.write(SVG_PLOTS_16)

    # Jupyter Plugin Manager PNGs
    jup_meta_dir = os.path.join(JUPYTER_RES, "META-INF")
    draw_jupyter_card(40, dark=False).save(os.path.join(jup_meta_dir, "pluginIcon.png"), "PNG")
    draw_jupyter_card(80, dark=False).save(os.path.join(jup_meta_dir, "pluginIcon@2x.png"), "PNG")
    draw_jupyter_card(40, dark=True).save(os.path.join(jup_meta_dir, "pluginIcon_dark.png"), "PNG")
    draw_jupyter_card(80, dark=True).save(os.path.join(jup_meta_dir, "pluginIcon_dark@2x.png"), "PNG")

    # 2. DataFrame Viewer
    df_icon_dir = os.path.join(DATAFRAME_RES, "icons")
    draw_dataframe(16).save(os.path.join(df_icon_dir, "dataframe_16.png"), "PNG")
    draw_dataframe(32).save(os.path.join(df_icon_dir, "dataframe_16@2x.png"), "PNG")
    draw_dataframe(24).save(os.path.join(df_icon_dir, "dataframe_24.png"), "PNG")
    draw_dataframe(32).save(os.path.join(df_icon_dir, "dataframe_32.png"), "PNG")
    with open(os.path.join(df_icon_dir, "dataframe_16.svg"), "w", encoding="utf-8") as f:
        f.write(SVG_DATAFRAME_16)

    df_meta_dir = os.path.join(DATAFRAME_RES, "META-INF")
    draw_dataframe_card(40, dark=False).save(os.path.join(df_meta_dir, "pluginIcon.png"), "PNG")
    draw_dataframe_card(80, dark=False).save(os.path.join(df_meta_dir, "pluginIcon@2x.png"), "PNG")
    draw_dataframe_card(40, dark=True).save(os.path.join(df_meta_dir, "pluginIcon_dark.png"), "PNG")
    draw_dataframe_card(80, dark=True).save(os.path.join(df_meta_dir, "pluginIcon_dark@2x.png"), "PNG")

    # 3. Database Analytics Studio
    db_icon_dir = os.path.join(DATABASE_RES, "icons")
    draw_database(16).save(os.path.join(db_icon_dir, "database_16.png"), "PNG")
    draw_database(32).save(os.path.join(db_icon_dir, "database_16@2x.png"), "PNG")
    draw_database(24).save(os.path.join(db_icon_dir, "database_24.png"), "PNG")
    draw_database(32).save(os.path.join(db_icon_dir, "database_32.png"), "PNG")
    with open(os.path.join(db_icon_dir, "database_16.svg"), "w", encoding="utf-8") as f:
        f.write(SVG_DATABASE_16)

    db_meta_dir = os.path.join(DATABASE_RES, "META-INF")
    draw_database_card(40, dark=False).save(os.path.join(db_meta_dir, "pluginIcon.png"), "PNG")
    draw_database_card(80, dark=False).save(os.path.join(db_meta_dir, "pluginIcon@2x.png"), "PNG")
    draw_database_card(40, dark=True).save(os.path.join(db_meta_dir, "pluginIcon_dark.png"), "PNG")
    draw_database_card(80, dark=True).save(os.path.join(db_meta_dir, "pluginIcon_dark@2x.png"), "PNG")

    # 4. Platform Shell - Add Extension UI Icons & Plugin Manager PNGs
    shell_icon_dir = os.path.join(SHELL_RES, "icons")
    draw_jupyter(16).save(os.path.join(shell_icon_dir, "jupyter_16.png"), "PNG")
    draw_jupyter(24).save(os.path.join(shell_icon_dir, "jupyter_24.png"), "PNG")
    draw_jupyter(32).save(os.path.join(shell_icon_dir, "jupyter_32.png"), "PNG")
    draw_plots(16).save(os.path.join(shell_icon_dir, "plots_16.png"), "PNG")

    draw_dataframe(16).save(os.path.join(shell_icon_dir, "dataframe_16.png"), "PNG")
    draw_dataframe(24).save(os.path.join(shell_icon_dir, "dataframe_24.png"), "PNG")
    draw_dataframe(32).save(os.path.join(shell_icon_dir, "dataframe_32.png"), "PNG")

    draw_database(16).save(os.path.join(shell_icon_dir, "database_16.png"), "PNG")
    draw_database(24).save(os.path.join(shell_icon_dir, "database_24.png"), "PNG")
    draw_database(32).save(os.path.join(shell_icon_dir, "database_32.png"), "PNG")

    shell_meta_dir = os.path.join(SHELL_RES, "META-INF")
    draw_shell_card(40, dark=False).save(os.path.join(shell_meta_dir, "pluginIcon.png"), "PNG")
    draw_shell_card(80, dark=False).save(os.path.join(shell_meta_dir, "pluginIcon@2x.png"), "PNG")
    draw_shell_card(40, dark=True).save(os.path.join(shell_meta_dir, "pluginIcon_dark.png"), "PNG")
    draw_shell_card(80, dark=True).save(os.path.join(shell_meta_dir, "pluginIcon_dark@2x.png"), "PNG")

    print("All extension and plugin icons generated successfully!")

if __name__ == "__main__":
    main()
