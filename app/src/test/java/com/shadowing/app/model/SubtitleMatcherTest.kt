package com.shadowing.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SubtitleMatcher の単体テスト。
 */
class SubtitleMatcherTest {

    @Test
    fun overlapMatchIsConfident() {
        val english = cue(1, 4000L, 8000L, "Hello")
        val japanese = listOf(
            cue(1, 0L, 2000L, "別"),
            cue(2, 5000L, 9000L, "こんにちは"),
        )

        val result = SubtitleMatcher.findJapaneseMatch(
            reference = english,
            japaneseCues = japanese,
            japaneseOffsetMs = 0L,
        )

        assertEquals(JapaneseMatchQuality.OVERLAP, result.quality)
        assertTrue(result.hasDisplayableJapanese)
        assertEquals(2, result.match!!.cue.index)
    }

    @Test
    fun farJapaneseReturnsNone() {
        val english = cue(1, 1000L, 3000L, "Hi")
        val japanese = listOf(
            cue(1, 10000L, 12000L, "遠い"),
        )

        val result = SubtitleMatcher.findJapaneseMatch(
            reference = english,
            japaneseCues = japanese,
            japaneseOffsetMs = 0L,
        )

        assertEquals(JapaneseMatchQuality.NONE, result.quality)
        assertFalse(result.hasDisplayableJapanese)
        assertNull(result.match)
        assertTrue(result.edgeGapMs > SubtitleMatcher.MAX_EDGE_GAP_MS)
    }

    @Test
    fun nearbyWithoutOverlapIsWeakMatch() {
        val english = cue(1, 1000L, 3000L, "Hi")
        val japanese = listOf(
            cue(1, 3200L, 5000L, "近い"),
        )

        val result = SubtitleMatcher.findJapaneseMatch(
            reference = english,
            japaneseCues = japanese,
            japaneseOffsetMs = 0L,
        )

        assertEquals(JapaneseMatchQuality.NEARBY, result.quality)
        assertTrue(result.hasDisplayableJapanese)
    }

    @Test
    fun offsetShiftsWhichJapaneseLineMatches() {
        val english = cue(1, 1000L, 3000L, "Hi")
        val japanese = listOf(
            cue(1, 0L, 1500L, "A"),
            cue(2, 4000L, 6000L, "B"),
        )

        val withoutOffset = SubtitleMatcher.findJapaneseMatch(
            reference = english,
            japaneseCues = japanese,
            japaneseOffsetMs = 0L,
        )
        val withOffset = SubtitleMatcher.findJapaneseMatch(
            reference = english,
            japaneseCues = japanese,
            japaneseOffsetMs = -2500L,
        )

        assertEquals(1, withoutOffset.match!!.cue.index)
        assertEquals(2, withOffset.match!!.cue.index)
    }

    @Test
    fun mergedEnglishCanReturnMultipleJapaneseOverlaps() {
        val english = cue(1, 1000L, 5000L, "Merged")
        val japanese = listOf(
            cue(1, 1200L, 2200L, "前半"),
            cue(2, 3000L, 4200L, "後半"),
        )

        val matches = SubtitleMatcher.findJapaneseMatches(
            reference = english,
            japaneseCues = japanese,
            japaneseOffsetMs = 0L,
        )

        assertEquals(2, matches.size)
        assertEquals(1, matches.first().cue.index)
        assertEquals(2, matches.last().cue.index)
    }

    private fun cue(index: Int, start: Long, end: Long, text: String): SubtitleCue {
        return SubtitleCue(index = index, startMs = start, endMs = end, text = text)
    }
}
