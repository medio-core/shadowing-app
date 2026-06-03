package com.shadowing.app.parser

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.shadowing.app.model.InternalSubtitleTrack
import com.shadowing.app.model.SubtitleCue
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * 動画ファイル内のテキスト字幕トラックを読み取る。
 */
object InternalSubtitleExtractor {

    private const val DEFAULT_CUE_DURATION_MS = 3000L
    private const val MAX_SAMPLE_BYTES = 1024 * 1024

    private val supportedMimeTypes = setOf(
        "application/x-subrip",
        "text/srt",
        "text/vtt",
        "text/x-ssa",
        "text/x-ass",
        "application/ttml+xml",
        "application/x-mp4-vtt",
        "application/x-quicktime-tx3g",
    )

    /**
     * Content URI の動画から読み取り可能な内部字幕トラックを返す。
     *
     * @param context ContentResolver を取得するための Context
     * @param uri 動画ファイルの URI
     * @return テキストとして読み取れた字幕トラック一覧
     */
    fun extract(context: Context, uri: Uri): List<InternalSubtitleTrack> {
        val probeExtractor = MediaExtractor()
        return try {
            probeExtractor.setDataSource(context, uri, null)
            val candidates = (0 until probeExtractor.trackCount)
                .mapNotNull { index ->
                    val format = probeExtractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: return@mapNotNull null
                    if (!isSupportedTextSubtitle(mime)) {
                        return@mapNotNull null
                    }
                    SubtitleTrackCandidate(
                        trackIndex = index,
                        mimeType = mime,
                        language = format.getOptionalString(MediaFormat.KEY_LANGUAGE),
                    )
                }
            candidates.mapNotNull { candidate ->
                readTrack(context, uri, candidate)
            }
        } finally {
            probeExtractor.release()
        }
    }

    /**
     * 指定された字幕トラックのサンプルを読み取る。
     */
    private fun readTrack(
        context: Context,
        uri: Uri,
        candidate: SubtitleTrackCandidate,
    ): InternalSubtitleTrack? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            extractor.selectTrack(candidate.trackIndex)

            val samples = mutableListOf<SubtitleSample>()
            val buffer = ByteBuffer.allocate(MAX_SAMPLE_BYTES)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    break
                }
                val sampleTrackIndex = extractor.sampleTrackIndex
                if (sampleTrackIndex == candidate.trackIndex && size > 0) {
                    val bytes = ByteArray(size)
                    buffer.position(0)
                    buffer.get(bytes)
                    val text = decodeSampleText(candidate.mimeType, bytes)
                    if (text.isNotBlank()) {
                        samples += SubtitleSample(
                            startMs = extractor.sampleTime.coerceAtLeast(0L) / 1000L,
                            text = text,
                        )
                    }
                }
                extractor.advance()
            }

            val cues = samplesToCues(samples)
            if (cues.isEmpty()) {
                return null
            }
            InternalSubtitleTrack(
                trackIndex = candidate.trackIndex,
                language = candidate.language,
                mimeType = candidate.mimeType,
                cues = cues,
            )
        } finally {
            extractor.release()
        }
    }

    /**
     * サンプル列を字幕 cue に変換する。
     */
    private fun samplesToCues(samples: List<SubtitleSample>): List<SubtitleCue> {
        if (samples.isEmpty()) {
            return emptyList()
        }

        val joined = samples.joinToString("\n\n") { it.text }
        val parsed = SrtParser.parse(joined)
        if (parsed.isNotEmpty()) {
            return parsed
        }

        val cues = samples.mapIndexed { index, sample ->
            val nextStartMs = samples.getOrNull(index + 1)?.startMs
            val endMs = nextStartMs
                ?.takeIf { it > sample.startMs }
                ?: (sample.startMs + DEFAULT_CUE_DURATION_MS)
            SubtitleCue(
                index = index + 1,
                startMs = sample.startMs,
                endMs = endMs,
                text = cleanSubtitleText(sample.text),
            )
        }.filter { it.text.isNotBlank() }
        return SubtitlePostProcessor.process(cues)
    }

    /**
     * サンプルバイト列を文字列に変換する。
     */
    internal fun decodeSampleText(mimeType: String, bytes: ByteArray): String {
        val rawText = when (mimeType) {
            "application/x-quicktime-tx3g" -> decodeTx3gText(bytes)
            "application/x-mp4-vtt" -> decodeMp4VttText(bytes)
            else -> decodeTextBytes(bytes)
        }
        return cleanSubtitleText(rawText)
    }

    /**
     * tx3g サンプルから本文だけを取り出す。
     */
    private fun decodeTx3gText(bytes: ByteArray): String {
        if (bytes.size <= 2) {
            return ""
        }
        val declaredLength = ((bytes[0].toInt() and 0xFF) shl 8) or
            (bytes[1].toInt() and 0xFF)
        val textLength = declaredLength.coerceAtMost(bytes.size - 2)
        val textBytes = bytes.copyOfRange(2, 2 + textLength)
        return decodeTextBytes(textBytes)
    }

    /**
     * MP4 WebVTT サンプルから payl ボックスの本文だけを取り出す。
     */
    private fun decodeMp4VttText(bytes: ByteArray): String {
        val payloads = mutableListOf<String>()
        readMp4Boxes(bytes, 0, bytes.size) { type, start, end ->
            if (type == "vttc") {
                readMp4Boxes(bytes, start, end) { childType, childStart, childEnd ->
                    if (childType == "payl") {
                        payloads += decodeTextBytes(
                            bytes.copyOfRange(childStart, childEnd),
                        )
                    }
                }
            } else if (type == "payl") {
                payloads += decodeTextBytes(bytes.copyOfRange(start, end))
            }
        }
        return payloads.joinToString("\n")
            .ifBlank { decodeTextBytes(bytes) }
    }

    /**
     * MP4 ボックスを順に読み取る。
     */
    private fun readMp4Boxes(
        bytes: ByteArray,
        start: Int,
        end: Int,
        onBox: (String, Int, Int) -> Unit,
    ) {
        var offset = start
        while (offset + 8 <= end) {
            val size = bytes.readInt(offset)
            val type = bytes.copyOfRange(offset + 4, offset + 8)
                .toString(StandardCharsets.US_ASCII)
            if (size < 8 || offset + size > end) {
                break
            }
            onBox(type, offset + 8, offset + size)
            offset += size
        }
    }

    /**
     * 4バイトのビッグエンディアン整数を読み取る。
     */
    private fun ByteArray.readInt(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    /**
     * 文字コードを推定して字幕サンプルを文字列に変換する。
     */
    private fun decodeTextBytes(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 0xFE && second == 0xFF) {
                return bytes.copyOfRange(2, bytes.size)
                    .toString(StandardCharsets.UTF_16BE)
            }
            if (first == 0xFF && second == 0xFE) {
                return bytes.copyOfRange(2, bytes.size)
                    .toString(StandardCharsets.UTF_16LE)
            }
        }

        return bytes.toString(StandardCharsets.UTF_8)
    }

    /**
     * 表示に不要な制御文字や簡単な装飾タグを取り除く。
     */
    private fun cleanSubtitleText(text: String): String {
        return text
            .replace("\uFEFF", "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\{\\\\[^}]+\\}"), "")
            .replace(
                Regex("(?s)(styl|tbox|blnk|hlit|hclr|krok|ftab)\\b.*"),
                "",
            )
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
            .replace('\uFFFD', ' ')
            .lines()
            .mapNotNull { line ->
                SubtitlePostProcessor.cleanDialogueLineBeforeNoiseFix(line)
            }
            .map { line -> line.chooseReadableSubtitleLine() }
            .mapNotNull { line ->
                SubtitlePostProcessor.cleanDialogueLineBeforeNoiseFix(line)
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * 行頭の候補を採点し、最も字幕として自然な行を返す。
     */
    private fun String.chooseReadableSubtitleLine(): String {
        if (isBlank()) {
            return ""
        }
        val trimmed = trimStart()
        if (trimmed.firstOrNull()?.isLetter() == true) {
            return trimmed
        }
        return (0..2)
            .mapNotNull { dropCount ->
                if (dropCount >= length) {
                    null
                } else {
                    drop(dropCount).trimStart()
                }
            }
            .maxByOrNull { candidate -> candidate.readabilityScore() }
            ?: this
    }

    /**
     * 字幕としての自然さを点数化する。
     */
    private fun String.readabilityScore(): Int {
        if (isBlank()) {
            return Int.MIN_VALUE
        }

        var score = 0
        val first = first()
        if (first.isLetterOrDigit() || first == '"' || first == '\'' || first == '(') {
            score += 8
        }
        if (first.isLetter()) {
            score += 3
        }
        if (first.isDigit()) {
            score -= 2
        }
        if (!first.isLetterOrDigit() && first != '"' && first != '\'' && first != '(') {
            score -= 8
        }
        if (count { it == '(' } != count { it == ')' }) {
            score -= 6
        }
        if (contains(Regex("\\b(styl|tbox|blnk|hlit|hclr|krok|ftab)\\b"))) {
            score -= 20
        }
        score += count { it.isLetterOrDigit() || it.isWhitespace() }
        score -= count { !it.isLetterOrDigit() && !it.isWhitespace() } * 2
        score -= length.coerceAtMost(2)
        return score
    }

    /**
     * UI 用にテキストとして扱える字幕 MIME かを判定する。
     */
    private fun isSupportedTextSubtitle(mimeType: String): Boolean {
        return mimeType in supportedMimeTypes || mimeType.startsWith("text/")
    }

    /**
     * MediaFormat の任意文字列を安全に取得する。
     */
    private fun MediaFormat.getOptionalString(key: String): String? {
        return if (containsKey(key)) {
            getString(key)
        } else {
            null
        }
    }
}

/**
 * 読み取り対象の内部字幕トラック情報。
 */
private data class SubtitleTrackCandidate(
    val trackIndex: Int,
    val mimeType: String,
    val language: String?,
)

/**
 * MediaExtractor から読み取った字幕サンプル。
 */
private data class SubtitleSample(
    val startMs: Long,
    val text: String,
)
