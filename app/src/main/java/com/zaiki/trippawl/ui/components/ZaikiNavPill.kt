package com.zaiki.trippawl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Icon types for the 4 top-level destinations in [ZaikiNavPill].
 */
enum class ZaikiNavIconType {
    HOME,
    RIDERS,
    HISTORY,
    SETTINGS
}

/**
 * Represents a top-level destination in the floating [ZaikiNavPill].
 */
data class ZaikiNavItem(
    val route: String,
    val label: String,
    val iconType: ZaikiNavIconType
)

/**
 * Zaiki Design System — Compact Frosted-Glass Floating Navigation Bar
 *
 * - Compact `62.dp` height with `22.dp` horizontal margin.
 * - Real live backdrop blur (`26.dp` Gaussian blur via [backdropGraphicsLayer]).
 * - Edge-to-edge fully filled purple active capsule that seamlessly meets its glowing purple border ring.
 */
@Composable
fun ZaikiNavPill(
    items: List<ZaikiNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdropGraphicsLayer: GraphicsLayer? = null,
    backdropOriginInRoot: Offset = Offset.Zero
) {
    val colors = ZaikiThemeTokens.colors
    val isDark = colors.isDark
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    var pillOffsetInRoot by remember { mutableStateOf(Offset.Zero) }

    // Signature Zaiki navPillSquish bounce when switching tabs
    val squishX = remember { Animatable(1f) }
    val squishY = remember { Animatable(1f) }

    LaunchedEffect(selectedIndex) {
        launch {
            squishX.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 440
                    1.00f at 0 using ZaikiEaseBounce
                    1.08f at 145 using ZaikiEaseBounce
                    0.97f at 300 using ZaikiEaseBounce
                    1.00f at 440
                }
            )
        }
        launch {
            squishY.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 440
                    1.00f at 0 using ZaikiEaseBounce
                    0.90f at 145 using ZaikiEaseBounce
                    1.03f at 300 using ZaikiEaseBounce
                    1.00f at 440
                }
            )
        }
    }

    val capsuleShape = RoundedCornerShape(percent = 50)

    val shellTint = if (isDark) {
        Color(0xFF16151E).copy(alpha = 0.74f)
    } else {
        Color(0xFFF3F3F8).copy(alpha = 0.84f)
    }

    val shellBorderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.05f)
            )
        } else {
            listOf(
                Color.Black.copy(alpha = 0.10f),
                Color.Black.copy(alpha = 0.05f)
            )
        }
    )

    // Uniform edge-to-edge purple fill brush that completely fills the active capsule up to the outer ring
    val activePillFillBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color(0xFF4B247B).copy(alpha = 0.94f),
                Color(0xFF391B60).copy(alpha = 0.96f)
            )
        } else {
            listOf(
                Color(0xFFEBD9FF).copy(alpha = 0.96f),
                Color(0xFFDCC2FF).copy(alpha = 0.98f)
            )
        }
    )

    val activePillBorderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color(0xFFA65BFF),
                Color(0xFF7B3FE4)
            )
        } else {
            listOf(
                Color(0xFF9348FF),
                Color(0xFF7B3FE4)
            )
        }
    )

    val activeContentColor = if (isDark) Color(0xFFC27AFF) else Color(0xFF6D28D9)
    val inactiveContentColor = if (isDark) Color(0xFFA8A8B3) else Color(0xFF646470)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .onGloballyPositioned { coords ->
                    pillOffsetInRoot = coords.positionInRoot()
                }
                .shadow(
                    elevation = 20.dp,
                    shape = capsuleShape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(capsuleShape)
                .border(
                    BorderStroke(1.dp, shellBorderBrush),
                    capsuleShape
                )
        ) {
            // 1. Live Frosted Glass Backdrop Blur Layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(
                        radius = 26.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
                    .drawBehind {
                        if (backdropGraphicsLayer != null) {
                            val dx = pillOffsetInRoot.x - backdropOriginInRoot.x
                            val dy = pillOffsetInRoot.y - backdropOriginInRoot.y
                            translate(left = -dx, top = -dy) {
                                drawLayer(backdropGraphicsLayer)
                            }
                        }
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFD89648).copy(alpha = if (isDark) 0.12f else 0.05f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.48f, size.height * 0.72f),
                                radius = size.height * 1.15f
                            ),
                            center = Offset(size.width * 0.48f, size.height * 0.72f),
                            radius = size.height * 1.15f
                        )
                    }
            )

            // 2. Dark Translucent Glass Tint + Top Specular Sheen
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(shellTint)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.045f else 0.40f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 3. Inner Padded Track for Active Capsule + 4 Tab Columns
            val horizontalInset = 4.dp
            val verticalInset = 4.dp
            val innerWidth = maxWidth - (horizontalInset * 2)
            val tabCount = items.size.coerceAtLeast(1)
            val tabWidth = innerWidth / tabCount

            val animatedIndicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "zaikiNavPillSlide"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalInset, vertical = verticalInset)
            ) {
                // Sliding & Squishing Active Purple Capsule:
                // Drawn with identical CornerRadius for both fill and border so the inner purple fill
                // 100% fills the entire capsule right up to the outer purple ring.
                Box(
                    modifier = Modifier
                        .offset(x = animatedIndicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = squishX.value
                            scaleY = squishY.value
                        }
                        .drawBehind {
                            val strokePx = 1.4.dp.toPx()
                            val halfStroke = strokePx / 2f
                            val radius = CornerRadius(size.height / 2f, size.height / 2f)

                            // Full edge-to-edge purple capsule fill
                            drawRoundRect(
                                brush = activePillFillBrush,
                                topLeft = Offset.Zero,
                                size = size,
                                cornerRadius = radius
                            )

                            // Outer glowing purple border ring aligned flush with the fill edge
                            drawRoundRect(
                                brush = activePillBorderBrush,
                                topLeft = Offset(halfStroke, halfStroke),
                                size = Size(size.width - strokePx, size.height - strokePx),
                                cornerRadius = CornerRadius(
                                    (size.height - strokePx) / 2f,
                                    (size.height - strokePx) / 2f
                                ),
                                style = Stroke(width = strokePx)
                            )
                        }
                )

                // 4 Equal Tab Columns (Icon above mixed-case Label)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        val tint by animateColorAsState(
                            targetValue = if (isSelected) activeContentColor else inactiveContentColor,
                            animationSpec = tween(durationMillis = 180),
                            label = "zaikiNavTabTint"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .zaikiSquishClickable { onNavigate(item.route) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ZaikiNavVectorIcon(
                                    iconType = item.iconType,
                                    color = tint,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 12.sp,
                                        letterSpacing = 0.01.em,
                                        fontWeight = if (isSelected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        }
                                    ),
                                    color = tint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom vector icons matching the 4 icons in the reference Zaiki navigation bar.
 */
@Composable
private fun ZaikiNavVectorIcon(
    iconType: ZaikiNavIconType,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.095f
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (iconType) {
            ZaikiNavIconType.HOME -> {
                val pad = w * 0.12f
                val gap = w * 0.11f
                val colW = (w - pad * 2 - gap) / 2f
                val tallH = (h - pad * 2 - gap) * 0.56f
                val shortH = (h - pad * 2 - gap) * 0.44f
                val r = CornerRadius(w * 0.09f, w * 0.09f)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad, pad),
                    size = Size(colW, tallH),
                    cornerRadius = r,
                    style = stroke
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad + colW + gap, pad),
                    size = Size(colW, shortH),
                    cornerRadius = r,
                    style = stroke
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad, pad + tallH + gap),
                    size = Size(colW, shortH),
                    cornerRadius = r,
                    style = stroke
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad + colW + gap, pad + shortH + gap),
                    size = Size(colW, tallH),
                    cornerRadius = r,
                    style = stroke
                )
            }

            ZaikiNavIconType.RIDERS -> {
                val wheelRadius = w * 0.095f
                val leftWheelCenter = Offset(w * 0.28f, h * 0.72f)
                val rightWheelCenter = Offset(w * 0.72f, h * 0.72f)
                val bottomY = h * 0.70f
                val beltY = h * 0.48f
                val roofY = h * 0.27f

                val carPath = Path().apply {
                    moveTo(leftWheelCenter.x + wheelRadius * 1.05f, bottomY)
                    lineTo(rightWheelCenter.x - wheelRadius * 1.05f, bottomY)

                    moveTo(rightWheelCenter.x + wheelRadius * 1.05f, bottomY)
                    lineTo(w * 0.87f, bottomY)
                    quadraticTo(w * 0.92f, bottomY, w * 0.92f, h * 0.60f)
                    lineTo(w * 0.92f, h * 0.54f)
                    quadraticTo(w * 0.92f, beltY, w * 0.82f, beltY)
                    lineTo(w * 0.74f, beltY)
                    lineTo(w * 0.63f, roofY)
                    quadraticTo(w * 0.60f, roofY, w * 0.54f, roofY)
                    lineTo(w * 0.27f, roofY)
                    quadraticTo(w * 0.21f, roofY, w * 0.17f, h * 0.35f)
                    lineTo(w * 0.11f, beltY)
                    quadraticTo(w * 0.08f, h * 0.52f, w * 0.08f, h * 0.60f)
                    lineTo(w * 0.08f, h * 0.64f)
                    quadraticTo(w * 0.08f, bottomY, w * 0.14f, bottomY)
                    lineTo(leftWheelCenter.x - wheelRadius * 1.05f, bottomY)
                }
                drawPath(path = carPath, color = color, style = stroke)

                drawCircle(
                    color = color,
                    radius = wheelRadius,
                    center = leftWheelCenter,
                    style = stroke
                )
                drawCircle(
                    color = color,
                    radius = wheelRadius,
                    center = rightWheelCenter,
                    style = stroke
                )
            }

            ZaikiNavIconType.HISTORY -> {
                val radius = w * 0.38f
                val center = Offset(w * 0.52f, h * 0.52f)
                val arcRect = Rect(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius
                )

                val historyPath = Path().apply {
                    addArc(arcRect, startAngleDegrees = -145f, sweepAngleDegrees = 305f)
                }
                drawPath(path = historyPath, color = color, style = stroke)

                val arrowTip = Offset(w * 0.19f, h * 0.32f)
                val arrowPath = Path().apply {
                    moveTo(w * 0.17f, h * 0.14f)
                    lineTo(arrowTip.x, arrowTip.y)
                    lineTo(w * 0.36f, h * 0.31f)
                }
                drawPath(path = arrowPath, color = color, style = stroke)

                val handsPath = Path().apply {
                    moveTo(center.x, center.y - radius * 0.52f)
                    lineTo(center.x, center.y)
                    lineTo(center.x + radius * 0.42f, center.y + radius * 0.26f)
                }
                drawPath(path = handsPath, color = color, style = stroke)
            }

            ZaikiNavIconType.SETTINGS -> {
                val cx = w * 0.5f
                val cy = h * 0.5f
                val outerR = w * 0.42f
                val valleyR = w * 0.31f
                val innerR = w * 0.145f
                val teeth = 8

                val gearPath = Path()
                val stepRad = (2.0 * Math.PI) / teeth
                val toothHalfOuter = stepRad * 0.17
                val toothHalfValley = stepRad * 0.27

                for (i in 0 until teeth) {
                    val baseAngle = i * stepRad - Math.PI / 2.0
                    val a1 = baseAngle - toothHalfValley
                    val a2 = baseAngle - toothHalfOuter
                    val a3 = baseAngle + toothHalfOuter
                    val a4 = baseAngle + toothHalfValley
                    val nextValleyStart = (i + 1) * stepRad - Math.PI / 2.0 - toothHalfValley

                    val p1 = Offset(
                        (cx + valleyR * cos(a1)).toFloat(),
                        (cy + valleyR * sin(a1)).toFloat()
                    )
                    val p2 = Offset(
                        (cx + outerR * cos(a2)).toFloat(),
                        (cy + outerR * sin(a2)).toFloat()
                    )
                    val p3 = Offset(
                        (cx + outerR * cos(a3)).toFloat(),
                        (cy + outerR * sin(a3)).toFloat()
                    )
                    val p4 = Offset(
                        (cx + valleyR * cos(a4)).toFloat(),
                        (cy + valleyR * sin(a4)).toFloat()
                    )
                    val p5 = Offset(
                        (cx + valleyR * cos(nextValleyStart)).toFloat(),
                        (cy + valleyR * sin(nextValleyStart)).toFloat()
                    )

                    if (i == 0) {
                        gearPath.moveTo(p1.x, p1.y)
                    }
                    gearPath.lineTo(p2.x, p2.y)
                    gearPath.lineTo(p3.x, p3.y)
                    gearPath.lineTo(p4.x, p4.y)
                    gearPath.lineTo(p5.x, p5.y)
                }
                gearPath.close()

                drawPath(path = gearPath, color = color, style = stroke)
                drawCircle(
                    color = color,
                    radius = innerR,
                    center = Offset(cx, cy),
                    style = stroke
                )
            }
        }
    }
}
