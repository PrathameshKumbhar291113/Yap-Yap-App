package com.yapyap.feature.call.presentation

import android.app.Activity
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

actual fun applyCallSystemChrome(active: Boolean, color: androidx.compose.ui.graphics.Color) {
    val activity = currentActivityOrNull() ?: return
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (active) {
        if (CallChromeState.savedStatusBarColor == null) {
            CallChromeState.savedStatusBarColor = window.statusBarColor
            CallChromeState.savedNavBarColor = window.navigationBarColor
            CallChromeState.savedLightStatusBars = controller.isAppearanceLightStatusBars
            CallChromeState.savedLightNavBars = controller.isAppearanceLightNavigationBars
        }
        window.statusBarColor = color.toArgb()
        controller.isAppearanceLightStatusBars = false
    } else {
        CallChromeState.savedStatusBarColor?.let { window.statusBarColor = it }
        CallChromeState.savedNavBarColor?.let { window.navigationBarColor = it }
        CallChromeState.savedLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
        CallChromeState.savedLightNavBars?.let { controller.isAppearanceLightNavigationBars = it }
        CallChromeState.savedStatusBarColor = null
        CallChromeState.savedNavBarColor = null
        CallChromeState.savedLightStatusBars = null
        CallChromeState.savedLightNavBars = null
        window.navigationBarColor = Color.TRANSPARENT
    }
}

private fun currentActivityOrNull(): Activity? = ActivityHolder.current

private object CallChromeState {
    var savedStatusBarColor: Int? = null
    var savedNavBarColor: Int? = null
    var savedLightStatusBars: Boolean? = null
    var savedLightNavBars: Boolean? = null
}
