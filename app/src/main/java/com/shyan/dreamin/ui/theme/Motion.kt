package com.shyan.dreamin.ui.theme

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object DreaminMotion {
    const val DurationInstant = 90
    const val DurationShort = 150
    const val DurationMedium = 200
    const val DurationLong = 300
    const val DurationAmbient = 1200

    fun <T> quick(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    fun <T> smooth(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> standard(): SpringSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun <T> emphasized(): SpringSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)

    fun <T> bouncy(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> enter(durationMillis: Int = DurationMedium): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = LinearOutSlowInEasing)

    fun <T> exit(durationMillis: Int = DurationShort): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = FastOutLinearInEasing)

    fun <T> ambient(durationMillis: Int = DurationAmbient): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
}
