package com.automattic.simplenote.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.repositories.MagicLinkRepository
import com.automattic.simplenote.repositories.MagicLinkResponseResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule

const val AUTH_KEY_TEST = "auth_key_test"
const val AUTH_CODE_TEST = "auth_code_test"

@ExperimentalCoroutinesApi
class CompleteMagicLinkViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private val repository: MagicLinkRepository = Mockito.mock(MagicLinkRepository::class.java)
    private val viewModel = CompleteMagicLinkViewModel(
        repository,
        coroutinesTestRule.testDispatcher
    )

    @Test
    fun instantiateViewModel() = runTest {
        assertEquals(MagicLinkUiState.Waiting, viewModel.magicLinkUiState.value)
    }

    @Test
    fun firingPostRequestShouldLeadToSuccess() = runTest {
        Mockito.`when`(
            repository.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST)
        ).thenReturn(MagicLinkResponseResult.MagicLinkCompleteSuccess(AUTH_KEY_TEST, AUTH_CODE_TEST))

        val states = mutableListOf<MagicLinkUiState>()
        viewModel.magicLinkUiState.observeForever {
            states.add(it)
        }
        viewModel.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST)

        assertEquals(MagicLinkUiState.Waiting::class.java, states[0]::class.java)
        assertEquals(MagicLinkUiState.Loading::class.java, states[1]::class.java)
        assertEquals(MagicLinkUiState.Success::class.java, states[2]::class.java)
    }

    @Test
    fun firingPostRequestShouldLeadToError() = runTest {
        Mockito.`when`(
            repository.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST)
        ).thenReturn(MagicLinkResponseResult.MagicLinkError(code = 400))

        val states = mutableListOf<MagicLinkUiState>()
        viewModel.magicLinkUiState.observeForever {
            states.add(it)
        }
        viewModel.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST)
        assertEquals(MagicLinkUiState.Waiting::class.java, states[0]::class.java)
        assertEquals(MagicLinkUiState.Loading::class.java, states[1]::class.java)
        assertEquals(MagicLinkUiState.Error::class.java, states[2]::class.java)
    }

    @Test
    fun userInitiatedRequestWhileInFlightReusesCurrentJob() = runTest {
        val repository = GatedMagicLinkRepository()
        val viewModel = CompleteMagicLinkViewModel(repository, coroutinesTestRule.testDispatcher)

        val firstRequest = viewModel.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST, userInitiated = true)
        val secondRequest = viewModel.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST, userInitiated = true)
        val changedRequest = viewModel.completeLogin(AUTH_KEY_TEST, "$AUTH_CODE_TEST-new", userInitiated = true)

        try {
            assertEquals(listOf(AUTH_KEY_TEST to AUTH_CODE_TEST), repository.requests)
            assertSame(firstRequest, secondRequest)
            assertSame(firstRequest, changedRequest)
        } finally {
            repository.response.complete(
                MagicLinkResponseResult.MagicLinkCompleteSuccess(AUTH_KEY_TEST, AUTH_CODE_TEST)
            )
            firstRequest.join()
            secondRequest.join()
            changedRequest.join()
        }
    }

    @Test
    fun completedRequestPreservesAutomaticDedupeAndManualRetry() = runTest {
        val repository = GatedMagicLinkRepository().apply {
            response.complete(MagicLinkResponseResult.MagicLinkCompleteSuccess(AUTH_KEY_TEST, AUTH_CODE_TEST))
        }
        val viewModel = CompleteMagicLinkViewModel(repository, coroutinesTestRule.testDispatcher)

        val firstRequest = viewModel.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST)
        firstRequest.join()
        val automaticRetry = viewModel.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST)
        automaticRetry.join()
        val manualRetry = viewModel.completeLogin(AUTH_KEY_TEST, AUTH_CODE_TEST, userInitiated = true)
        manualRetry.join()

        assertNotSame(firstRequest, automaticRetry)
        assertNotSame(automaticRetry, manualRetry)
        assertEquals(
            listOf(AUTH_KEY_TEST to AUTH_CODE_TEST, AUTH_KEY_TEST to AUTH_CODE_TEST),
            repository.requests
        )
    }

    private class GatedMagicLinkRepository : MagicLinkRepository {
        val requests = mutableListOf<Pair<String, String>>()
        val response = CompletableDeferred<MagicLinkResponseResult>()

        override suspend fun requestLogin(username: String): MagicLinkResponseResult {
            error("Not used")
        }

        override suspend fun completeLogin(username: String, authCode: String): MagicLinkResponseResult {
            requests.add(username to authCode)
            return response.await()
        }
    }
}
