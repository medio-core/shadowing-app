package com.shadowing.app.data

/**
 * 前回の再生状態を保存・復元するためのデータ。
 */
data class PlaybackSession(
    val videoUri: String,
    val japaneseSrtUri: String?,
    val englishSrtUri: String?,
    val playbackPositionMs: Long,
    val englishOffsetMs: Long,
    val japaneseOffsetMs: Long,
    val playbackSpeed: Float,
    val seekSliderOnRight: Boolean,
)
