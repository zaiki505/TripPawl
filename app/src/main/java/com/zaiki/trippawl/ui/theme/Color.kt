package com.zaiki.trippawl.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Zaiki Design System — Color Tokens
 * Source: zaiki-design-system/project/tokens/colors.css
 * Dark-first palette with ONE purple accent (#A754FF) and light-theme overrides.
 */
object ZaikiPalette {
    // Primary Brand Accent ("THE purple accent") + gradient partners
    val HighlightPurple = Color(0xFFA754FF)
    val AccentSoft = Color(0xFFD48CFF)
    val AccentPale = Color(0xFFC2A0F5)
    val AccentDeep = Color(0xFF7B5BFF)
    val PrimaryHoverPurple = Color(0xFFC893FA)

    // Status hues (--available / --unavailable)
    val AvailableGreen = Color(0xFF00E676)
    val UnavailableRed = Color(0xFFFF1744)

    // Aurora companions (hero background blobs, full opacity — alpha applied when drawn)
    val AuroraPurple = Color(0xFFA754FF)
    val AuroraPink = Color(0xFFEC489A)
    val AuroraBlue = Color(0xFF2DABFF)

    // Category tag backgrounds / borders (shared by both themes)
    val TagWebBg = Color(0x243B9EFF)
    val TagWebBorder = Color(0x523B9EFF)
    val TagSystemBg = Color(0x24A754FF)
    val TagSystemBorder = Color(0x52A754FF)
    val TagCreativeBg = Color(0x24FF7A3C)
    val TagCreativeBorder = Color(0x57FF7A3C)
    val TagPersonalBg = Color(0x2410B981)
    val TagPersonalBorder = Color(0x5710B981)

    // CyberCat mascot palette
    val CatFur = Color(0xFF423B4F)
    val CatNose = Color(0xFFDB68D7)
    val CatBlush = Color(0xA6FF82B4)
    val CatSweat = Color(0xFF7FD4FF)
}

/**
 * Semantic tokens consumed by every TripPawl composable.
 * Glass surfaces are built from 4 layers: fill → top sheen → accent glow → gradient hairline.
 */
@Immutable
data class ZaikiExtendedColors(
    val isDark: Boolean,
    // Page
    val bgPage: Color,
    val bgRaised: Color,
    // Glassmorphism layers
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassSheenTop: Color,
    val glassSheenBottom: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val navShell: Color,
    val sheetBg: Color,
    val scrim: Color,
    // Controls
    val surfaceChip: Color,
    val inputFill: Color,
    // Text
    val textPrimary: Color,
    val textBody: Color,
    val textMuted: Color,
    val textFaint: Color,
    // Borders
    val borderHairline: Color,
    val borderChip: Color,
    val borderAccent: Color,
    // Accent
    val highlight: Color,
    val accentSoft: Color,
    val accentPale: Color,
    val accentDeep: Color,
    val accentWash: Color,
    // Status
    val available: Color,
    val unavailable: Color,
    // Tag text
    val tagWebText: Color,
    val tagSystemText: Color,
    val tagCreativeText: Color,
    val tagPersonalText: Color,
    // Aurora intensity multiplier
    val auroraStrength: Float,
    val grainAlpha: Float
)

val DarkZaikiExtendedColors = ZaikiExtendedColors(
    isDark = true,
    bgPage = Color(0xFF141418),
    bgRaised = Color(0xFF20202A),
    glassFill = Color(0x52000000),          // rgba(0,0,0,0.32) — .z-panel / .bento-card
    glassFillStrong = Color(0xEB17171D),    // dialogs & sheets (no backdrop to blur)
    glassSheenTop = Color(0x14FFFFFF),
    glassSheenBottom = Color(0x03FFFFFF),
    glassBorderTop = Color(0x26FFFFFF),
    glassBorderBottom = Color(0x0AFFFFFF),
    navShell = Color(0xD91A1A21),
    sheetBg = Color(0xFF16161B),
    scrim = Color(0x99000000),
    surfaceChip = Color(0x12FFFFFF),        // --surface-chip
    inputFill = Color(0x0FFFFFFF),          // .z-field input rgba(255,255,255,0.06)
    textPrimary = Color(0xFFFFFFFF),
    textBody = Color(0xB8FFFFFF),
    textMuted = Color(0x8CFFFFFF),
    textFaint = Color(0x66FFFFFF),
    borderHairline = Color(0x14FFFFFF),
    borderChip = Color(0x1AFFFFFF),
    borderAccent = Color(0x47A754FF),
    highlight = ZaikiPalette.HighlightPurple,
    accentSoft = ZaikiPalette.AccentSoft,
    accentPale = ZaikiPalette.AccentPale,
    accentDeep = ZaikiPalette.AccentDeep,
    accentWash = Color(0x1FA754FF),
    available = Color(0xFF00E676),
    unavailable = Color(0xFFFF4D6A),
    tagWebText = Color(0xFF79C2FF),
    tagSystemText = Color(0xFFC79BFF),
    tagCreativeText = Color(0xFFFF9D6B),
    tagPersonalText = Color(0xFF44D6A7),
    auroraStrength = 1f,
    grainAlpha = 0.05f
)

val LightZaikiExtendedColors = ZaikiExtendedColors(
    isDark = false,
    bgPage = Color(0xFFF6F6F8),
    bgRaised = Color(0xFFE0E0E4),
    glassFill = Color(0xB8FFFFFF),
    glassFillStrong = Color(0xF7FFFFFF),
    glassSheenTop = Color(0x80FFFFFF),
    glassSheenBottom = Color(0x00FFFFFF),
    glassBorderTop = Color(0x14000000),
    glassBorderBottom = Color(0x0D000000),
    navShell = Color(0xE6FFFFFF),
    sheetBg = Color(0xFFF6F6F8),
    scrim = Color(0x52000000),
    surfaceChip = Color(0x0A000000),
    inputFill = Color(0x0A000000),
    textPrimary = Color(0xFF1C1C1C),
    textBody = Color(0xAD1C1C1C),
    textMuted = Color(0x801C1C1C),
    textFaint = Color(0x731C1C1C),
    borderHairline = Color(0x14000000),
    borderChip = Color(0x14000000),
    borderAccent = Color(0x47A754FF),
    highlight = ZaikiPalette.HighlightPurple,
    accentSoft = ZaikiPalette.AccentSoft,
    accentPale = ZaikiPalette.AccentPale,
    accentDeep = ZaikiPalette.AccentDeep,
    accentWash = Color(0x16A754FF),
    available = Color(0xFF059669),
    unavailable = Color(0xFFE11D48),
    tagWebText = Color(0xFF1F6FD6),
    tagSystemText = Color(0xFF7A31D6),
    tagCreativeText = Color(0xFFD2540F),
    tagPersonalText = Color(0xFF0B8A63),
    auroraStrength = 0.55f,
    grainAlpha = 0.035f
)

val LocalZaikiColors = staticCompositionLocalOf { DarkZaikiExtendedColors }
