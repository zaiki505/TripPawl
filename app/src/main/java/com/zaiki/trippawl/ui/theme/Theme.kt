package com.zaiki.trippawl.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ZaikiDarkColorScheme = darkColorScheme(
    primary = ZaikiPalette.HighlightPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF331857),
    onPrimaryContainer = ZaikiPalette.AccentPale,
    secondary = ZaikiPalette.AccentSoft,
    onSecondary = Color(0xFF1C1C1C),
    background = DarkZaikiExtendedColors.bgPage,
    onBackground = DarkZaikiExtendedColors.textPrimary,
    surface = DarkZaikiExtendedColors.sheetBg,
    onSurface = DarkZaikiExtendedColors.textPrimary,
    surfaceVariant = DarkZaikiExtendedColors.bgRaised,
    onSurfaceVariant = DarkZaikiExtendedColors.textBody,
    surfaceContainer = DarkZaikiExtendedColors.bgRaised,
    error = DarkZaikiExtendedColors.unavailable,
    onError = Color.White,
    outline = DarkZaikiExtendedColors.borderChip
)

private val ZaikiLightColorScheme = lightColorScheme(
    primary = ZaikiPalette.HighlightPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E4FF),
    onPrimaryContainer = Color(0xFF421278),
    secondary = ZaikiPalette.AccentDeep,
    onSecondary = Color.White,
    background = LightZaikiExtendedColors.bgPage,
    onBackground = LightZaikiExtendedColors.textPrimary,
    surface = Color.White,
    onSurface = LightZaikiExtendedColors.textPrimary,
    surfaceVariant = LightZaikiExtendedColors.bgRaised,
    onSurfaceVariant = LightZaikiExtendedColors.textBody,
    surfaceContainer = Color.White,
    error = LightZaikiExtendedColors.unavailable,
    onError = Color.White,
    outline = LightZaikiExtendedColors.borderChip
)

/**
 * Zaiki Theme wrapper. Theme switches cross-fade over 450ms (`--dur-theme: 0.45s`).
 */
@Composable
fun ZaikiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val target = if (darkTheme) DarkZaikiExtendedColors else LightZaikiExtendedColors
    val spec = tween<Color>(durationMillis = 450)

    val bgPage by animateColorAsState(target.bgPage, spec, label = "bgPage")
    val glassFill by animateColorAsState(target.glassFill, spec, label = "glassFill")
    val textPrimary by animateColorAsState(target.textPrimary, spec, label = "textPrimary")
    val textBody by animateColorAsState(target.textBody, spec, label = "textBody")
    val textMuted by animateColorAsState(target.textMuted, spec, label = "textMuted")
    val navShell by animateColorAsState(target.navShell, spec, label = "navShell")

    val animated = target.copy(
        bgPage = bgPage,
        glassFill = glassFill,
        textPrimary = textPrimary,
        textBody = textBody,
        textMuted = textMuted,
        navShell = navShell
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalZaikiColors provides animated) {
        MaterialTheme(
            colorScheme = if (darkTheme) ZaikiDarkColorScheme else ZaikiLightColorScheme,
            typography = ZaikiTypography,
            shapes = ZaikiShapes,
            content = content
        )
    }
}

/**
 * Convenience accessor for Zaiki extended design tokens.
 */
object ZaikiThemeTokens {
    val colors: ZaikiExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZaikiColors.current
}
