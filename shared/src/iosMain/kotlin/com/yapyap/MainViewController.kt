package com.yapyap

import androidx.compose.ui.window.ComposeUIViewController
import com.yapyap.core.di.initKoin

fun MainViewController() = ComposeUIViewController {
    initKoin()
    YapYapApp()
}
