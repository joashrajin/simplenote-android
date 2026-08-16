package com.automattic.simplenote.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.authentication.SessionManager
import com.automattic.simplenote.repositories.CollaboratorsRepository
import com.simperium.Simperium
import com.simperium.client.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AddCollaboratorCurrentUserCaseTest {
    @get:Rule
    val rule = InstantTaskExecutorRule()

    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private val collaboratorsRepository = mock<CollaboratorsRepository>()
    private val simperium = mock<Simperium>()
    private val viewModel = AddCollaboratorViewModel(collaboratorsRepository, SessionManager(simperium))

    @Before
    fun setup() {
        val user = User().apply {
            email = "test@test.com"
            accessToken = "access-token"
        }
        whenever(simperium.user).thenReturn(user)
    }

    @Test
    fun currentUserEmailComparisonIgnoresCase() {
        viewModel.addCollaborator("note-id", "TEST@TEST.COM")

        assertSame(AddCollaboratorViewModel.Event.CollaboratorCurrentUser, viewModel.event.value)
        verifyNoInteractions(collaboratorsRepository)
    }
}
