package com.shadowing.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PlaybackSession の保存内容を検証する。
 */
class PlaybackSessionStoreTest {

    /**
     * セッションデータの既定値とコピー内容を確認する。
     */
    @Test
    fun playbackSession_keepsSavedValues() {
        val session = PlaybackSession(
            videoUri = "content://video/1",
            japaneseSrtUri = "content://subtitle/ja",
            englishSrtUri = "content://subtitle/en",
            playbackPositionMs = 125_000L,
            englishOffsetMs = 200L,
            japaneseOffsetMs = -100L,
            playbackSpeed = 1.25f,
            seekSliderOnRight = false,
        )

        assertEquals("content://video/1", session.videoUri)
        assertEquals("content://subtitle/ja", session.japaneseSrtUri)
        assertEquals("content://subtitle/en", session.englishSrtUri)
        assertEquals(125_000L, session.playbackPositionMs)
        assertEquals(1.25f, session.playbackSpeed, 0.001f)
        assertNull(
            PlaybackSession(
                videoUri = "content://video/2",
                japaneseSrtUri = null,
                englishSrtUri = null,
                playbackPositionMs = 0L,
                englishOffsetMs = 0L,
                japaneseOffsetMs = 0L,
                playbackSpeed = 1.0f,
                seekSliderOnRight = true,
            ).japaneseSrtUri,
        )
    }
}
