package com.yapyap.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/** iOS UIKit already applies status-bar safe area; TopAppBar insets double-count. */
@Composable
actual fun platformTopAppBarStandalone(): WindowInsets = YapZeroWindowInsets

/** iOS UIKit already applies safe-area offset for nested tab scaffolds. */
@Composable
actual fun platformTopAppBarNested(): WindowInsets = YapZeroWindowInsets

/** iOS home-indicator inset is applied by UIKit; NavigationBar insets double-count. */
@Composable
actual fun platformBottomNavigationBar(): WindowInsets = YapZeroWindowInsets

/** iOS UIKit already applies safe-area insets for full-screen overlays. */
@Composable
actual fun platformFullScreenOverlay(): WindowInsets = YapZeroWindowInsets
