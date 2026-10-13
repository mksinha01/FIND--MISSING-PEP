package com.findmissingpep.app.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Blue & Cream Official Color Scheme (Human Design) ───────────────────────
private val BlueAndCreamColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueWash,
    onPrimaryContainer = BlueDeep,

    secondary = GreenSuccess,
    onSecondary = Color.White,
    secondaryContainer = GreenSuccessBg,
    onSecondaryContainer = GreenSuccess,

    tertiary = AmberAlert,
    onTertiary = Color.White,
    tertiaryContainer = AmberAlertBg,
    onTertiaryContainer = AmberAlert,

    error = RedDanger,
    onError = Color.White,
    errorContainer = RedDangerBg,
    onErrorContainer = RedDanger,

    background = CreamBackground,
    onBackground = InkText,

    surface = PaperWhite,
    onSurface = InkText,
    surfaceVariant = PaperSubtle,
    onSurfaceVariant = InkMuted,

    outline = LineBorder,
    outlineVariant = LineBorderSubtle,
    inverseSurface = InkText,
    inverseOnSurface = CreamBackground,
    inversePrimary = BlueWash,

    surfaceTint = BluePrimary
)

@Composable
fun FindMissingPepTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CreamBackground.toArgb()
            window.navigationBarColor = PaperWhite.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = BlueAndCreamColorScheme,
        typography = AppTypography,
        content = content
    )
}
