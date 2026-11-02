package com.shadowing.app.viewmodel

/**
 * 1件の字幕読み込み要求を識別する世代情報。
 *
 * @property videoVersion 対象動画の世代
 * @property subtitleVersion 対象言語の字幕要求世代
 * @property isJapanese 日本語字幕の要求か
 */
internal data class SubtitleLoadVersion(
    val videoVersion: Long,
    val subtitleVersion: Long,
    val isJapanese: Boolean,
)

/**
 * 動画・字幕の非同期読み込み要求を世代番号で管理する。
 */
internal class LoadRequestVersions {

    private var videoVersion = 0L
    private var japaneseSubtitleVersion = 0L
    private var englishSubtitleVersion = 0L
    private var userSelectionVersion = 0L

    /** 現在の動画世代を返す。 */
    fun currentVideoVersion(): Long = videoVersion

    /** 現在のユーザー選択世代を返す。 */
    fun currentUserSelectionVersion(): Long = userSelectionVersion

    /**
     * 動画または字幕に対するユーザー操作を記録する。
     *
     * @return 新しいユーザー選択世代
     */
    fun markUserSelection(): Long {
        userSelectionVersion += 1L
        return userSelectionVersion
    }

    /**
     * 指定したユーザー選択世代が現在も有効か判定する。
     *
     * @param expectedVersion 確認するユーザー選択世代
     * @return 操作が追加されていなければ true
     */
    fun isCurrentUserSelection(expectedVersion: Long): Boolean {
        return userSelectionVersion == expectedVersion
    }

    /**
     * 新しい動画選択を開始し、以前の字幕要求をすべて無効化する。
     *
     * @return 新しい動画世代
     */
    fun startVideoSelection(): Long {
        videoVersion += 1L
        japaneseSubtitleVersion += 1L
        englishSubtitleVersion += 1L
        return videoVersion
    }

    /**
     * 指定動画世代が現在も有効か判定する。
     *
     * @param expectedVideoVersion 確認する動画世代
     * @return 現在の動画世代なら true
     */
    fun isCurrentVideo(expectedVideoVersion: Long): Boolean {
        return videoVersion == expectedVideoVersion
    }

    /**
     * 新しい字幕読み込みを開始する。
     *
     * @param isJapanese 日本語字幕なら true
     * @return 読み込み結果の反映可否を確認するための世代情報
     */
    fun startSubtitleLoad(isJapanese: Boolean): SubtitleLoadVersion {
        val subtitleVersion = if (isJapanese) {
            japaneseSubtitleVersion += 1L
            japaneseSubtitleVersion
        } else {
            englishSubtitleVersion += 1L
            englishSubtitleVersion
        }
        return SubtitleLoadVersion(
            videoVersion = videoVersion,
            subtitleVersion = subtitleVersion,
            isJapanese = isJapanese,
        )
    }

    /**
     * 字幕読み込み要求が現在も有効か判定する。
     *
     * @param version 確認する字幕要求世代
     * @return 同じ動画・同じ言語の最新要求なら true
     */
    fun isCurrent(version: SubtitleLoadVersion): Boolean {
        if (videoVersion != version.videoVersion) {
            return false
        }
        val currentSubtitleVersion = if (version.isJapanese) {
            japaneseSubtitleVersion
        } else {
            englishSubtitleVersion
        }
        return currentSubtitleVersion == version.subtitleVersion
    }
}
