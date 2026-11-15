package com.example.dailygoals.engine

enum class WallpaperStyle { PULSE, EMBERS, GLITCH, MINIMAL }

/** Animated uses the app's own live wallpaper; static sets one generated picture and puts the old one back. */
enum class WallpaperMode { ANIMATED, STATIC }

/** When the warning wallpaper goes away. A corrected failure always ends it early. */
enum class WallpaperRestore { MIDNIGHT, BLOCK_END }

data class FailedGoal(val name: String, val stake: String?, val blockEndsAtMillis: Long?)

/** What the warning wallpaper shows: the failed goals, one penalty line, and when it ends. */
data class WarningWallpaper(val goalNames: List<String>, val stake: String?, val endsAtMillis: Long)

object WallpaperRules {
    const val MAX_NAMES_SHOWN = 3

    /** Only the strongest punishment changes the wallpaper, and never during the emergency pause. */
    fun triggers(intensity: Intensity, skippedByPause: Boolean): Boolean = intensity == Intensity.STRONG && !skippedByPause

    /**
     * One wallpaper lists every failed goal of the day and is rebuilt when the list changes. With no failed
     * goal, or once the end time has passed, there is nothing to show.
     */
    fun state(failed: List<FailedGoal>, restore: WallpaperRestore, nowMillis: Long, nextMidnightMillis: Long): WarningWallpaper? {
        if (failed.isEmpty()) return null
        val names = failed.map { it.name }.distinct()
        val stake = failed.firstNotNullOfOrNull { it.stake?.trim()?.takeIf(String::isNotEmpty) }
        val blockEnd = failed.mapNotNull { it.blockEndsAtMillis }.filter { it > nowMillis }.maxOrNull()
        val ends = if (restore == WallpaperRestore.BLOCK_END) blockEnd ?: nextMidnightMillis else nextMidnightMillis
        return if (ends > nowMillis) WarningWallpaper(names, stake, ends) else null
    }

    /** The names that fit on the screen, with "+N more" for the rest. */
    fun displayNames(names: List<String>, moreText: (Int) -> String): List<String> =
        if (names.size <= MAX_NAMES_SHOWN) names else names.take(MAX_NAMES_SHOWN - 1) + moreText(names.size - (MAX_NAMES_SHOWN - 1))

    /** The countdown as mm:ss up to an hour, then h:mm:ss. */
    fun countdown(leftMillis: Long): String {
        val total = (leftMillis.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** The fraction of the warning still to run, for the ring; 1 right after the failure, 0 at the end. */
    fun fractionLeft(startedAtMillis: Long, endsAtMillis: Long, nowMillis: Long): Float {
        val span = (endsAtMillis - startedAtMillis).coerceAtLeast(1)
        return ((endsAtMillis - nowMillis).toFloat() / span).coerceIn(0f, 1f)
    }
}
