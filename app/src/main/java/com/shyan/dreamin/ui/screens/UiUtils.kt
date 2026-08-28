@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shyan.dreamin.ui.screens

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.blur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shyan.dreamin.ui.components.AmbientSoundwaveHeader
import com.shyan.dreamin.data.model.*
import com.shyan.dreamin.ui.components.EqualizerBottomSheet
import com.shyan.dreamin.ui.components.FluidMeshGradientBackground
import com.shyan.dreamin.ui.components.SyncedLyricsView
import com.shyan.dreamin.viewmodel.MusicPlayerViewModel
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke





val Background = Color(0xFF07070A)
val SurfaceContainer = Color(0xFF0F0F16)
val SurfaceHigh = Color(0xFF14141E)
val SurfaceHighest = Color(0xFF1C1C2A)
val SurfaceBright = Color(0xFF242436)
val Primary = Color(0xFF8B5CF6)
val PrimaryDim = Color(0xFF6D28D9)
val Secondary = Color(0xFF06B6D4)
val OnSurface = Color.White.copy(alpha = 0.96f)
val OnSurfaceVariant = Color.White.copy(alpha = 0.65f)
val OutlineVariant = Color.White.copy(alpha = 0.08f)
val BlackOverlay50 = Color.Black.copy(alpha = 0.50f)
val BlackOverlay35 = Color.Black.copy(alpha = 0.35f)








@androidx.compose.runtime.Immutable
data class DreaminColors(
    val background: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val surfaceBright: Color,
    val primary: Color,
    val primaryDim: Color,
    val secondary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outlineVariant: Color
)

val SonicNocturneColors = DreaminColors(
    background = Background,
    surfaceContainer = SurfaceContainer,
    surfaceHigh = SurfaceHigh,
    surfaceHighest = SurfaceHighest,
    surfaceBright = SurfaceBright,
    primary = Primary,
    primaryDim = PrimaryDim,
    secondary = Secondary,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outlineVariant = OutlineVariant
)

val LocalDreaminColors = compositionLocalOf { SonicNocturneColors }
val LocalPlaylists = staticCompositionLocalOf<List<com.shyan.dreamin.data.local.Playlist>> { emptyList() }

fun blendDominantTint(base: Color, tint: Color, alpha: Float): Color = Color(
    red   = base.red   * (1f - alpha) + tint.red   * alpha,
    green = base.green * (1f - alpha) + tint.green * alpha,
    blue  = base.blue  * (1f - alpha) + tint.blue  * alpha,
    alpha = 1f
)



// Unified Motion System for fluid 120 FPS animations & transitions
object DreaminMotion {
    val FluidGlide: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 320f
    )
    val FluidSlide: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.85f,
        stiffness = 320f
    )
    val TactileBouncy: SpringSpec<Float> = spring(
        dampingRatio = 0.62f,
        stiffness = 420f
    )
    val SnappySnap: SpringSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 600f
    )
    val SmoothTween: TweenSpec<Float> = tween(
        durationMillis = 280,
        easing = FastOutSlowInEasing
    )
}

@Composable
fun DreaminRippleTheme(
    content: @Composable () -> Unit
) {
    val colors = LocalDreaminColors.current
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = colors.primary,
            onPrimary = colors.onSurface,
            surface = colors.surfaceContainer,
            onSurface = colors.onSurface
        ),
        content = content
    )
}

@Composable
fun Modifier.staggeredEntry(index: Int, baseDelayMs: Int = 22, maxStaggerItems: Int = 10, triggerKey: Any? = Unit): Modifier {
    // Only stagger the initial visible items on screen (index < maxStaggerItems).
    // Items scrolled into view beyond the initial viewport render immediately with full visibility!
    if (index >= maxStaggerItems) return this

    val animState = remember(triggerKey) { Animatable(0f) }
    LaunchedEffect(triggerKey) {
        val staggerDelay = (index * baseDelayMs).toLong()
        if (staggerDelay > 0L) delay(staggerDelay)
        animState.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return this.graphicsLayer {
        alpha = animState.value
        translationY = (1f - animState.value) * 28f
        scaleX = 0.95f + 0.05f * animState.value
        scaleY = 0.95f + 0.05f * animState.value
    }
}

@Composable
fun Modifier.staggeredHorizontalEntry(index: Int, baseDelayMs: Int = 20, maxStaggerItems: Int = 8): Modifier {
    if (index >= maxStaggerItems) return this
    val animState = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val staggerDelay = (index * baseDelayMs).toLong()
        if (staggerDelay > 0) delay(staggerDelay)
        animState.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return this.graphicsLayer {
        val p = animState.value
        alpha = p
        translationX = (1f - p) * 28f
        scaleX = 0.96f + 0.04f * p
        scaleY = 0.96f + 0.04f * p
    }
}

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * ⚡ Tactile Tap-to-Recoil Press Physics Modifier.
 * Compresses slightly on finger press and snaps back on release with realistic spring physics.
 */
@Composable
fun Modifier.pressRecoil(
    targetScale: Float = 0.96f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "press_recoil_scale"
    )
    return this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }
}

