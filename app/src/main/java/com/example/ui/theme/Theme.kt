package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val StudioColorScheme = darkColorScheme(
    primary = RoseGoldPrimary,
    onPrimary = OnRoseGold,
    primaryContainer = RoseGoldContainer,
    onPrimaryContainer = OnRoseGoldContainer,
    secondary = CopperSecondary,
    onSecondary = OnCopper,
    secondaryContainer = CopperContainer,
    onSecondaryContainer = OnCopperContainer,
    tertiary = ChampagneTertiary,
    onTertiary = OnChampagne,
    background = StudioBackground,
    onBackground = StudioOnSurface,
    surface = StudioSurface,
    onSurface = StudioOnSurface,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = StudioOnSurfaceMuted,
    surfaceContainerHigh = StudioSurfaceElevated,
    outline = StudioOutline,
    error = StudioError,
    errorContainer = StudioErrorContainer
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AIHairColorStudioTheme(
    isPersian: Boolean = LocalConfiguration.current.locales[0]?.language.equals("fa", ignoreCase = true),
    content: @Composable () -> Unit
) {
    val layoutDirection = if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = if (isPersian) PersianTypography else EnglishTypography

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = StudioColorScheme,
            typography = typography,
            shapes = StudioShapes,
            content = content
        )
    }
}
