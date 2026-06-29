package com.yapyap.core.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toCValues
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

/**
 * Debug-only file log mirroring Kermit output to Documents/yapyap-runtime.log.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDebugFileLogWriter : LogWriter() {
    private val logPath: String? by lazy { resolveLogPath() }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val path = logPath ?: return
        val timestamp = NSDateFormatter().apply {
            dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        }.stringFromDate(NSDate()) ?: ""
        val line = buildString {
            append(timestamp)
            append(' ')
            append(severity.name.first())
            append(" [")
            append(tag)
            append("] ")
            append(message)
            if (throwable != null) {
                append(" :: ")
                append(throwable.message ?: throwable::class.simpleName)
            }
            append('\n')
        }
        appendLine(path, line)
        println("[YapRuntimeDiag] ${line.trimEnd()}")
    }

    private fun resolveLogPath(): String? {
        val urls = NSFileManager.defaultManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask,
        )
        val documentsUrl = (urls.firstOrNull() as? NSURL) ?: return null
        return documentsUrl.path?.let { "$it/yapyap-runtime.log" }
    }

    private fun appendLine(path: String, line: String) {
        val file = fopen(path, "a") ?: run {
            NSFileManager.defaultManager.createFileAtPath(path, contents = null, attributes = null)
            fopen(path, "a")
        } ?: return
        try {
            fputs(line, file)
        } finally {
            fclose(file)
        }
    }

    companion object {
        const val LOG_FILE_NAME = "yapyap-runtime.log"
    }
}
