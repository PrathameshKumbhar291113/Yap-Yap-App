package com.yapyap.core.util

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import com.yapyap.core.config.AppBuildConfig

internal actual fun platformLogWriters(isDebug: Boolean) {
    if (isDebug) {
        Logger.setLogWriters(platformLogWriter(), IosDebugFileLogWriter())
    } else {
        Logger.setLogWriters(platformLogWriter())
    }
}

internal actual fun logPlatformHint() {
    if (AppBuildConfig.IS_DEBUG) {
        println(
            "[YapRuntimeDiag] iOS debug logs: run `./scripts/ios-logs.sh` " +
                "or open Documents/yapyap-runtime.log in the app container",
        )
    }
}
