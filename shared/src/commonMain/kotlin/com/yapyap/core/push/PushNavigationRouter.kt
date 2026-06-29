package com.yapyap.core.push

import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.push.model.PushNavigationTarget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class PushNavigationRouter {
    private val _targets = MutableSharedFlow<PushNavigationTarget>(extraBufferCapacity = 4)
    val targets: SharedFlow<PushNavigationTarget> = _targets.asSharedFlow()

    private val _pendingTarget = MutableStateFlow<PushNavigationTarget?>(null)
    val pendingTarget: StateFlow<PushNavigationTarget?> = _pendingTarget.asStateFlow()

    fun navigate(target: PushNavigationTarget) {
        RuntimeDiagnostics.enter("DeepLink", "PushNavigationRouter.navigate", "DEEP_LINK_REQUEST target=$target")
        _pendingTarget.value = target
        _targets.tryEmit(target)
    }

    fun consumePending(): PushNavigationTarget? {
        val target = _pendingTarget.value ?: return null
        _pendingTarget.value = null
        return target
    }

    fun replayPending() {
        _pendingTarget.value?.let { pending ->
            RuntimeDiagnostics.event("DeepLink", "DEEP_LINK_REPLAY target=$pending")
            _targets.tryEmit(pending)
        }
    }
}
