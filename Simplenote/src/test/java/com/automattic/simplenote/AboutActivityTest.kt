package com.automattic.simplenote

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/**
 * Pins the Intent each About row fires. Rows are found by their visible text, so the same
 * assertions hold for the View and the Compose screen. Activity resolution is checked
 * (checkActivities), so an unhandled scheme throws ActivityNotFoundException as it does on a
 * device and the fallback paths run.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class AboutActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        shadowOf(application).checkActivities(true)
    }

    @Test
    fun blogOpensBlog() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://simplenote.com/blog", clickAndCaptureIntent(text(R.string.blog)))
    }

    @Test
    fun helpOpensHelp() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://simplenote.com/help", clickAndCaptureIntent(text(R.string.about_help_title)))
    }

    @Test
    fun twitterOpensTwitterAppWhenInstalled() {
        installHandler(BROWSER, "http", "https")
        installHandler(TWITTER, "twitter")
        assertViewIntent("twitter://user?screen_name=simplenoteapp", clickAndCaptureIntent(text(R.string.twitter)))
    }

    @Test
    fun twitterFallsBackToWebProfileWithoutApp() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://twitter.com/#!/simplenoteapp", clickAndCaptureIntent(text(R.string.twitter)))
    }

    @Test
    fun storeOpensPlayStoreListing() {
        installHandler(BROWSER, "http", "https")
        installHandler(STORE, "market")

        val intent = clickAndCaptureIntent(text(R.string.rate_us))

        assertViewIntent("market://details?id=${application.packageName}", intent)
        val flags = Intent.FLAG_ACTIVITY_NO_HISTORY or
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        assertEquals(flags, intent!!.flags and flags)
    }

    @Test
    fun storeWithoutPlayStoreFallsBackToTheHttpsListing() {
        installHandler(BROWSER, "http", "https")

        assertViewIntent(
            "https://play.google.com/store/apps/details?id=${application.packageName}",
            clickAndCaptureIntent(text(R.string.rate_us)),
        )
    }

    @Test
    fun contributeOpensGitHub() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent(
            "https://github.com/Automattic/simplenote-android",
            clickAndCaptureIntent(text(R.string.about_contribute_title)),
        )
    }

    @Test
    fun careersOpensWorkWithUs() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent(
            "https://automattic.com/work-with-us/",
            clickAndCaptureIntent(text(R.string.about_hiring_title)),
        )
    }

    @Test
    fun privacyOpensPrivacyPolicy() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://automattic.com/privacy", clickAndCaptureIntent(link(R.string.link_privacy)))
    }

    @Test
    fun termsOpensTerms() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://simplenote.com/terms", clickAndCaptureIntent(link(R.string.link_terms)))
    }

    @Test
    fun californiaOpensUsPrivacyLaws() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent(
            "https://automattic.com/privacy/#us-privacy-laws",
            clickAndCaptureIntent(link(R.string.link_california)),
        )
    }

    @Test
    fun linkRowWithoutBrowserShowsBrowserErrorDialog() {
        assertNull(clickAndCaptureIntent(text(R.string.blog)))
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
    }

    @Test
    fun storeWithoutBrowserStillOpensPlayStore() {
        installHandler(STORE, "market")

        assertViewIntent(
            "market://details?id=${application.packageName}",
            clickAndCaptureIntent(text(R.string.rate_us)),
        )
        assertNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun storeWithNeitherPlayStoreNorBrowserShowsBrowserErrorDialog() {
        assertNull(clickAndCaptureIntent(text(R.string.rate_us)))
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
    }

    // California is the one row that skips the browser check and toasts instead of the dialog.
    @Test
    fun californiaWithoutBrowserToasts() {
        assertNull(clickAndCaptureIntent(link(R.string.link_california)))
        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(application.getString(R.string.no_browser_available), ShadowToast.getTextOfLatestToast())
    }

    private fun clickAndCaptureIntent(label: String): Intent? {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        clickableAncestor(findText(activity.window.decorView, label)).performClick()
        return shadowOf(application).nextStartedActivity
    }

    private fun text(@StringRes label: Int): String = application.getString(label)

    // The legal link strings wrap their text in four markup placeholders.
    private fun link(@StringRes label: Int): String = application.getString(label, "", "", "", "")

    private fun findText(view: View, label: String): View {
        if (view is TextView && view.text.toString() == label) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                runCatching { return findText(view.getChildAt(i), label) }
            }
        }
        throw AssertionError("No view shows \"$label\"")
    }

    private fun clickableAncestor(view: View): View =
        if (view.isClickable) view else clickableAncestor(view.parent as View)

    private fun assertViewIntent(expectedUri: String, intent: Intent?) {
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent!!.action)
        assertEquals(Uri.parse(expectedUri), intent.data)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    private fun installHandler(component: ComponentName, vararg schemes: String) {
        val packageManager = shadowOf(application.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                schemes.forEach(::addDataScheme)
            },
        )
    }

    private companion object {
        val BROWSER = ComponentName("org.example.browser", "org.example.browser.BrowserActivity")
        val TWITTER = ComponentName("org.example.twitter", "org.example.twitter.ProfileActivity")
        val STORE = ComponentName("org.example.store", "org.example.store.ListingActivity")
    }
}
