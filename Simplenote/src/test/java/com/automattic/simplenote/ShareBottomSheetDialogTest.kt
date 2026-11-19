package com.automattic.simplenote

import android.content.Context
import android.net.ConnectivityManager
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.screenshottest.AppStyle
import com.automattic.simplenote.screenshottest.ScreenshotHarness
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Pins the share sheet: it only shows while its host fragment is added, under [ShareBottomSheetDialog.TAG],
 * fully expanded with collapse skipped, with Publish or Unpublish offered by the note's published state; each
 * action reaches its [ShareBottomSheetDialog.ShareSheetListener] callback, Publish and Unpublish only when a
 * network is available (a toast otherwise), and "Other" dismisses the sheet after its callback. Only a dismissal of
 * the dialog itself (swipe, back, outside tap) reaches onShareDismissed; DialogFragment.dismiss() does not.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class ShareBottomSheetDialogTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<TagsActivity>
    private lateinit var activity: FragmentActivity
    private lateinit var host: Fragment
    private val listener = RecordingListener()

    @Before
    fun setUp() {
        scenario = ScreenshotHarness.launchSettled(TagsActivity::class.java, AppStyle.DEFAULT, darkMode = false)
        scenario.onActivity { activity = it }
        host = Fragment()
        activity.supportFragmentManager.beginTransaction().add(host, "share-host").commitNow()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun tagIsTheSimpleClassName() {
        assertEquals("ShareBottomSheetDialog", ShareBottomSheetDialog.TAG)
    }

    @Test
    fun showsNothingWhileTheHostFragmentIsNotAdded() {
        val sheet = ShareBottomSheetDialog(Fragment(), listener)

        sheet.show(activity.supportFragmentManager, note(published = false))
        ScreenshotHarness.settle()

        assertNull(activity.supportFragmentManager.findFragmentByTag(ShareBottomSheetDialog.TAG))
        assertFalse(sheet.isAdded)
    }

    @Test
    fun showsUnderItsTagFullyExpandedWithCollapseSkipped() {
        val sheet = show(note(published = false))

        assertSame(sheet, activity.supportFragmentManager.findFragmentByTag(ShareBottomSheetDialog.TAG))
        val dialog = sheet.dialog as BottomSheetDialog
        assertTrue(dialog.isShowing)
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, dialog.behavior.state)
        assertTrue(dialog.behavior.skipCollapsed)
        assertTrue(sheet.dialog!!.findViewById<View>(R.id.share_custom_buttons) != null)
    }

    @Test
    fun unpublishedNoteOffersPublish() {
        val sheet = show(note(published = false))

        assertEquals(View.VISIBLE, sheet.button(R.id.share_publish_button).visibility)
        assertEquals(View.GONE, sheet.button(R.id.share_unpublish_button).visibility)
    }

    @Test
    fun publishedNoteOffersUnpublish() {
        val sheet = show(note(published = true))

        assertEquals(View.GONE, sheet.button(R.id.share_publish_button).visibility)
        assertEquals(View.VISIBLE, sheet.button(R.id.share_unpublish_button).visibility)
    }

    @Test
    fun collaborateAndWordPressReachTheirCallbacksAndKeepTheSheet() {
        val sheet = show(note(published = false))

        sheet.button(R.id.share_collaborate_button).performClick()
        sheet.button(R.id.share_wp_post).performClick()
        ScreenshotHarness.settle()

        assertEquals(listOf("collaborate", "wordpress"), listener.events)
        assertTrue(sheet.dialog!!.isShowing)
    }

    @Test
    fun publishAndUnpublishReachTheirCallbacksWhenOnline() {
        val sheet = show(note(published = false))

        sheet.button(R.id.share_publish_button).performClick()
        sheet.button(R.id.share_unpublish_button).performClick()
        ScreenshotHarness.settle()

        assertEquals(listOf("publish", "unpublish"), listener.events)
        assertNull(ShadowToast.getLatestToast())
        assertTrue(sheet.dialog!!.isShowing)
    }

    @Test
    fun publishAndUnpublishToastInsteadWhenOffline() {
        val sheet = show(note(published = false))
        val connectivity = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(connectivity).setActiveNetworkInfo(null)

        sheet.button(R.id.share_publish_button).performClick()
        assertEquals(activity.getString(R.string.error_network_required), ShadowToast.getTextOfLatestToast())
        ShadowToast.reset()
        sheet.button(R.id.share_unpublish_button).performClick()
        ScreenshotHarness.settle()

        assertEquals(activity.getString(R.string.error_network_required), ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<String>(), listener.events)
        assertTrue(sheet.dialog!!.isShowing)
    }

    @Test
    fun otherReachesItsCallbackThenDismissesTheSheetWithoutTheDismissCallback() {
        val sheet = show(note(published = false))

        sheet.button(R.id.share_other_button).performClick()
        ScreenshotHarness.settle()

        assertEquals(listOf("other"), listener.events)
        assertFalse(sheet.isAdded)
        assertNull(activity.supportFragmentManager.findFragmentByTag(ShareBottomSheetDialog.TAG))
    }

    @Test
    fun dismissingTheDialogItselfReachesTheDismissCallback() {
        val sheet = show(note(published = false))

        sheet.dialog!!.dismiss()
        ScreenshotHarness.settle()

        assertEquals(listOf("dismissed"), listener.events)
    }

    // DialogFragment.dismiss() clears the dialog's dismiss listener before dismissing it.
    @Test
    fun dismissingTheFragmentSkipsTheDismissCallback() {
        val sheet = show(note(published = false))

        sheet.dismiss()
        ScreenshotHarness.settle()

        assertEquals(emptyList<String>(), listener.events)
        assertFalse(sheet.isAdded)
    }

    @Test
    fun theSheetIsReusableAcrossShows() {
        val sheet = show(note(published = false))
        sheet.dismiss()
        ScreenshotHarness.settle()

        sheet.show(activity.supportFragmentManager, note(published = true))
        ScreenshotHarness.settle()

        assertTrue(sheet.dialog!!.isShowing)
        assertEquals(View.GONE, sheet.button(R.id.share_publish_button).visibility)
        assertEquals(View.VISIBLE, sheet.button(R.id.share_unpublish_button).visibility)
    }

    private fun show(note: Note): ShareBottomSheetDialog {
        val sheet = ShareBottomSheetDialog(host, listener)
        sheet.show(activity.supportFragmentManager, note)
        ScreenshotHarness.settle()
        return sheet
    }

    private fun ShareBottomSheetDialog.button(id: Int): TextView = dialog!!.findViewById(id)

    private fun note(published: Boolean): Note = Note("share-note").apply {
        content = "Shared note"
        if (published) {
            setProperty(Note.PUBLISH_URL_PROPERTY, "abc123")
            setPublished(true)
        }
    }

    private class RecordingListener : ShareBottomSheetDialog.ShareSheetListener {
        val events = mutableListOf<String>()

        override fun onShareCollaborateClicked() {
            events += "collaborate"
        }

        override fun onShareDismissed() {
            events += "dismissed"
        }

        override fun onShareOtherClicked() {
            events += "other"
        }

        override fun onSharePublishClicked() {
            events += "publish"
        }

        override fun onShareUnpublishClicked() {
            events += "unpublish"
        }

        override fun onWordPressPostClicked() {
            events += "wordpress"
        }
    }
}
