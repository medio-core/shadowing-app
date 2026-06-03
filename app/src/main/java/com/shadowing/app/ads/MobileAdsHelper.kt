package com.shadowing.app.ads

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/**
 * Mobile Ads SDK の初期化を 1 回だけ行い、準備完了後にコールバックする。
 */
object MobileAdsHelper {

    private const val TAG = "MobileAdsHelper"

    @Volatile
    private var initialized = false

    private val lock = Any()

    private val pendingReadyCallbacks = mutableListOf<() -> Unit>()

    /**
     * SDK 初期化を保証し、完了後に onReady を呼ぶ。
     *
     * @param context Activity または Application の Context
     * @param onReady 初期化完了後の処理
     */
    fun ensureInitialized(context: Context, onReady: () -> Unit = {}) {
        if (initialized) {
            onReady()
            return
        }
        synchronized(lock) {
            if (initialized) {
                onReady()
                return
            }
            pendingReadyCallbacks.add(onReady)
            if (pendingReadyCallbacks.size > 1) {
                return
            }
            val appContext = context.applicationContext
            configureTestDevices(appContext)
            MobileAds.initialize(appContext) { status ->
                Log.d(TAG, "MobileAds initialized: $status")
                synchronized(lock) {
                    initialized = true
                    val callbacks = pendingReadyCallbacks.toList()
                    pendingReadyCallbacks.clear()
                    callbacks.forEach { it() }
                }
            }
        }
    }

    /**
     * デバッグビルドではエミュレータ向けテスト広告設定を有効にする。
     */
    private fun configureTestDevices(context: Context) {
        if (!isDebuggable(context)) {
            return
        }
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build(),
        )
    }

    /**
     * デバッグ可能なビルドか判定する。
     */
    private fun isDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
