package com.notch.island.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IslandDarkColorScheme = darkColorScheme(
    primary = TextPrimary,
    surface = OledBlack,
    background = OledBlack,
    onPrimary = OledBlack,
    onSurface = TextPrimary,
    onBackground = TextPrimary
)

@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IslandDarkColorScheme,
        content = content
    )
}
