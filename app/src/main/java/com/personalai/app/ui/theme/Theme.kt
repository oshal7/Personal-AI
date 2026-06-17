package com.personalai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = PersonalAiPrimary,
    onPrimary = PersonalAiOnPrimary,
    background = PersonalAiBackground,
    surface = PersonalAiSurface,
    onSurface = PersonalAiOnSurface,
)

private val LightColors = lightColorScheme(
    primary = PersonalAiPrimary,
    onPrimary = PersonalAiOnPrimary,
)

@Composable
fun PersonalAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
