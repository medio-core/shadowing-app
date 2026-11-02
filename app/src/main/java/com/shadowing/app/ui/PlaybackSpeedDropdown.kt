package com.shadowing.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 再生速度をプルダウンで選択する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedDropdown(
    label: String,
    selectedSpeed: Float,
    options: List<Float>,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = formatPlaybackSpeedLabel(selectedSpeed),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { speed ->
                DropdownMenuItem(
                    text = { Text(formatPlaybackSpeedLabel(speed)) },
                    onClick = {
                        onSpeedSelected(speed)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * 再生速度ラベルを表示用に整形する。
 */
fun formatPlaybackSpeedLabel(speed: Float): String {
    val intSpeed = speed.toInt()
    return if (speed == intSpeed.toFloat()) {
        "${intSpeed}.0x"
    } else {
        "${speed}x"
    }
}
