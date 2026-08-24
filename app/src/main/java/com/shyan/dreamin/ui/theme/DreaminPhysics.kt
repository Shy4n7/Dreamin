package com.shyan.dreamin.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

object DreaminPhysics {

    // Snappy tactile response for buttons and list items
    val SnappySpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )

    // Bouncy spring for modal pops, hearts, and hero cards
    val BouncySpring = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    )

    // Fluid sheet spring for dismiss gestures
    val SheetSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow
    )

    // Gentle floating spring for ambient waves and halos
    val GentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessVeryLow
    )
}

/**
 * Attaches physical tactile compression and bounce response to any Compose element on touch.
 */
fun Modifier.tactilePressPhysics(
    pressScale: Float = 0.95f,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = DreaminPhysics.SnappySpring,
        label = "tactile_press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}
