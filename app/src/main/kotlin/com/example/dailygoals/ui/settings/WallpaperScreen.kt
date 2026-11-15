package com.example.dailygoals.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.WallpaperMode
import com.example.dailygoals.engine.WallpaperRestore
import com.example.dailygoals.engine.WallpaperStyle
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.wallpaper.WallpaperPreviewActivity

@Composable
fun WallpaperScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { (context.applicationContext as SkritiApp).wallpaper }
    val store = controller.store
    var style by remember { mutableStateOf(store.style) }
    var mode by remember { mutableStateOf(store.mode) }
    var restore by remember { mutableStateOf(store.restore) }
    var lock by remember { mutableStateOf(store.includeLockScreen) }
    var hasNormal by remember { mutableStateOf(store.hasNormalWallpaper) }
    var live by remember { mutableStateOf(controller.liveActive()) }
    var failed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { live = controller.liveActive() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = controller.saveNormalWallpaper(uri)
            hasNormal = store.hasNormalWallpaper
            failed = !ok
        }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        PageHeader(R.string.wallpaper_title, onClose)
        Text(stringResource(R.string.wallpaper_help), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))

        Label(R.string.wallpaper_style)
        SegmentedChoice(
            options = listOf(
                WallpaperStyle.PULSE to stringResource(R.string.wallpaper_style_pulse),
                WallpaperStyle.EMBERS to stringResource(R.string.wallpaper_style_embers),
                WallpaperStyle.GLITCH to stringResource(R.string.wallpaper_style_glitch),
                WallpaperStyle.MINIMAL to stringResource(R.string.wallpaper_style_minimal),
            ),
            selected = style,
            onSelect = { style = it; store.style = it },
        )
        SettingsButton(stringResource(R.string.wallpaper_preview), Modifier.padding(top = 10.dp)) {
            context.startActivity(Intent(context, WallpaperPreviewActivity::class.java))
        }

        Label(R.string.wallpaper_normal)
        Text(
            stringResource(if (hasNormal) R.string.wallpaper_normal_set else R.string.wallpaper_normal_missing),
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Text(stringResource(R.string.wallpaper_normal_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (failed) Text(stringResource(R.string.wallpaper_normal_failed), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        SettingsButton(
            stringResource(if (hasNormal) R.string.wallpaper_normal_change else R.string.wallpaper_normal_pick),
            Modifier.padding(top = 8.dp),
        ) { picker.launch("image/*") }

        Label(R.string.wallpaper_mode)
        SegmentedChoice(
            options = listOf(
                WallpaperMode.ANIMATED to stringResource(R.string.wallpaper_mode_animated),
                WallpaperMode.STATIC to stringResource(R.string.wallpaper_mode_static),
            ),
            selected = mode,
            onSelect = { mode = it; store.mode = it },
        )
        Text(stringResource(R.string.wallpaper_live_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Text(
            stringResource(if (live) R.string.wallpaper_live_active else R.string.wallpaper_live_inactive),
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!live) {
            SettingsButton(stringResource(R.string.wallpaper_live_setup), Modifier.padding(top = 8.dp)) {
                runCatching { context.startActivity(controller.setupIntent()) }
            }
        }
        Text(stringResource(R.string.wallpaper_live_back), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

        Label(R.string.wallpaper_restore)
        SegmentedChoice(
            options = listOf(
                WallpaperRestore.MIDNIGHT to stringResource(R.string.wallpaper_restore_midnight),
                WallpaperRestore.BLOCK_END to stringResource(R.string.wallpaper_restore_block),
            ),
            selected = restore,
            onSelect = { restore = it; store.restore = it },
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.wallpaper_lock), fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(checked = lock, onCheckedChange = { lock = it; store.includeLockScreen = it })
        }
    }
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}
