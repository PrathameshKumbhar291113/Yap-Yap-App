package com.yapyap.core.diagnostics

import android.os.Process

actual fun runtimeContext(): String {
    val ts = System.currentTimeMillis()
    val thread = Thread.currentThread().name
    val pid = Process.myPid()
    val process = runCatching {
        java.io.File("/proc/$pid/cmdline").readText().trim('\u0000')
    }.getOrDefault("unknown")
    return "ts=$ts thread=$thread pid=$pid process=$process"
}
