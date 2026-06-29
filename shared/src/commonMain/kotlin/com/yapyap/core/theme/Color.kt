package com.yapyap.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Shared brand palette — identical on Android and iOS (no platform-specific accents).

/** Deep teal / emerald primary */
val YapPrimary = Color(0xFF00897B)
val YapOnPrimary = Color(0xFFFFFFFF)
val YapPrimaryContainerLight = Color(0xFFB2DFDB)
val YapOnPrimaryContainerLight = Color(0xFF00251A)

/** Mint accent / secondary */
val YapSecondary = Color(0xFF4DB6AC)
val YapOnSecondaryLight = Color(0xFF003731)
val YapSecondaryContainerLight = Color(0xFFD4F5F0)
val YapOnSecondaryContainerLight = Color(0xFF00201C)

val YapBackgroundLight = Color(0xFFFAFAFA)
val YapSurfaceLight = Color(0xFFFFFFFF)
val YapSurfaceVariantLight = Color(0xFFEEEEEE)
val YapOutlineLight = Color(0xFFBDBDBD)

val YapBackgroundDark = Color(0xFF121212)
val YapSurfaceDark = Color(0xFF1E1E1E)
val YapSurfaceVariantDark = Color(0xFF2C2C2C)
val YapPrimaryContainerDark = Color(0xFF004D40)
val YapOnPrimaryContainerDark = Color(0xFFB2DFDB)
val YapSecondaryContainerDark = Color(0xFF005047)
val YapOnSecondaryContainerDark = Color(0xFFB2DFDB)
val YapOutlineDark = Color(0xFF5C5C5C)

val YapError = Color(0xFFB3261E)
val YapOnError = Color(0xFFFFFFFF)
val YapErrorDark = Color(0xFFF2B8B5)
val YapOnErrorDark = Color(0xFF601410)

/** Read receipt / delivered tick accent */
val YapReadReceipt = Color(0xFF4FC3F7)
val YapOnReadReceipt = Color(0xFF003544)

val YapSuccess = Color(0xFF2E7D32)

val YapLightColorScheme = lightColorScheme(
    primary = YapPrimary,
    onPrimary = YapOnPrimary,
    primaryContainer = YapPrimaryContainerLight,
    onPrimaryContainer = YapOnPrimaryContainerLight,
    secondary = YapSecondary,
    onSecondary = YapOnPrimary,
    secondaryContainer = YapSecondaryContainerLight,
    onSecondaryContainer = YapOnSecondaryContainerLight,
    tertiary = YapReadReceipt,
    onTertiary = YapOnReadReceipt,
    background = YapBackgroundLight,
    onBackground = Color(0xFF1A1C1E),
    surface = YapSurfaceLight,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = YapSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    outline = YapOutlineLight,
    outlineVariant = Color(0xFFE0E0E0),
    error = YapError,
    onError = YapOnError,
)

val YapDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF003731),
    primaryContainer = YapPrimaryContainerDark,
    onPrimaryContainer = YapOnPrimaryContainerDark,
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = YapSecondaryContainerDark,
    onSecondaryContainer = YapOnSecondaryContainerDark,
    tertiary = YapReadReceipt,
    onTertiary = YapOnReadReceipt,
    background = YapBackgroundDark,
    onBackground = Color(0xFFE6E1E5),
    surface = YapSurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = YapSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF363636),
    outline = YapOutlineDark,
    outlineVariant = Color(0xFF3D3D3D),
    error = YapErrorDark,
    onError = YapOnErrorDark,
)
