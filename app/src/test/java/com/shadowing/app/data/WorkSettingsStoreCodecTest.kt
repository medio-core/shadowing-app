package com.shadowing.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 作品設定 JSON のエンコード・デコードを検証する。
 */
class WorkSettingsStoreCodecTest {

    @Test
    fun encodeAndDecode_roundTrip() {
        val original = listOf(
            WorkSettings(
                videoUri = "content://video/1",
                japaneseSrtUri = "content://srt/ja",
                englishSrtUri = null,
                englishOffsetMs = 150L,
                japaneseOffsetMs = -50L,
                playbackSpeed = 1.25f,
                seekSliderOnRight = false,
                lastUsedAtMs = 42L,
            ),
        )

        val json = WorkSettingsStore.encodeEntries(original)
        val decoded = WorkSettingsStore.decodeEntries(json)

        assertEquals(original, decoded)
    }
}
