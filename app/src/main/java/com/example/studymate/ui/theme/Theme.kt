package com.example.studymate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = StudyBlue,
    onPrimary = StudySurface,
    primaryContainer = StudyBlueContainer,
    onPrimaryContainer = StudyText,

    secondary = StudyGreen,
    onSecondary = StudySurface,
    secondaryContainer = StudyGreenContainer,
    onSecondaryContainer = StudyText,

    tertiary = StudyAmber,
    onTertiary = StudySurface,
    tertiaryContainer = StudyAmberContainer,
    onTertiaryContainer = StudyText,

    background = StudyBackground,
    onBackground = StudyText,
    surface = StudySurface,
    onSurface = StudyText,
    surfaceVariant = StudySurfaceVariant,
    onSurfaceVariant = StudyMutedText,
    error = StudyError,
    outline = StudyOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = StudyDarkBlue,
    onPrimary = StudyDarkBackground,
    primaryContainer = StudyDarkBlueContainer,
    onPrimaryContainer = StudyDarkText,

    secondary = StudyDarkGreen,
    onSecondary = StudyDarkBackground,
    secondaryContainer = StudyDarkGreenContainer,
    onSecondaryContainer = StudyDarkText,

    tertiary = StudyDarkAmber,
    onTertiary = StudyDarkBackground,
    tertiaryContainer = StudyDarkAmberContainer,
    onTertiaryContainer = StudyDarkText,

    background = StudyDarkBackground,
    onBackground = StudyDarkText,
    surface = StudyDarkSurface,
    onSurface = StudyDarkText,
    surfaceVariant = StudyDarkSurfaceVariant,
    onSurfaceVariant = StudyDarkMutedText,
    error = StudyDarkError,
    outline = StudyDarkOutline
)

@Composable
fun StudyMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}