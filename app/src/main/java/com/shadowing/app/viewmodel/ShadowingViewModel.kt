package com.shadowing.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shadowing.app.data.AppSettings
import com.shadowing.app.data.AppSettingsStore
import com.shadowing.app.data.PlaybackSession
import com.shadowing.app.data.PlaybackSessionStore
import com.shadowing.app.data.WorkSettings
import com.shadowing.app.data.WorkSettingsStore
import com.shadowing.app.model.InternalSubtitleTrack
import com.shadowing.app.model.SubtitleCue
import com.shadowing.app.model.SubtitleNavigator
import com.shadowing.app.model.withOffset
import com.shadowing.app.parser.InternalSubtitleExtractor
import com.shadowing.app.parser.SrtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 動画・字幕・シーク操作を管理するViewModel。
 */
class ShadowingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val OFFSET_STEP_MS = 100L
        const val OFFSET_LARGE_STEP_MS = 500L
        val PLAYBACK_SPEED_OPTIONS = listOf(
            0.5f,
            0.75f,
            1.0f,
            1.25f,
            1.5f,
            2.0f,
        )
        private const val PLAYER_SUBTITLE_DURATION_MS = 3000L
        private const val PLAYER_SUBTITLE_MATCH_WINDOW_MS = 1500L
        private const val SESSION_SAVE_INTERVAL_MS = 2000L
        private const val RESTORE_SUBTITLE_RETRY_COUNT = 3
        private const val RESTORE_SUBTITLE_RETRY_DELAY_MS = 250L
    }

    private val sessionStore = PlaybackSessionStore(application)
    private val workSettingsStore = WorkSettingsStore(application)
    private val appSettingsStore = AppSettingsStore(application)
    private val _appSettings = MutableStateFlow(appSettingsStore.load())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()
    private val _uiState = MutableStateFlow(ShadowingUiState())
    val uiState: StateFlow<ShadowingUiState> = _uiState.asStateFlow()
    private var isRestoringSession = false
    private var lastSessionSaveAtMs = 0L

    init {
        applyDefaultSettingsToUi()
        viewModelScope.launch {
            if (_appSettings.value.restoreSessionOnLaunch) {
                val restored = restoreLastSessionIfAvailable()
                if (!restored) {
                    applyDefaultSettingsToUi()
                }
            } else {
                applyDefaultSettingsToUi()
            }
        }
    }

    /**
     * 起動時セッション復元の ON/OFF を保存する。
     */
    fun setRestoreSessionOnLaunch(enabled: Boolean) {
        updateAppSettings { it.copy(restoreSessionOnLaunch = enabled) }
    }

    /**
     * 初期再生速度を保存する。
     */
    fun setDefaultPlaybackSpeed(speed: Float) {
        if (!PLAYBACK_SPEED_OPTIONS.contains(speed)) {
            return
        }
        updateAppSettings { it.copy(defaultPlaybackSpeed = speed) }
        _uiState.update { it.copy(playbackSpeed = speed) }
        persistSessionNow()
    }

    /**
     * 初期スライダー位置（右）を保存する。
     */
    fun setDefaultSeekSliderOnRight(onRight: Boolean) {
        updateAppSettings { it.copy(defaultSeekSliderOnRight = onRight) }
        _uiState.update { it.copy(seekSliderOnRight = onRight) }
        persistSessionNow()
    }

    /**
     * 初回ガイドを表示済みにする。
     */
    fun markGuideSeen() {
        updateAppSettings { it.copy(hasSeenGuide = true) }
    }

    /**
     * 使い方ガイドを再表示できるようフラグを戻す。
     */
    fun resetGuideSeen() {
        updateAppSettings { it.copy(hasSeenGuide = false) }
    }

    /**
     * 開発用の広告非表示設定を反映する。
     */
    fun setAdsRemoved(removed: Boolean) {
        updateAppSettings { it.copy(adsRemoved = removed) }
    }

    /**
     * 設定画面向けのメッセージを表示用に出す。
     */
    fun showSettingsMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    /**
     * 設定を更新して永続化する。
     */
    private fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_appSettings.value)
        _appSettings.value = updated
        appSettingsStore.save(updated)
    }

    /**
     * 保存済み初期値を UI 状態へ反映する。
     */
    private fun applyDefaultSettingsToUi() {
        val settings = _appSettings.value
        _uiState.update {
            it.copy(
                playbackSpeed = settings.defaultPlaybackSpeed,
                seekSliderOnRight = settings.defaultSeekSliderOnRight,
            )
        }
    }

    /**
     * 動画ファイルURIを設定する。
     */
    fun setVideoUri(uri: Uri) {
        val preset = workSettingsStore.loadForVideo(uri.toString())
        val defaults = _appSettings.value
        _uiState.update {
            it.copy(
                videoUri = uri,
                japaneseSrtUri = null,
                englishSrtUri = null,
                japaneseCues = emptyList(),
                englishCues = emptyList(),
                internalSubtitleTracks = emptyList(),
                isScanningInternalSubtitles = true,
                usePlayerSubtitleAsJapanese = false,
                usePlayerSubtitleAsEnglish = false,
                playerSubtitleText = "",
                playerSubtitleCues = emptyList(),
                playerSubtitleIndex = 0,
                preferredTextLanguage = null,
                isPlayingSegment = false,
                isPlayingVideo = false,
                sessionRestoreSeekMs = null,
                playbackPositionMs = 0L,
                videoDurationMs = 0L,
                currentIndex = 0,
                englishOffsetMs = preset?.englishOffsetMs ?: 0L,
                japaneseOffsetMs = preset?.japaneseOffsetMs ?: 0L,
                playbackSpeed = preset?.playbackSpeed ?: defaults.defaultPlaybackSpeed,
                seekSliderOnRight = preset?.seekSliderOnRight
                    ?: defaults.defaultSeekSliderOnRight,
                errorMessage = null,
            )
        }
        scanInternalSubtitles(uri, applyDetectedCues = true)
        if (preset != null) {
            viewModelScope.launch {
                loadWorkSubtitleUris(preset)
            }
        }
        persistSessionNow()
    }

    /**
     * 日本語SRTファイルを読み込む。
     */
    fun loadJapaneseSrt(uri: Uri) {
        loadSrt(uri, isJapanese = true)
    }

    /**
     * 英語SRTファイルを読み込む。
     */
    fun loadEnglishSrt(uri: Uri) {
        loadSrt(uri, isJapanese = false)
    }

    /**
     * 内部日本語字幕を読み込む。
     */
    fun loadInternalJapaneseSubtitle(trackIndex: Int) {
        loadInternalSubtitle(trackIndex = trackIndex, isJapanese = true)
    }

    /**
     * 内部英語字幕を読み込む。
     */
    fun loadInternalEnglishSubtitle(trackIndex: Int) {
        loadInternalSubtitle(trackIndex = trackIndex, isJapanese = false)
    }

    /**
     * 前の字幕へ移動する。
     */
    fun previousCue() {
        val state = _uiState.value
        if (!state.canNavigate) {
            return
        }
        if (state.usesPlayerSubtitles) {
            val newIndex = (state.playerSubtitleIndex - 1).coerceAtLeast(0)
            playPlayerSubtitleAtIndex(newIndex)
            return
        }
        val newIndex = (state.currentIndex - 1).coerceAtLeast(0)
        playCueAtIndex(newIndex)
    }

    /**
     * 次の字幕へ移動する。
     */
    fun nextCue() {
        val state = _uiState.value
        if (!state.canNavigate) {
            return
        }
        if (state.usesPlayerSubtitles) {
            val lastIndex = state.playerSubtitleCues.lastIndex
            val newIndex = (state.playerSubtitleIndex + 1).coerceAtMost(lastIndex)
            playPlayerSubtitleAtIndex(newIndex)
            return
        }
        val lastIndex = state.englishCues.lastIndex
        val newIndex = (state.currentIndex + 1).coerceAtMost(lastIndex)
        playCueAtIndex(newIndex)
    }

    /**
     * 指定インデックスの字幕区間へシークして再生する。
     */
    fun playCueAtIndex(index: Int) {
        val state = _uiState.value
        if (!state.canNavigate) {
            return
        }
        if (state.usesPlayerSubtitles) {
            val safeIndex = index.coerceIn(0, state.playerSubtitleCues.lastIndex)
            playPlayerSubtitleAtIndex(safeIndex)
            return
        }
        val safeIndex = index.coerceIn(0, state.englishCues.lastIndex)
        playSegmentAtIndex(safeIndex)
    }

    /**
     * 指定インデックスの字幕位置をプレビュー用に表示する。
     */
    fun previewCueAtIndex(index: Int) {
        val state = _uiState.value
        if (!state.canNavigate) {
            return
        }
        if (state.usesPlayerSubtitles) {
            val safeIndex = index.coerceIn(0, state.playerSubtitleCues.lastIndex)
            previewPlayerSubtitleAtIndex(safeIndex)
            return
        }
        val safeIndex = index.coerceIn(0, state.englishCues.lastIndex)
        val cue = state.englishCues[safeIndex].withOffset(state.englishOffsetMs)
        previewCuePosition(index = safeIndex, positionMs = cue.startMs)
    }

    /**
     * 英語字幕のタイミングオフセットをずらす。
     */
    fun nudgeEnglishOffset(deltaMs: Long) {
        _uiState.update {
            it.copy(
                englishOffsetMs = it.englishOffsetMs + deltaMs,
                isPlayingSegment = false,
                isPlayingVideo = false,
            )
        }
        persistSessionNow()
    }

    /**
     * 日本語字幕のタイミングオフセットをずらす。
     */
    fun nudgeJapaneseOffset(deltaMs: Long) {
        _uiState.update {
            it.copy(
                japaneseOffsetMs = it.japaneseOffsetMs + deltaMs,
                isPlayingSegment = false,
                isPlayingVideo = false,
            )
        }
        persistSessionNow()
    }

    /**
     * 英語字幕のオフセットをリセットする。
     */
    fun resetEnglishOffset() {
        _uiState.update {
            it.copy(
                englishOffsetMs = 0L,
                isPlayingSegment = false,
                isPlayingVideo = false,
            )
        }
        persistSessionNow()
    }

    /**
     * 日本語字幕のオフセットをリセットする。
     */
    fun resetJapaneseOffset() {
        _uiState.update {
            it.copy(
                japaneseOffsetMs = 0L,
                isPlayingSegment = false,
                isPlayingVideo = false,
            )
        }
        persistSessionNow()
    }

    /**
     * 動画の通常再生と一時停止を切り替える。
     */
    fun toggleVideoPlayback() {
        val state = _uiState.value
        if (state.videoUri == null) {
            return
        }

        if (state.isPlayingVideo) {
            _uiState.update {
                it.copy(
                    isPlayingSegment = false,
                    isPlayingVideo = false,
                    errorMessage = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isPlayingSegment = false,
                isPlayingVideo = true,
                videoPlayRequestId = it.videoPlayRequestId + 1L,
                errorMessage = null,
            )
        }
    }

    /**
     * 動画を指定位置へシークする。
     */
    fun seekVideoTo(positionMs: Long) {
        val state = _uiState.value
        val durationMs = state.seekableDurationMs
        if (state.videoUri == null || durationMs <= 0L) {
            return
        }

        val safePosition = positionMs.coerceIn(0L, durationMs)
        _uiState.update {
            it.copy(
                isPlayingSegment = false,
                videoSeekPositionMs = safePosition,
                videoSeekRequestId = it.videoSeekRequestId + 1L,
                playbackPositionMs = safePosition,
                errorMessage = null,
            )
        }
    }

    /**
     * 縦型シークスライダーの左右位置を切り替える。
     */
    fun toggleSeekSliderSide() {
        _uiState.update {
            it.copy(seekSliderOnRight = !it.seekSliderOnRight)
        }
        persistSessionNow()
    }

    /**
     * 選択された再生速度を設定する。
     */
    fun setPlaybackSpeed(speed: Float) {
        if (!PLAYBACK_SPEED_OPTIONS.contains(speed)) {
            return
        }
        _uiState.update {
            it.copy(playbackSpeed = speed)
        }
        persistSessionNow()
    }

    /**
     * 現在の字幕区間を先頭から再生する。
     */
    fun repeatCurrentSegment() {
        val state = _uiState.value
        if (!state.canNavigate) {
            return
        }
        _uiState.update {
            it.copy(
                isPlayingSegment = true,
                isPlayingVideo = false,
                segmentSeekRequestId = it.segmentSeekRequestId + 1L,
                errorMessage = null,
            )
        }
    }

    /**
     * 動画の現在位置に合わせて表示字幕を更新する。
     */
    fun onPlaybackPositionChanged(positionMs: Long, durationMs: Long) {
        _uiState.update { state ->
            val playbackUpdated = state.copy(
                playbackPositionMs = positionMs.coerceAtLeast(0L),
                videoDurationMs = durationMs.coerceAtLeast(0L),
            )

            if (playbackUpdated.usesPlayerSubtitles) {
                val playerIndex = findPlayerSubtitleIndexAtPosition(
                    cues = playbackUpdated.playerSubtitleCues,
                    positionMs = positionMs,
                ) ?: return@update playbackUpdated
                if (playerIndex == playbackUpdated.playerSubtitleIndex) {
                    playbackUpdated
                } else {
                    playbackUpdated.copy(playerSubtitleIndex = playerIndex)
                }
            } else if (
                playbackUpdated.isPlayingSegment ||
                playbackUpdated.englishCues.isEmpty()
            ) {
                playbackUpdated
            } else {
                val index = SubtitleNavigator.findCueIndexAtPosition(
                    cues = playbackUpdated.englishCues,
                    offsetMs = playbackUpdated.englishOffsetMs,
                    positionMs = positionMs,
                ) ?: return@update playbackUpdated

                if (index == playbackUpdated.currentIndex) {
                    playbackUpdated
                } else {
                    playbackUpdated.copy(currentIndex = index)
                }
            }
        }
        maybePersistSession()
    }

    /**
     * 起動時の復元シークが完了したことを記録する。
     */
    fun onSessionRestoreSeekApplied() {
        _uiState.update { it.copy(sessionRestoreSeekMs = null) }
        syncCueIndexToPlaybackPosition()
        persistSessionNow()
    }

    /**
     * 動画内字幕の履歴から現在位置に近い字幕を探す。
     */
    private fun findPlayerSubtitleIndexAtPosition(
        cues: List<SubtitleCue>,
        positionMs: Long,
    ): Int? {
        if (cues.isEmpty()) {
            return null
        }
        return cues.indexOfLast { cue ->
            positionMs >= cue.startMs
        }.takeIf { it >= 0 }
    }

    /**
     * セグメント再生が終了したときに呼ぶ。
     */
    fun onSegmentPlaybackFinished() {
        _uiState.update {
            it.copy(
                isPlayingSegment = false,
                isPlayingVideo = false,
            )
        }
    }

    /**
     * エラーメッセージをクリアする。
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        persistSessionNow()
        super.onCleared()
    }

    /**
     * 直ちに再生状態を保存する。
     */
    fun persistSessionNow() {
        val state = _uiState.value
        val videoUri = state.videoUri ?: return
        if (isRestoringSession) {
            return
        }
        val session = PlaybackSession(
            videoUri = videoUri.toString(),
            japaneseSrtUri = state.japaneseSrtUri?.toString(),
            englishSrtUri = state.englishSrtUri?.toString(),
            playbackPositionMs = state.playbackPositionMs,
            englishOffsetMs = state.englishOffsetMs,
            japaneseOffsetMs = state.japaneseOffsetMs,
            playbackSpeed = state.playbackSpeed,
            seekSliderOnRight = state.seekSliderOnRight,
        )
        sessionStore.save(session)
        workSettingsStore.upsertFromSession(session, System.currentTimeMillis())
        lastSessionSaveAtMs = System.currentTimeMillis()
    }

    /**
     * 指定インデックスの字幕区間へ移動して再生する。
     */
    private fun playSegmentAtIndex(index: Int) {
        _uiState.update {
            it.copy(
                currentIndex = index,
                isPlayingSegment = true,
                isPlayingVideo = false,
                segmentSeekRequestId = it.segmentSeekRequestId + 1L,
                errorMessage = null,
            )
        }
    }

    /**
     * 動画内字幕の履歴リストから指定位置へシークして再生する。
     */
    private fun playPlayerSubtitleAtIndex(index: Int) {
        _uiState.update {
            it.copy(
                playerSubtitleIndex = index,
                isPlayingSegment = true,
                isPlayingVideo = false,
                segmentSeekRequestId = it.segmentSeekRequestId + 1L,
                errorMessage = null,
            )
        }
    }

    /**
     * 通常字幕の位置を一時停止したままプレビューする。
     */
    private fun previewCuePosition(index: Int, positionMs: Long) {
        val state = _uiState.value
        val durationMs = state.seekableDurationMs
        if (state.videoUri == null || durationMs <= 0L) {
            return
        }
        val safePosition = positionMs.coerceIn(0L, durationMs)
        _uiState.update {
            it.copy(
                currentIndex = index,
                isPlayingSegment = false,
                isPlayingVideo = false,
                videoSeekPositionMs = safePosition,
                videoSeekRequestId = it.videoSeekRequestId + 1L,
                playbackPositionMs = safePosition,
                errorMessage = null,
            )
        }
    }

    /**
     * 動画内字幕の位置を一時停止したままプレビューする。
     */
    private fun previewPlayerSubtitleAtIndex(index: Int) {
        val state = _uiState.value
        val cue = state.playerSubtitleCues.getOrNull(index) ?: return
        val durationMs = state.seekableDurationMs
        if (state.videoUri == null || durationMs <= 0L) {
            return
        }
        val safePosition = cue.startMs.coerceIn(0L, durationMs)
        _uiState.update {
            it.copy(
                playerSubtitleIndex = index,
                isPlayingSegment = false,
                isPlayingVideo = false,
                videoSeekPositionMs = safePosition,
                videoSeekRequestId = it.videoSeekRequestId + 1L,
                playbackPositionMs = safePosition,
                errorMessage = null,
            )
        }
    }

    /**
     * SRTファイルを読み込んでパースする。
     */
    private fun loadSrt(
        uri: Uri,
        isJapanese: Boolean,
        resetIndex: Boolean = true,
    ) {
        viewModelScope.launch {
            loadSrtInternal(uri, isJapanese, resetIndex)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = error.message ?: "字幕の読み込みに失敗しました",
                        )
                    }
                }
        }
    }

    /**
     * SRTファイルを読み込んで状態へ反映する。
     */
    private suspend fun loadSrtInternal(
        uri: Uri,
        isJapanese: Boolean,
        resetIndex: Boolean,
    ): Result<Unit> {
        return runCatching {
            val content = readText(uri)
            val cues = SrtParser.parse(content)
            if (cues.isEmpty()) {
                error("字幕が1件も見つかりませんでした")
            }
            _uiState.update { state ->
                val updated = if (isJapanese) {
                    state.copy(
                        japaneseSrtUri = uri,
                        japaneseCues = cues,
                        usePlayerSubtitleAsJapanese = false,
                        playerSubtitleCues = emptyList(),
                        playerSubtitleIndex = 0,
                        isPlayingSegment = false,
                        isPlayingVideo = false,
                        errorMessage = null,
                    )
                } else {
                    state.copy(
                        englishSrtUri = uri,
                        englishCues = cues,
                        usePlayerSubtitleAsEnglish = false,
                        playerSubtitleCues = emptyList(),
                        playerSubtitleIndex = 0,
                        isPlayingSegment = false,
                        isPlayingVideo = false,
                        errorMessage = null,
                    )
                }
                if (resetIndex) {
                    updated.copy(currentIndex = 0)
                } else {
                    updated
                }
            }
            if (!resetIndex) {
                syncCueIndexToPlaybackPosition()
            }
            persistSessionNow()
        }
    }

    /**
     * 動画内のテキスト字幕を検出して、可能なら自動読み込みする。
     */
    private fun scanInternalSubtitles(uri: Uri, applyDetectedCues: Boolean) {
        viewModelScope.launch {
            scanInternalSubtitlesInternal(uri, applyDetectedCues)
        }
    }

    /**
     * 動画内字幕を検出して状態へ反映する。
     */
    private suspend fun scanInternalSubtitlesInternal(
        uri: Uri,
        applyDetectedCues: Boolean,
    ) {
        runCatching {
            withContext(Dispatchers.IO) {
                InternalSubtitleExtractor.extract(getApplication(), uri)
            }
        }.onSuccess { tracks ->
            _uiState.update { state ->
                if (state.videoUri != uri) {
                    return@update state
                }
                autoApplyInternalSubtitles(
                    state = state,
                    tracks = tracks,
                    applyDetectedCues = applyDetectedCues,
                )
            }
            if (!applyDetectedCues) {
                syncCueIndexToPlaybackPosition()
            }
        }.onFailure { error ->
            _uiState.update { state ->
                if (state.videoUri != uri) {
                    state
                } else {
                    state.copy(
                        isScanningInternalSubtitles = false,
                        errorMessage = "内部字幕の読み込みに失敗しました: " +
                            (error.message ?: "不明なエラー"),
                    )
                }
            }
        }
    }

    /**
     * 内部字幕を言語に応じて初期適用する。
     */
    private fun autoApplyInternalSubtitles(
        state: ShadowingUiState,
        tracks: List<InternalSubtitleTrack>,
        applyDetectedCues: Boolean,
    ): ShadowingUiState {
        if (!applyDetectedCues) {
            return state.copy(
                internalSubtitleTracks = tracks,
                isScanningInternalSubtitles = false,
            )
        }

        val englishTrack = tracks.firstOrNull {
            it.matchesAnyLanguage(
                "en",
                "eng",
                "english",
                "英語",
            )
        }
            ?: tracks.firstOrNull()
        val japaneseTrack = tracks.firstOrNull {
            it.matchesAnyLanguage(
                "ja",
                "jp",
                "jpn",
                "japanese",
                "日本語",
            )
        }

        val fallbackEnglishTrack = englishTrack ?: tracks.firstOrNull()
        val shouldApplyInternalEnglish = state.englishSrtUri == null ||
            state.englishCues.isEmpty()
        val shouldApplyInternalJapanese = state.japaneseSrtUri == null ||
            state.japaneseCues.isEmpty()

        return state.copy(
            internalSubtitleTracks = tracks,
            isScanningInternalSubtitles = false,
            usePlayerSubtitleAsEnglish = false,
            usePlayerSubtitleAsJapanese = false,
            playerSubtitleCues = emptyList(),
            playerSubtitleIndex = 0,
            preferredTextLanguage = null,
            englishCues = if (shouldApplyInternalEnglish) {
                fallbackEnglishTrack?.cues ?: state.englishCues
            } else {
                state.englishCues
            },
            japaneseCues = if (shouldApplyInternalJapanese) {
                japaneseTrack?.cues ?: state.japaneseCues
            } else {
                state.japaneseCues
            },
            currentIndex = if (shouldApplyInternalEnglish && fallbackEnglishTrack != null) {
                0
            } else {
                state.currentIndex
            },
            isPlayingSegment = false,
            isPlayingVideo = false,
            errorMessage = state.errorMessage,
        )
    }

    /**
     * 選択された内部字幕トラックを読み込む。
     */
    private fun loadInternalSubtitle(trackIndex: Int, isJapanese: Boolean) {
        _uiState.update { state ->
            val track = state.internalSubtitleTracks
                .firstOrNull { it.trackIndex == trackIndex }
                ?: return@update state.copy(
                    errorMessage = "選択した内部字幕が見つかりません",
                )
            if (isJapanese) {
                state.copy(
                    japaneseSrtUri = null,
                    japaneseCues = track.cues,
                    usePlayerSubtitleAsJapanese = false,
                    usePlayerSubtitleAsEnglish = false,
                    playerSubtitleCues = emptyList(),
                    playerSubtitleIndex = 0,
                    preferredTextLanguage = null,
                    currentIndex = 0,
                    isPlayingSegment = false,
                    isPlayingVideo = false,
                    errorMessage = null,
                )
            } else {
                state.copy(
                    englishSrtUri = null,
                    englishCues = track.cues,
                    usePlayerSubtitleAsJapanese = false,
                    usePlayerSubtitleAsEnglish = false,
                    playerSubtitleCues = emptyList(),
                    playerSubtitleIndex = 0,
                    preferredTextLanguage = null,
                    currentIndex = 0,
                    isPlayingSegment = false,
                    isPlayingVideo = false,
                    errorMessage = null,
                )
            }
        }
        persistSessionNow()
    }

    /**
     * 指定言語候補に近い内部字幕かを判定する。
     */
    private fun InternalSubtitleTrack.matchesAnyLanguage(
        vararg candidates: String,
    ): Boolean {
        val normalized = language
            ?.lowercase()
            ?.replace("_", "-")
            ?.trim()
            ?: return false
        return candidates.any { candidate ->
            normalized == candidate ||
                normalized.startsWith("$candidate-")
        }
    }

    /**
     * ExoPlayer が実際にデコードした字幕テキストを保存する。
     */
    fun onPlayerSubtitleTextChanged(text: String, positionMs: Long) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            return
        }

        _uiState.update { state ->
            if (!state.usesPlayerSubtitles) {
                return@update state
            }
            val safePositionMs = positionMs.coerceAtLeast(0L)
            val existingIndex = state.playerSubtitleCues.indexOfLast { cue ->
                cue.text == cleanText &&
                    distanceMs(cue.startMs, safePositionMs) <=
                    PLAYER_SUBTITLE_MATCH_WINDOW_MS
            }
            if (existingIndex >= 0) {
                return@update state.copy(
                    playerSubtitleText = cleanText,
                    playerSubtitleIndex = existingIndex,
                )
            }

            val closedCues = state.playerSubtitleCues.closeLastCueAt(safePositionMs)
            val newCue = SubtitleCue(
                index = closedCues.size + 1,
                startMs = safePositionMs,
                endMs = safePositionMs + PLAYER_SUBTITLE_DURATION_MS,
                text = cleanText,
            )
            state.copy(
                playerSubtitleText = cleanText,
                playerSubtitleCues = closedCues + newCue,
                playerSubtitleIndex = closedCues.size,
            )
        }
    }

    /**
     * 直前の動画内字幕の終了時刻を次の字幕開始時刻へ寄せる。
     */
    private fun List<SubtitleCue>.closeLastCueAt(positionMs: Long): List<SubtitleCue> {
        val lastCue = lastOrNull() ?: return this
        if (positionMs <= lastCue.startMs) {
            return this
        }
        return dropLast(1) + lastCue.copy(endMs = positionMs)
    }

    /**
     * 2つの時刻の差分を返す。
     */
    private fun distanceMs(firstMs: Long, secondMs: Long): Long {
        return if (firstMs >= secondMs) {
            firstMs - secondMs
        } else {
            secondMs - firstMs
        }
    }

    /**
     * Content URIからテキストを読み込む。
     */
    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver
            .openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("ファイルを開けませんでした")
    }

    /**
     * 前回の再生状態を復元する。
     *
     * @return 復元に成功した場合 true
     */
    private suspend fun restoreLastSessionIfAvailable(): Boolean {
        val session = sessionStore.load() ?: return false
        val videoUri = Uri.parse(session.videoUri)
        if (!canReadUri(videoUri)) {
            return false
        }

        isRestoringSession = true
        delay(300L)
        val restorePositionMs = session.playbackPositionMs.coerceAtLeast(0L)
        val hasExternalSubtitleUris = session.japaneseSrtUri != null ||
            session.englishSrtUri != null
        val preset = workSettingsStore.loadForVideo(session.videoUri)
        val defaults = _appSettings.value
        _uiState.update {
            it.copy(
                videoUri = videoUri,
                japaneseSrtUri = session.japaneseSrtUri?.let(Uri::parse),
                englishSrtUri = session.englishSrtUri?.let(Uri::parse),
                englishOffsetMs = session.englishOffsetMs,
                japaneseOffsetMs = session.japaneseOffsetMs,
                playbackSpeed = preset?.playbackSpeed
                    ?: defaults.defaultPlaybackSpeed,
                seekSliderOnRight = preset?.seekSliderOnRight
                    ?: defaults.defaultSeekSliderOnRight,
                internalSubtitleTracks = emptyList(),
                isScanningInternalSubtitles = true,
                usePlayerSubtitleAsJapanese = false,
                usePlayerSubtitleAsEnglish = false,
                playerSubtitleText = "",
                playerSubtitleCues = emptyList(),
                playerSubtitleIndex = 0,
                preferredTextLanguage = null,
                isPlayingSegment = false,
                isPlayingVideo = false,
                sessionRestoreSeekMs = restorePositionMs,
                playbackPositionMs = restorePositionMs,
                videoSeekPositionMs = restorePositionMs,
                videoDurationMs = 0L,
                currentIndex = 0,
                errorMessage = null,
            )
        }

        var loadedExternalSubtitle = false
        try {
            var attemptedExternalSubtitle = false
            session.japaneseSrtUri?.let { uriString ->
                attemptedExternalSubtitle = true
                val uri = Uri.parse(uriString)
                val result = loadSrtInternalWithRetry(
                    uri = uri,
                    isJapanese = true,
                )
                if (result) {
                    loadedExternalSubtitle = true
                }
            }
            session.englishSrtUri?.let { uriString ->
                attemptedExternalSubtitle = true
                val uri = Uri.parse(uriString)
                val result = loadSrtInternalWithRetry(
                    uri = uri,
                    isJapanese = false,
                )
                if (result) {
                    loadedExternalSubtitle = true
                }
            }

            scanInternalSubtitlesInternal(
                uri = videoUri,
                applyDetectedCues = true,
            )
            syncCueIndexToPlaybackPosition()
            if (attemptedExternalSubtitle && !loadedExternalSubtitle) {
                _uiState.update {
                    it.copy(
                        errorMessage = "前回の外部字幕を復元できませんでした。" +
                            "必要なら字幕を再選択してください",
                    )
                }
            }
        } finally {
            isRestoringSession = false
            if (!hasExternalSubtitleUris || loadedExternalSubtitle) {
                persistSessionNow()
            }
        }
        return true
    }

    /**
     * 作品プリセットに保存された外部字幕 URI を読み込む。
     */
    private suspend fun loadWorkSubtitleUris(preset: WorkSettings) {
        preset.japaneseSrtUri?.let { uriString ->
            val uri = Uri.parse(uriString)
            if (canReadUri(uri)) {
                loadSrtInternalWithRetry(uri = uri, isJapanese = true)
            }
        }
        preset.englishSrtUri?.let { uriString ->
            val uri = Uri.parse(uriString)
            if (canReadUri(uri)) {
                loadSrtInternalWithRetry(uri = uri, isJapanese = false)
            }
        }
    }

    /**
     * 復元用の字幕読み込みを短いリトライ付きで実行する。
     */
    private suspend fun loadSrtInternalWithRetry(uri: Uri, isJapanese: Boolean): Boolean {
        repeat(RESTORE_SUBTITLE_RETRY_COUNT) { attempt ->
            val result = loadSrtInternal(
                uri = uri,
                isJapanese = isJapanese,
                resetIndex = false,
            )
            if (result.isSuccess) {
                return true
            }
            if (attempt < RESTORE_SUBTITLE_RETRY_COUNT - 1) {
                delay(RESTORE_SUBTITLE_RETRY_DELAY_MS)
            }
        }
        return false
    }

    /**
     * 再生位置に合わせて現在の字幕インデックスを更新する。
     */
    private fun syncCueIndexToPlaybackPosition() {
        _uiState.update { state ->
            if (state.englishCues.isEmpty()) {
                return@update state
            }
            val index = SubtitleNavigator.findCueIndexAtPosition(
                cues = state.englishCues,
                offsetMs = state.englishOffsetMs,
                positionMs = state.playbackPositionMs,
            ) ?: return@update state
            if (index == state.currentIndex) {
                state
            } else {
                state.copy(currentIndex = index)
            }
        }
    }

    /**
     * 一定間隔で再生状態を保存する。
     */
    private fun maybePersistSession() {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastSessionSaveAtMs < SESSION_SAVE_INTERVAL_MS) {
            return
        }
        persistSessionNow()
    }

    /**
     * URI が読み取り可能か確認する。
     */
    private fun canReadUri(uri: Uri): Boolean {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.openInputStream(uri)?.use { } ?: error("cannot open")
        }.isSuccess
    }
}
