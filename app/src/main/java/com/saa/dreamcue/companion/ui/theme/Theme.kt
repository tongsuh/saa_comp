package com.saa.dreamcue.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = PureBlack,
    surface = DarkSurface,
    onSurface = TextPrimary,
    background = PureBlack,
    onBackground = TextPrimary,
    outline = DarkBorder
)

@Composable
fun DreamCueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
