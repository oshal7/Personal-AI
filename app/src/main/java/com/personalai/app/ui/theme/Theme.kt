package com.personalai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = PersonalAiPrimary,
    onPrimary = PersonalAiOnPrimary,
    secondary = PersonalAiSecondary,
    onSecondary = PersonalAiOnSecondary,
    tertiary = PersonalAiTertiary,
    onTertiary = PersonalAiOnTertiary,
    background = PersonalAiBackgroundDark,
    onBackground = PersonalAiOnSurfaceDark,
    surface = PersonalAiSurfaceDark,
    onSurface = PersonalAiOnSurfaceDark,
    surfaceVariant = PersonalAiSurfaceVariantDark,
    onSurfaceVariant = PersonalAiOnSurfaceVariantDark,
    outline = PersonalAiOutlineDark,
)

private val LightColors = lightColorScheme(
    primary = PersonalAiPrimaryDark,
    onPrimary = PersonalAiOnPrimary,
    secondary = PersonalAiSecondary,
    onSecondary = PersonalAiOnSecondary,
    tertiary = PersonalAiTertiary,
    onTertiary = PersonalAiOnTertiary,
    background = PersonalAiBackgroundLight,
    onBackground = PersonalAiOnSurfaceLight,
    surface = PersonalAiSurfaceLight,
    onSurface = PersonalAiOnSurfaceLight,
    surfaceVariant = PersonalAiSurfaceVariantLight,
    onSurfaceVariant = PersonalAiOnSurfaceVariantLight,
    outline = PersonalAiOutlineLight,
)

/** Bubble color for the user's own messages — distinct from [ColorScheme.primary] so user/assistant bubbles read clearly apart. */
val ColorScheme.userBubble: Color
    get() = if (this === DarkColors) PersonalAiUserBubbleDark else PersonalAiUserBubbleLight

@Composable
fun PersonalAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = PersonalAiShapes,
        content = content,
    )
}
