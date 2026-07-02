package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.mp.KoinPlatformTools

private var callBootstrapScope: CoroutineScope? = null

fun bootstrapIosCallStack() {
    val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
    if (callBootstrapScope != null) return

    val dispatcherProvider = koin.get<DispatcherProvider>()
    val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)
    callBootstrapScope = scope
    koin.get<CallCoordinator>().start(scope)
}
