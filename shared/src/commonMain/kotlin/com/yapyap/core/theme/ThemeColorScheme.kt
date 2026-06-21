package com.yapyap.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun rememberYapColorScheme(darkTheme: Boolean): ColorScheme
