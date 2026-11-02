# -*- coding: utf-8 -*-
"""
Play Console 用スクリーンショットの検証・リサイズ。

使い方:
  python prepare_play_screenshots.py check  *.png
  python prepare_play_screenshots.py fix    input.png output.png
  python prepare_play_screenshots.py normalize input.png output.png
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    raise SystemExit("Pillow が必要です: pip install pillow")

MIN_SIDE = 320
MAX_SIDE = 3840
MAX_BYTES = 8 * 1024 * 1024
RATIOS = (16 / 9, 9 / 16)
RATIO_TOL = 0.02


def aspect_ok(w: int, h: int) -> bool:
    """16:9 または 9:16 か判定する。"""
    if h == 0:
        return False
    r = w / h
    return any(abs(r - target) <= RATIO_TOL for target in RATIOS)


def validate(path: Path) -> list[str]:
    """1枚の画像を Play 要件で検証し、問題リストを返す。"""
    issues: list[str] = []
    if path.stat().st_size > MAX_BYTES:
        issues.append(f"8 MB 超過 ({path.stat().st_size} bytes)")
    with Image.open(path) as img:
        w, h = img.size
        if w < MIN_SIDE or h < MIN_SIDE:
            issues.append(f"短辺が {MIN_SIDE}px 未満 ({w}x{h})")
        if w > MAX_SIDE or h > MAX_SIDE:
            issues.append(f"長辺が {MAX_SIDE}px 超過 ({w}x{h})")
        if not aspect_ok(w, h):
            issues.append(f"アスペクト比が 16:9 / 9:16 でない ({w}x{h})")
        if img.format not in ("PNG", "JPEG", None):
            issues.append(f"形式: {img.format}")
        expected_format = {
            ".png": "PNG",
            ".jpg": "JPEG",
            ".jpeg": "JPEG",
        }.get(path.suffix.lower())
        if expected_format and img.format != expected_format:
            issues.append(
                f"拡張子 {path.suffix.lower()} と実形式 {img.format} が不一致"
            )
    return issues


def normalize_format(src: Path, dst: Path) -> None:
    """
    画像の寸法を維持したまま、出力先の拡張子に対応する形式で保存する。

    引数:
        src: 変換元の画像パス。
        dst: PNG、JPG、JPEG のいずれかを拡張子に持つ出力先パス。

    戻り値:
        なし。
    """
    output_format = {
        ".png": "PNG",
        ".jpg": "JPEG",
        ".jpeg": "JPEG",
    }.get(dst.suffix.lower())
    if output_format is None:
        raise ValueError(f"未対応の出力拡張子です: {dst.suffix}")

    with Image.open(src) as img:
        converted = img.convert("RGB")
        if src.resolve() == dst.resolve():
            temp = dst.with_name(f"{dst.stem}.normalize-tmp{dst.suffix}")
            try:
                converted.save(temp, output_format, optimize=True)
                temp.replace(dst)
            finally:
                temp.unlink(missing_ok=True)
        else:
            dst.parent.mkdir(parents=True, exist_ok=True)
            converted.save(dst, output_format, optimize=True)


def fix_to_9_16(src: Path, dst: Path, long_side: int = 1080) -> None:
    """
    画像を 9:16 に収め、letterbox なしで中央クロップして保存する。
    """
    target_w, target_h = 9, 16
    if long_side == 1080:
        out_w, out_h = 1080, 1920
    else:
        out_h = long_side
        out_w = int(out_h * target_w / target_h)

    with Image.open(src) as img:
        img = img.convert("RGB")
        w, h = img.size
        target_ratio = target_w / target_h
        current = w / h
        if current > target_ratio:
            new_w = int(h * target_ratio)
            left = (w - new_w) // 2
            img = img.crop((left, 0, left + new_w, h))
        else:
            new_h = int(w / target_ratio)
            top = (h - new_h) // 2
            img = img.crop((0, top, w, top + new_h))
        img = img.resize((out_w, out_h), Image.Resampling.LANCZOS)
        dst.parent.mkdir(parents=True, exist_ok=True)
        img.save(dst, "PNG", optimize=True)


def pad_to_9_16(src: Path, dst: Path, long_side: int = 1920) -> None:
    """画像を縮小せず、9:16 キャンバス中央に配置する（UI を切らない）。"""
    out_w, out_h = 1080, 1920
    if long_side != 1920:
        out_h = long_side
        out_w = int(out_h * 9 / 16)

    with Image.open(src) as img:
        img = img.convert("RGB")
        scale = min(out_w / img.width, out_h / img.height)
        nw = int(img.width * scale)
        nh = int(img.height * scale)
        resized = img.resize((nw, nh), Image.Resampling.LANCZOS)
        canvas = Image.new("RGB", (out_w, out_h), (245, 243, 255))
        x = (out_w - nw) // 2
        y = (out_h - nh) // 2
        canvas.paste(resized, (x, y))
        dst.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(dst, "PNG", optimize=True)


def main() -> None:
    """コマンド check / fix を実行する。"""
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    cmd = sys.argv[1]
    if cmd == "check":
        paths = [Path(p) for p in sys.argv[2:]]
        if not paths:
            print("ファイルを指定してください")
            sys.exit(1)
        ok_all = True
        for p in paths:
            if not p.exists():
                print(f"[NG] {p} がありません")
                ok_all = False
                continue
            issues = validate(p)
            if issues:
                ok_all = False
                print(f"[NG] {p}")
                for i in issues:
                    print(f"  - {i}")
            else:
                with Image.open(p) as img:
                    print(f"[OK] {p}  {img.size[0]}x{img.size[1]}")
        sys.exit(0 if ok_all else 2)
    if cmd == "pad" and len(sys.argv) == 4:
        pad_to_9_16(Path(sys.argv[2]), Path(sys.argv[3]))
        print(f"Saved: {sys.argv[3]}")
        issues = validate(Path(sys.argv[3]))
        if issues:
            print("警告:", issues)
        sys.exit(0)
    if cmd == "fix" and len(sys.argv) == 4:
        fix_to_9_16(Path(sys.argv[2]), Path(sys.argv[3]))
        print(f"Saved: {sys.argv[3]}")
        issues = validate(Path(sys.argv[3]))
        if issues:
            print("警告:", issues)
        sys.exit(0)
    if cmd == "normalize" and len(sys.argv) == 4:
        normalize_format(Path(sys.argv[2]), Path(sys.argv[3]))
        print(f"Saved: {sys.argv[3]}")
        issues = validate(Path(sys.argv[3]))
        if issues:
            print("警告:", issues)
        sys.exit(0)
    print(__doc__)
    sys.exit(1)


if __name__ == "__main__":
    main()
