package com.shadowing.app

import android.os.Bundle
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowing.app.ads.MobileAdsHelper
import com.shadowing.app.ui.FirstLaunchGuideDialog
import com.shadowing.app.ui.SettingsScreen
import com.shadowing.app.ui.ShadowingScreen
import com.shadowing.app.viewmodel.ShadowingViewModel

/**
 * シャドーイングアプリのエントリポイント Activity。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ShadowingViewModel by viewModels()
    private lateinit var mediaSession: MediaSession

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                persistReadPermission(uri)
                viewModel.setVideoUri(uri)
            }
        }

    private val pickJapaneseSrtLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                persistReadPermission(uri)
                viewModel.loadJapaneseSrt(uri)
            }
        }

    private val pickEnglishSrtLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                persistReadPermission(uri)
                viewModel.loadEnglishSrt(uri)
            }
        }

    override fun onStop() {
        super.onStop()
        viewModel.persistSessionNow()
    }

    override fun onStart() {
        super.onStart()
        mediaSession.isActive = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAdsHelper.ensureInitialized(this)
        setupMediaSession()

        setContent {
            val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var showSettings by remember { mutableStateOf(false) }
            var forceShowGuide by remember { mutableStateOf(false) }

            BackHandler(enabled = showSettings) {
                showSettings = false
            }

            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface {
                    if (showSettings) {
                        SettingsScreen(
                            settings = appSettings,
                            settingsMessage = uiState.errorMessage,
                            onSettingsMessageShown = viewModel::clearError,
                            onBack = { showSettings = false },
                            onRestoreSessionChanged =
                                viewModel::setRestoreSessionOnLaunch,
                            onDefaultSpeedSelected =
                                viewModel::setDefaultPlaybackSpeed,
                            onDefaultSeekSliderSideChanged =
                                viewModel::setDefaultSeekSliderOnRight,
                            onShowGuideAgain = {
                                viewModel.resetGuideSeen()
                                forceShowGuide = true
                                showSettings = false
                            },
                            onDebugHideAds = {
                                viewModel.setAdsRemoved(true)
                                viewModel.showSettingsMessage(
                                    "開発用に広告を非表示にしました。",
                                )
                            },
                            onDebugShowAds = {
                                viewModel.setAdsRemoved(false)
                                viewModel.showSettingsMessage(
                                    "広告表示を戻しました。",
                                )
                            },
                        )
                    } else {
                        ShadowingScreen(
                            viewModel = viewModel,
                            onPickVideo = {
                                pickVideoLauncher.launch(arrayOf("video/*"))
                            },
                            onPickJapaneseSrt = {
                                pickJapaneseSrtLauncher.launch(arrayOf("*/*"))
                            },
                            onPickEnglishSrt = {
                                pickEnglishSrtLauncher.launch(arrayOf("*/*"))
                            },
                            onOpenSettings = { showSettings = true },
                        )
                    }

                    if (!showSettings &&
                        (forceShowGuide || !appSettings.hasSeenGuide)
                    ) {
                        FirstLaunchGuideDialog(
                            onDismiss = {
                                forceShowGuide = false
                                viewModel.markGuideSeen()
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * Bluetooth機器などのメディアボタンを字幕操作へ割り当てる。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isShadowingMediaKey(event.keyCode)) {
            return super.dispatchKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_UP) {
            performMediaAction(event.keyCode)
        }
        return true
    }

    /**
     * この画面で字幕操作に使うメディアキーか判定する。
     */
    private fun isShadowingMediaKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_NEXT -> true
            else -> false
        }
    }

    /**
     * メディアキーを字幕の区間再生と前後移動へ変換する。
     */
    private fun performMediaAction(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> viewModel.repeatCurrentSegment()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> viewModel.previousCue()
            KeyEvent.KEYCODE_MEDIA_NEXT -> viewModel.nextCue()
        }
    }

    /**
     * Bluetoothメディアキー受信用の MediaSession を初期化する。
     */
    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "ShadowingMediaSession").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        viewModel.repeatCurrentSegment()
                    }

                    override fun onPause() {
                        viewModel.repeatCurrentSegment()
                    }

                    override fun onSkipToNext() {
                        viewModel.nextCue()
                    }

                    override fun onSkipToPrevious() {
                        viewModel.previousCue()
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: android.content.Intent): Boolean {
                        val keyEvent = mediaButtonIntent
                            .getParcelableExtra<KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
                            ?: return super.onMediaButtonEvent(mediaButtonIntent)
                        if (keyEvent.action == KeyEvent.ACTION_UP &&
                            isShadowingMediaKey(keyEvent.keyCode)
                        ) {
                            performMediaAction(keyEvent.keyCode)
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                },
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_PLAY_PAUSE,
                    )
                    .setState(
                        PlaybackState.STATE_PLAYING,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        1.0f,
                    )
                    .build(),
            )
            isActive = true
        }
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    /**
     * 可能な場合のみ URI 読み取り権限を永続化する。
     */
    private fun persistReadPermission(uri: android.net.Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
