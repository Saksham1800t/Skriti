package com.example.dailygoals.wallpaper

import android.content.Context
import android.content.SharedPreferences
import com.example.dailygoals.engine.WallpaperMode
import com.example.dailygoals.engine.WallpaperRestore
import com.example.dailygoals.engine.WallpaperStyle
import com.example.dailygoals.engine.WarningWallpaper
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The wallpaper's settings and the warning it should show now, in plain preferences so the live wallpaper
 * service can read them with no screen of the app open and hear about changes at once.
 */
class WallpaperStore(private val context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var style: WallpaperStyle
        get() = runCatching { WallpaperStyle.valueOf(prefs.getString(KEY_STYLE, null).orEmpty()) }.getOrDefault(WallpaperStyle.PULSE)
        set(value) = prefs.edit().putString(KEY_STYLE, value.name).apply()

    var mode: WallpaperMode
        get() = runCatching { WallpaperMode.valueOf(prefs.getString(KEY_MODE, null).orEmpty()) }.getOrDefault(WallpaperMode.ANIMATED)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var restore: WallpaperRestore
        get() = runCatching { WallpaperRestore.valueOf(prefs.getString(KEY_RESTORE, null).orEmpty()) }.getOrDefault(WallpaperRestore.MIDNIGHT)
        set(value) = prefs.edit().putString(KEY_RESTORE, value.name).apply()

    /** Also change the lock screen; off by default so only the home screen is touched. */
    var includeLockScreen: Boolean
        get() = prefs.getBoolean(KEY_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK, value).apply()

    val normalFile: File get() = File(context.filesDir, NORMAL_FILE)

    val hasNormalWallpaper: Boolean get() = normalFile.exists() && normalFile.length() > 0

    /** The warning being shown, or null while the wallpaper is calm. */
    var warning: WarningWallpaper?
        get() {
            val text = prefs.getString(KEY_WARNING, null) ?: return null
            return runCatching {
                val parts = text.split('\t')
                WarningWallpaper(
                    goalNames = parts[0].split('\n').filter { it.isNotEmpty() }.map(::dec),
                    stake = dec(parts[1]).ifEmpty { null },
                    endsAtMillis = parts[2].toLong(),
                )
            }.getOrNull()
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_WARNING).remove(KEY_STARTED)
            } else {
                if (warning == null) editor.putLong(KEY_STARTED, System.currentTimeMillis())
                editor.putString(KEY_WARNING, value.goalNames.joinToString("\n", transform = ::enc) + "\t" + enc(value.stake.orEmpty()) + "\t" + value.endsAtMillis)
            }
            editor.apply()
        }

    /** Changes whenever a new normal wallpaper is stored, so the live wallpaper reloads it. */
    fun bumpNormalVersion() = prefs.edit().putLong(KEY_NORMAL_VERSION, System.currentTimeMillis()).apply()

    val startedAtMillis: Long get() = prefs.getLong(KEY_STARTED, System.currentTimeMillis())

    /** True while the static picture is the home screen wallpaper, so it is put back exactly once. */
    var staticApplied: Boolean
        get() = prefs.getBoolean(KEY_STATIC_APPLIED, false)
        set(value) = prefs.edit().putBoolean(KEY_STATIC_APPLIED, value).apply()

    /** Which picture is currently set as the static warning, so it is only redrawn when the list changes. */
    var staticKey: String
        get() = prefs.getString(KEY_STATIC_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_STATIC_KEY, value).apply()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun dec(value: String) = URLDecoder.decode(value, "UTF-8")

    companion object {
        const val PREFS = "warning_wallpaper"
        private const val NORMAL_FILE = "normal-wallpaper.jpg"
        const val KEY_STYLE = "style"
        private const val KEY_MODE = "mode"
        private const val KEY_RESTORE = "restore"
        private const val KEY_LOCK = "lock_screen"
        const val KEY_WARNING = "warning"
        private const val KEY_STARTED = "started_at"
        const val KEY_NORMAL_VERSION = "normal_version"
        private const val KEY_STATIC_APPLIED = "static_applied"
        private const val KEY_STATIC_KEY = "static_key"
    }
}
