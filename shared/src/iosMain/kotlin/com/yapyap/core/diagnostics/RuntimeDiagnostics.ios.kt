package com.yapyap.core.diagnostics

actual fun runtimeContext(): String {
    val ts = kotlin.time.Clock.System.now().toEpochMilliseconds()
    return "ts=$ts thread=main pid=ios"
}
