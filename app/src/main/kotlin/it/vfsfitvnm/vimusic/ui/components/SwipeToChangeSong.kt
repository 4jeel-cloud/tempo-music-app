package it.vfsfitvnm.vimusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SwipeToChangeSong(
    enabled: Boolean,
    sensitivity: Float,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onGoPrevious: () -> Unit,
    onGoNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val (colorPalette) = LocalAppearance.current
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()

    val offsetX = remember {
        Animatable(0f)
    }

    val animationSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    }

    val normalizedSensitivity = sensitivity.coerceIn(0f, 1f)
    val autoSwipeThreshold = remember(normalizedSensitivity) {
        (600f / (1f + exp(
            (11.44748f * normalizedSensitivity - 9.04945f).toDouble()
        ).toFloat())).roundToInt().toFloat()
    }
    val velocityThreshold = (normalizedSensitivity * -8.25f) + 8.5f

    var dragStartTime by remember {
        mutableStateOf(0L)
    }
    var totalDragDistance by remember {
        mutableStateOf(0f)
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            offsetX.animateTo(0f, animationSpec)
        }
    }

    val swipeModifier = if (!enabled) {
        Modifier
    } else {
        Modifier.pointerInput(
            enabled,
            sensitivity,
            canGoPrevious,
            canGoNext,
            layoutDirection
        ) {
            detectHorizontalDragGestures(
                onDragStart = {
                    dragStartTime = System.currentTimeMillis()
                    totalDragDistance = 0f
                },
                onDragCancel = {
                    coroutineScope.launch {
                        offsetX.animateTo(0f, animationSpec)
                    }
                },
                onHorizontalDrag = { _, dragAmount ->
                    val adjustedDragAmount =
                        if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount

                    val tryingToSwipeRight = adjustedDragAmount > 0
                    val tryingToSwipeLeft = adjustedDragAmount < 0
                    val allowRight = tryingToSwipeRight && canGoPrevious
                    val allowLeft = tryingToSwipeLeft && canGoNext
                    val canReturnToCenter =
                        (tryingToSwipeRight && !canGoPrevious && offsetX.value < 0) ||
                            (tryingToSwipeLeft && !canGoNext && offsetX.value > 0)

                    if (allowRight || allowLeft || canReturnToCenter) {
                        totalDragDistance += abs(adjustedDragAmount)
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + adjustedDragAmount)
                        }
                    }
                },
                onDragEnd = {
                    val dragDuration = System.currentTimeMillis() - dragStartTime
                    val velocity = if (dragDuration > 0) {
                        totalDragDistance / dragDuration
                    } else {
                        0f
                    }
                    val currentOffset = offsetX.value
                    val shouldChangeSong =
                        (abs(currentOffset) > 50f && velocity > velocityThreshold) ||
                            (abs(currentOffset) > autoSwipeThreshold)

                    if (shouldChangeSong) {
                        when {
                            currentOffset > 0 && canGoPrevious -> {
                                onGoPrevious()
                            }

                            currentOffset <= 0 && canGoNext -> {
                                onGoNext()
                            }
                        }
                    }

                    coroutineScope.launch {
                        offsetX.animateTo(0f, animationSpec)
                    }
                }
            )
        }
    }

    Box(
        modifier = modifier.then(swipeModifier)
    ) {
        content(
            Modifier.graphicsLayer {
                translationX = offsetX.value
            }
        )

        val offset = offsetX.value

        if (abs(offset) > 50f) {
            Image(
                painter = painterResource(
                    if (offset > 0) R.drawable.play_skip_back else R.drawable.play_skip_forward
                ),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    colorPalette.accent.copy(
                        alpha = (abs(offset) / autoSwipeThreshold).coerceIn(0f, 1f)
                    )
                ),
                modifier = Modifier
                    .align(
                        if (offset > 0) Alignment.CenterStart else Alignment.CenterEnd
                    )
                    .padding(horizontal = 16.dp)
                    .size(24.dp)
            )
        }
    }
}
