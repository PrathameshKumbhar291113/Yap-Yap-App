package com.yapyap.core.push

import android.content.Intent
import com.yapyap.core.diagnostics.RuntimeDiagnostics

/** Android-only intent / FCM payload dump helpers for killed-app investigations. */
object AndroidPushRuntimeTrace {

    fun dumpIntent(component: String, label: String, intent: Intent?) {
        if (intent == null) {
            RuntimeDiagnostics.event(component, "$label intent=null")
            return
        }
        val extras = intent.extras
        val extraPairs = extras?.keySet()?.sorted()?.joinToString(" ") { key ->
            val value = extras.get(key)?.toString()?.replace('\n', ' ') ?: "null"
            "$key=$value"
        } ?: "none"
        RuntimeDiagnostics.enter(
            component,
            label,
            "action=${intent.action} data=${intent.dataString} flags=0x${intent.flags.toString(16)} " +
                "component=${intent.component} categories=${intent.categories} extras={$extraPairs}",
        )
    }

    fun dumpPendingIntentExtras(component: String, intent: Intent) {
        dumpIntent(component, "PendingIntent.targetIntent", intent)
    }
}
