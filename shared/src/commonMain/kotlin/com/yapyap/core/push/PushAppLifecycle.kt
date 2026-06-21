package com.yapyap.core.push

internal expect fun registerPushAppForegroundCallback(callback: () -> Unit)
