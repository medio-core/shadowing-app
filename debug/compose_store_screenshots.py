# -*- coding: utf-8 -*-
"""
ストア用スクショ: 動画フレームのピクセルだけを差し替える（UI・字幕は不変）。
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = Path(
    r"C:\Users\Me\.cursor\projects\c-Users-Me-Desktop-shadowing-app\assets"
)
OUT_DIR = ROOT / "docs" / "play-store" / "screenshots"

# 初回取り込み用（assets にある元スクショ）
LEGACY_IMPORT = [
    (
        ASSETS
        / "c__Users_Me_AppData_Roaming_Cursor_User_workspaceStorage_0355a24bdd4707251143a79221d14bef_images_1000007100-547faf05-b95c-4d18-84bb-b5916e2d3e3b.png",
        "phone-01-english.png",
    ),
    (
        ASSETS
        / "c__Users_Me_AppData_Roaming_Cursor_User_workspaceStorage_0355a24bdd4707251143a79221d14bef_images_1000007101-00895811-31ee-4db6-9486-fd77737dc72a.png",
        "phone-02-translation.png",
    ),
]

# 探索範囲（460x1024 実測のゆるい枠）
SCAN_TOP = 200
SCAN_BOTTOM = 450
VIDEO_BRIGHT_MAX = 520
TITLE_BRIGHT_MAX = 80


def resolve_anime_path() -> Path:
    """アニメ差し替え用画像のパスを返す。"""
    for p in (
        ASSETS / "anime-lab-scene.png",
        ROOT / "docs" / "play-store" / "anime-lab-scene.png",
    ):
        if p.exists():
            return p
    raise FileNotFoundError("anime-lab-scene.png が見つかりません")


def find_video_rect(img: Image.Image) -> tuple[int, int, int, int]:
    """
    元画像から動画フレームの外接矩形を検出する。
    行の一定割合以上が暗い行だけを動画とみなす。
    """
    w, h = img.size
    y_end = min(SCAN_BOTTOM, h)
    row_dark_ratio_min = 0.22
    top = None
    bottom = None
    for y in range(SCAN_TOP, y_end):
        dark = 0
        for x in range(0, w, 2):
            r, g, b = img.getpixel((x, y))
            if r + g + b <= VIDEO_BRIGHT_MAX:
                dark += 1
        samples = (w + 1) // 2
        if dark / samples >= row_dark_ratio_min:
            if top is None:
                top = y
            bottom = y
    if top is None or bottom is None:
        raise RuntimeError("動画領域を検出できませんでした")

    # 下端: 中央がまだ明るい行は字幕境界なので除外
    cx = w // 2
    while bottom > top and sum(img.getpixel((cx, bottom))) > 400:
        bottom -= 1

    left, right = w, 0
    for y in range(top, bottom + 1):
        for x in range(w):
            r, g, b = img.getpixel((x, y))
            if r + g + b <= VIDEO_BRIGHT_MAX:
                left = min(left, x)
                right = max(right, x)
    return left, top, right + 1, bottom + 1


MIN_VIDEO_HEIGHT = 80


def has_video_frame(box: tuple[int, int, int, int]) -> bool:
    """動画として十分な高さがあるか。"""
    return box[3] - box[1] >= MIN_VIDEO_HEIGHT


def replace_video_only(img: Image.Image, anime: Image.Image) -> Image.Image:
    """
    検出した動画矩形だけをアニメに差し替え、タイトル帯も消す。
    """
    out = img.copy()
    box = find_video_rect(img)
    l, t, r, b = box
    vw, vh = r - l, b - t
    fitted = anime.resize((vw, vh), Image.Resampling.LANCZOS)
    out.paste(fitted, (l, t))

    # ファイル名の黒帯だけ上端を再ペースト
    title_h = 0
    for y in range(t, min(t + 50, b)):
        for x in range(l, r, max(1, (r - l) // 20)):
            r0, g0, b0 = img.getpixel((x, y))
            if r0 + g0 + b0 <= TITLE_BRIGHT_MAX:
                title_h = max(title_h, y - t + 1)
    title_h = max(title_h, 8)
    out.paste(fitted.crop((0, 0, vw, min(title_h, vh))), (l, t))
    return out


def list_targets() -> list[Path]:
    """加工対象の phone-*.png を列挙する。"""
    skip = ("-play", "promo")
    return sorted(
        p
        for p in OUT_DIR.glob("phone-*.png")
        if not any(s in p.stem for s in skip)
    )


def main() -> None:
    """phone-*.png の動画領域だけを差し替える。"""
    anime_path = resolve_anime_path()
    with Image.open(anime_path) as raw:
        anime = raw.convert("RGB")

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for src, name in LEGACY_IMPORT:
        if src.exists() and not (OUT_DIR / name).exists():
            with Image.open(src) as shot:
                (OUT_DIR / name).parent.mkdir(parents=True, exist_ok=True)
                shot.convert("RGB").save(OUT_DIR / name, "PNG")

    targets = list_targets()
    if not targets:
        print(f"対象なし: {OUT_DIR}/phone-*.png")
        return

    for path in targets:
        with Image.open(path) as shot:
            shot = shot.convert("RGB")
            try:
                box = find_video_rect(shot)
            except RuntimeError:
                print(f"[SKIP] {path.name}  動画なし")
                continue
            if not has_video_frame(box):
                print(f"[SKIP] {path.name}  動画なし（設定画面など）")
                continue
            out = replace_video_only(shot, anime)
            out.save(path, "PNG", optimize=True)
            print(f"[OK] {path.name}  box={box}")


if __name__ == "__main__":
    main()
