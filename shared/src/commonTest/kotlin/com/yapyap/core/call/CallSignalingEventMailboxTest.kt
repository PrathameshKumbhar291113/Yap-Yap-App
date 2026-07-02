package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallSignalingEventMailboxTest {

    @Test
    fun eventsProcessSeriallyInEnqueueOrder() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val processed = mutableListOf<String>()
        val mailbox = CallSignalingEventMailbox(
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            processor = { event ->
                processed += event.callSessionIdOrNull().orEmpty()
            },
        )
        mailbox.start(this)

        repeat(20) { index ->
            mailbox.enqueue(
                CallSignalingEvent.End(
                    callSessionId = "call-$index",
                    durationSec = index,
                ),
            )
        }
        advanceUntilIdle()

        assertEquals(
            expected = (0 until 20).map { "call-$it" },
            actual = processed,
        )
        mailbox.close()
    }

    @Test
    fun inviteAndEndDoNotOverlap() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val processing = mutableListOf<String>()
        val mailbox = CallSignalingEventMailbox(
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            processor = { event ->
                processing += "start:${event::class.simpleName}"
                when (event) {
                    is CallSignalingEvent.Invite -> {
                        // Simulate suspend work previously done only for Invite.
                        kotlinx.coroutines.delay(1)
                    }
                    else -> Unit
                }
                processing += "end:${event::class.simpleName}"
            },
        )
        mailbox.start(this)

        mailbox.enqueue(
            CallSignalingEvent.Invite(
                callSessionId = "call-1",
                conversationId = 1,
                callerId = 2,
                callerName = "Bob",
                callTypeRaw = "voice",
            ),
        )
        mailbox.enqueue(CallSignalingEvent.End(callSessionId = "call-1"))
        advanceUntilIdle()

        assertEquals(
            expected = listOf(
                "start:Invite",
                "end:Invite",
                "start:End",
                "end:End",
            ),
            actual = processing,
        )
        mailbox.close()
    }

    private fun CallSignalingEvent.callSessionIdOrNull(): String? = when (this) {
        is CallSignalingEvent.Answer -> callSessionId
        is CallSignalingEvent.End -> callSessionId
        is CallSignalingEvent.IceCandidate -> callSessionId
        is CallSignalingEvent.Invite -> callSessionId
        is CallSignalingEvent.Reject -> callSessionId
        is CallSignalingEvent.Sync -> callSessionId
    }

    private class TestDispatcherProvider(
        private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }
}
