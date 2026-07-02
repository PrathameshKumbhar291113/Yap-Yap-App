package com.yapyap.feature.call.presentation

import androidx.compose.runtime.Composable

@Composable
actual fun CallBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // iOS has no system back gesture for full-screen overlays; minimize via in-call UI.
}
