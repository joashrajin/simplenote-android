package com.automattic.simplenote.integration

import android.content.Context
import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.BaseUITest
import com.automattic.simplenote.NoteEditorActivity
import com.automattic.simplenote.NoteEditorFragment
import com.automattic.simplenote.utils.IntentUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class NoteEditorActivityBackPressTest : BaseUITest() {
    @Test
    fun legacyBackEntryInvokesTheDownstreamCallbackOnce() {
        val callbackCount = AtomicInteger()

        ActivityScenario.launchActivityForResult<NoteEditorActivity>(editorIntent()).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isTaskRoot)
                assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
                activity.onBackPressedDispatcher.addCallback(
                    activity,
                    object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            callbackCount.incrementAndGet()
                        }
                    },
                )

                @Suppress("DEPRECATION")
                activity.onBackPressed()
            }
        }

        assertEquals(1, callbackCount.get())
    }

    @Test
    fun toolbarBackDelegatesToTheDispatcherOnce() {
        val callbackCount = AtomicInteger()

        ActivityScenario.launchActivityForResult<NoteEditorActivity>(editorIntent()).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isTaskRoot)
                activity.onBackPressedDispatcher.addCallback(
                    activity,
                    object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            callbackCount.incrementAndGet()
                        }
                    },
                )
            }

            onView(
                withContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description),
            ).perform(click())
        }

        assertEquals(1, callbackCount.get())
    }

    @Test
    fun taskRootDispatcherRoutesToTheNotesActivityOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(
            IntentUtils.getMainActivityClassName(context),
            null,
            true,
        )

        try {
            val intent = editorIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<NoteEditorActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(activity.isTaskRoot)
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertTrue(activity.isFinishing)
                }
            }

            assertEquals(1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun editorIntent(): Intent {
        val note = createNote("Back dispatch test", emptyList())
        return Intent(
            ApplicationProvider.getApplicationContext(),
            NoteEditorActivity::class.java,
        ).putExtra(NoteEditorFragment.ARG_ITEM_ID, note.simperiumKey)
            .putExtra(NoteEditorFragment.ARG_MARKDOWN_ENABLED, false)
            .putExtra(NoteEditorFragment.ARG_PREVIEW_ENABLED, false)
    }
}
