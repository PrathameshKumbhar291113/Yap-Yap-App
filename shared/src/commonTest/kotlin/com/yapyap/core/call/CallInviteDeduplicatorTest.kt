package com.yapyap.core.call

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallInviteDeduplicatorTest {

    @Test
    fun tryClaimReturnsTrueOnlyOnceForSameSession() = runTest {
        val deduplicator = CallInviteDeduplicator()

        assertTrue(deduplicator.tryClaim("call-1"))
        assertFalse(deduplicator.tryClaim("call-1"))
    }

    @Test
    fun tryClaimAllowsDistinctSessions() = runTest {
        val deduplicator = CallInviteDeduplicator()

        assertTrue(deduplicator.tryClaim("call-1"))
        assertTrue(deduplicator.tryClaim("call-2"))
    }

    @Test
    fun tryClaimRejectsBlankSessionId() = runTest {
        val deduplicator = CallInviteDeduplicator()

        assertFalse(deduplicator.tryClaim(""))
        assertFalse(deduplicator.tryClaim("   "))
    }

    @Test
    fun concurrentTryClaimAllowsOnlyOneWinner() = runTest {
        val deduplicator = CallInviteDeduplicator()

        val results = (1..20).map {
            async { deduplicator.tryClaim("call-race") }
        }.awaitAll()

        assertEquals(1, results.count { it })
        assertEquals(19, results.count { !it })
    }
}
