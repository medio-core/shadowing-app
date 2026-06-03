package com.shadowing.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字幕オフセット計算の単体テスト。
 */
class SubtitleCueOffsetTest {

    @Test
    fun withOffset_shiftsStartAndEnd() {
        val cue = SubtitleCue(
            index = 1,
            startMs = 1_000L,
            endMs = 3_000L,
            text = "Hello",
        )

        val shifted = cue.withOffset(250L)

        assertEquals(1_250L, shifted.startMs)
        assertEquals(3_250L, shifted.endMs)
    }

    @Test
    fun withOffset_clampsStartAtZero() {
        val cue = SubtitleCue(
            index = 1,
            startMs = 200L,
            endMs = 800L,
            text = "Hi",
        )

        val shifted = cue.withOffset(-500L)

        assertEquals(0L, shifted.startMs)
        assertEquals(300L, shifted.endMs)
    }

    @Test
    fun formatOffsetSec_showsSignAndOneDecimal() {
        assertEquals("+0.2s", formatOffsetSec(200L))
        assertEquals("-1.5s", formatOffsetSec(-1_500L))
    }
}
