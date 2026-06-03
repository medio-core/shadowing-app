package com.shadowing.app.data

/**
 * アプリ全体の設定値。
 */
data class AppSettings(
    val restoreSessionOnLaunch: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val defaultSeekSliderOnRight: Boolean = true,
    val hasSeenGuide: Boolean = false,
    val adsRemoved: Boolean = false,
)
