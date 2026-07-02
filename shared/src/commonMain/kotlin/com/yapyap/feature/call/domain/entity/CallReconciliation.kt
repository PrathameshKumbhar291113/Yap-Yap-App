package com.yapyap.feature.call.domain.entity

enum class CallReconcileAction {
    Resume,
    Wait,
    EndLocal,
    ;

    companion object {
        fun fromRaw(raw: String?): CallReconcileAction = when (raw?.lowercase()) {
            "resume" -> Resume
            "wait" -> Wait
            "end_local" -> EndLocal
            else -> Wait
        }
    }
}

data class CallReconciliation(
    val action: CallReconcileAction,
    val forceEndLocal: Boolean = false,
    val canJoinMedia: Boolean = false,
    val reason: String? = null,
)
