package com.yapyap.core.push

internal expect fun registerAppLifecycleCallbacks(
    onForeground: () -> Unit,
    onBackground: () -> Unit,
)

internal expect fun isAppInForeground(): Boolean
