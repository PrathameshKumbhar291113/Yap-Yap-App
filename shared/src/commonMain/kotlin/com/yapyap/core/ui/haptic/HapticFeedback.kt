package com.yapyap.core.ui.haptic

import androidx.compose.runtime.Composable

@Composable
expect fun rememberSendHapticEffect(): () -> Unit
