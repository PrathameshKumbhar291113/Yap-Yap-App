package com.yapyap.core.push

import com.yapyap.core.session.SessionManager
import org.koin.mp.KoinPlatformTools

fun registerYapAppLifecycleCallbacks() {
    val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
    registerAppLifecycleCallbacks(
        onForeground = {
            koin.get<SessionManager>().onAppForeground()
            koin.get<PushNotificationCoordinator>().onAppForeground()
        },
        onBackground = {
            koin.get<SessionManager>().onAppBackground()
        },
    )
}
