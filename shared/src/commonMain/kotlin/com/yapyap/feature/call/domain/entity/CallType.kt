package com.yapyap.feature.call.domain.entity

enum class CallType {
    Voice,
    Video,
    ;

    companion object {
        fun fromRaw(raw: String?): CallType = when (raw?.lowercase()) {
            "video" -> Video
            "audio", "voice" -> Voice
            else -> Voice
        }
    }
}
