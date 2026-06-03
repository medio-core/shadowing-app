package com.shadowing.app.data

/**
 * 作品（動画 URI）ごとに保存する練習設定。
 */
data class WorkSettings(
    val videoUri: String,
    val japaneseSrtUri: String?,
    val englishSrtUri: String?,
    val englishOffsetMs: Long,
    val japaneseOffsetMs: Long,
    val playbackSpeed: Float,
    val seekSliderOnRight: Boolean,
    val lastUsedAtMs: Long,
)

/**
 * 作品設定リストの更新ルール（最大件数・LRU）。
 */
object WorkSettingsRepository {

    const val MAX_ENTRIES = 30

    /**
     * 1件を追加または更新し、最大30件に切り詰める。
     *
     * @param entries 既存リスト
     * @param entry 保存する設定（lastUsedAtMs は上書きされる）
     * @param nowMs 最終利用時刻
     */
    fun upsert(
        entries: List<WorkSettings>,
        entry: WorkSettings,
        nowMs: Long = System.currentTimeMillis(),
    ): List<WorkSettings> {
        val updatedEntry = entry.copy(lastUsedAtMs = nowMs)
        val without = entries.filter { it.videoUri != entry.videoUri }
        return (without + updatedEntry)
            .sortedByDescending { it.lastUsedAtMs }
            .take(MAX_ENTRIES)
    }

    /**
     * 動画 URI に一致する設定を返す。
     */
    fun findByVideoUri(
        entries: List<WorkSettings>,
        videoUri: String,
    ): WorkSettings? {
        return entries.firstOrNull { it.videoUri == videoUri }
    }
}
