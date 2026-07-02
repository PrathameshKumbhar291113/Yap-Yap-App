package com.yapyap.feature.call.presentation

import androidx.compose.runtime.Composable

@Composable
expect fun CallBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
