package com.yapyap.feature.call.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.yapyap.feature.call.domain.entity.CallType

data class OngoingCallChrome(
    val showGreenAppBar: Boolean = false,
    val callType: CallType? = null,
    val onReturnToCall: () -> Unit = {},
)

val LocalOngoingCallChrome = compositionLocalOf { OngoingCallChrome() }

@Composable
fun ProvideOngoingCallChrome(
    chrome: OngoingCallChrome,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalOngoingCallChrome provides chrome) {
        content()
    }
}
