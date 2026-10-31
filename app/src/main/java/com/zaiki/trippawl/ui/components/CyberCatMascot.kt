package com.zaiki.trippawl.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaiki.trippawl.ui.theme.ZaikiPalette
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mood states for Zaiki's interactive CyberCat mascot ("Pawl").
 */
enum class CyberCatMood {
    NEUTRAL,
    HAPPY,
    ANGRY,
    ANNOYED,
    REACT,
    FLEE
}

/**
 * Zaiki Design System — Interactive CyberCat Mascot ("Pawl")
 * Source: zaiki-design-system/project/components/brand/CyberCat.jsx
 *
 * Faithfully reproduces the SVG geometry (`viewBox="0 0 100 100"`) and interactive behavior
 * of the Zaiki CyberCat:
 * - Tapping cycles playful moods (`HAPPY`, `ANGRY`, `ANNOYED`, `REACT`) with carpool quips.
 * - Spam-tapping 4+ times within 900ms triggers the `@keyframes zcatFlee` squash-and-stretch
 *   runaway animation!
 * - Pupils dilate in dark theme and narrow in light theme.
 */
@Composable
fun CyberCatMascot(
    modifier: Modifier = Modifier,
    catSize: Dp = 86.dp,
    unpaidTripCount: Int = 0
) {
    val colors = ZaikiThemeTokens.colors
    val scope = rememberCoroutineScope()

    var mood by remember { mutableStateOf(CyberCatMood.NEUTRAL) }
    var rapidClickCount by remember { mutableIntStateOf(0) }
    var lastClickTimeMs by remember { mutableLongStateOf(0L) }

    val fleeOffsetX = remember { Animatable(0f) }
    val fleeOffsetY = remember { Animatable(0f) }
    val catScaleX = remember { Animatable(1f) }
    val catScaleY = remember { Animatable(1f) }

    LaunchedEffect(mood) {
        if (mood == CyberCatMood.FLEE) {
            launch {
                fleeOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 2800
                        0f at 0
                        -18f at 160
                        120f at 550
                        650f at 1200
                        650f at 1950
                        70f at 2450
                        0f at 2800
                    }
                )
            }
            launch {
                fleeOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 2800
                        0f at 0
                        8f at 160
                        0f at 550
                        -35f at 1200
                        0f at 1950
                        -25f at 2450
                        0f at 2800
                    }
                )
            }
            launch {
                catScaleX.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 2800
                        1.0f at 0
                        1.3f at 160
                        1.32f at 550
                        0.85f at 1200
                        1.0f at 1950
                        0.85f at 2450
                        1.0f at 2800
                    }
                )
            }
            launch {
                catScaleY.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 2800
                        1.0f at 0
                        0.7f at 160
                        0.68f at 550
                        1.15f at 1200
                        1.0f at 1950
                        1.15f at 2450
                        1.0f at 2800
                    }
                )
            }
            delay(2850)
            mood = CyberCatMood.NEUTRAL
        } else if (mood != CyberCatMood.NEUTRAL) {
            delay(950)
            if (mood != CyberCatMood.FLEE) {
                mood = CyberCatMood.NEUTRAL
            }
        }
    }

    val statusHint = when (mood) {
        CyberCatMood.FLEE -> "Zooming off to avoid toll booths! 🐾💨"
        CyberCatMood.HAPPY -> "Purr-fect carpool ledger! ✨"
        CyberCatMood.ANGRY -> "Hey! Pay your fuel share first! 😾"
        CyberCatMood.ANNOYED -> "Stop poking the co-pilot..."
        CyberCatMood.REACT -> "Did fuel prices change?! 💧"
        CyberCatMood.NEUTRAL -> if (unpaidTripCount > 0) {
            "Pawl says: $unpaidTripCount unpaid trip(s) waiting · tap me!"
        } else {
            "Pawl says: All riders settled up! Meow! 🐾"
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(catSize)
                .graphicsLayer {
                    translationX = fleeOffsetX.value
                    translationY = fleeOffsetY.value
                    scaleX = catScaleX.value
                    scaleY = catScaleY.value
                }
                .zaikiSquishClickable {
                    if (mood == CyberCatMood.FLEE) return@zaikiSquishClickable
                    val now = System.currentTimeMillis()
                    if (now - lastClickTimeMs < 900L) {
                        rapidClickCount += 1
                    } else {
                        rapidClickCount = 1
                    }
                    lastClickTimeMs = now

                    if (rapidClickCount >= 4) {
                        rapidClickCount = 0
                        mood = CyberCatMood.FLEE
                    } else {
                        val nextMoods = listOf(
                            CyberCatMood.HAPPY,
                            CyberCatMood.ANGRY,
                            CyberCatMood.ANNOYED,
                            CyberCatMood.REACT
                        )
                        mood = nextMoods[rapidClickCount % nextMoods.size]
                        scope.launch {
                            catScaleX.animateTo(1.08f, tween(100))
                            catScaleX.animateTo(1f, tween(150))
                        }
                    }
                }
        ) {
            val s = size.minDimension / 100f

            // 1. Outer Ears (M24 40 L17 15 L43 30 Z and M76 40 L83 15 L57 30 Z)
            val leftEar = Path().apply {
                moveTo(24f * s, 40f * s)
                lineTo(17f * s, 15f * s)
                lineTo(43f * s, 30f * s)
                close()
            }
            val rightEar = Path().apply {
                moveTo(76f * s, 40f * s)
                lineTo(83f * s, 15f * s)
                lineTo(57f * s, 30f * s)
                close()
            }
            drawPath(leftEar, color = ZaikiPalette.CatFur)
            drawPath(rightEar, color = ZaikiPalette.CatFur)

            // 2. Inner Ears (#a754ff)
            val leftInnerEar = Path().apply {
                moveTo(26f * s, 38f * s)
                lineTo(21f * s, 21f * s)
                lineTo(39f * s, 31f * s)
                close()
            }
            val rightInnerEar = Path().apply {
                moveTo(74f * s, 38f * s)
                lineTo(79f * s, 21f * s)
                lineTo(61f * s, 31f * s)
                close()
            }
            drawPath(leftInnerEar, color = colors.highlight)
            drawPath(rightInnerEar, color = colors.highlight)

            // 3. Head ellipse (cx=50, cy=58, rx=35, ry=31)
            drawOval(
                color = ZaikiPalette.CatFur,
                topLeft = Offset((50f - 35f) * s, (58f - 31f) * s),
                size = Size(70f * s, 62f * s)
            )

            // 4. Whiskers
            val whiskerColor = Color.White.copy(alpha = 0.35f)
            val whiskerStroke = 1.4f * s
            drawLine(whiskerColor, Offset(7f * s, 56f * s), Offset(26f * s, 58f * s), whiskerStroke, StrokeCap.Round)
            drawLine(whiskerColor, Offset(7f * s, 64f * s), Offset(26f * s, 63f * s), whiskerStroke, StrokeCap.Round)
            drawLine(whiskerColor, Offset(93f * s, 56f * s), Offset(74f * s, 58f * s), whiskerStroke, StrokeCap.Round)
            drawLine(whiskerColor, Offset(93f * s, 64f * s), Offset(74f * s, 63f * s), whiskerStroke, StrokeCap.Round)

            // 5. Blush (visible when HAPPY)
            if (mood == CyberCatMood.HAPPY) {
                drawCircle(ZaikiPalette.CatBlush, radius = 5.5f * s, center = Offset(28f * s, 64f * s))
                drawCircle(ZaikiPalette.CatBlush, radius = 5.5f * s, center = Offset(72f * s, 64f * s))
            }

            // 6. Eyes
            if (mood == CyberCatMood.HAPPY) {
                val happyEyes = Path().apply {
                    moveTo(30f * s, 56f * s)
                    quadraticTo(37f * s, 48f * s, 44f * s, 56f * s)
                    moveTo(56f * s, 56f * s)
                    quadraticTo(63f * s, 48f * s, 70f * s, 56f * s)
                }
                drawPath(
                    path = happyEyes,
                    color = Color.White,
                    style = Stroke(width = 3f * s, cap = StrokeCap.Round)
                )
            } else {
                val eyeScaleY = when (mood) {
                    CyberCatMood.ANGRY -> 0.68f
                    CyberCatMood.ANNOYED -> 0.50f
                    CyberCatMood.REACT -> 1.15f
                    else -> 1.0f
                }
                val eyeRx = 8f * s
                val eyeRy = 10f * s * eyeScaleY

                // White sclera
                drawOval(
                    color = Color.White,
                    topLeft = Offset(37f * s - eyeRx, 54f * s - eyeRy),
                    size = Size(eyeRx * 2, eyeRy * 2)
                )
                drawOval(
                    color = Color.White,
                    topLeft = Offset(63f * s - eyeRx, 54f * s - eyeRy),
                    size = Size(eyeRx * 2, eyeRy * 2)
                )

                // Pupils (dilated in dark theme, slit in light theme or annoyed mood)
                val pupilRx = when {
                    mood == CyberCatMood.ANNOYED -> 1.6f * s
                    !colors.isDark -> 2.3f * s
                    else -> 4.4f * s
                }
                val pupilRy = 4.5f * s * eyeScaleY.coerceAtMost(1f)
                drawOval(
                    color = Color(0xFF1C1C1C),
                    topLeft = Offset(37f * s - pupilRx, 55f * s - pupilRy),
                    size = Size(pupilRx * 2, pupilRy * 2)
                )
                drawOval(
                    color = Color(0xFF1C1C1C),
                    topLeft = Offset(63f * s - pupilRx, 55f * s - pupilRy),
                    size = Size(pupilRx * 2, pupilRy * 2)
                )
            }

            // 7. Angry Brows
            if (mood == CyberCatMood.ANGRY) {
                drawLine(Color.White, Offset(28f * s, 41f * s), Offset(45f * s, 47f * s), 2.6f * s, StrokeCap.Round)
                drawLine(Color.White, Offset(72f * s, 41f * s), Offset(55f * s, 47f * s), 2.6f * s, StrokeCap.Round)
            }

            // 8. Pink Nose (M46 62 H54 L50 67 Z)
            val nosePath = Path().apply {
                moveTo(46f * s, 62f * s)
                lineTo(54f * s, 62f * s)
                lineTo(50f * s, 67f * s)
                close()
            }
            drawPath(nosePath, color = ZaikiPalette.CatNose)

            // 9. Mouth
            val mouthStroke = Stroke(width = 2.1f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val mouthColor = Color.White.copy(alpha = 0.88f)
            when (mood) {
                CyberCatMood.HAPPY -> {
                    val p = Path().apply {
                        moveTo(38f * s, 68f * s)
                        quadraticTo(50f * s, 79f * s, 62f * s, 68f * s)
                    }
                    drawPath(p, mouthColor, style = mouthStroke)
                }
                CyberCatMood.ANGRY -> {
                    val p = Path().apply {
                        moveTo(40f * s, 74f * s)
                        quadraticTo(50f * s, 67f * s, 60f * s, 74f * s)
                    }
                    drawPath(p, mouthColor, style = mouthStroke)
                }
                CyberCatMood.ANNOYED, CyberCatMood.REACT -> {
                    drawLine(mouthColor, Offset(42f * s, 70f * s), Offset(58f * s, 70f * s), 2.1f * s, StrokeCap.Round)
                }
                else -> {
                    val p = Path().apply {
                        moveTo(50f * s, 67f * s)
                        quadraticTo(45f * s, 72f * s, 40f * s, 68f * s)
                        moveTo(50f * s, 67f * s)
                        quadraticTo(55f * s, 72f * s, 60f * s, 68f * s)
                    }
                    drawPath(p, mouthColor, style = mouthStroke)
                }
            }

            // 10. Sweat drop on REACT
            if (mood == CyberCatMood.REACT) {
                val sweat = Path().apply {
                    moveTo(84f * s, 39f * s)
                    quadraticTo(80f * s, 46f * s, 84f * s, 50f * s)
                    quadraticTo(88f * s, 46f * s, 84f * s, 39f * s)
                    close()
                }
                drawPath(sweat, color = ZaikiPalette.CatSweat)
            }
        }

        Text(
            text = statusHint,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textFaint
        )
    }
}
