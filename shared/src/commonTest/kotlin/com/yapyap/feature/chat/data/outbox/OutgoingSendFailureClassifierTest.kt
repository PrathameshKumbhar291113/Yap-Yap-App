package com.yapyap.feature.chat.data.outbox

import com.yapyap.core.network.ApiException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.io.IOException

class OutgoingSendFailureClassifierTest {
    @Test
    fun networkErrorsAreRetryable() {
        assertTrue(
            OutgoingSendFailureClassifier.isRetryable(
                throwable = IOException("Failed to connect to base URL"),
                socketReady = false,
                networkOnline = false,
            ),
        )
    }

    @Test
    fun authErrorsArePermanent() {
        assertTrue(
            OutgoingSendFailureClassifier.isPermanent(
                ApiException.AuthInvalid("Unauthorized"),
            ),
        )
        assertTrue(
            OutgoingSendFailureClassifier.isPermanent(
                ApiException.ValidationError("Invalid payload"),
            ),
        )
    }

    @Test
    fun serverErrorsAreRetryableWhenOnline() {
        assertFalse(
            OutgoingSendFailureClassifier.isPermanent(
                ApiException.Unknown("Server error", httpStatus = 503),
            ),
        )
    }
}
