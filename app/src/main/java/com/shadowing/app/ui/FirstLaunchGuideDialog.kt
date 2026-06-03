package com.shadowing.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 初回起動時の使い方ガイドを表示する。
 */
@Composable
fun FirstLaunchGuideDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使い方") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("1. 動画を選択する")
                Text("2. 英語・翻訳の字幕を選択する")
                Text("3. 字幕欄を横スワイプで英語・翻訳を切り替える")
                Text("4. 字幕を上下スクロールして区間へ移動する")
                Text("5. 再生ボタンで区間リピート練習する")
                Text("6. 前へ・次へで前後の字幕へ移動する")
                Text("設定から前回セッション復元や初期速度を変更できます")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("はじめる")
            }
        },
    )
}
