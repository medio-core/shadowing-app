# -*- coding: utf-8 -*-
"""Play Console 用フィーチャーグラフィック 1024x500 を生成する。"""
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    raise SystemExit(
        "Pillow が必要です: pip install pillow"
    )

OUT = Path(__file__).resolve().parent.parent / "docs" / "play-store" / "feature-graphic.png"
W, H = 1024, 500
BLUE = (37, 99, 235)
WHITE = (255, 255, 255)
LIGHT = (220, 255, 255, 255)

img = Image.new("RGB", (W, H), BLUE)
draw = ImageDraw.Draw(img)

play = [(72, 130), (200, 250), (72, 370)]
draw.polygon(play, fill=WHITE)
draw.rectangle([40, 390, 240, 412], fill=WHITE)
draw.rectangle([56, 422, 224, 438], fill=WHITE)

def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    """日本語対応フォントを探して読み込む。"""
    names = []
    if bold:
        names.extend(["meiryob.ttc", "YuGothB.ttc", "msgothic.ttc"])
    else:
        names.extend(["meiryo.ttc", "YuGothM.ttc", "msgothic.ttc"])
    windir = Path("C:/Windows/Fonts")
    for name in names:
        path = windir / name
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()

title_f = load_font(48, bold=True)
sub_f = load_font(24, bold=False)

draw.text((260, 150), "シャドーイング専用プレーヤー", font=title_f, fill=WHITE)
draw.text(
    (262, 235),
    "動画と英語・翻訳字幕で、フレーズ単位のシャドーイング練習",
    font=sub_f,
    fill=LIGHT[:3],
)
draw.text(
    (262, 295),
    "区間リピート / タイミング調整 / SRT・内部字幕対応",
    font=sub_f,
    fill=LIGHT[:3],
)

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT, "PNG", optimize=True)
print(f"Saved: {OUT}")
print(f"Bytes: {OUT.stat().st_size}")
print(f"Pixels: {img.size[0]} x {img.size[1]}")
