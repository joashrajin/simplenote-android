package com.automattic.simplenote.integration

import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.MediumTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.automattic.simplenote.BaseUITest
import com.automattic.simplenote.R
import com.automattic.simplenote.TagsActivity
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.utils.assertToastShown
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for the converted Compose Tags screen, exercising the TagsViewModel over the
 * Simperium repositories backed by [com.automattic.simplenote.utils.TestBucket]. The Compose parts
 * (list, search, FAB, delete-confirm dialog) drive compose-test APIs; the surfaces that stayed in
 * the View world (AddTagActivity, the rename TagDialogFragment) keep their Espresso assertions.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4ClassRunner::class)
@MediumTest
class TagsActivityTest : BaseUITest() {
    private lateinit var activityScenario: ActivityScenario<TagsActivity>

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun listTagsWithEmptyTagsShouldShowEmptyView() {
        launchTagsActivityEmptyTags()

        composeRule.waitUntilExactlyOneExists(hasText(getResourceString(R.string.empty_tags)), TIMEOUT)
        composeRule.onNodeWithText(getResourceString(R.string.empty_tags)).assertIsDisplayed()
    }

    @Test
    fun listTagsShouldShowCompleteListTags() {
        val testData = launchTagsActivityWithTestData()

        waitForTagRows(testData.tags)

        composeRule.onNodeWithText(getResourceString(R.string.empty_tags)).assertDoesNotExist()
    }

    @Test
    fun searchTagsShouldReturnMatchedTags() {
        val testData = launchTagsActivityWithTestData()
        waitForTagRows(testData.tags)

        val firstSearchPhrase = "tag"
        val secondSearchPhrase = "ot"

        // Activate search bar
        composeRule.onNodeWithContentDescription(getResourceString(R.string.search_tags)).performClick()

        // Type in the first search phrase
        composeRule.onNode(hasSetTextAction()).performTextInput(firstSearchPhrase)

        // All tags starting with tag should be shown
        val filteredTagsFirstPhrase = testData.tags.filter { it.tag.name.startsWith(firstSearchPhrase) }
        composeRule.waitUntilDoesNotExist(hasText("other"), TIMEOUT)
        waitForTagRows(filteredTagsFirstPhrase)

        // Type in the second search phrase
        composeRule.onNode(hasSetTextAction()).performTextReplacement(secondSearchPhrase)

        // Just the tag other should be shown
        val filteredTagsSecondPhrase = testData.tags.filter { it.tag.name.startsWith(secondSearchPhrase) }
        composeRule.waitUntilDoesNotExist(hasText("tag1"), TIMEOUT)
        waitForTagRows(filteredTagsSecondPhrase)

        composeRule.onNodeWithText(getResourceString(R.string.empty_tags_search)).assertDoesNotExist()
    }

    @Test
    fun searchTagsShouldKeepFilteredListAtReenter() {
        val testData = launchTagsActivityWithTestData()
        waitForTagRows(testData.tags)

        val firstSearchPhrase = "tag"

        // Activate search bar
        composeRule.onNodeWithContentDescription(getResourceString(R.string.search_tags)).performClick()

        // Type in the first search phrase
        composeRule.onNode(hasSetTextAction()).performTextInput(firstSearchPhrase)

        // All tags starting with tag should be shown
        val filteredTags = testData.tags.filter { it.tag.name.startsWith(firstSearchPhrase) }
        composeRule.waitUntilDoesNotExist(hasText("other"), TIMEOUT)
        waitForTagRows(filteredTags)

        // Change the state of the activity to CREATED which calls onPause and onStop
        activityScenario.moveToState(Lifecycle.State.CREATED)

        // Change the state of the activity to RESUMED
        activityScenario.moveToState(Lifecycle.State.RESUMED)

        // All tags starting with tag should be shown when activity becomes RESUMED again
        waitForTagRows(filteredTags)
        composeRule.onNodeWithText("other").assertDoesNotExist()
        composeRule
            .onAllNodesWithContentDescription(getResourceString(R.string.delete_tag))
            .assertCountEquals(filteredTags.count())
    }

    @Test
    fun searchTagsWithNoResultsShouldShowEmptyView() {
        val testData = launchTagsActivityWithTestData()
        waitForTagRows(testData.tags)

        composeRule.onNodeWithText(getResourceString(R.string.empty_tags_search)).assertDoesNotExist()

        val notMatchesPhrase = "nomatches"
        // Activate search bar
        composeRule.onNodeWithContentDescription(getResourceString(R.string.search_tags)).performClick()

        // Type in the search phrase
        composeRule.onNode(hasSetTextAction()).performTextInput(notMatchesPhrase)

        composeRule.waitUntilExactlyOneExists(hasText(getResourceString(R.string.empty_tags_search)), TIMEOUT)
        composeRule.onNodeWithText(getResourceString(R.string.empty_tags_search)).assertIsDisplayed()
    }

    @Test
    fun clickOnAddTagShouldShowDialog() {
        launchTagsActivityEmptyTags()

        val addTagTitle = getResourceString(R.string.add_tag)
        // The FAB kept the View layout's add_tag contentDescription.
        composeRule.waitUntilExactlyOneExists(hasContentDescription(addTagTitle), TIMEOUT)
        composeRule.onNodeWithContentDescription(addTagTitle).performClick()

        // AddTagActivity stays in the View world; its faux-dialog title is asserted with Espresso.
        waitForView { onView(withText(addTagTitle)).check(matches(isDisplayed())) }
    }

    @Test
    fun longTabOnAddTagShouldShowToast() {
        // Known-flaky on API 30+: text toasts render outside the app window, so this relies on the
        // UiAutomation accessibility-event path (see assertToastShown), which wobbles on emulators.
        // The flake predates the Compose conversion and stays a documented baseline item.
        launchTagsActivityEmptyTags()

        val addTagTitle = getResourceString(R.string.add_tag)
        composeRule.waitUntilExactlyOneExists(hasContentDescription(addTagTitle), TIMEOUT)
        assertToastShown(addTagTitle) {
            composeRule.onNodeWithContentDescription(addTagTitle).performTouchInput { longClick() }
        }
    }

    @Test
    fun clickOnEditTagShouldShowDialog() {
        val testData = launchTagsActivityWithTestData()
        waitForTagRows(testData.tags)

        composeRule.onNodeWithText("tag2").performClick()

        // The rename dialog stays a View DialogFragment; assert through Espresso.
        val renameTagTitle = getResourceString(R.string.rename_tag)
        waitForView { onView(withText(renameTagTitle)).check(matches(isDisplayed())) }
    }

    @Test
    fun deleteTagsShowRemoveTagsFromNotes() {
        val testData = launchTagsActivityWithTestData()

        // Check data is displayed
        waitForTagRows(testData.tags)

        // Delete tag other: it tags no notes, so the delete runs without a confirmation dialog
        composeRule.onNode(trashButtonForTag("other")).performClick()
        composeRule.waitUntil(TIMEOUT) { tagsBucket.count() == 3 }

        assertEquals(tagsBucket.count(), 3)
        assertEquals(notesBucket.count(), 3)
        assertEquals(testData.notes[0].tags, listOf("tag1", "tag2"))
        assertEquals(testData.notes[1].tags, listOf("tag2"))
        assertEquals(testData.notes[2].tags, listOf("tag1", "tag2", "tag3"))

        // Try Delete tag tag2, dismissing the Compose confirmation dialog with No
        val deleteTagTitle = getResourceString(R.string.delete_tag)
        val confirmDeleteTagMessage = getResourceString(R.string.confirm_delete_tag)

        composeRule.onNode(trashButtonForTag("tag2")).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(deleteTagTitle), TIMEOUT)
        composeRule.onNodeWithText(confirmDeleteTagMessage).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.no)).performClick()
        composeRule.waitUntilDoesNotExist(hasText(deleteTagTitle), TIMEOUT)

        assertEquals(tagsBucket.count(), 3)
        assertEquals(notesBucket.count(), 3)
        assertEquals(testData.notes[0].tags, listOf("tag1", "tag2"))
        assertEquals(testData.notes[1].tags, listOf("tag2"))
        assertEquals(testData.notes[2].tags, listOf("tag1", "tag2", "tag3"))

        // Delete tag tag2, confirming with Yes
        composeRule.onNode(trashButtonForTag("tag2")).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(deleteTagTitle), TIMEOUT)
        composeRule.onNodeWithText(confirmDeleteTagMessage).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.yes)).performClick()

        composeRule.waitUntil(TIMEOUT) { tagsBucket.count() == 2 }
        assertEquals(tagsBucket.count(), 2)
        assertEquals(notesBucket.count(), 3)
        assertEquals(testData.notes[0].tags, listOf("tag1"))
        assertEquals(testData.notes[1].tags, listOf<String>())
        assertEquals(testData.notes[2].tags, listOf("tag1", "tag3"))
    }

    @Test
    fun scrollTags() {
        for (i in 1 until 51) {
            val tagName = "tag${i}"
            createTag(tagName)
        }

        assertEquals(tagsBucket.count(), 50)

        activityScenario = ActivityScenario.launch(TagsActivity::class.java)

        composeRule.waitUntilExactlyOneExists(hasText("tag1"), TIMEOUT)
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("tag50"))
        composeRule.onNodeWithText("tag50").performClick()

        val renameTagTitle = getResourceString(R.string.rename_tag)
        waitForView { onView(withText(renameTagTitle)).check(matches(isDisplayed())) }
        onView(allOf(withText("tag50"), isDescendantOfA(withId(R.id.input_tag_name))))
            .check(matches(isDisplayed()))

        assertEquals(tagsBucket.count(), 50)
    }

    /**
     * The trash button of the row for [tagName]. The clickable row is a merging semantics
     * boundary, so its name/count texts merge into the row node and the trash button (its own
     * boundary, Role.Button) hangs under it as a child — hence ancestor, not sibling, matching.
     */
    private fun trashButtonForTag(tagName: String) =
        hasContentDescription(getResourceString(R.string.delete_tag)) and hasAnyAncestor(hasText(tagName))

    /** Waits until each tag row renders, then asserts its name (and count, when tagged) shows. */
    private fun waitForTagRows(tags: List<TagAndCounter>) {
        tags.forEach { tagAndCounter ->
            composeRule.waitUntilExactlyOneExists(hasText(tagAndCounter.tag.name), TIMEOUT)
            if (tagAndCounter.counter.isNotEmpty()) {
                // Both texts merge into the same row node.
                composeRule
                    .onNode(hasText(tagAndCounter.tag.name) and hasText(tagAndCounter.counter))
                    .assertIsDisplayed()
            }
        }
    }

    /** Polls an Espresso assertion: window transitions out of the Compose world settle async. */
    private fun waitForView(assertion: () -> Unit) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT
        while (true) {
            try {
                assertion()
                return
            } catch (error: Throwable) {
                if (SystemClock.uptimeMillis() >= deadline) throw error
                SystemClock.sleep(POLL_INTERVAL_MS)
            }
        }
    }

    private fun launchTagsActivityWithTestData(): TestData {
        val tags = mutableListOf<TagAndCounter>()
        val notes = mutableListOf<Note>()

        tags.add(TagAndCounter(createTag("tag1"), "2"))
        tags.add(TagAndCounter(createTag("tag2"), "3"))
        tags.add(TagAndCounter(createTag("tag3"), "1"))
        tags.add(TagAndCounter(createTag("other"), ""))
        // To edit tags, tags should belong a note
        notes.add(createNote("Hello1", listOf("tag1", "tag2")))
        notes.add(createNote("Hello2", listOf("tag2")))
        notes.add(createNote("Hello3", listOf("tag1", "tag2", "tag3")))

        assertEquals(tagsBucket.count(), 4)
        assertEquals(notesBucket.count(), 3)

        activityScenario = ActivityScenario.launch(TagsActivity::class.java)

        return TestData(tags, notes)
    }

    private fun launchTagsActivityEmptyTags() {
        activityScenario = ActivityScenario.launch(TagsActivity::class.java)
    }

    inner class TagAndCounter(val tag: Tag, val counter: String)
    inner class TestData(val tags: List<TagAndCounter>, val notes: List<Note>)

    companion object {
        private const val TIMEOUT = 10_000L
        private const val POLL_INTERVAL_MS = 50L
    }
}
