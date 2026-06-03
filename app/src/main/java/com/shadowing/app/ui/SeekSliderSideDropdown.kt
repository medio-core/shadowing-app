package com.shadowing.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * シークスライダーの左右をプルダウンで選択する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekSliderSideDropdown(
    label: String,
    onRight: Boolean,
    onSideSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = formatSeekSliderSideLabel(onRight),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("左") },
                onClick = {
                    onSideSelected(false)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("右") },
                onClick = {
                    onSideSelected(true)
                    expanded = false
                },
            )
        }
    }
}

/**
 * スライダー位置ラベルを表示用に整形する。
 */
fun formatSeekSliderSideLabel(onRight: Boolean): String {
    return if (onRight) {
        "右"
    } else {
        "左"
    }
}
