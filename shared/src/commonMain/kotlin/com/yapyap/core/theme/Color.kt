package com.yapyap.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Distinct teal-green accent (WhatsApp-inspired but shifted toward teal)
val YapPrimary = Color(0xFF00A884)
val YapPrimaryDark = Color(0xFF008F72)
val YapOnPrimary = Color(0xFFFFFFFF)
val YapPrimaryContainerLight = Color(0xFFD4F5EB)
val YapOnPrimaryContainerLight = Color(0xFF002117)
val YapSecondary = Color(0xFF26A69A)
val YapBackgroundLight = Color(0xFFF4F7F6)
val YapSurfaceLight = Color(0xFFFFFFFF)
val YapSurfaceVariantLight = Color(0xFFE3EBE9)
val YapOutlineLight = Color(0xFFB0BFC0)

val YapBackgroundDark = Color(0xFF0B141A)
val YapSurfaceDark = Color(0xFF1A252C)
val YapSurfaceVariantDark = Color(0xFF2A3942)
val YapPrimaryContainerDark = Color(0xFF005741)
val YapOnPrimaryContainerDark = Color(0xFFB8F5E3)
val YapOutlineDark = Color(0xFF3D4F57)

val YapError = Color(0xFFDC362E)
val YapOnError = Color(0xFFFFFFFF)
val YapReadReceipt = Color(0xFF53BDEB)

val YapLightColorScheme = lightColorScheme(
    primary = YapPrimary,
    onPrimary = YapOnPrimary,
    primaryContainer = YapPrimaryContainerLight,
    onPrimaryContainer = YapOnPrimaryContainerLight,
    secondary = YapSecondary,
    onSecondary = YapOnPrimary,
    background = YapBackgroundLight,
    onBackground = Color(0xFF1A1C1E),
    surface = YapSurfaceLight,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = YapSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF43474E),
    outline = YapOutlineLight,
    error = YapError,
    onError = YapOnError,
)

val YapDarkColorScheme = darkColorScheme(
    primary = Color(0xFF5ED9B5),
    onPrimary = Color(0xFF003828),
    primaryContainer = YapPrimaryContainerDark,
    onPrimaryContainer = YapOnPrimaryContainerDark,
    secondary = YapSecondary,
    onSecondary = Color(0xFF003731),
    background = YapBackgroundDark,
    onBackground = Color(0xFFE1E3E3),
    surface = YapSurfaceDark,
    onSurface = Color(0xFFE1E3E3),
    surfaceVariant = YapSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFBFC8CD),
    outline = YapOutlineDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
