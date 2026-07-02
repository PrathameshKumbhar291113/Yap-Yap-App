package com.yapyap.feature.call.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import com.yapyap.core.theme.YapCallGreen

@Composable
fun CallActiveChromeEffect(isActive: Boolean) {
    DisposableEffect(isActive) {
        if (isActive) {
            applyCallSystemChrome(active = true, color = YapCallGreen)
        }
        onDispose {
            applyCallSystemChrome(active = false, color = YapCallGreen)
        }
    }
}

expect fun applyCallSystemChrome(active: Boolean, color: Color)
