package com.shadowing.app.ui

import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.shadowing.app.R
import com.shadowing.app.ads.MobileAdsHelper

private const val TAG = "TopAdBanner"
private const val MAX_LOAD_RETRIES = 3
private const val RETRY_DELAY_MS = 2_000L

/**
 * 画面幅からアダプティブバナーの AdSize を求める。
 *
 * @param adWidthDp バナー表示幅（dp）
 */
@Composable
fun rememberAdaptiveTopBannerAdSize(adWidthDp: Int): AdSize {
    val context = LocalContext.current
    return remember(adWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
            context,
            adWidthDp.coerceAtLeast(1),
        )
    }
}

/**
 * AdSize の高さを Compose の Dp に変換する。
 */
fun AdSize.toHeightDp(): Dp = height.coerceAtLeast(50).dp

/**
 * 画面上部に固定表示する AdMob アダプティブバナー。
 */
@Composable
fun TopAdBanner(
    adSize: AdSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adUnitId = context.getString(R.string.admob_banner_unit_id)
    val isDebugBuild =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    var adsReady by remember { mutableStateOf(false) }
    var shouldLoad by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        MobileAdsHelper.ensureInitialized(context) {
            adsReady = true
            shouldLoad = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    Box(modifier = modifier) {
        if (!adsReady) {
            Spacer(modifier = Modifier.fillMaxSize())
            return@Box
        }

        var adViewRef by remember { mutableStateOf<AdView?>(null) }

        DisposableEffect(lifecycleOwner, adViewRef) {
            val adView = adViewRef
            if (adView == null) {
                onDispose {}
            } else {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> adView.pause()
                        Lifecycle.Event.ON_RESUME -> adView.resume()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    adView.destroy()
                    adViewRef = null
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                AdView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.adUnitId = adUnitId
                    setAdSize(adSize)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            loadFailed = false
                            lastError = null
                            Log.d(TAG, "Banner ad loaded")
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            loadFailed = true
                            lastError = "${error.code}: ${error.message}"
                            Log.w(TAG, "Banner ad failed: $lastError")
                            if (retryCount < MAX_LOAD_RETRIES) {
                                retryCount++
                                mainHandler.postDelayed({
                                    shouldLoad = true
                                }, RETRY_DELAY_MS)
                            }
                        }
                    }
                }
            },
            update = { adView ->
                adViewRef = adView
                if (adView.adSize != adSize) {
                    adView.setAdSize(adSize)
                }
                if (shouldLoad) {
                    shouldLoad = false
                    adView.loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { adView ->
                mainHandler.removeCallbacksAndMessages(null)
                adView.destroy()
            },
        )

        if (loadFailed && isDebugBuild) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "広告読込失敗\n${lastError ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
