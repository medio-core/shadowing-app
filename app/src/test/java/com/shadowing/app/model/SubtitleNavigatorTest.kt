package com.shadowing.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SubtitleNavigator の単体テスト。
 */
class SubtitleNavigatorTest {

    @Test
    fun positionInsideCueReturnsIndex() {
        val cues = listOf(
            cue(1, 1000L, 2000L),
            cue(2, 3000L, 4000L),
        )

        val index = SubtitleNavigator.findCueIndexAtPosition(
            cues = cues,
            offsetMs = 0L,
            positionMs = 3500L,
        )

        assertEquals(1, index)
    }

    @Test
    fun positionOutsideCueReturnsNull() {
        val cues = listOf(cue(1, 1000L, 2000L))

        val index = SubtitleNavigator.findCueIndexAtPosition(
            cues = cues,
            offsetMs = 0L,
            positionMs = 2500L,
        )

        assertNull(index)
    }

    @Test
    fun offsetIsAppliedBeforeMatching() {
        val cues = listOf(cue(1, 1000L, 2000L))

        val index = SubtitleNavigator.findCueIndexAtPosition(
            cues = cues,
            offsetMs = 500L,
            positionMs = 2250L,
        )

        assertEquals(0, index)
    }

    @Test
    fun boundaryPositionReturnsNextCue() {
        val cues = listOf(
            cue(1, 1000L, 2000L),
            cue(2, 2000L, 3000L),
        )

        val index = SubtitleNavigator.findCueIndexAtPosition(
            cues = cues,
            offsetMs = 0L,
            positionMs = 2000L,
        )

        assertEquals(1, index)
    }

    private fun cue(index: Int, start: Long, end: Long): SubtitleCue {
        return SubtitleCue(index = index, startMs = start, endMs = end, text = "")
    }
}
