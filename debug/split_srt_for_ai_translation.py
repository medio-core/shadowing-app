"""AI翻訳用にSRT字幕を番号範囲ごとのチャンクへ分割する。"""

from __future__ import annotations

from pathlib import Path


SOURCE_PATH = Path(
    r"\\192.168.1.66\video\Movie"
    r"\Breaking.Bad.S01-S05.1080p.BluRay.10bit.HEVC.6CH-MkvCage.ws"
    r"\Breaking.Bad.S01.1080p.BluRay.10bit.HEVC.6CH-MkvCage.ws"
    r"\英語学習用\Breaking.Bad.S01e01.2025-03-09.srt"
)
CHUNK_DIR = Path(__file__).with_name("ai_translation_chunks")
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


def main() -> None:
    """指定範囲ごとのSRTチャンクを出力する。"""
    CHUNK_DIR.mkdir(exist_ok=True)
    blocks = split_blocks(read_srt(SOURCE_PATH))

    for start, end in CHUNK_RANGES:
        selected = [
            block for block in blocks
            if (number := block_number(block)) is not None
            and start <= number <= end
        ]
        output_path = CHUNK_DIR / f"source_{start:03d}_{end:03d}.srt"
        output_path.write_text("\n\n".join(selected) + "\n", encoding="utf-8")
        print(f"wrote {output_path}")


if __name__ == "__main__":
    main()
