package com.rmfacilities.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = RmPrimary,
    secondary = RmSecondary,
    background = RmBackground,
    surface = RmSurface,
    onPrimary = RmSurface,
    onSecondary = RmText,
    onBackground = RmText,
    onSurface = RmText,
    error = RmError
)

private val DarkColors = darkColorScheme(
    primary = RmPrimary,
    secondary = RmSecondary,
    background = RmText,
    surface = RmPrimary,
    onPrimary = RmSurface,
    onSecondary = RmText,
    onBackground = RmSurface,
    onSurface = RmSurface,
    error = RmError
)

@Composable
fun RMFacilitiesTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
