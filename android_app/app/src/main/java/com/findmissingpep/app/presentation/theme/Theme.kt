package com.findmissingpep.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Slate950,
    primaryContainer = CyanDark,
    onPrimaryContainer = CyanLight,

    secondary = EmeraldPrimary,
    onSecondary = Slate950,
    secondaryContainer = EmeraldDark,
    onSecondaryContainer = EmeraldLight,

    tertiary = AmberPrimary,
    onTertiary = Slate950,
    tertiaryContainer = AmberDark,
    onTertiaryContainer = AmberLight,

    error = RosePrimary,
    onError = Slate950,
    errorContainer = RoseDark,
    onErrorContainer = RoseLight,

    background = Slate950,
    onBackground = TextPrimary,

    surface = Slate900,
    onSurface = TextPrimary,
    surfaceVariant = Slate800,
    onSurfaceVariant = TextSecondary,

    outline = Slate700,
    outlineVariant = Slate600,
    inverseSurface = Slate100,
    inverseOnSurface = Slate900,
    inversePrimary = CyanDark,

    surfaceTint = CyanPrimary
)

@Composable
fun FindMissingPepTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Slate950.toArgb()
            window.navigationBarColor = Slate950.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
