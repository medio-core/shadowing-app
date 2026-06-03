package com.shadowing.app.model

/**
 * 英語cueと日本語cueの対応の確信度。
 */
enum class JapaneseMatchQuality {
    /** 区間が重なっている。 */
    OVERLAP,
    /** 重なりはないが中心時刻が近い（不明確）。 */
    NEARBY,
    /** 対応する日本語字幕なし。 */
    NONE,
}

/**
 * 英語cueに対する日本語マッチの判定結果。
 */
data class JapaneseMatchResult(
    val quality: JapaneseMatchQuality,
    val match: JapaneseMatch? = null,
    val centerDistanceMs: Long = 0L,
) {
    /** 日本語テキストを表示してよいか。 */
    val hasDisplayableJapanese: Boolean
        get() = quality != JapaneseMatchQuality.NONE && match != null
}

/**
 * 英語cueに対して時刻が最も合う日本語cueのマッチ結果。
 */
data class JapaneseMatch(
    val cue: SubtitleCue,
    val listIndex: Int,
    val overlapMs: Long,
) {
    /** 区間が重なっているか。 */
    val hasOverlap: Boolean
        get() = overlapMs > 0L
}

/**
 * 英語字幕の時刻区間に基づき日本語字幕を選ぶ。
 */
object SubtitleMatcher {

    /**
     * 重なりなしでも「おおよそ近い」とみなす中心時刻の最大差（ms）。
     */
    const val MAX_CENTER_GAP_MS = 1500L

    /**
     * 基準cue（調整済み）に対する日本語マッチを判定する。
     */
    fun findJapaneseMatch(
        reference: SubtitleCue,
        japaneseCues: List<SubtitleCue>,
        japaneseOffsetMs: Long,
    ): JapaneseMatchResult {
        if (japaneseCues.isEmpty()) {
            return JapaneseMatchResult(quality = JapaneseMatchQuality.NONE)
        }

        val best = findBestCandidate(reference, japaneseCues, japaneseOffsetMs)
            ?: return JapaneseMatchResult(quality = JapaneseMatchQuality.NONE)

        val centerDist = kotlin.math.abs(
            centerMs(reference) - centerMs(best.cue),
        )

        val quality = when {
            best.overlapMs > 0L -> JapaneseMatchQuality.OVERLAP
            centerDist <= MAX_CENTER_GAP_MS -> JapaneseMatchQuality.NEARBY
            else -> JapaneseMatchQuality.NONE
        }

        return JapaneseMatchResult(
            quality = quality,
            match = if (quality == JapaneseMatchQuality.NONE) null else best,
            centerDistanceMs = centerDist,
        )
    }

    /**
     * 基準cueに時刻重なりする日本語cueをすべて返す。
     * 重なりがない場合は従来の最良候補1件（NEARBY相当）を返す。
     */
    fun findJapaneseMatches(
        reference: SubtitleCue,
        japaneseCues: List<SubtitleCue>,
        japaneseOffsetMs: Long,
    ): List<JapaneseMatch> {
        if (japaneseCues.isEmpty()) {
            return emptyList()
        }

        val overlaps = japaneseCues.mapIndexedNotNull { listIndex, raw ->
            val candidate = raw.withOffset(japaneseOffsetMs)
            val overlap = overlapMs(reference, candidate)
            if (overlap > 0L) {
                JapaneseMatch(
                    cue = candidate,
                    listIndex = listIndex,
                    overlapMs = overlap,
                )
            } else {
                null
            }
        }.sortedBy { it.cue.startMs }

        if (overlaps.isNotEmpty()) {
            return overlaps
        }

        val best = findBestCandidate(reference, japaneseCues, japaneseOffsetMs)
            ?: return emptyList()
        val centerDist = kotlin.math.abs(
            centerMs(reference) - centerMs(best.cue),
        )
        return if (centerDist <= MAX_CENTER_GAP_MS) {
            listOf(best)
        } else {
            emptyList()
        }
    }

    /**
     * 候補の中からスコア最大の日本語cueを返す。
     */
    private fun findBestCandidate(
        reference: SubtitleCue,
        japaneseCues: List<SubtitleCue>,
        japaneseOffsetMs: Long,
    ): JapaneseMatch? {
        var best: JapaneseMatch? = null
        var bestCenterDist = Long.MAX_VALUE

        japaneseCues.forEachIndexed { listIndex, raw ->
            val candidate = raw.withOffset(japaneseOffsetMs)
            val overlap = overlapMs(reference, candidate)
            val centerDist = kotlin.math.abs(
                centerMs(reference) - centerMs(candidate),
            )
            val currentBest = best
            val better = when {
                currentBest == null -> true
                overlap > currentBest.overlapMs -> true
                overlap == currentBest.overlapMs && overlap > 0L &&
                    centerDist < bestCenterDist -> true
                overlap == 0L && currentBest.overlapMs == 0L &&
                    centerDist < bestCenterDist -> true
                else -> false
            }
            if (better) {
                best = JapaneseMatch(
                    cue = candidate,
                    listIndex = listIndex,
                    overlapMs = overlap,
                )
                bestCenterDist = centerDist
            }
        }

        return best
    }

    /**
     * 2つの字幕区間の重なり長（ミリ秒）を返す。
     */
    private fun overlapMs(a: SubtitleCue, b: SubtitleCue): Long {
        val start = maxOf(a.startMs, b.startMs)
        val end = minOf(a.endMs, b.endMs)
        return (end - start).coerceAtLeast(0L)
    }

    /**
     * 字幕区間の中心時刻（ミリ秒）を返す。
     */
    private fun centerMs(cue: SubtitleCue): Long {
        return (cue.startMs + cue.endMs) / 2L
    }
}
