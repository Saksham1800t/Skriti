package com.example.dailygoals.engine

import com.example.dailygoals.model.GoalType

/**
 * Whether a goal depends on the blocking service (the accessibility service that reads which app is in front).
 * An avoid goal with apps uses it for the "did you use it?" question; a do goal uses it only when a failure
 * blocks those apps.
 */
object BlockerNeed {
    fun needed(type: GoalType, intensity: Intensity, packages: Set<String>): Boolean =
        packages.isNotEmpty() && (type == GoalType.AVOID || intensity != Intensity.NONE)
}
