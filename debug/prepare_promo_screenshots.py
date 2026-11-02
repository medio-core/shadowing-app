# -*- coding: utf-8 -*-
"""
プロモーション用スクショを 1080x1920 に揃え、要件を検証する。

入力: docs/play-store/screenshots/phone-*.png（-play / promo 除く）
出力: docs/play-store/screenshots/promo/promo-01.png ...
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

from prepare_play_screenshots import pad_to_9_16, validate

ROOT = Path(__file__).resolve().parent.parent
IN_DIR = ROOT / "docs" / "play-store" / "screenshots"
OUT_DIR = IN_DIR / "promo"
PROMO_MIN_SIDE = 1080
PROMO_MIN_COUNT = 4


def validate_promo(path: Path) -> list[str]:
    """プロモ用: 各辺 1080 以上 + 通常 Play 検証。"""
    issues = validate(path)
    with Image.open(path) as img:
        w, h = img.size
        if w < PROMO_MIN_SIDE:
            issues.append(f"幅が {PROMO_MIN_SIDE}px 未満 ({w})")
        if h < PROMO_MIN_SIDE:
            issues.append(f"高さが {PROMO_MIN_SIDE}px 未満 ({h})")
    return issues


def list_sources() -> list[Path]:
    """加工済み phone-*.png を名前順で列挙する。"""
    skip = ("-play", "promo", "raw")
    files = []
    for p in sorted(IN_DIR.glob("phone-*.png")):
        if any(s in p.stem for s in skip):
            continue
        files.append(p)
    return files


def main() -> None:
    """phone-*.png を promo/*.png に変換し、結果を表示する。"""
    sources = list_sources()
    if not sources:
        print(f"入力がありません: {IN_DIR}/phone-*.png")
        sys.exit(1)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ok = 0
    for i, src in enumerate(sources, start=1):
        dst = OUT_DIR / f"promo-{i:02d}.png"
        pad_to_9_16(src, dst, long_side=1920)
        issues = validate_promo(dst)
        if issues:
            print(f"[NG] {dst.name}: {issues}")
        else:
            print(f"[OK] {dst.name}  1080x1920  ← {src.name}")
            ok += 1

    print()
    print(f"プロモ用に準備できた枚数: {ok} / {PROMO_MIN_COUNT} 枚以上必要")
    if ok < PROMO_MIN_COUNT:
        need = PROMO_MIN_COUNT - ok
        print(f"あと {need} 枚: phone-03-*.png, phone-04-*.png を追加して再実行")
        print("推奨画面: タイミング調整パネル / 設定画面")
        sys.exit(2)
    sys.exit(0)


if __name__ == "__main__":
    main()
