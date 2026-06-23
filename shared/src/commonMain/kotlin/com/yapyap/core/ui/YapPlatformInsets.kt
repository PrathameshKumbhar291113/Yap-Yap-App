package com.yapyap.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Bottom safe-area padding for the chat composer (Android nav bar). */
@Composable
expect fun Modifier.chatMessageInputInsets(): Modifier
