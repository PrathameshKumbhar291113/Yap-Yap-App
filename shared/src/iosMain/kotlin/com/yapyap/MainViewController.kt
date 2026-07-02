package com.yapyap

import androidx.compose.ui.window.ComposeUIViewController
import com.yapyap.core.di.initKoin
import com.yapyap.core.call.bootstrapIosCallStack
import com.yapyap.core.push.registerYapAppLifecycleCallbacks
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    bootstrapIosCallStack()
    registerYapAppLifecycleCallbacks()
    return ComposeUIViewController { YapYapApp() }
}
