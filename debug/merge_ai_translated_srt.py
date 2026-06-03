"""AI翻訳済みSRTチャンクを検証して1本に結合する。"""

from __future__ import annotations

import re
from pathlib import Path


OUTPUT_PATH = Path(
    r"\\192.168.1.66\video\Movie"
    r"\Breaking.Bad.S01-S05.1080p.BluRay.10bit.HEVC.6CH-MkvCage.ws"
    r"\Breaking.Bad.S01.1080p.BluRay.10bit.HEVC.6CH-MkvCage.ws"
    r"\英語学習用\Breaking.Bad.S01e01.2025-03-09.ja.ai.srt"
)
CHUNK_DIR = Path(__file__).with_name("ai_translation_chunks")
CHUNK_FILES = (
    CHUNK_DIR / "translated_001_160.srt",
    CHUNK_DIR / "translated_161_320.srt",
    CHUNK_DIR / "translated_321_480.srt",
    CHUNK_DIR / "translated_481_640.srt",
    CHUNK_DIR / "translated_641_771.srt",
)
TIMESTAMP_PATTERN = re.compile(
    r"^\d{2}:\d{2}:\d{2},\d{3}\s+-->\s+\d{2}:\d{2}:\d{2},\d{3}$"
)


def split_blocks(content: str) -> list[list[str]]:
    """SRT本文をブロック単位に分解する。"""
    normalized = content.replace("\r\n", "\n").replace("\r", "\n")
    blocks = normalized.strip().split("\n\n")
    return [block.splitlines() for block in blocks if block.strip()]


def read_chunk(path: Path) -> list[list[str]]:
    """翻訳済みチャンクを読み込む。"""
    return split_blocks(path.read_text(encoding="utf-8-sig"))


def validate_blocks(blocks: list[list[str]]) -> None:
    """字幕番号とタイムコードがSRTとして妥当か検証する。"""
    expected = 1
    for block in blocks:
        if len(block) < 3:
            raise ValueError(f"Invalid block at cue {expected}: too short")
        if block[0].strip() != str(expected):
            raise ValueError(
                f"Invalid cue number: expected {expected}, got {block[0]!r}"
            )
        if not TIMESTAMP_PATTERN.match(block[1].strip()):
            raise ValueError(f"Invalid timestamp at cue {expected}: {block[1]!r}")
        expected += 1

    if expected != 772:
        raise ValueError(f"Invalid cue count: expected 771, got {expected - 1}")


def main() -> None:
    """翻訳済みチャンクを結合して出力する。"""
    blocks: list[list[str]] = []
    for path in CHUNK_FILES:
        blocks.extend(read_chunk(path))

    validate_blocks(blocks)
    content = "\n\n".join("\n".join(block) for block in blocks) + "\n"
    OUTPUT_PATH.write_text(content, encoding="utf-8")
    print(f"wrote: {OUTPUT_PATH}")
    print(f"cue_count: {len(blocks)}")


if __name__ == "__main__":
    main()
