package com.shadowing.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 作品設定リストの LRU 更新を検証する。
 */
class WorkSettingsRepositoryTest {

    @Test
    fun upsert_updatesExistingEntry() {
        val first = entry("video-a", lastUsed = 100L)
        val second = entry("video-b", lastUsed = 200L)
        val updated = entry("video-a", lastUsed = 0L, englishOffsetMs = 300L)

        val result = WorkSettingsRepository.upsert(
            entries = listOf(first, second),
            entry = updated,
            nowMs = 500L,
        )

        assertEquals(2, result.size)
        val saved = WorkSettingsRepository.findByVideoUri(result, "video-a")
        assertEquals(300L, saved?.englishOffsetMs)
        assertEquals(500L, saved?.lastUsedAtMs)
    }

    @Test
    fun upsert_trimsToMaxEntries() {
        val base = (1..31).map { index ->
            entry("video-$index", lastUsed = index.toLong())
        }
        val newest = entry("video-new", lastUsed = 0L)

        val result = WorkSettingsRepository.upsert(
            entries = base,
            entry = newest,
            nowMs = 9_999L,
        )

        assertEquals(WorkSettingsRepository.MAX_ENTRIES, result.size)
        assertNull(WorkSettingsRepository.findByVideoUri(result, "video-1"))
        assertEquals(
            9_999L,
            WorkSettingsRepository.findByVideoUri(result, "video-new")?.lastUsedAtMs,
        )
    }

    private fun entry(
        videoUri: String,
        lastUsed: Long,
        englishOffsetMs: Long = 0L,
    ): WorkSettings {
        return WorkSettings(
            videoUri = videoUri,
            japaneseSrtUri = null,
            englishSrtUri = null,
            englishOffsetMs = englishOffsetMs,
            japaneseOffsetMs = 0L,
            playbackSpeed = 1.0f,
            seekSliderOnRight = true,
            lastUsedAtMs = lastUsed,
        )
    }
}
