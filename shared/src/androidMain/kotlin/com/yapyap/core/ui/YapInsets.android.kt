package com.yapyap.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

@Composable
actual fun platformTopAppBarStandalone(): WindowInsets = TopAppBarDefaults.windowInsets

@Composable
actual fun platformTopAppBarNested(): WindowInsets = TopAppBarDefaults.windowInsets

/** Bottom nav consumes system nav-bar inset once (edge-to-edge). */
@Composable
actual fun platformBottomNavigationBar(): WindowInsets =
    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
