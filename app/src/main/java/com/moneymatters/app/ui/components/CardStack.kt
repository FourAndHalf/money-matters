package com.moneymatters.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moneymatters.app.ui.theme.MoneyTheme
import kotlinx.coroutines.launch

private val StackEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val PeekStep = 14.dp
private const val ScaleStep = 0.06f
private const val VisibleBehind = 2
private val SwipeThreshold = 70.dp

/**
 * Samsung Wallet–style deck (design/DESIGN.md → Account card stack).
 *
 * - Cards behind the front sit [PeekStep] higher and 6% smaller; at most two show.
 * - Swipe left brings the next card forward, right brings the previous one back.
 * - Tapping the peeking edge brings that card forward; arrow keys and TalkBack actions work too.
 * - The front card follows the finger with a slight rotation (dx / 22 degrees).
 */
@Composable
fun <T> CardStack(
    items: List<T>,
    frontIndex: Int,
    onFrontIndexChange: (Int) -> Unit,
    cardLabel: (T) -> String,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 200.dp,
    card: @Composable BoxScope.(T) -> Unit,
) {
    val n = items.size
    if (n == 0) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val front by rememberUpdatedState(frontIndex)
    val onChange by rememberUpdatedState(onFrontIndexChange)
    val headroom = PeekStep * VisibleBehind + 2.dp

    fun next() {
        if (busy) return
        busy = true
        scope.launch {
            dragX.animateTo(-widthPx * 1.15f, tween(230))
            onChange((front + 1) % n)
            dragX.snapTo(0f)
            busy = false
        }
    }

    fun prev() {
        if (busy) return
        busy = true
        scope.launch {
            onChange((front - 1 + n) % n)
            dragX.snapTo(widthPx * 1.15f)
            dragX.animateTo(0f, tween(450, easing = StackEasing))
            busy = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight + headroom)
            .onSizeChanged { widthPx = it.width }
            .semantics {
                contentDescription = "Account cards, ${frontIndex + 1} of $n: ${cardLabel(items[frontIndex])}"
                customActions = listOf(
                    CustomAccessibilityAction("Next account") { next(); true },
                    CustomAccessibilityAction("Previous account") { prev(); true },
                )
            }
            .focusable()
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (it.key) {
                    Key.DirectionRight -> { next(); true }
                    Key.DirectionLeft -> { prev(); true }
                    else -> false
                }
            }
            .pointerInput(n) {
                // Only the strip above the front card belongs to the peeking card.
                detectTapGestures { if (it.y < headroom.toPx()) next() }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val dx = dragX.value
                        val threshold = SwipeThreshold.toPx()
                        when {
                            dx < -threshold -> next()
                            dx > threshold -> prev()
                            else -> scope.launch { dragX.animateTo(0f, tween(250, easing = StackEasing)) }
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f, tween(250, easing = StackEasing)) } },
                ) { change, amount ->
                    if (busy) return@detectHorizontalDragGestures
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + amount) }
                }
            },
    ) {
        items.forEachIndexed { i, item ->
            val pos = (i - frontIndex + n) % n
            val offsetY by animateDpAsState(-PeekStep * pos.coerceAtMost(VisibleBehind + 1), tween(450, easing = StackEasing), label = "y")
            val scale by animateFloatAsState(1f - ScaleStep * pos.coerceAtMost(VisibleBehind + 1), tween(450, easing = StackEasing), label = "scale")
            val alpha by animateFloatAsState(if (pos > VisibleBehind) 0f else 1f, tween(450, easing = StackEasing), label = "alpha")
            val shape = MoneyTheme.shapes.xl
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex((n - pos).toFloat())
                    .fillMaxWidth()
                    .height(cardHeight)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = with(density) { offsetY.toPx() }
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        if (pos == 0) {
                            translationX = dragX.value
                            rotationZ = dragX.value / density.density / 22f
                        }
                    }
                    .dropShadow(shape, Shadow(radius = 30.dp, color = ShadowInk.copy(alpha = 0.16f), offset = DpOffset(0.dp, 12.dp)))
                    .clip(shape),
            ) {
                card(item)
                // Cards further back read slightly dimmer, like the physical deck.
                if (pos > 0) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.035f * pos)))
            }
        }
    }
}

/** Position dots under a [CardStack]; the active dot stretches into an 18dp pill. */
@Composable
fun StackDots(count: Int, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == selected
            val w by animateDpAsState(if (on) 18.dp else 6.dp, tween(300, easing = StackEasing), label = "dot")
            Box(
                Modifier
                    .width(w)
                    .height(18.dp)
                    .pointerInput(i) { detectTapGestures { onSelect(i) } },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (on) c.anchor else c.barIdle),
                )
            }
        }
    }
}
