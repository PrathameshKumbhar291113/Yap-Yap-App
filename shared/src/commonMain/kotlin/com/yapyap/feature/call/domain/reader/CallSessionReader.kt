package com.yapyap.feature.call.domain.reader

import com.yapyap.feature.call.domain.entity.CallState
import kotlinx.coroutines.flow.StateFlow

interface CallSessionReader {
    val state: StateFlow<CallState>
}
