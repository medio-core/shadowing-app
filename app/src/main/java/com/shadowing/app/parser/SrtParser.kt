package com.shadowing.app.parser

import com.shadowing.app.model.SubtitleCue

/**
 * SRTファイル文字列を SubtitleCue のリストに変換する。
 */
object SrtParser {

    private val timePattern =
        Regex(
            """(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*""" +
                """(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""",
        )

    /**
     * SRT本文をパースして字幕リストを返す。
     *
     * @param content SRTファイルの全文
     * @return 開始時刻順の字幕リスト
     */
    fun parse(content: String): List<SubtitleCue> {
        val normalized = content
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

        if (normalized.isEmpty()) {
            return emptyList()
        }

        val blocks = normalized.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SubtitleCue>()

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) {
                continue
            }

            val timeLineIndex = lines.indexOfFirst { "-->" in it }
            if (timeLineIndex < 0) {
                continue
            }

            val timeMatch = timePattern.find(lines[timeLineIndex]) ?: continue
            val startMs = toMillis(timeMatch, groupOffset = 1)
            val endMs = toMillis(timeMatch, groupOffset = 5)
            val text = lines.drop(timeLineIndex + 1).joinToString("\n")

            val index = lines.firstOrNull()?.toIntOrNull() ?: (cues.size + 1)
            cues += SubtitleCue(
                index = index,
                startMs = startMs,
                endMs = endMs,
                text = text,
            )
        }

        return SubtitlePostProcessor.process(cues.sortedBy { it.startMs })
    }

  /**
   * タイムスタンプ正規表現グループをミリ秒に変換する。
   */
    private fun toMillis(match: MatchResult, groupOffset: Int): Long {
        val hours = match.groupValues[groupOffset].toLong()
        val minutes = match.groupValues[groupOffset + 1].toLong()
        val seconds = match.groupValues[groupOffset + 2].toLong()
        val millis = match.groupValues[groupOffset + 3].toLong()
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
    }
}
