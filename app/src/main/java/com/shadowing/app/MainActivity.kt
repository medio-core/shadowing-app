package com.shadowing.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowing.app.ads.ConsentManager
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
                val permissionPersisted = persistReadPermission(uri)
                viewModel.setVideoUri(uri)
                notifyIfPermissionIsTemporary(permissionPersisted)
            }
        }

    private val pickJapaneseSrtLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val permissionPersisted = persistReadPermission(uri)
                viewModel.loadJapaneseSrt(uri)
                notifyIfPermissionIsTemporary(permissionPersisted)
            }
        }

    private val pickEnglishSrtLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val permissionPersisted = persistReadPermission(uri)
                viewModel.loadEnglishSrt(uri)
                notifyIfPermissionIsTemporary(permissionPersisted)
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
        setupMediaSession()

        setContent {
            val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val canRequestAds by ConsentManager.canRequestAds
                .collectAsStateWithLifecycle()
            val privacyOptionsRequired by ConsentManager.privacyOptionsRequired
                .collectAsStateWithLifecycle()
            var showSettings by remember { mutableStateOf(false) }
            var forceShowGuide by remember { mutableStateOf(false) }

            LaunchedEffect(canRequestAds) {
                if (canRequestAds) {
                    MobileAdsHelper.ensureInitialized(applicationContext)
                }
            }

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
                            privacyOptionsRequired = privacyOptionsRequired,
                            onOpenPrivacyOptions = {
                                ConsentManager.showPrivacyOptionsForm(this) { error ->
                                    if (error != null) {
                                        viewModel.showSettingsMessage(
                                            "プライバシー設定を開けませんでした。",
                                        )
                                    }
                                }
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
                                pickJapaneseSrtLauncher.launch(SRT_MIME_TYPES)
                            },
                            onPickEnglishSrt = {
                                pickEnglishSrtLauncher.launch(SRT_MIME_TYPES)
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

        // UMP がフォーム表示に使う画面コンテナを setContent で作成してから、
        // 起動ごとの同意情報を更新し、広告リクエスト可否を確定する。
        ConsentManager.gatherConsent(this)
    }

    /**
     * Bluetooth機器などのメディアボタンを字幕操作へ割り当てる。
     *
     * ComponentActivity.dispatchKeyEvent は androidx 内部向けに
     * @RestrictedApi が付いているが、Activity のオーバーライドとしては正当なため抑制する。
     */
    @SuppressLint("RestrictedApi")
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
                        val keyEvent = IntentCompat.getParcelableExtra(
                            mediaButtonIntent,
                            android.content.Intent.EXTRA_KEY_EVENT,
                            KeyEvent::class.java,
                        )
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
     * URI 読み取り権限を永続化する。
     *
     * @param uri 永続化するURI
     * @return 永続権限を取得済み、または今回取得できた場合 true
     */
    private fun persistReadPermission(uri: android.net.Uri): Boolean {
        val persisted = runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        return persisted || contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    /**
     * 永続権限を取得できない場合に、次回再選択が必要な可能性を通知する。
     *
     * @param permissionPersisted 永続権限を取得できたか
     */
    private fun notifyIfPermissionIsTemporary(permissionPersisted: Boolean) {
        if (permissionPersisted) {
            return
        }
        Toast.makeText(
            this,
            "このファイルは次回起動時に再選択が必要になる場合があります。",
            Toast.LENGTH_LONG,
        ).show()
    }

    companion object {
        private val SRT_MIME_TYPES = arrayOf(
            "application/x-subrip",
            "text/plain",
            "text/srt",
            "application/octet-stream",
        )
    }
}
