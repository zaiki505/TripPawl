package com.zaiki.trippawl.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * Zaiki Design System — Signature Motion
 * Source: zaiki-design-system/project/tokens/motion.css
 *
 * Implements the global `uiPop` squish interaction:
 * - 0%   { scale: 1.00, 1.00 }
 * - 30%  { scale: 0.90, 1.10 }
 * - 50%  { scale: 1.10, 0.90 }
 * - 70%  { scale: 0.97, 1.03 }
 * - 100% { scale: 1.00, 1.00 }
 */
val ZaikiEaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
val ZaikiEaseBounce = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
val ZaikiEaseSquish = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)

class ZaikiSquishController {
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)

    suspend fun triggerSquish() {
        kotlinx.coroutines.coroutineScope {
            launch {
                scaleX.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        1.00f at 0 using ZaikiEaseSquish
                        0.90f at 120 using ZaikiEaseSquish
                        1.10f at 200 using ZaikiEaseSquish
                        0.97f at 280 using ZaikiEaseSquish
                        1.00f at 400
                    }
                )
            }
            launch {
                scaleY.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        1.00f at 0 using ZaikiEaseSquish
                        1.10f at 120 using ZaikiEaseSquish
                        0.90f at 200 using ZaikiEaseSquish
                        1.03f at 280 using ZaikiEaseSquish
                        1.00f at 400
                    }
                )
            }
        }
    }
}

@Composable
fun rememberZaikiSquishController(): ZaikiSquishController {
    return remember { ZaikiSquishController() }
}

fun Modifier.zaikiSquishLayer(controller: ZaikiSquishController): Modifier = this.graphicsLayer {
    scaleX = controller.scaleX.value
    scaleY = controller.scaleY.value
}

/**
 * Applies the signature Zaiki `uiPop` squish animation on click.
 */
fun Modifier.zaikiSquishClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val controller = rememberZaikiSquishController()
    val interactionSource = remember { MutableInteractionSource() }

    this
        .zaikiSquishLayer(controller)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) {
            scope.launch { controller.triggerSquish() }
            onClick()
        }
}

/**
 * Applies the signature Zaiki `uiPop` squish animation on click or long-click.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.zaikiSquishCombinedClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val controller = rememberZaikiSquishController()
    val interactionSource = remember { MutableInteractionSource() }

    this
        .zaikiSquishLayer(controller)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onLongClick = {
                scope.launch { controller.triggerSquish() }
                onLongClick?.invoke()
            },
            onClick = {
                scope.launch { controller.triggerSquish() }
                onClick()
            }
        )
}
