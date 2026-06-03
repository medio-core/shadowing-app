package com.shadowing.app.parser

import com.shadowing.app.model.SubtitleCue

/**
 * 読み込んだ字幕を表示・シーク向けに整える。
 */
object SubtitlePostProcessor {

    private const val MAX_MERGE_GAP_MS = 1200L
    private val soundEffectKeywords = setOf(
        "APPLAUSE",
        "BEEP",
        "CRYING",
        "DOOR",
        "FOREIGN LANGUAGE",
        "GIGGLING",
        "GROAN",
        "INDISTINCTLY",
        "LAUGH",
        "MUSIC",
        "PLAYING",
        "RINGING",
        "SCREAM",
        "SIGH",
        "SPEAKERS",
        "SPEAKING",
        "WHISPERING",
    )
    private val soundEffectPhrases = setOf(
        "FOREIGN LANGUAGE",
        "MUSIC PLAYING",
        "SPEAKING FOREIGN LANGUAGE",
    )

    /**
     * 効果音行の除外と文断片の結合を行う。
     *
     * @param cues 読み込み直後の字幕リスト
     * @return 表示用に整えた字幕リスト
     */
    fun process(cues: List<SubtitleCue>): List<SubtitleCue> {
        val dialogueCues = cues
            .mapNotNull { cue -> cue.withoutSoundEffectLines() }
            .sortedBy { it.startMs }

        return mergeSentenceFragments(dialogueCues)
            .mapIndexed { index, cue -> cue.copy(index = index + 1) }
    }

    /**
     * 1行が効果音・ト書きなら null、それ以外ならトリム済み文字列を返す。
     */
    fun cleanDialogueLineBeforeNoiseFix(line: String): String? {
        val trimmed = line.trim()
        return if (
            trimmed.isBlank() ||
            trimmed.isSoundEffectLine() ||
            trimmed.hasSoundEffectLineAfterLeadingNoise()
        ) {
            null
        } else {
            trimmed
        }
    }

    /**
     * 字幕内から括弧で囲まれた効果音行を取り除く。
     */
    private fun SubtitleCue.withoutSoundEffectLines(): SubtitleCue? {
        val dialogueText = text
            .lines()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.isSoundEffectLine() }
            .joinToString("\n")
            .trim()

        return if (dialogueText.isBlank()) {
            null
        } else {
            copy(text = dialogueText)
        }
    }

    /**
     * 括弧だけで構成された行を効果音・ト書きとして扱う。
     */
    private fun String.isSoundEffectLine(): Boolean {
        val trimmed = trim()
        return (trimmed.startsWith("(") && trimmed.endsWith(")")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("（") && trimmed.endsWith("）")) ||
            trimmed.isMissingOpeningBracketSoundEffect() ||
            trimmed.isUnbracketedSoundEffectLine()
    }

    /**
     * 括弧の前に短い装飾ノイズが付いた効果音行かを判定する。
     */
    private fun String.hasSoundEffectLineAfterLeadingNoise(): Boolean {
        val bracketIndex = indexOfFirst { char ->
            char == '(' || char == '[' || char == '（'
        }
        return bracketIndex in 1..2 && drop(bracketIndex).isSoundEffectLine()
    }

    /**
     * 先頭括弧だけ欠けた効果音行かを判定する。
     */
    private fun String.isMissingOpeningBracketSoundEffect(): Boolean {
        val trimmed = trim()
        val hasClosingBracket = trimmed.endsWith(")") ||
            trimmed.endsWith("]") ||
            trimmed.endsWith("）")
        if (!hasClosingBracket) {
            return false
        }
        val letterCount = trimmed.count { it.isLetter() }
        if (letterCount < 3) {
            return false
        }
        return trimmed.dropLast(1).isUnbracketedSoundEffectLine()
    }

    /**
     * 括弧が失われた効果音・ト書き行かを判定する。
     */
    private fun String.isUnbracketedSoundEffectLine(): Boolean {
        val normalized = trim()
            .replace(Regex("[^A-Za-z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .uppercase()
            .trim()
        if (normalized.length < 4) {
            return false
        }
        if (normalized in soundEffectPhrases) {
            return true
        }
        val words = normalized.split(" ")
            .filter { it.isNotBlank() }
        return words.isNotEmpty() && words.all { word ->
            word in soundEffectKeywords
        }
    }

    /**
     * 途中で切られた1文らしい隣接字幕を結合する。
     */
    private fun mergeSentenceFragments(cues: List<SubtitleCue>): List<SubtitleCue> {
        val merged = mutableListOf<SubtitleCue>()
        cues.forEach { cue ->
            val previous = merged.lastOrNull()
            if (previous != null && shouldMerge(previous, cue)) {
                merged[merged.lastIndex] = previous.copy(
                    endMs = cue.endMs,
                    text = joinSentenceFragments(previous.text, cue.text),
                )
            } else {
                merged += cue
            }
        }
        return merged
    }

    /**
     * 前後の字幕が同じ文の分割かを判定する。
     */
    private fun shouldMerge(previous: SubtitleCue, next: SubtitleCue): Boolean {
        val gapMs = next.startMs - previous.endMs
        if (gapMs < 0L || gapMs > MAX_MERGE_GAP_MS) {
            return false
        }
        if (previous.text.hasSentenceEnding()) {
            return false
        }
        return next.text.startsWithContinuation()
    }

    /**
     * 文末として自然な記号で終わっているかを返す。
     */
    private fun String.hasSentenceEnding(): Boolean {
        val last = trim()
            .trimEnd('"', '\'', '”', '’', ')', ']', '）')
            .lastOrNull()
        return last in setOf('.', '?', '!', '。', '？', '！', '…')
    }

    /**
     * 次字幕が文の続きらしく始まっているかを返す。
     */
    private fun String.startsWithContinuation(): Boolean {
        val trimmed = trimStart('"', '\'', '“', '‘').trimStart()
        val first = trimmed.firstOrNull() ?: return false
        return first.isLowerCase()
    }

    /**
     * 2つの文断片を1文として結合する。
     */
    private fun joinSentenceFragments(first: String, second: String): String {
        return first.trimEnd() + " " + second.trimStart()
    }
}
