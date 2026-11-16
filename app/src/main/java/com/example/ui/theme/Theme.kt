package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ProMonitorColorScheme = darkColorScheme(
    primary = ActiveGreen,
    onPrimary = CinemaBlack,
    primaryContainer = ActiveGreenDim,
    onPrimaryContainer = ActiveGreen,
    secondary = SignalCyan,
    onSecondary = CinemaBlack,
    secondaryContainer = SignalCyanDim,
    onSecondaryContainer = SignalCyan,
    tertiary = WarningOrange,
    onTertiary = CinemaBlack,
    tertiaryContainer = WarningOrangeDim,
    onTertiaryContainer = WarningOrange,
    error = RecordingRed,
    onError = TextPrimary,
    errorContainer = RecordingRedDim,
    onErrorContainer = RecordingRed,
    background = CinemaBlack,
    onBackground = TextPrimary,
    surface = CharcoalBase,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalPanel,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = CinemaBlack,
    surfaceContainerLow = CharcoalBase,
    surfaceContainer = CharcoalPanel,
    surfaceContainerHigh = CharcoalElevated,
    outline = CharcoalBorder,
    outlineVariant = CharcoalBorder
)

val ProMonitorShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ProMonitorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ProMonitorColorScheme,
        typography = ProMonitorTypography,
        shapes = ProMonitorShapes,
        content = content
    )
}
