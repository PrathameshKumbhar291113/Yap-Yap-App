package com.yapyap.core.call

interface CallSignalingRepository {
    fun start(parentScope: kotlinx.coroutines.CoroutineScope)

    fun handleEvent(event: CallSignalingEvent)
}
