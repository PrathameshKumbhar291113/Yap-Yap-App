package com.yapyap

import androidx.compose.ui.window.ComposeUIViewController
import com.yapyap.core.di.initKoin
import com.yapyap.core.push.registerYapAppLifecycleCallbacks

fun MainViewController() = ComposeUIViewController {
    initKoin()
    registerYapAppLifecycleCallbacks()
    YapYapApp()
}
