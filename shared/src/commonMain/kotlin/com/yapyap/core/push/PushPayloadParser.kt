package com.yapyap.core.push

import com.yapyap.core.push.model.PushMessageType
import com.yapyap.core.push.model.PushPayload

object PushPayloadParser {
    fun parse(data: Map<String, String>): PushPayload {
        return PushPayload(
            type = PushMessageType.fromRaw(data["type"]),
            raw = data,
        )
    }
}
