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
     * Horizontal safe drawing only for nested scaffolds inside [com.yapyap.core.navigation.MainScaffold].
     * Top inset is consumed by each screen's TopAppBar; bottom by the navigation bar.
     */
    val scaffoldContent: WindowInsets
        @Composable
        get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

    /**
     * Horizontal safe drawing for standalone screens (chat, auth) that own their TopAppBar.
     */
    val screenContent: WindowInsets
        @Composable
        get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

    /**
     * TopAppBar on standalone screens (Chat, Auth) — consumes status-bar safe area once.
     */
    val topAppBarStandalone: WindowInsets
        @Composable
        get() = platformTopAppBarStandalone()

    /**
     * TopAppBar on tab screens nested inside [com.yapyap.core.navigation.MainScaffold].
     * On iOS the UIKit safe area already offsets content; extra TopAppBar insets double-count.
     */
    val topAppBarNested: WindowInsets
        @Composable
        get() = platformTopAppBarNested()

    /** Bottom navigation bar — platform-tuned to avoid double-counting home indicator. */
    val bottomNavigationBar: WindowInsets
        @Composable
        get() = platformBottomNavigationBar()
}

@Composable
expect fun platformTopAppBarStandalone(): WindowInsets

@Composable
expect fun platformTopAppBarNested(): WindowInsets

@Composable
expect fun platformBottomNavigationBar(): WindowInsets
