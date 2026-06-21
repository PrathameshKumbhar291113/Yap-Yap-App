package com.yapyap.core.ui.haptic

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberSendHapticEffect(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }
}
