package com.shadowing.app.parser

import com.shadowing.app.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SubtitlePostProcessor の単体テスト。
 */
class SubtitlePostProcessorTest {

    @Test
    fun removesParenthesizedSoundEffectCues() {
        val cues = listOf(
            cue(1, 1000L, 2000L, "(DOOR SLAMS)"),
            cue(2, 2100L, 3000L, "I'm here."),
        )

        val processed = SubtitlePostProcessor.process(cues)

        assertEquals(1, processed.size)
        assertEquals("I'm here.", processed[0].text)
        assertEquals(1, processed[0].index)
    }

    @Test
    fun removesParenthesizedSoundEffectLinesInsideCue() {
        val cues = listOf(
            cue(1, 1000L, 3000L, "(MUSIC PLAYING)\nLet's go."),
        )

        val processed = SubtitlePostProcessor.process(cues)

        assertEquals(1, processed.size)
        assertEquals("Let's go.", processed[0].text)
    }

    @Test
    fun removesUnbracketedSoundEffectLines() {
        val cues = listOf(
            cue(1, 1000L, 3000L, "MUSIC PLAYING\nLet's go."),
            cue(2, 3100L, 5000L, "SPEAKING FOREIGN LANGUAGE"),
        )

        val processed = SubtitlePostProcessor.process(cues)

        assertEquals(1, processed.size)
        assertEquals("Let's go.", processed[0].text)
    }

    @Test
    fun keepsUppercaseDialogueLines() {
        val cues = listOf(
            cue(1, 1000L, 2000L, "I AM SERIOUS."),
            cue(2, 2100L, 3000L, "GET OUT OF HERE!"),
        )

        val processed = SubtitlePostProcessor.process(cues)

        assertEquals(2, processed.size)
        assertEquals("I AM SERIOUS.", processed[0].text)
        assertEquals("GET OUT OF HERE!", processed[1].text)
    }

    @Test
    fun mergesClearlySplitSentenceFragments() {
        val cues = listOf(
            cue(1, 1000L, 2000L, "You will not believe"),
            cue(2, 2050L, 3000L, "who's cleaning Chad's car."),
        )

        val processed = SubtitlePostProcessor.process(cues)

        assertEquals(1, processed.size)
        assertEquals(
            "You will not believe who's cleaning Chad's car.",
            processed[0].text,
        )
        assertEquals(1000L, processed[0].startMs)
        assertEquals(3000L, processed[0].endMs)
    }

    @Test
    fun doesNotMergeCompleteSentences() {
        val cues = listOf(
            cue(1, 1000L, 2000L, "Let's go."),
            cue(2, 2050L, 3000L, "Right now."),
        )

        val processed = SubtitlePostProcessor.process(cues)

        assertEquals(2, processed.size)
    }

    /**
     * テスト用字幕を作る。
     */
    private fun cue(index: Int, startMs: Long, endMs: Long, text: String): SubtitleCue {
        return SubtitleCue(
            index = index,
            startMs = startMs,
            endMs = endMs,
            text = text,
        )
    }
}
