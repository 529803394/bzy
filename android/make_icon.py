from PIL import Image, ImageDraw, ImageFilter
import math, os, sys

def draw_polar_bear(draw, cx, cy, scale, outline):
    # 北极熊轮廓：头部+身体+四肢。白色填充。
    # 头 (椭圆)
    head_r = int(60 * scale)
    head = [cx - head_r, cy - head_r + int(80*scale),
            cx + head_r, cy + head_r + int(80*scale)]
    # 耳朵 (两个小半圆)
    ear_r = int(16 * scale)
    # 身体 (大椭圆)
    body_w = int(160 * scale)
    body_h = int(120 * scale)
    body = [cx - body_w, cy + int(20*scale),
            cx + body_w, cy + body_h + int(20*scale)]
    # 前腿
    leg_w = int(32 * scale)
    leg_h = int(80 * scale)
    leg1 = [cx - int(110*scale), cy + int(40*scale),
            cx - int(110*scale) + leg_w, cy + int(40*scale) + leg_h]
    leg2 = [cx - int(40*scale), cy + int(40*scale),
            cx - int(40*scale) + leg_w, cy + int(40*scale) + leg_h]
    leg3 = [cx + int(20*scale), cy + int(40*scale),
            cx + int(20*scale) + leg_w, cy + int(40*scale) + leg_h]
    leg4 = [cx + int(90*scale), cy + int(40*scale),
            cx + int(90*scale) + leg_w, cy + int(40*scale) + leg_h]

    white = (250, 250, 252, 255)
    # 身体
    draw.ellipse(body, fill=white)
    # 头
    draw.ellipse(head, fill=white)
    # 耳朵（头两侧）
    ear1 = [cx - head_r + int(10*scale), cy - head_r + int(70*scale),
            cx - head_r + int(40*scale), cy - head_r + int(110*scale)]
    ear2 = [cx + head_r - int(40*scale), cy - head_r + int(70*scale),
            cx + head_r - int(10*scale), cy - head_r + int(110*scale)]
    draw.ellipse(ear1, fill=white)
    draw.ellipse(ear2, fill=white)
    # 腿
    draw.ellipse(leg1, fill=white)
    draw.ellipse(leg2, fill=white)
    draw.ellipse(leg3, fill=white)
    draw.ellipse(leg4, fill=white)

    # 轮廓线（柔和深蓝描边）
    stroke = (70, 95, 130, 255)
    draw.ellipse(body, outline=stroke, width=3)
    draw.ellipse(head, outline=stroke, width=3)

    # 眼睛 + 鼻子
    nose_r = int(8 * scale)
    eye_r = int(5 * scale)
    dark = (35, 55, 85, 255)
    # 鼻子
    draw.ellipse([cx - nose_r, cy + int(85*scale) - nose_r,
                  cx + nose_r, cy + int(85*scale) + nose_r], fill=dark)
    # 眼睛（头两侧）
    draw.ellipse([cx - int(28*scale) - eye_r, cy + int(60*scale) - eye_r,
                  cx - int(28*scale) + eye_r, cy + int(60*scale) + eye_r], fill=dark)
    draw.ellipse([cx + int(28*scale) - eye_r, cy + int(60*scale) - eye_r,
                  cx + int(28*scale) + eye_r, cy + int(60*scale) + eye_r], fill=dark)

def draw_background(draw, W, H):
    # 北极渐变天空：顶部深蓝 -> 浅蓝
    for y in range(H):
        t = y / float(H)
        r = int(180 + (80 - 180) * t)
        g = int(200 + (100 - 200) * t)
        b = int(240 + (160 - 240) * t)
        color = (r, g, b, 255)
        draw.line([(0, y), (W, y)], fill=color, width=1)

def draw_ice(draw, W, H, cx, cy_base):
    # 前景的冰山/冰面：白色 + 浅蓝阴影
    # 冰面横向大波浪
    ice_color = (245, 248, 252, 255)
    shadow = (170, 195, 220, 255)
    # 绘制冰川形状（叠加波浪）
    for i in range(4):
        y_off = cy_base + i * 8
        points = []
        for x in range(0, W + 1, 10):
            yy = y_off + int(math.sin((x + i * 30) / 60.0) * 15)
            points.append((x, yy))
        # 填充到图片底部
        points.append((W, H))
        points.append((0, H))
        color = ice_color if i == 0 else shadow
        draw.polygon(points, fill=color)

def make_icon(size, outpath):
    W = H = size
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # 背景：天空渐变
    draw_background(draw, W, H)
    # 冰面/冰山
    cy_base = int(H * 0.62)
    draw_ice(draw, W, H, W // 2, cy_base)
    # 北极熊（站在冰面上）
    scale = size / 512.0
    draw_polar_bear(draw, W // 2, int(H * 0.32), scale, None)
    # 柔和模糊
    img = img.filter(ImageFilter.SMOOTH)
    img.save(outpath, "PNG")
    print("saved", outpath, size)

outdir = "/workspace/helloworld/app/src/main/res"
# mipmap 尺寸
for folder, size in [("mipmap-mdpi", 48), ("mipmap-hdpi", 72),
                      ("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144),
                      ("mipmap-xxxhdpi", 192)]:
    d = os.path.join(outdir, folder)
    os.makedirs(d, exist_ok=True)
    make_icon(size, os.path.join(d, "ic_launcher.png"))
    make_icon(size, os.path.join(d, "ic_launcher_round.png"))
# drawable 放 192 版本
os.makedirs(os.path.join(outdir, "drawable"), exist_ok=True)
make_icon(192, os.path.join(outdir, "drawable", "ic_launcher.png"))
print("done")
