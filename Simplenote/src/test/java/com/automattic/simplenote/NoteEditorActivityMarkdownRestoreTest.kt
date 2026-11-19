package com.automattic.simplenote

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.widgets.NoteEditorViewPager
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Toggling Markdown from the editor menu must reach the saved state, or a recreated editor brings
 * back the tabs and paging of the note's old setting. Only create() runs, so the pager never lays
 * out its fragments (NoteEditorFragment needs the production Simplenote application).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class NoteEditorActivityMarkdownRestoreTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun markdownTurnedOffStaysOffAfterRestore() {
        val restored = restoreAfterToggle(launchedWithMarkdown = true, toggledTo = false)

        assertEquals(View.GONE, restored.findViewById<TabLayout>(R.id.tabs).visibility)
        assertFalse(restored.findViewById<NoteEditorViewPager>(R.id.pager).isPagingEnabled)
    }

    @Test
    fun markdownTurnedOnStaysOnAfterRestore() {
        val restored = restoreAfterToggle(launchedWithMarkdown = false, toggledTo = true)

        assertEquals(View.VISIBLE, restored.findViewById<TabLayout>(R.id.tabs).visibility)
        assertTrue(restored.findViewById<NoteEditorViewPager>(R.id.pager).isPagingEnabled)
    }

    private fun restoreAfterToggle(launchedWithMarkdown: Boolean, toggledTo: Boolean): NoteEditorActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), NoteEditorActivity::class.java)
            .putExtra(NoteEditorFragment.ARG_MARKDOWN_ENABLED, launchedWithMarkdown)
        val savedState = Bundle()
        Robolectric.buildActivity(NoteEditorActivity::class.java, intent).create().apply {
            get().setMarkdownEnabled(toggledTo)
            saveInstanceState(savedState)
        }

        return Robolectric.buildActivity(NoteEditorActivity::class.java, intent).create(savedState).get()
    }
}
