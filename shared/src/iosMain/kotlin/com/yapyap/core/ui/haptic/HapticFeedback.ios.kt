package com.yapyap.core.ui.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberSendHapticEffect(): () -> Unit = remember { {} }
