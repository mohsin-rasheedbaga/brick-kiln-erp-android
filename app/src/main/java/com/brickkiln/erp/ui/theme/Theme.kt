package com.brickkiln.erp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrickRed = Color(0xFFB7410E)
private val BrickRedDark = Color(0xFF8B2E0A)
private val Cream = Color(0xFFFAF5EE)

private val LightColors = lightColorScheme(
    primary = BrickRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCD1),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF77574C),
    background = Cream,
    surface = Color.White,
    onSurface = Color(0xFF211A17),
)

private val DarkColors = darkColorScheme(
    primary = BrickRed,
    onPrimary = Color.White,
    primaryContainer = BrickRedDark,
    onPrimaryContainer = Color(0xFFFFDCD1),
    secondary = Color(0xFFE7BDB0),
    background = Color(0xFF211A17),
    surface = Color(0xFF2C2421),
    onSurface = Color(0xFFEDE0DB),
)

@Composable
fun BrickKilnTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
