package com.shadowing.app.model

/**
 * 動画ファイル内から読み取ったテキスト字幕トラック。
 */
data class InternalSubtitleTrack(
    val trackIndex: Int,
    val language: String?,
    val mimeType: String,
    val cues: List<SubtitleCue>,
) {
    /** 選択ダイアログに表示するラベル。 */
    val displayName: String
        get() = buildString {
            append("内部 #")
            append(trackIndex)
            if (!language.isNullOrBlank()) {
                append(" ")
                append(language)
            }
            append(" / ")
            append(mimeType)
            append(" / ")
            append(cues.size)
            append("件")
        }
}
