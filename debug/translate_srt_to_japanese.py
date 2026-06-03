"""SRT字幕を日本語へ翻訳して別ファイルへ出力するデバッグ用スクリプト。"""

from __future__ import annotations

import json
import re
import time
from pathlib import Path

from deep_translator import GoogleTranslator


SOURCE_PATH = Path(
    r"\\192.168.1.66\video\Movie"
    r"\Breaking.Bad.S01-S05.1080p.BluRay.10bit.HEVC.6CH-MkvCage.ws"
    r"\Breaking.Bad.S01.1080p.BluRay.10bit.HEVC.6CH-MkvCage.ws"
    r"\英語学習用\Breaking.Bad.S01e01.2025-03-09.srt"
)
OUTPUT_PATH = SOURCE_PATH.with_name("Breaking.Bad.S01e01.2025-03-09.ja.srt")
CACHE_PATH = Path(__file__).with_name("translate_srt_to_japanese_cache.json")

TIMESTAMP_PATTERN = re.compile(
    r"^\d{2}:\d{2}:\d{2},\d{3}\s+-->\s+\d{2}:\d{2}:\d{2},\d{3}$"
)
SLEEP_SECONDS = 0.05


def read_text_file(path: Path) -> str:
    """字幕ファイルをUTF-8系として読み込む。"""
    for encoding in ("utf-8-sig", "utf-8"):
        try:
            return path.read_text(encoding=encoding)
        except UnicodeDecodeError:
            continue
    return path.read_text(encoding="cp932")


def split_blocks(content: str) -> list[list[str]]:
    """SRT本文を空行区切りのブロックへ分解する。"""
    normalized = content.replace("\r\n", "\n").replace("\r", "\n")
    raw_blocks = normalized.strip().split("\n\n")
    return [block.split("\n") for block in raw_blocks if block.strip()]


def load_cache() -> dict[str, str]:
    """翻訳済みテキストのキャッシュを読み込む。"""
    if not CACHE_PATH.exists():
        return {}
    return json.loads(CACHE_PATH.read_text(encoding="utf-8"))


def save_cache(cache: dict[str, str]) -> None:
    """翻訳済みテキストのキャッシュを保存する。"""
    CACHE_PATH.write_text(
        json.dumps(cache, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def text_part(lines: list[str]) -> str:
    """SRTブロックから字幕本文だけを取り出す。"""
    if len(lines) < 3:
        return ""
    if not TIMESTAMP_PATTERN.match(lines[1]):
        return ""
    return "\n".join(line for line in lines[2:] if line.strip())


def translate_missing_texts(
    texts: list[str],
    cache: dict[str, str],
    translator: GoogleTranslator,
) -> None:
    """未翻訳の字幕本文を1件ずつ翻訳する。"""
    missing = [text for text in dict.fromkeys(texts) if text and text not in cache]
    total = len(missing)
    for index, source in enumerate(missing, start=1):
        cache[source] = translator.translate(source)
        if index % 10 == 0 or index == total:
            save_cache(cache)
            print(f"translated {index} / {total}", flush=True)
        time.sleep(SLEEP_SECONDS)


def build_output(blocks: list[list[str]], cache: dict[str, str]) -> str:
    """翻訳キャッシュを使って日本語SRT本文を組み立てる。"""
    output_blocks: list[str] = []
    for lines in blocks:
        translated_lines = lines.copy()
        source_text = text_part(lines)
        if source_text:
            translated_lines = lines[:2] + [cache.get(source_text, source_text)]
        output_blocks.append("\n".join(translated_lines))
    return "\n\n".join(output_blocks) + "\n"


def main() -> None:
    """SRTを日本語へ翻訳して出力する。"""
    content = read_text_file(SOURCE_PATH)
    blocks = split_blocks(content)
    texts = [text_part(block) for block in blocks]
    cache = load_cache()
    translator = GoogleTranslator(source="en", target="ja")

    translate_missing_texts(texts, cache, translator)
    OUTPUT_PATH.write_text(build_output(blocks, cache), encoding="utf-8")
    print(f"wrote: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
