package com.yapyap.core.push

import com.yapyap.core.session.SessionManager
import org.koin.mp.KoinPlatformTools

fun registerYapAppLifecycleCallbacks() {
    val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
    registerAppLifecycleCallbacks(
        onForeground = {
            runCatching { koin.get<SessionManager>().onAppForeground() }
            runCatching { koin.get<PushNotificationCoordinator>().onAppForeground() }
        },
        onBackground = {
            runCatching { koin.get<SessionManager>().onAppBackground() }
        },
    )
}
