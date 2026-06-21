package com.yapyap.core.push

import com.yapyap.core.push.model.PushNavigationTarget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PushNavigationRouter {
    private val _targets = MutableSharedFlow<PushNavigationTarget>(extraBufferCapacity = 4)
    val targets: SharedFlow<PushNavigationTarget> = _targets.asSharedFlow()

    fun navigate(target: PushNavigationTarget) {
        _targets.tryEmit(target)
    }
}
