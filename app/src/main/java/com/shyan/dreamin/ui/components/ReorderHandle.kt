package com.shyan.dreamin.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.shyan.dreamin.ui.theme.DreaminMotion
import com.shyan.dreamin.ui.theme.LocalDreaminColors

@Composable
fun ReorderHandle(
    dragKey: String,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val handleScale by animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1.0f,
        animationSpec = DreaminMotion.bouncy(),
        label = "drag_handle_scale"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .pointerInput(dragKey) {
                detectVerticalDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Reorder",
            tint = if (isDragging) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = handleScale; scaleY = handleScale }
        )
    }
}
