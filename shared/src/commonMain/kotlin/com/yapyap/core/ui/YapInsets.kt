package com.yapyap.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/** Zero insets for nested scaffolds whose parent already applied safe-area padding. */
val YapZeroWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)

object YapInsets {
    /**
     * Status bar only — bottom safe area is handled by [androidx.compose.material3.Scaffold] bottom bars.
     * Avoids stacking top + bottom padding twice on chat input and tabs.
     */
    val content: WindowInsets
        @Composable
        get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    /**
     * Parent [androidx.compose.material3.Scaffold] already applies [content]; TopAppBar should not
     * add status-bar padding again.
     */
    val topAppBar: WindowInsets
        @Composable
        get() = YapZeroWindowInsets
}
