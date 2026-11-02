package com.shadowing.app.parser

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 入力サイズを制限して UTF-8 テキストを読み込む。
 */
object LimitedTextReader {

    /**
     * 入力を上限バイト数まで読み込み、UTF-8文字列へ変換する。
     *
     * @param input 読み込む入力ストリーム
     * @param maxBytes 許容する最大バイト数
     * @return 読み込んだUTF-8文字列
     * @throws IllegalArgumentException 入力が上限を超えた場合
     */
    fun readUtf8(input: InputStream, maxBytes: Int): String {
        require(maxBytes > 0) { "最大バイト数は1以上で指定してください" }
        val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val readBytes = input.read(buffer)
            if (readBytes < 0) {
                break
            }
            if (totalBytes + readBytes > maxBytes) {
                throw IllegalArgumentException(
                    "字幕ファイルが大きすぎます（上限 ${maxBytes / (1024 * 1024)}MB）",
                )
            }
            output.write(buffer, 0, readBytes)
            totalBytes += readBytes
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private const val DEFAULT_BUFFER_SIZE = 8 * 1024
}
