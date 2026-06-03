package com.shadowing.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 作品ごとの設定を最大30件まで SharedPreferences に保存する。
 */
class WorkSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存済みの全作品設定を読み込む。
     */
    fun loadAll(): List<WorkSettings> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { decodeEntries(raw) }.getOrDefault(emptyList())
    }

    /**
     * 指定動画の設定を読み込む。
     */
    fun loadForVideo(videoUri: String): WorkSettings? {
        return WorkSettingsRepository.findByVideoUri(loadAll(), videoUri)
    }

    /**
     * 1作品分の設定を保存する（LRU で最大30件）。
     */
    fun upsert(settings: WorkSettings) {
        val merged = WorkSettingsRepository.upsert(loadAll(), settings)
        prefs.edit().putString(KEY_ENTRIES, encodeEntries(merged)).apply()
    }

    /**
     * PlaybackSession から作品設定へ変換する。
     */
    fun upsertFromSession(session: PlaybackSession, nowMs: Long) {
        upsert(
            WorkSettings(
                videoUri = session.videoUri,
                japaneseSrtUri = session.japaneseSrtUri,
                englishSrtUri = session.englishSrtUri,
                englishOffsetMs = session.englishOffsetMs,
                japaneseOffsetMs = session.japaneseOffsetMs,
                playbackSpeed = session.playbackSpeed,
                seekSliderOnRight = session.seekSliderOnRight,
                lastUsedAtMs = nowMs,
            ),
        )
    }

    companion object {
        private const val PREFS_NAME = "work_settings"
        private const val KEY_ENTRIES = "entries_v2"
        private const val FIELD_SEP = '\u001f'

        /**
         * 保存形式を作品設定リストへ復元する。
         */
        fun decodeEntries(raw: String): List<WorkSettings> {
            if (raw.isBlank()) {
                return emptyList()
            }
            return raw.lineSequence()
                .filter { it.isNotBlank() }
                .map { decodeLine(it) }
                .toList()
        }

        /**
         * 作品設定リストを保存用文字列へ変換する。
         */
        fun encodeEntries(entries: List<WorkSettings>): String {
            return entries.joinToString("\n") { encodeLine(it) }
        }

        private fun encodeLine(settings: WorkSettings): String {
            return listOf(
                escape(settings.videoUri),
                escape(settings.japaneseSrtUri.orEmpty()),
                escape(settings.englishSrtUri.orEmpty()),
                settings.englishOffsetMs.toString(),
                settings.japaneseOffsetMs.toString(),
                settings.playbackSpeed.toString(),
                if (settings.seekSliderOnRight) "1" else "0",
                settings.lastUsedAtMs.toString(),
            ).joinToString(FIELD_SEP.toString())
        }

        private fun decodeLine(line: String): WorkSettings {
            val parts = splitEscaped(line)
            require(parts.size == 8) { "Invalid work settings line" }
            return WorkSettings(
                videoUri = unescape(parts[0]),
                japaneseSrtUri = parts[1].ifBlank { null }?.let(::unescape),
                englishSrtUri = parts[2].ifBlank { null }?.let(::unescape),
                englishOffsetMs = parts[3].toLong(),
                japaneseOffsetMs = parts[4].toLong(),
                playbackSpeed = parts[5].toFloat(),
                seekSliderOnRight = parts[6] == "1",
                lastUsedAtMs = parts[7].toLong(),
            )
        }

        private fun splitEscaped(line: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var index = 0
            while (index < line.length) {
                val ch = line[index]
                if (ch == '\\' && index + 1 < line.length) {
                    when (line[index + 1]) {
                        '\\' -> current.append('\\')
                        'f' -> current.append(FIELD_SEP)
                        else -> current.append(line[index + 1])
                    }
                    index += 2
                    continue
                }
                if (ch == FIELD_SEP) {
                    result.add(current.toString())
                    current.clear()
                    index++
                    continue
                }
                current.append(ch)
                index++
            }
            result.add(current.toString())
            return result
        }

        private fun escape(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace(FIELD_SEP.toString(), "\\f")
        }

        private fun unescape(value: String): String {
            val out = StringBuilder()
            var index = 0
            while (index < value.length) {
                if (value[index] == '\\' && index + 1 < value.length) {
                    when (value[index + 1]) {
                        '\\' -> out.append('\\')
                        'f' -> out.append(FIELD_SEP)
                        else -> out.append(value[index + 1])
                    }
                    index += 2
                    continue
                }
                out.append(value[index])
                index++
            }
            return out.toString()
        }
    }
}
