package com.zaiki.trippawl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaiki.trippawl.ui.theme.ZaikiPalette
import com.zaiki.trippawl.ui.theme.ZaikiRadii
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens

/**
 * Zaiki Primary Pill CTA Button (`.cta-primary`)
 * - Background: `#a754ff` (`--highlight`) with subtle top sheen
 * - Purple glow shadow (`--shadow-accent`)
 * - Pill radius (`999px`)
 * - Squishes on click (`uiPop`)
 */
@Composable
fun ZaikiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
) {
    val colors = ZaikiThemeTokens.colors
    val brush = if (enabled) {
        Brush.linearGradient(
            colors = listOf(colors.highlight, colors.accentDeep)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                colors.highlight.copy(alpha = 0.38f),
                colors.accentDeep.copy(alpha = 0.38f)
            )
        )
    }

    Box(
        modifier = modifier
            .zaikiSquishClickable(enabled = enabled, onClick = onClick)
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = ZaikiRadii.Pill,
                ambientColor = colors.highlight,
                spotColor = colors.highlight
            )
            .clip(ZaikiRadii.Pill)
            .background(brush)
            .border(
                BorderStroke(
                    1.dp,
                    if (enabled) Color.White.copy(alpha = 0.24f) else Color.Transparent
                ),
                ZaikiRadii.Pill
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Zaiki Secondary Pill Button (`.cta-secondary` / `.contact-pill`)
 */
@Composable
fun ZaikiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAccentTinted: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) {
    val colors = ZaikiThemeTokens.colors
    val bg = if (isAccentTinted) colors.accentWash else colors.surfaceChip
    val border = if (isAccentTinted) colors.borderAccent else colors.borderChip
    val textColor = if (isAccentTinted) colors.highlight else colors.textPrimary

    Box(
        modifier = modifier
            .zaikiSquishClickable(enabled = enabled, onClick = onClick)
            .clip(ZaikiRadii.Pill)
            .background(bg)
            .border(BorderStroke(1.dp, border), ZaikiRadii.Pill)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) textColor else textColor.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Zaiki Compact Theme Toggle Button (`.themebutton` with 🌙 / ☀️)
 */
@Composable
fun ZaikiThemeToggleButton(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFFE4E4E4) else Color(0xFF4A4949)
    val fg = if (isDark) Color(0xFF2E2E2E) else Color.White

    Box(
        modifier = modifier
            .zaikiSquishClickable(onClick = onToggle)
            .shadow(elevation = 4.dp, shape = ZaikiRadii.Panel)
            .clip(ZaikiRadii.Panel)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isDark) "🌙 Dark" else "☀️ Light",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Zaiki Category Tag Hues (`.tag-web`, `.tag-system`, `.tag-creative`, `.tag-personal`)
 */
enum class ZaikiTagCategory {
    WEB,      // Blue: Short Trip tags
    SYSTEM,   // Purple: Long Trip tags
    CREATIVE, // Orange: Tolls / Unpaid badges
    PERSONAL  // Green: Settled / Multi-passenger split badges
}

@Composable
fun ZaikiTagPill(
    text: String,
    category: ZaikiTagCategory = ZaikiTagCategory.SYSTEM,
    modifier: Modifier = Modifier
) {
    val colors = ZaikiThemeTokens.colors
    val (bg, border, fg) = when (category) {
        ZaikiTagCategory.WEB -> Triple(
            ZaikiPalette.TagWebBg,
            ZaikiPalette.TagWebBorder,
            colors.tagWebText
        )
        ZaikiTagCategory.SYSTEM -> Triple(
            ZaikiPalette.TagSystemBg,
            ZaikiPalette.TagSystemBorder,
            colors.tagSystemText
        )
        ZaikiTagCategory.CREATIVE -> Triple(
            ZaikiPalette.TagCreativeBg,
            ZaikiPalette.TagCreativeBorder,
            colors.tagCreativeText
        )
        ZaikiTagCategory.PERSONAL -> Triple(
            ZaikiPalette.TagPersonalBg,
            ZaikiPalette.TagPersonalBorder,
            colors.tagPersonalText
        )
    }

    Box(
        modifier = modifier
            .clip(ZaikiRadii.Pill)
            .background(bg)
            .border(BorderStroke(1.dp, border), ZaikiRadii.Pill)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Colored Payment Status Chip ("PAID" vs "UNPAID") with glowing status dot and optional
 * single-tap quick toggle action.
 */
@Composable
fun ZaikiPaymentStatusChip(
    isPaid: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showQuickActionHint: Boolean = false
) {
    val colors = ZaikiThemeTokens.colors
    val statusColor = if (isPaid) colors.available else colors.unavailable
    val bgColor = statusColor.copy(alpha = 0.14f)
    val borderColor = statusColor.copy(alpha = 0.42f)

    val clickMod = if (onClick != null) {
        Modifier.zaikiSquishClickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(clickMod)
            .clip(ZaikiRadii.Pill)
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), ZaikiRadii.Pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = when {
                isPaid -> "PAID ✓"
                showQuickActionHint -> "PAY NOW"
                else -> "UNPAID"
            },
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Zaiki Filter / Skill Chip (`.skill-chip`) with squishy selection state.
 */
@Composable
fun ZaikiFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null
) {
    val colors = ZaikiThemeTokens.colors
    val bg = if (selected) colors.accentWash else colors.surfaceChip
    val border = if (selected) colors.borderAccent else colors.borderChip
    val textColor = if (selected) colors.highlight else colors.textBody

    Row(
        modifier = modifier
            .zaikiSquishClickable(onClick = onClick)
            .clip(ZaikiRadii.Pill)
            .background(bg)
            .border(BorderStroke(1.dp, border), ZaikiRadii.Pill)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
        if (badgeCount != null) {
            Box(
                modifier = Modifier
                    .clip(ZaikiRadii.Pill)
                    .background(if (selected) colors.highlight else colors.borderChip)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else colors.textMuted
                )
            }
        }
    }
}

/**
 * Zaiki Form Field (`.z-field`)
 * Source: zaiki-design-system/project/tokens/components.css
 * - Monospace bold label
 * - 10dp rounded input container
 * - Purple border & soft accent wash on focus
 */
@Composable
fun ZaikiFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    suffix: String? = null,
    helperText: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = ZaikiThemeTokens.colors
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = if (isFocused) colors.highlight.copy(alpha = 0.65f) else colors.borderChip
    val bgColor = if (isFocused) {
        colors.highlight.copy(alpha = 0.09f)
    } else {
        colors.inputFill
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) colors.textPrimary else colors.textMuted
            ),
            cursorBrush = SolidColor(colors.highlight),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .clip(ZaikiRadii.InputField)
                .background(bgColor)
                .border(
                    BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
                    ZaikiRadii.InputField
                )
                .padding(horizontal = 13.dp, vertical = 11.dp),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    if (!suffix.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.highlight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        )

        if (!helperText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
    }
}

/**
 * Zaiki Form Status Banner (`.form-status[data-state="success"|"error"]`)
 */
@Composable
fun ZaikiFormStatusBanner(
    message: String?,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val visible = !message.isNullOrBlank()
    val bgColor = if (isError) Color(0x1FFF1744) else Color(0x1F00E676)
    val borderColor = if (isError) Color(0x59FF1744) else Color(0x5900E676)
    val textColor = if (isError) Color(0xFFFF6B81) else Color(0xFF34D399)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(ZaikiRadii.InputField)
                .background(bgColor)
                .border(BorderStroke(1.dp, borderColor), ZaikiRadii.InputField)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Zaiki Circular Purple `+` Floating Action Button matching the reference screenshot:
 * - `58.dp` circle with vibrant purple fill (`#B05CFF` -> `#9D44F5`)
 * - `2.dp` lighter lavender inner rim (`#CA88FF`)
 * - Crisp white `+` icon (`24.dp`, rounded stroke)
 * - Squishes on click (`uiPop`)
 */
@Composable
fun ZaikiCircularAddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fillBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFB35CFF), Color(0xFF9E44F7))
    )

    Box(
        modifier = modifier
            .size(52.dp)
            .zaikiSquishClickable(onClick = onClick)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color(0xFFA754FF)
            )
            .clip(CircleShape)
            .background(fillBrush)
            .border(
                BorderStroke(2.dp, Color(0xFFCA88FF)),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val strokeWidth = size.width * 0.115f
            val center = size.width / 2f
            drawLine(
                color = Color.White,
                start = Offset(center, 0f),
                end = Offset(center, size.height),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, center),
                end = Offset(size.width, center),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Floating "Back to Top" (`↑`) button that appears when the user scrolls down a list.
 */
@Composable
fun ZaikiBackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZaikiThemeTokens.colors

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .zaikiSquishClickable(onClick = onClick)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black,
                    spotColor = colors.highlight
                )
                .clip(CircleShape)
                .background(colors.bgPage)
                .background(colors.glassFillStrong)
                .background(colors.accentWash)
                .border(
                    BorderStroke(1.5.dp, colors.highlight),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val stroke = size.width * 0.13f
                val cx = size.width / 2f
                val topY = size.height * 0.12f
                val bottomY = size.height * 0.88f
                val wingX = size.width * 0.34f
                val wingY = size.height * 0.44f

                // Vertical stem
                drawLine(
                    color = colors.highlight,
                    start = Offset(cx, bottomY),
                    end = Offset(cx, topY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                // Left arrowhead wing
                drawLine(
                    color = colors.highlight,
                    start = Offset(cx - wingX, wingY),
                    end = Offset(cx, topY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                // Right arrowhead wing
                drawLine(
                    color = colors.highlight,
                    start = Offset(cx + wingX, wingY),
                    end = Offset(cx, topY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
