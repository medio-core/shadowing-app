package com.shadowing.app.parser

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LimitedTextReaderTest {

    @Test
    fun readsUtf8TextWithinLimit() {
        val text = "Hello\nこんにちは"

        val result = LimitedTextReader.readUtf8(
            ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),
            maxBytes = 1024,
        )

        assertEquals(text, result)
    }

    @Test
    fun rejectsInputOverLimit() {
        val input = ByteArrayInputStream(ByteArray(9) { 'a'.code.toByte() })

        assertThrows(IllegalArgumentException::class.java) {
            LimitedTextReader.readUtf8(input, maxBytes = 8)
        }
    }
}
