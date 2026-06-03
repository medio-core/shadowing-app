package com.shadowing.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 前回の再生状態を SharedPreferences へ保存・読み込みする。
 */
class PlaybackSessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存済みの再生状態を読み込む。
     */
    fun load(): PlaybackSession? {
        val videoUri = prefs.getString(KEY_VIDEO_URI, null) ?: return null
        return PlaybackSession(
            videoUri = videoUri,
            japaneseSrtUri = prefs.getString(KEY_JAPANESE_SRT_URI, null),
            englishSrtUri = prefs.getString(KEY_ENGLISH_SRT_URI, null),
            playbackPositionMs = prefs.getLong(KEY_PLAYBACK_POSITION_MS, 0L),
            englishOffsetMs = prefs.getLong(KEY_ENGLISH_OFFSET_MS, 0L),
            japaneseOffsetMs = prefs.getLong(KEY_JAPANESE_OFFSET_MS, 0L),
            playbackSpeed = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f),
            seekSliderOnRight = prefs.getBoolean(KEY_SEEK_SLIDER_ON_RIGHT, true),
        )
    }

    /**
     * 再生状態を保存する。
     */
    fun save(session: PlaybackSession) {
        prefs.edit()
            .putString(KEY_VIDEO_URI, session.videoUri)
            .putString(KEY_JAPANESE_SRT_URI, session.japaneseSrtUri)
            .putString(KEY_ENGLISH_SRT_URI, session.englishSrtUri)
            .putLong(KEY_PLAYBACK_POSITION_MS, session.playbackPositionMs)
            .putLong(KEY_ENGLISH_OFFSET_MS, session.englishOffsetMs)
            .putLong(KEY_JAPANESE_OFFSET_MS, session.japaneseOffsetMs)
            .putFloat(KEY_PLAYBACK_SPEED, session.playbackSpeed)
            .putBoolean(KEY_SEEK_SLIDER_ON_RIGHT, session.seekSliderOnRight)
            .apply()
    }

    /**
     * 保存済みの再生状態を削除する。
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "playback_session"
        private const val KEY_VIDEO_URI = "video_uri"
        private const val KEY_JAPANESE_SRT_URI = "japanese_srt_uri"
        private const val KEY_ENGLISH_SRT_URI = "english_srt_uri"
        private const val KEY_PLAYBACK_POSITION_MS = "playback_position_ms"
        private const val KEY_ENGLISH_OFFSET_MS = "english_offset_ms"
        private const val KEY_JAPANESE_OFFSET_MS = "japanese_offset_ms"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_SEEK_SLIDER_ON_RIGHT = "seek_slider_on_right"
    }
}
