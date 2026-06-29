package com.yapyap.core.util

import co.touchlab.kermit.platformLogWriter

internal actual fun platformLogWriters(isDebug: Boolean) {
    co.touchlab.kermit.Logger.setLogWriters(platformLogWriter())
}

internal actual fun logPlatformHint() = Unit
