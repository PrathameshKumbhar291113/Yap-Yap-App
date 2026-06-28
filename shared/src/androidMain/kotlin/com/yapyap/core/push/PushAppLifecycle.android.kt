package com.yapyap.core.push

import android.content.Context
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.mp.KoinPlatformTools

internal object AppForegroundTracker {
    @Volatile
    var isInForeground: Boolean = false
}

private var lifecycleCallbacksRegistered = false

internal actual fun registerAppLifecycleCallbacks(
    onForeground: () -> Unit,
    onBackground: () -> Unit,
) {
    if (lifecycleCallbacksRegistered) return
    lifecycleCallbacksRegistered = true

    val context = KoinPlatformTools.defaultContext().getOrNull()?.get<Context>()
        ?: return

    PushNotificationChannels.create(context.applicationContext)

    ProcessLifecycleOwner.get().lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppForegroundTracker.isInForeground = true
                RuntimeDiagnostics.event("Lifecycle", "ProcessLifecycle.onStart (foreground=true)")
                onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                AppForegroundTracker.isInForeground = false
                RuntimeDiagnostics.event("Lifecycle", "ProcessLifecycle.onStop (foreground=false)")
                onBackground()
            }
        },
    )
}

internal actual fun isAppInForeground(): Boolean = AppForegroundTracker.isInForeground
