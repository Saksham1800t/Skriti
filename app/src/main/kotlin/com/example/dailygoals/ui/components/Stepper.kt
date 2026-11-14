package com.example.dailygoals.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A labelled number with minus and plus buttons that stays inside [range]. */
@Composable
fun StepperRow(label: String, shown: String, range: IntRange, value: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { Text("−", fontSize = 20.sp) }
        Text(shown, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { Text("+", fontSize = 20.sp) }
    }
}
