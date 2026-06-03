package com.shadowing.app.model

/**
 * SRTの1字幕ブロックを表すデータクラス。
 */
data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    /** 字幕区間の長さ（ミリ秒）。 */
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * オフセット（ミリ秒）を加えた字幕コピーを返す。
 */
fun SubtitleCue.withOffset(offsetMs: Long): SubtitleCue {
    val newStart = (startMs + offsetMs).coerceAtLeast(0L)
    val newEnd = (endMs + offsetMs).coerceAtLeast(newStart + 1L)
    return copy(startMs = newStart, endMs = newEnd)
}

/**
 * ミリ秒を SRT 風の時刻文字列に変換する。
 */
fun formatCueTimeMs(ms: Long): String {
    val totalSec = ms / 1000L
    val millis = ms % 1000L
    val sec = totalSec % 60L
    val min = (totalSec / 60L) % 60L
    val hour = totalSec / 3600L
    return if (hour > 0L) {
        String.format("%d:%02d:%02d.%03d", hour, min, sec, millis)
    } else {
        String.format("%02d:%02d.%03d", min, sec, millis)
    }
}

/**
 * オフセット値を表示用の秒文字列に変換する。
 */
fun formatOffsetSec(offsetMs: Long): String {
    val sign = if (offsetMs >= 0L) "+" else ""
    val sec = offsetMs / 1000.0
    return String.format("%s%.1fs", sign, sec)
}
