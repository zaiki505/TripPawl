package com.zaiki.trippawl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaiki.trippawl.ui.theme.ZaikiDimens
import com.zaiki.trippawl.ui.theme.ZaikiRadii
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens

/**
 * Zaiki Design System — True Multi-Layer Glassmorphic Bento Card (`.bento-card` / `.bento-card-main`)
 * Source: zaiki-design-system/project/tokens/surfaces.css & components.css
 *
 * Layered construction:
 * 1. Soft elevation shadow (`--shadow-card` / `--shadow-accent-soft`)
 * 2. Translucent frosted glass base (`glassFill`)
 * 3. Top-to-bottom specular glass sheen (`glassSheenTop` -> `glassSheenBottom`)
 * 4. Optional top-right purple radial spotlight (`radial-gradient(circle at 80% 20%, rgba(167,84,255,0.14), transparent)`)
 * 5. 1dp directional glass rim border (brighter top-left edge, subtle bottom-right edge)
 */
@Composable
fun ZaikiBentoCard(
    modifier: Modifier = Modifier,
    isMainAccent: Boolean = false,
    accentBorderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(ZaikiDimens.CardPadding),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ZaikiThemeTokens.colors

    val borderBrush = when {
        accentBorderColor != null -> Brush.linearGradient(
            colors = listOf(
                accentBorderColor.copy(alpha = 0.65f),
                accentBorderColor.copy(alpha = 0.22f)
            )
        )
        isMainAccent -> Brush.linearGradient(
            colors = listOf(
                colors.highlight.copy(alpha = 0.55f),
                colors.borderAccent,
                colors.glassBorderBottom
            )
        )
        else -> Brush.linearGradient(
            colors = listOf(
                colors.glassBorderTop,
                colors.glassBorderBottom
            )
        )
    }

    val clickModifier = when {
        onClick != null || onLongClick != null -> Modifier.zaikiSquishCombinedClickable(
            onLongClick = onLongClick,
            onClick = { onClick?.invoke() }
        )
        else -> Modifier
    }

    val highlightColor = colors.highlight
    val isDark = colors.isDark
    val glassFill = colors.glassFill
    val sheenTop = colors.glassSheenTop
    val sheenBottom = colors.glassSheenBottom

    Box(
        modifier = modifier
            .then(clickModifier)
            .shadow(
                elevation = if (isDark) 12.dp else 5.dp,
                shape = ZaikiRadii.Card,
                ambientColor = Color.Black,
                spotColor = if (isMainAccent) highlightColor.copy(alpha = 0.45f) else Color.Black
            )
            .clip(ZaikiRadii.Card)
            .drawBehind {
                // Layer 1: Translucent dark/light glass base
                drawRect(color = glassFill)

                // Layer 2: Specular top-to-bottom glass reflection sheen
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(sheenTop, sheenBottom)
                    )
                )

                // Layer 3: .bento-card-main radial purple glow at 80% 20%
                if (isMainAccent && size.width > 0f && size.height > 0f) {
                    val radius = (size.maxDimension * 0.75f).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                highlightColor.copy(alpha = if (isDark) 0.20f else 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.82f, size.height * 0.18f),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(size.width * 0.82f, size.height * 0.18f)
                    )
                }
            }
            .border(width = 1.dp, brush = borderBrush, shape = ZaikiRadii.Card)
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * Zaiki Design System — Detail Panel (`.detail-panel` / `.z-panel`, 12dp radius)
 */
@Composable
fun ZaikiDetailPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val rimBrush = if (borderColor != null) {
        Brush.linearGradient(
            colors = listOf(borderColor.copy(alpha = 0.6f), borderColor.copy(alpha = 0.25f))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(colors.glassBorderTop, colors.glassBorderBottom)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ZaikiRadii.Panel)
            .background(colors.glassFill)
            .background(
                Brush.verticalGradient(
                    colors = listOf(colors.glassSheenTop, colors.glassSheenBottom)
                )
            )
            .border(width = 1.dp, brush = rimBrush, shape = ZaikiRadii.Panel)
            .padding(contentPadding)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.highlight,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        content()
    }
}

/**
 * Zaiki Design System — Section Header with UPPERCASE purple kicker (`.section-kicker`)
 * and bold title.
 */
@Composable
fun ZaikiSectionHeader(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = ZaikiThemeTokens.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kicker.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.highlight,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.padding(start = 12.dp))
            trailingContent()
        }
    }
}

@Composable
fun ZaikiHairlineDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp
) {
    val colors = ZaikiThemeTokens.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(colors.borderHairline)
    )
}
