package com.example.dailygoals.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.ui.components.AppIcon
import com.example.dailygoals.ui.components.AppIconKind

/**
 * A section of the goal form that stays closed until the user wants it. The closed row says what is set, so the
 * form can be checked without opening anything. A section with an error opens itself so the error is never hidden.
 */
@Composable
internal fun Fold(title: Int, summary: String, forceOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val shown = open || forceOpen
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val name = stringResource(title)
    val state = stringResource(if (shown) R.string.fold_open else R.string.fold_closed)
    Column(Modifier.padding(top = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.surface)
                .border(1.dp, scheme.outline, shape)
                .clickable(role = Role.Button) { open = !open }
                .semantics { contentDescription = "$name, $summary, $state" }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            }
            AppIcon(AppIconKind.CHEVRON_RIGHT, scheme.onSurfaceVariant, Modifier.rotate(if (shown) -90f else 90f))
        }
        if (shown) content()
    }
}
