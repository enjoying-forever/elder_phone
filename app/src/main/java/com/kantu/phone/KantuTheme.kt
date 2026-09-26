package com.kantu.phone

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Blue/white high-contrast palette shared by both phone screens. */
internal object KantuColors {
    val PageBg = Color(0xFFF1F5FA)
    val Card = Color.White
    val Primary = Color(0xFF245BC4)
    val OnPrimary = Color.White
    val CallAction = Color(0xFF23864B)
    val OnCallAction = Color.White
    val TextPrimary = Color(0xFF152642)
    val TextSecondary = Color(0xFF52627A)
    val Danger = Color(0xFFB83D35)
    val Missed = Color(0xFFC83B36)
    val Hairline = Color(0xFFCFD9E7)
    val TabTrack = Color(0xFFE3EBF6)
    val Avatar = Color(0xFFE2ECFF)
    val CallAvatar = Color(0xFFDFF2E7)
    val Banner = Color(0xFFFFE6A3)
}

internal object KantuSpacing {
    val Page = 12.dp
    val Gap = 8.dp
    val KeyGap = 4.dp
    val CardPadding = 12.dp
}

internal val CardShape = RoundedCornerShape(12.dp)
internal val KeyShape = RoundedCornerShape(8.dp)
internal val PillShape = RoundedCornerShape(12.dp)
internal val TabShape = RoundedCornerShape(8.dp)

@Composable
internal fun kantuButtonElevation() =
    ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 0.dp)

/**
 * Text is already sized for elders. Many of them also set the system font to its maximum,
 * which would push labels out of the fixed-size keys and buttons, so the extra scale is capped.
 */
internal const val MAX_FONT_SCALE = 1.3f

@Composable
internal fun KantuTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val capped = remember(density) {
        Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE))
    }
    CompositionLocalProvider(LocalDensity provides capped) {
        KantuMaterialTheme(content)
    }
}

@Composable
private fun KantuMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = KantuColors.Primary,
            onPrimary = KantuColors.OnPrimary,
            primaryContainer = KantuColors.Avatar,
            onPrimaryContainer = KantuColors.Primary,
            secondary = KantuColors.Primary,
            onSecondary = KantuColors.OnPrimary,
            secondaryContainer = KantuColors.Avatar,
            onSecondaryContainer = KantuColors.TextPrimary,
            background = KantuColors.PageBg,
            onBackground = KantuColors.TextPrimary,
            surface = KantuColors.Card,
            onSurface = KantuColors.TextPrimary,
            surfaceVariant = KantuColors.TabTrack,
            onSurfaceVariant = KantuColors.TextSecondary,
            surfaceTint = KantuColors.Primary,
            outline = KantuColors.TextSecondary,
            outlineVariant = KantuColors.Hairline,
            error = KantuColors.Danger,
            onError = Color.White,
        ),
        content = content,
    )
}
