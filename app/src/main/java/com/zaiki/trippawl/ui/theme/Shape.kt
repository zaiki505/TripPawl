package com.zaiki.trippawl.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Zaiki Design System — Corner Radii
 * Source: zaiki-design-system/project/tokens/surfaces.css
 * - Buttons: 8dp · Inputs: 10dp · Panels: 12dp · Cards: 18dp · Banner/dialog: 24dp · Pills: 999px
 */
object ZaikiRadii {
    val Button = RoundedCornerShape(8.dp)
    val InputField = RoundedCornerShape(10.dp)
    val Panel = RoundedCornerShape(12.dp)
    val Block = RoundedCornerShape(15.dp)
    val Card = RoundedCornerShape(18.dp)
    val Banner = RoundedCornerShape(24.dp)
    val NavShell = RoundedCornerShape(50.dp)
    val Pill = CircleShape
}

/**
 * Shared spacing so every screen uses the same rhythm.
 */
object ZaikiDimens {
    val ScreenHorizontal = 20.dp
    val ScreenTop = 12.dp
    val SectionGap = 14.dp
    val CardPadding = 18.dp
    val CardPaddingCompact = 14.dp
    val InnerGap = 12.dp

    /** Space reserved under scrolling content so the floating NavPill never covers it. */
    val NavPillClearance = 112.dp

    /** Extra space on the Dashboard for the floating "Log Trip" button above the NavPill. */
    val FabClearance = 76.dp

    fun screenPadding(bottom: androidx.compose.ui.unit.Dp = NavPillClearance) = PaddingValues(
        start = ScreenHorizontal,
        end = ScreenHorizontal,
        top = ScreenTop,
        bottom = bottom
    )
}

val ZaikiShapes = Shapes(
    extraSmall = ZaikiRadii.Button,
    small = ZaikiRadii.Panel,
    medium = ZaikiRadii.Card,
    large = ZaikiRadii.Banner,
    extraLarge = ZaikiRadii.Banner
)
