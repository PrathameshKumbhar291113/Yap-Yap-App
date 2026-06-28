package com.yapyap.core.network

import platform.Foundation.NSProcessInfo

internal actual fun isVirtualDevice(): Boolean {
    return NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
}
