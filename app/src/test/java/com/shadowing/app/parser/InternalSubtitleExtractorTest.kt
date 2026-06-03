package com.shadowing.app.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * InternalSubtitleExtractor の単体テスト。
 */
class InternalSubtitleExtractorTest {

    @Test
    fun tx3gUsesDeclaredTextLengthOnly() {
        val sample = byteArrayOf(0, 5) +
            "Hello".toByteArray(Charsets.UTF_8) +
            "styl".toByteArray(Charsets.UTF_8)

        val text = InternalSubtitleExtractor.decodeSampleText(
            mimeType = "application/x-quicktime-tx3g",
            bytes = sample,
        )

        assertEquals("Hello", text)
    }

    @Test
    fun tx3gRemovesStyleBoxMarkerFromText() {
        val rawText = "It is fascinating, really.styl$"
        val textBytes = rawText.toByteArray(Charsets.UTF_8)
        val sample = byteArrayOf(
            ((textBytes.size shr 8) and 0xFF).toByte(),
            (textBytes.size and 0xFF).toByte(),
        ) + textBytes

        val text = InternalSubtitleExtractor.decodeSampleText(
            mimeType = "application/x-quicktime-tx3g",
            bytes = sample,
        )

        assertEquals("It is fascinating, really.", text)
    }

    @Test
    fun tx3gRemovesLeadingStyleNoiseFromLines() {
        val rawText = ";Just hand this disc\n#I mean, you're flat\n" +
            "&Hey, yo, Kraze.\n31 mean, I'm sure\n9You ain't Welcome\n" +
            "/\"Cheese-dick.\"\n'Anyway, it's the\n" +
            ")Come on\n:Yeah, I'll tell you\n-You lost your partner\n" +
            "I'm not leaving"
        val textBytes = rawText.toByteArray(Charsets.UTF_8)
        val sample = byteArrayOf(
            ((textBytes.size shr 8) and 0xFF).toByte(),
            (textBytes.size and 0xFF).toByte(),
        ) + textBytes

        val text = InternalSubtitleExtractor.decodeSampleText(
            mimeType = "application/x-quicktime-tx3g",
            bytes = sample,
        )

        assertEquals(
            "Just hand this disc\nI mean, you're flat\n" +
                "Hey, yo, Kraze.\nmean, I'm sure\nYou ain't Welcome\n" +
                "\"Cheese-dick.\"\nAnyway, it's the\n" +
                "Come on\nYeah, I'll tell you\nYou lost your partner\n" +
                "I'm not leaving",
            text,
        )
    }

    @Test
    fun tx3gRemovesBracketedSoundEffectBeforeNoiseFix() {
        val rawText = "(MUSIC PLAYING)"
        val textBytes = rawText.toByteArray(Charsets.UTF_8)
        val sample = byteArrayOf(
            ((textBytes.size shr 8) and 0xFF).toByte(),
            (textBytes.size and 0xFF).toByte(),
        ) + textBytes

        val text = InternalSubtitleExtractor.decodeSampleText(
            mimeType = "application/x-quicktime-tx3g",
            bytes = sample,
        )

        assertEquals("", text)
    }

    @Test
    fun tx3gRemovesBracketedSoundEffectWithLeadingNoise() {
        val rawText = "%(MUSIC PLAYING)"
        val textBytes = rawText.toByteArray(Charsets.UTF_8)
        val sample = byteArrayOf(
            ((textBytes.size shr 8) and 0xFF).toByte(),
            (textBytes.size and 0xFF).toByte(),
        ) + textBytes

        val text = InternalSubtitleExtractor.decodeSampleText(
            mimeType = "application/x-quicktime-tx3g",
            bytes = sample,
        )

        assertEquals("", text)
    }

    @Test
    fun tx3gRemovesSoundEffectWithMissingOpeningBracket() {
        val rawText = "MUSIC PLAYING)"
        val textBytes = rawText.toByteArray(Charsets.UTF_8)
        val sample = byteArrayOf(
            ((textBytes.size shr 8) and 0xFF).toByte(),
            (textBytes.size and 0xFF).toByte(),
        ) + textBytes

        val text = InternalSubtitleExtractor.decodeSampleText(
            mimeType = "application/x-quicktime-tx3g",
            bytes = sample,
        )

        assertEquals("", text)
    }

    @Test
    fun tx3gRemovesUnbracketedSoundEffect() {
        val rawText = "MUSIC PLAYING"
        val textBytes = rawText.toByteArray(Charsets.UTF_8)
        val sample = byteArrayOf(
            ((textBytes.size shr 8) and 0xFF).toByte(),
            (textBytes.size and 0xFF).toByte(),
        ) + textBytes

        val text = InternalSubtitleExtractor.decodeSampleText(
            mimeType = "application/x-quicktime-tx3g",
            bytes = sample,
        )

        assertEquals("", text)
    }

    @Test
    fun mp4VttUsesPayloadBoxOnly() {
        val sample = box(
            type = "vttc",
            payload = box("sttg", "line:0".toByteArray(Charsets.UTF_8)) +
                box("payl", "Good morning".toByteArray(Charsets.UTF_8)),
        )

        val text = InternalSubtitleExtractor.decodeSampleText(
            mimeType = "application/x-mp4-vtt",
            bytes = sample,
        )

        assertEquals("Good morning", text)
    }

    /**
     * MP4 ボックスのテスト用バイト列を作る。
     */
    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = payload.size + 8
        return byteArrayOf(
            ((size shr 24) and 0xFF).toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte(),
        ) + type.toByteArray(Charsets.US_ASCII) + payload
    }
}
