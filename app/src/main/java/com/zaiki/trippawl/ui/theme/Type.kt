package com.zaiki.trippawl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zaiki.trippawl.R

/**
 * Zaiki Design System — Typography
 * Non-negotiable rule: JetBrains Mono for EVERYTHING.
 *
 * The font files are bundled in `res/font/` (JetBrains Mono, OFL-1.1) so the app works fully
 * offline with no dependency on Google Play Services' downloadable-font provider.
 *
 * Monospace glyphs are wide (0.6em advance), so the scale is slightly tighter than Material
 * defaults to keep money values and headings on one line on 360dp phones.
 */
val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold)
)

private fun mono(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0
) = TextStyle(
    fontFamily = JetBrainsMonoFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val ZaikiTypography = Typography(
    // Hero money values (tight 1.05 display line-height)
    displayLarge = mono(FontWeight.ExtraBold, 34, 36, -1.0),
    displayMedium = mono(FontWeight.ExtraBold, 28, 30, -0.8),
    displaySmall = mono(FontWeight.Bold, 24, 27, -0.5),
    // Page titles
    headlineLarge = mono(FontWeight.Bold, 24, 28, -0.4),
    headlineMedium = mono(FontWeight.Bold, 20, 24, -0.3),
    headlineSmall = mono(FontWeight.Bold, 18, 22, -0.2),
    // Card headings
    titleLarge = mono(FontWeight.Bold, 17, 22, -0.2),
    titleMedium = mono(FontWeight.Bold, 15, 20),
    titleSmall = mono(FontWeight.SemiBold, 13, 18),
    // Body (1.6 line-height)
    bodyLarge = mono(FontWeight.Normal, 14, 22),
    bodyMedium = mono(FontWeight.Normal, 13, 20),
    bodySmall = mono(FontWeight.Normal, 12, 18),
    // Buttons / kickers / tag pills
    labelLarge = mono(FontWeight.Bold, 13, 18, 0.2),
    labelMedium = mono(FontWeight.Bold, 11, 15, 1.1),  // kicker: --tracking-kicker 0.1em
    labelSmall = mono(FontWeight.Bold, 10, 13, 0.5)     // tag: --tracking-tag 0.05em
)
