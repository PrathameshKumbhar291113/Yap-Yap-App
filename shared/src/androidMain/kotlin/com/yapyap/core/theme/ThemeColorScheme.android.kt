package com.yapyap.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun rememberYapColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) YapDarkColorScheme else YapLightColorScheme
