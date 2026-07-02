package com.yapyap.feature.call.domain.usecase

import com.yapyap.core.call.CallDiagnostics
import com.yapyap.feature.call.domain.repository.CallMediaRepository

class ToggleCameraUseCase(
    private val callMediaRepository: CallMediaRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        val next = !callMediaRepository.isCameraEnabled.value
        return callMediaRepository.setCameraEnabled(next).also { result ->
            CallDiagnostics.stage(
                stage = if (next) "CALL_CAMERA_ENABLED" else "CALL_CAMERA_DISABLED",
                extra = mapOf("success" to result.isSuccess.toString()),
            )
        }
    }
}
