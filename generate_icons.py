import math
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_master_icon(size=1024, is_round=False):
    # Master high-res canvas
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    
    # 1. Background Mask
    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    if is_round:
        pad = int(size * 0.04)
        mask_draw.ellipse([pad, pad, size - pad, size - pad], fill=255)
    else:
        pad = int(size * 0.04)
        radius = int(size * 0.22)
        mask_draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=radius, fill=255)

    # 2. Gradient background (Deep Premium Charcoal / Slate)
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)
    for y in range(size):
        f = y / float(size)
        r = int(14 + f * 14)
        g = int(18 + f * 18)
        b = int(26 + f * 26)
        bg_draw.line([(0, y), (size, y)], fill=(r, g, b, 255))
        
    # Subtle radial glow behind camera
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    cx, cy = size // 2, int(size * 0.53)
    glow_r = int(size * 0.42)
    for r_step in range(glow_r, 0, -8):
        alpha = int(45 * (1.0 - r_step / float(glow_r)))
        glow_draw.ellipse([cx - r_step, cy - r_step, cx + r_step, cy + r_step], fill=(0, 160, 255, alpha))
    bg = Image.alpha_composite(bg, glow)

    # Paste masked background
    img.paste(bg, (0, 0), mask)

    # Subtle border highlight
    border_mask = Image.new("L", (size, size), 0)
    b_draw = ImageDraw.Draw(border_mask)
    if is_round:
        b_draw.ellipse([pad, pad, size - pad, size - pad], outline=255, width=int(size * 0.015))
    else:
        b_draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=radius, outline=255, width=int(size * 0.015))
    border_img = Image.new("RGBA", (size, size), (255, 255, 255, 35))
    img.paste(border_img, (0, 0), border_mask)

    draw = ImageDraw.Draw(img)

    # 3. Camera Body
    # Top Viewfinder bump
    vf_w = int(size * 0.32)
    vf_h = int(size * 0.12)
    vf_top = int(size * 0.20)
    draw.rounded_rectangle(
        [cx - vf_w // 2, vf_top, cx + vf_w // 2, vf_top + vf_h + 30],
        radius=int(size * 0.035),
        fill=(28, 33, 44, 255)
    )
    # Hotshoe mount
    hs_w = int(size * 0.16)
    hs_h = int(size * 0.025)
    draw.rounded_rectangle(
        [cx - hs_w // 2, vf_top - hs_h + 6, cx + hs_w // 2, vf_top + 6],
        radius=int(size * 0.008),
        fill=(68, 76, 92, 255)
    )

    # Shutter button on right grip
    sb_w = int(size * 0.10)
    sb_h = int(size * 0.032)
    sb_x = cx + int(size * 0.28)
    body_top = int(size * 0.27)
    draw.rounded_rectangle(
        [sb_x - sb_w // 2, body_top - sb_h + 8, sb_x + sb_w // 2, body_top + 8],
        radius=int(size * 0.012),
        fill=(220, 225, 235, 255)
    )

    # Camera Main Body
    body_w = int(size * 0.78)
    body_h = int(size * 0.50)
    body_left = cx - body_w // 2
    draw.rounded_rectangle(
        [body_left, body_top, body_left + body_w, body_top + body_h],
        radius=int(size * 0.08),
        fill=(22, 26, 36, 255),
        outline=(52, 60, 78, 255),
        width=int(size * 0.012)
    )

    # Grip texture (left side)
    grip_x = body_left + int(size * 0.05)
    draw.rounded_rectangle(
        [grip_x, body_top + int(body_h * 0.12), grip_x + int(size * 0.025), body_top + int(body_h * 0.88)],
        radius=int(size * 0.01),
        fill=(36, 42, 56, 255)
    )

    # Tether Active / Sync LED (Cyan/Green neon)
    led_x = body_left + int(body_w * 0.86)
    led_y = body_top + int(body_h * 0.22)
    led_r = int(size * 0.022)
    draw.ellipse([led_x - led_r, led_y - led_r, led_x + led_r, led_y + led_r], fill=(0, 230, 195, 255))
    draw.ellipse([led_x - led_r - 4, led_y - led_r - 4, led_x + led_r + 4, led_y + led_r + 4], outline=(0, 230, 195, 120), width=3)

    # 4. Outer Lens Barrel
    lens_r = int(size * 0.25)
    draw.ellipse(
        [cx - lens_r, cy - lens_r, cx + lens_r, cy + lens_r],
        fill=(14, 17, 24, 255),
        outline=(65, 75, 96, 255),
        width=int(size * 0.014)
    )

    # 5. Sony Alpha Signature Metallic Orange Mount Ring
    mount_r = int(size * 0.225)
    mount_w = int(size * 0.032)
    for i in range(mount_w):
        cur_r = mount_r - i
        f = i / float(mount_w)
        ro = int(255 - f * 25)
        go = int(95 - f * 35)
        bo = int(0 + f * 5)
        draw.ellipse(
            [cx - cur_r, cy - cur_r, cx + cur_r, cy + cur_r],
            outline=(ro, go, bo, 255),
            width=2
        )

    # 6. Deep Glass Element with Optical Sheen
    glass_r = int(size * 0.18)
    glass_img = Image.new("RGBA", (glass_r * 2, glass_r * 2), (0, 0, 0, 0))
    g_draw = ImageDraw.Draw(glass_img)
    for y in range(glass_r * 2):
        f = y / float(glass_r * 2)
        rg = int(10 + f * 15)
        gg = int(20 + f * 35)
        bg_val = int(38 + f * 55)
        g_draw.line([(0, y), (glass_r * 2, y)], fill=(rg, gg, bg_val, 255))
        
    g_mask = Image.new("L", (glass_r * 2, glass_r * 2), 0)
    ImageDraw.Draw(g_mask).ellipse([0, 0, glass_r * 2, glass_r * 2], fill=255)
    img.paste(glass_img, (cx - glass_r, cy - glass_r), g_mask)

    # Inner lens ring border
    draw.ellipse(
        [cx - glass_r, cy - glass_r, cx + glass_r, cy + glass_r],
        outline=(15, 60, 90, 220),
        width=int(size * 0.008)
    )

    # Optical light reflection arc
    draw.arc(
        [cx - glass_r + 16, cy - glass_r + 16, cx + glass_r - 16, cy + glass_r - 16],
        start=205, end=310,
        fill=(120, 225, 255, 160),
        width=int(size * 0.016)
    )

    # 7. Greek letter Alpha (α) in center
    try:
        font = ImageFont.truetype("segoeui.ttf", int(size * 0.22))
    except:
        font = ImageFont.truetype("arial.ttf", int(size * 0.22))
        
    alpha_text = "α"
    bbox = font.getbbox(alpha_text)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    tx = cx - tw // 2 - bbox[0]
    ty = cy - th // 2 - bbox[1] - int(size * 0.015)
    
    # Shadow for Alpha
    draw.text((tx + 2, ty + 3), alpha_text, font=font, fill=(0, 0, 0, 160))
    # Crisp white Alpha
    draw.text((tx, ty), alpha_text, font=font, fill=(255, 255, 255, 250))

    # Fast tether lightning/sync symbol under Alpha
    sync_w = int(size * 0.09)
    sync_h = int(size * 0.04)
    sync_y = cy + int(size * 0.10)
    # Glowing orange dot
    draw.ellipse(
        [cx - 8, sync_y - 8, cx + 8, sync_y + 8],
        fill=(255, 110, 0, 255)
    )
    draw.ellipse(
        [cx - 14, sync_y - 14, cx + 14, sync_y + 14],
        outline=(255, 140, 20, 140),
        width=3
    )

    return img

def export_all():
    os.makedirs("app_icons", exist_ok=True)
    master_sq = create_master_icon(1024, is_round=False)
    master_rd = create_master_icon(1024, is_round=True)
    
    master_sq.save("app_icons/ic_launcher_512.png")
    master_rd.save("app_icons/ic_launcher_round_512.png")

    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }

    base_res = r"app\src\main\res"
    for dens, px in densities.items():
        folder = os.path.join(base_res, f"mipmap-{dens}")
        os.makedirs(folder, exist_ok=True)

        sq = master_sq.resize((px, px), Image.Resampling.LANCZOS)
        rd = master_rd.resize((px, px), Image.Resampling.LANCZOS)

        sq.save(os.path.join(folder, "ic_launcher.webp"), format="WEBP")
        rd.save(os.path.join(folder, "ic_launcher_round.webp"), format="WEBP")
        sq.save(os.path.join(folder, "ic_launcher.png"), format="PNG")
        rd.save(os.path.join(folder, "ic_launcher_round.png"), format="PNG")
        print(f"Exported {dens} ({px}x{px})")

if __name__ == "__main__":
    export_all()
    print("Done exporting all densities!")
