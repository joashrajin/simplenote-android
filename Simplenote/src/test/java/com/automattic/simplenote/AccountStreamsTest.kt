package com.automattic.simplenote

import com.automattic.simplenote.repositories.AccountRepository
import com.automattic.simplenote.repositories.AccountStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AccountStreamsTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    @Test
    fun watchAccountStatusSurvivesAThrowingConsumer() {
        val upstream = MutableSharedFlow<AccountStatus>(extraBufferCapacity = 8)
        val repository = mock<AccountRepository>()
        whenever(repository.accountChanges()).thenReturn(upstream)
        val delivered = mutableListOf<AccountStatus>()
        var thrown = false

        AccountStreams.watchAccountStatus(repository) { status ->
            if (!thrown) {
                thrown = true
                throw IllegalStateException("fragment transaction after onSaveInstanceState")
            }
            delivered.add(status)
        }

        assertTrue(upstream.tryEmit(AccountStatus.Unverified(hasSentEmail = false)))
        assertTrue(upstream.tryEmit(AccountStatus.Verified))

        // The first delivery threw and was contained; the collection stayed alive for the next.
        assertTrue(thrown)
        assertEquals(listOf<AccountStatus>(AccountStatus.Verified), delivered)
    }
}
