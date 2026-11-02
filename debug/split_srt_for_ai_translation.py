"""AI翻訳用にSRT字幕を番号範囲ごとのチャンクへ分割する。"""

from __future__ import annotations

import argparse
from pathlib import Path


CHUNK_RANGES = (
    (1, 160),
    (161, 320),
    (321, 480),
    (481, 640),
    (641, 771),
)


def read_srt(path: Path) -> str:
    """SRTファイルをUTF-8優先で読み込む。"""
    for encoding in ("utf-8-sig", "utf-8", "cp932"):
        try:
            return path.read_text(encoding=encoding)
        except UnicodeDecodeError:
            continue
    return path.read_text()


def split_blocks(content: str) -> list[str]:
    """SRT本文を空行区切りのブロックに分解する。"""
    normalized = content.replace("\r\n", "\n").replace("\r", "\n")
    return [block for block in normalized.strip().split("\n\n") if block.strip()]


def block_number(block: str) -> int | None:
    """SRTブロックの先頭番号を取得する。"""
    first_line = block.splitlines()[0].strip()
    return int(first_line) if first_line.isdigit() else None


def parse_args() -> argparse.Namespace:
    """コマンドライン引数を解析する。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="分割するSRTファイル")
    parser.add_argument(
        "--chunk-dir",
        type=Path,
        default=Path(__file__).with_name("ai_translation_chunks"),
        help="チャンクの出力ディレクトリ",
    )
    return parser.parse_args()


def main() -> None:
    """指定範囲ごとのSRTチャンクを出力する。"""
    args = parse_args()
    args.chunk_dir.mkdir(parents=True, exist_ok=True)
    blocks = split_blocks(read_srt(args.source))

    for start, end in CHUNK_RANGES:
        selected = [
            block for block in blocks
            if (number := block_number(block)) is not None
            and start <= number <= end
        ]
        output_path = args.chunk_dir / f"source_{start:03d}_{end:03d}.srt"
        output_path.write_text("\n\n".join(selected) + "\n", encoding="utf-8")
        print(f"wrote {output_path}")


if __name__ == "__main__":
    main()
