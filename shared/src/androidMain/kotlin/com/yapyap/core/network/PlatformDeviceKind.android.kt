package com.yapyap.core.network

import android.os.Build

internal actual fun isVirtualDevice(): Boolean {
    return Build.FINGERPRINT.startsWith("generic")
        || Build.FINGERPRINT.startsWith("unknown")
        || Build.MODEL.contains("google_sdk", ignoreCase = true)
        || Build.MODEL.contains("Emulator", ignoreCase = true)
        || Build.MODEL.contains("Android SDK built for x86", ignoreCase = true)
        || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
        || Build.HARDWARE.contains("goldfish", ignoreCase = true)
        || Build.HARDWARE.contains("ranchu", ignoreCase = true)
        || Build.PRODUCT.contains("sdk_google", ignoreCase = true)
        || Build.PRODUCT.contains("sdk", ignoreCase = true)
        || Build.PRODUCT.contains("sdk_x86", ignoreCase = true)
        || Build.PRODUCT.contains("vbox86p", ignoreCase = true)
        || Build.PRODUCT.contains("emulator", ignoreCase = true)
        || Build.PRODUCT.contains("simulator", ignoreCase = true)
}
