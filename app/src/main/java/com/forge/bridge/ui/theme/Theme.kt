package com.forge.bridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ForgeOrange,
    onPrimary = ForgeDark,
    secondary = ForgeOrangeDark,
    background = ForgeDark,
    onBackground = ForgeText,
    surface = ForgeGray,
    onSurface = ForgeText,
    surfaceVariant = ForgeGrayLight,
    onSurfaceVariant = ForgeTextDim,
    error = ForgeError
)

private val LightColors = lightColorScheme(
    primary = ForgeOrange,
    secondary = ForgeOrangeDark,
    error = ForgeError
)

@Composable
fun ForgeBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
