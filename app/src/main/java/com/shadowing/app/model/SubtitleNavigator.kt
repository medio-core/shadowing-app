package com.shadowing.app.model

/**
 * 再生位置から表示すべき字幕を探す。
 */
object SubtitleNavigator {

    /**
     * 指定した再生位置を含む英語字幕のインデックスを返す。
     *
     * @param cues 英語字幕リスト
     * @param offsetMs 英語字幕に適用するオフセット
     * @param positionMs 動画の現在再生位置
     * @return 該当する字幕インデックス。見つからない場合は null
     */
    fun findCueIndexAtPosition(
        cues: List<SubtitleCue>,
        offsetMs: Long,
        positionMs: Long,
    ): Int? {
        return cues.indexOfFirst { cue ->
            val adjusted = cue.withOffset(offsetMs)
            positionMs >= adjusted.startMs && positionMs < adjusted.endMs
        }.takeIf { it >= 0 }
    }

}
