package com.shadowing.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo

/**
 * アプリ設定を SharedPreferences へ保存・読み込みする。
 */
class AppSettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存済み設定を読み込む。
     */
    fun load(): AppSettings {
        val settings = AppSettings(
            restoreSessionOnLaunch = prefs.getBoolean(
                KEY_RESTORE_SESSION_ON_LAUNCH,
                true,
            ),
            defaultPlaybackSpeed = prefs.getFloat(
                KEY_DEFAULT_PLAYBACK_SPEED,
                1.0f,
            ),
            defaultSeekSliderOnRight = prefs.getBoolean(
                KEY_DEFAULT_SEEK_SLIDER_ON_RIGHT,
                true,
            ),
            hasSeenGuide = prefs.getBoolean(KEY_HAS_SEEN_GUIDE, false),
            adsRemoved = prefs.getBoolean(KEY_ADS_REMOVED, false),
        )
        return if (isDebugBuild()) {
            settings
        } else {
            settings.copy(adsRemoved = false)
        }
    }

    /**
     * 設定を保存する。
     */
    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_RESTORE_SESSION_ON_LAUNCH, settings.restoreSessionOnLaunch)
            .putFloat(KEY_DEFAULT_PLAYBACK_SPEED, settings.defaultPlaybackSpeed)
            .putBoolean(
                KEY_DEFAULT_SEEK_SLIDER_ON_RIGHT,
                settings.defaultSeekSliderOnRight,
            )
            .putBoolean(KEY_HAS_SEEN_GUIDE, settings.hasSeenGuide)
            .putBoolean(KEY_ADS_REMOVED, settings.adsRemoved)
            .apply()
    }

    /**
     * 開発用の広告非表示設定を保存する。
     */
    fun setAdsRemoved(removed: Boolean) {
        val current = loadRaw()
        save(current.copy(adsRemoved = removed))
    }

    /**
     * デバッグビルドか判定する。
     */
    private fun isDebugBuild(): Boolean {
        return (appContext.applicationInfo.flags and
            ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * 広告設定を含めて生の設定値を読み込む。
     */
    private fun loadRaw(): AppSettings {
        return AppSettings(
            restoreSessionOnLaunch = prefs.getBoolean(
                KEY_RESTORE_SESSION_ON_LAUNCH,
                true,
            ),
            defaultPlaybackSpeed = prefs.getFloat(
                KEY_DEFAULT_PLAYBACK_SPEED,
                1.0f,
            ),
            defaultSeekSliderOnRight = prefs.getBoolean(
                KEY_DEFAULT_SEEK_SLIDER_ON_RIGHT,
                true,
            ),
            hasSeenGuide = prefs.getBoolean(KEY_HAS_SEEN_GUIDE, false),
            adsRemoved = prefs.getBoolean(KEY_ADS_REMOVED, false),
        )
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_RESTORE_SESSION_ON_LAUNCH =
            "restore_session_on_launch"
        private const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        private const val KEY_DEFAULT_SEEK_SLIDER_ON_RIGHT =
            "default_seek_slider_on_right"
        private const val KEY_HAS_SEEN_GUIDE = "has_seen_guide"
        private const val KEY_ADS_REMOVED = "ads_removed"
    }
}
