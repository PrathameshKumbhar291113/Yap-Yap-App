package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.reader.CallSessionReader
import kotlinx.coroutines.flow.StateFlow

class ObserveActiveCallUseCase(
    private val callSessionReader: CallSessionReader,
) {
    operator fun invoke(): StateFlow<CallState> = callSessionReader.state
}
