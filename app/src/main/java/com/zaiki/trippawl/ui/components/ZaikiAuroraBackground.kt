package com.zaiki.trippawl.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.zaiki.trippawl.ui.theme.ZaikiPalette
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens

/**
 * Zaiki Design System — Drifting Aurora Backdrop
 * Source: zaiki-design-system/project/readme.md & tokens/colors.css
 *
 * Renders the flat dark/light page base + slow drifting purple/pink/blue aurora blobs
 * (`@keyframes heroAurora` 7s alternate) + subtle diagonal light grid so glassmorphic
 * cards refract rich color beneath them.
 */
@Composable
fun ZaikiAuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val infiniteTransition = rememberInfiniteTransition(label = "heroAurora")

    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraDrift"
    )

    val strength = colors.auroraStrength

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val purpleRadius = (w * 0.78f).coerceAtLeast(1f)
            val pinkRadius = (w * 0.68f).coerceAtLeast(1f)
            val blueRadius = (w * 0.72f).coerceAtLeast(1f)

            // 1. Primary top-left Purple Aurora Blob (--aurora-purple)
            val purpleCenter = Offset(
                x = w * (0.18f + 0.14f * drift),
                y = h * (0.10f + 0.06f * drift)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZaikiPalette.AuroraPurple.copy(alpha = 0.24f * strength),
                        ZaikiPalette.AuroraPurple.copy(alpha = 0.08f * strength),
                        Color.Transparent
                    ),
                    center = purpleCenter,
                    radius = purpleRadius
                ),
                radius = purpleRadius,
                center = purpleCenter
            )

            // 2. Mid-right Pink Aurora Blob (--aurora-pink)
            val pinkCenter = Offset(
                x = w * (0.88f - 0.15f * drift),
                y = h * (0.40f + 0.08f * drift)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZaikiPalette.AuroraPink.copy(alpha = 0.19f * strength),
                        ZaikiPalette.AuroraPink.copy(alpha = 0.05f * strength),
                        Color.Transparent
                    ),
                    center = pinkCenter,
                    radius = pinkRadius
                ),
                radius = pinkRadius,
                center = pinkCenter
            )

            // 3. Bottom-left Blue Aurora Blob (--aurora-blue)
            val blueCenter = Offset(
                x = w * (0.20f + 0.12f * (1f - drift)),
                y = h * (0.80f - 0.07f * drift)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZaikiPalette.AuroraBlue.copy(alpha = 0.17f * strength),
                        Color.Transparent
                    ),
                    center = blueCenter,
                    radius = blueRadius
                ),
                radius = blueRadius,
                center = blueCenter
            )
        }

        content()
    }
}
