package com.automattic.simplenote.integration

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.automattic.simplenote.BaseUITest
import com.automattic.simplenote.CollaboratorsActivity
import com.automattic.simplenote.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-ui tests for the converted Collaborators screen, exercising the frozen
 * CollaboratorsViewModel/AddCollaboratorViewModel over the Simperium repositories backed by
 * [com.automattic.simplenote.utils.TestBucket] — the same seam the Espresso integration tests use.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4ClassRunner::class)
@MediumTest
class CollaboratorsActivityComposeTest : BaseUITest() {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun listCollaboratorsShouldShowHeaderAndRows() {
        val note = createNote("Lorem ipsum", listOf("tag1", "name@example.co.jp", "name@test.com"))

        launchCollaborators(note.simperiumKey).use {
            composeRule.waitUntilExactlyOneExists(
                hasText(getResourceString(R.string.shared_with)), TIMEOUT
            )
            composeRule.onNodeWithText("name@example.co.jp").assertIsDisplayed()
            composeRule.onNodeWithText("name@test.com").assertIsDisplayed()
            // Non-email tags are not collaborators.
            composeRule.onNodeWithText("tag1").assertDoesNotExist()
            composeRule
                .onAllNodesWithContentDescription(getResourceString(R.string.remove_collaborator))
                .assertCountEquals(2)
        }
    }

    @Test
    fun noteWithoutCollaboratorsShouldShowEmptyState() {
        val note = createNote("Lorem ipsum", listOf("tag1"))

        launchCollaborators(note.simperiumKey).use {
            composeRule.waitUntilExactlyOneExists(
                hasText(getResourceString(R.string.no_collaborators)), TIMEOUT
            )
            composeRule
                .onNodeWithText(getResourceString(R.string.add_email_collaborator_message))
                .assertIsDisplayed()
            composeRule
                .onNodeWithText(getResourceString(R.string.shared_with))
                .assertDoesNotExist()
        }
    }

    @Test
    fun addCollaboratorWithInvalidEmailShouldShowInlineError() {
        val note = createNote("Lorem ipsum", listOf("name@test.com"))

        launchCollaborators(note.simperiumKey).use {
            composeRule.waitUntilExactlyOneExists(hasText("name@test.com"), TIMEOUT)

            // The FAB kept the View layout's add_tag contentDescription.
            composeRule.onNodeWithContentDescription(getResourceString(R.string.add_tag)).performClick()
            composeRule.waitUntilExactlyOneExists(
                hasText(getResourceString(R.string.add_collaborator)), TIMEOUT
            )

            composeRule.onNode(hasSetTextAction()).performTextInput("not-an-email")
            composeRule.onNodeWithText(getResourceString(R.string.accept)).performClick()

            composeRule.waitUntilExactlyOneExists(
                hasText(getResourceString(R.string.invalid_collaborator)), TIMEOUT
            )
            // The dialog stays open on validation failure.
            composeRule
                .onNodeWithText(getResourceString(R.string.add_collaborator))
                .assertIsDisplayed()
        }
    }

    @Test
    fun removeCollaboratorAfterConfirmationShouldRemoveRow() {
        val note = createNote("Lorem ipsum", listOf("name@test.com"))

        launchCollaborators(note.simperiumKey).use {
            composeRule.waitUntilExactlyOneExists(hasText("name@test.com"), TIMEOUT)

            composeRule
                .onNodeWithContentDescription(getResourceString(R.string.remove_collaborator))
                .performClick()

            composeRule.waitUntilExactlyOneExists(
                hasText(getResourceString(R.string.remove_collaborator_message)), TIMEOUT
            )
            composeRule.onNodeWithText(getResourceString(R.string.remove)).performClick()

            // Last collaborator removed: the list gives way to the default empty state.
            composeRule.waitUntilExactlyOneExists(
                hasText(getResourceString(R.string.no_collaborators)), TIMEOUT
            )
            composeRule.onNodeWithText("name@test.com").assertDoesNotExist()
        }
    }

    private fun launchCollaborators(noteId: String): ActivityScenario<CollaboratorsActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CollaboratorsActivity::class.java)
            .putExtra(CollaboratorsActivity.NOTE_ID_ARG, noteId)
        return ActivityScenario.launch(intent)
    }

    companion object {
        private const val TIMEOUT = 10_000L
    }
}
