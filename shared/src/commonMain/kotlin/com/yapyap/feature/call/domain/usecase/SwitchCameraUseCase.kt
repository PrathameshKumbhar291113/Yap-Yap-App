package com.yapyap.feature.call.domain.usecase

import com.yapyap.core.call.CallDiagnostics
import com.yapyap.feature.call.domain.repository.CallMediaRepository

class SwitchCameraUseCase(
    private val callMediaRepository: CallMediaRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        CallDiagnostics.stage(stage = "CALL_CAMERA_SWITCH_REQUEST")
        return callMediaRepository.switchCamera().also { result ->
            CallDiagnostics.stage(
                stage = if (result.isSuccess) "CALL_CAMERA_SWITCH_SUCCESS" else "CALL_CAMERA_SWITCH_FAILED",
                extra = mapOf(
                    "error" to (result.exceptionOrNull()?.message ?: ""),
                ),
            )
        }
    }
}
