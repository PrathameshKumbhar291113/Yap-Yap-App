package com.yapyap.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun YapYapTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = rememberYapColorScheme(darkTheme),
        typography = YapTypography,
        content = content,
    )
}
