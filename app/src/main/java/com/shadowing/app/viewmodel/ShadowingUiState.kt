package com.shadowing.app.viewmodel

import android.net.Uri
import com.shadowing.app.model.InternalSubtitleTrack
import com.shadowing.app.model.JapaneseMatchResult
import com.shadowing.app.model.SubtitleCue
import com.shadowing.app.model.SubtitleMatcher
import com.shadowing.app.model.withOffset

/**
 * シャドーイング画面のUI状態。
 */
data class ShadowingUiState(
    val videoUri: Uri? = null,
    val japaneseSrtUri: Uri? = null,
    val englishSrtUri: Uri? = null,
    val japaneseCues: List<SubtitleCue> = emptyList(),
    val englishCues: List<SubtitleCue> = emptyList(),
    val internalSubtitleTracks: List<InternalSubtitleTrack> = emptyList(),
    val isScanningInternalSubtitles: Boolean = false,
    val usePlayerSubtitleAsJapanese: Boolean = false,
    val usePlayerSubtitleAsEnglish: Boolean = false,
    val playerSubtitleText: String = "",
    val playerSubtitleCues: List<SubtitleCue> = emptyList(),
    val playerSubtitleIndex: Int = 0,
    val preferredTextLanguage: String? = null,
    val currentIndex: Int = 0,
    val englishOffsetMs: Long = 0L,
    val japaneseOffsetMs: Long = 0L,
    val isPlayingSegment: Boolean = false,
    val isPlayingVideo: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val segmentSeekRequestId: Long = 0L,
    val videoPlayRequestId: Long = 0L,
    val videoSeekRequestId: Long = 0L,
    val videoSeekPositionMs: Long = 0L,
    val sessionRestoreSeekMs: Long? = null,
    val playbackPositionMs: Long = 0L,
    val videoDurationMs: Long = 0L,
    val seekSliderOnRight: Boolean = true,
    val errorMessage: String? = null,
) {
    /** 英語字幕を基準にした総cue数。 */
    val totalCues: Int
        get() = if (usesPlayerSubtitles) playerSubtitleCues.size else englishCues.size

    /** ExoPlayer の内部字幕を表示に使っているか。 */
    val usesPlayerSubtitles: Boolean
        get() = usePlayerSubtitleAsJapanese || usePlayerSubtitleAsEnglish

    /** 現在の英語字幕（オフセット適用前）。 */
    val currentEnglish: SubtitleCue?
        get() = englishCues.getOrNull(currentIndex)

    /** 現在の英語字幕（オフセット適用後・再生基準）。 */
    val currentEnglishAdjusted: SubtitleCue?
        get() = currentEnglish?.withOffset(englishOffsetMs)

    /** 現在の英語区間に対する日本語マッチ判定。 */
    val japaneseMatchResult: JapaneseMatchResult?
        get() {
            val english = currentEnglishAdjusted ?: return null
            return SubtitleMatcher.findJapaneseMatch(
                reference = english,
                japaneseCues = japaneseCues,
                japaneseOffsetMs = japaneseOffsetMs,
            )
        }

    /** 表示用の日本語字幕（対応なしのときは null）。 */
    val matchedJapaneseAdjusted: SubtitleCue?
        get() = japaneseMatchResult
            ?.takeIf { it.hasDisplayableJapanese }
            ?.match
            ?.cue

    /** シーク先の開始位置（英語＋英語オフセット）。 */
    val seekStartMs: Long
        get() = if (usesPlayerSubtitles) {
            playerSubtitleCues.getOrNull(playerSubtitleIndex)?.startMs ?: 0L
        } else {
            currentEnglishAdjusted?.startMs ?: 0L
        }

    /** 現在cueの終了位置（英語＋英語オフセット）。 */
    val segmentEndMs: Long
        get() = if (usesPlayerSubtitles) {
            playerSubtitleCues.getOrNull(playerSubtitleIndex)?.endMs ?: 0L
        } else {
            currentEnglishAdjusted?.endMs ?: 0L
        }

    /** シークバーで使う動画長。動画長が未取得なら字幕の最終時刻を使う。 */
    val seekableDurationMs: Long
        get() = maxOf(
            videoDurationMs,
            englishCues.lastOrNull()?.withOffset(englishOffsetMs)?.endMs ?: 0L,
            playerSubtitleCues.lastOrNull()?.endMs ?: 0L,
        )

    val canNavigate: Boolean
        get() = videoUri != null &&
            if (usesPlayerSubtitles) {
                playerSubtitleCues.isNotEmpty()
            } else {
                englishCues.isNotEmpty()
            }
}
