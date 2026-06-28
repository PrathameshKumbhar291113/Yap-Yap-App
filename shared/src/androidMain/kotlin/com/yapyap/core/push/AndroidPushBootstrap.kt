package com.yapyap.core.push

import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

private var bootstrapScope: CoroutineScope? = null

/**
 * Starts push coordinator as early as possible so FCM token registration is not blocked
 * waiting for Compose [LaunchedEffect].
 */
fun bootstrapAndroidPush() {
    val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
    if (bootstrapScope != null) return

    val dispatcherProvider = koin.get<DispatcherProvider>()
    val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)
    bootstrapScope = scope

    RuntimeDiagnostics.event("PushBootstrap", "starting PushNotificationCoordinator from Application")
    scope.launch {
        runCatching {
            koin.get<PushNotificationCoordinator>().start(scope)
        }.onFailure { error ->
            RuntimeDiagnostics.event("PushBootstrap", "start failed: ${error.message}")
        }
    }
}
