package com.automattic.simplenote

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.view.View
import androidx.annotation.IdRes
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
 * Pins the Intent each About row fires, as the behavioural baseline for the Compose rewrite.
 * Activity resolution is checked (checkActivities), so an unhandled scheme throws
 * ActivityNotFoundException as it does on a device and the fallback paths run.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class AboutFragmentTest {
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
        assertViewIntent("https://simplenote.com/blog", clickAndCaptureIntent(R.id.about_blog))
    }

    @Test
    fun helpOpensHelp() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://simplenote.com/help", clickAndCaptureIntent(R.id.about_help))
    }

    @Test
    fun twitterOpensTwitterAppWhenInstalled() {
        installHandler(BROWSER, "http", "https")
        installHandler(TWITTER, "twitter")
        assertViewIntent("twitter://user?screen_name=simplenoteapp", clickAndCaptureIntent(R.id.about_twitter))
    }

    @Test
    fun twitterFallsBackToWebProfileWithoutApp() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://twitter.com/#!/simplenoteapp", clickAndCaptureIntent(R.id.about_twitter))
    }

    @Test
    fun storeOpensPlayStoreListing() {
        installHandler(BROWSER, "http", "https")
        installHandler(STORE, "market")

        val intent = clickAndCaptureIntent(R.id.about_store)

        assertViewIntent("market://details?id=${application.packageName}", intent)
        val flags = Intent.FLAG_ACTIVITY_NO_HISTORY or
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        assertEquals(flags, intent!!.flags and flags)
    }

    // The fallback retries the same market:// URI through the browser path rather than the https
    // listing, so without a store app the second ActivityNotFoundException escapes the click.
    @Test
    fun storeWithoutPlayStoreRetriesMarketUriAndThrows() {
        installHandler(BROWSER, "http", "https")
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()

        assertThrows(ActivityNotFoundException::class.java) {
            activity.findViewById<View>(R.id.about_store).performClick()
        }
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun contributeOpensGitHub() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent(
            "https://github.com/Automattic/simplenote-android",
            clickAndCaptureIntent(R.id.about_contribute),
        )
    }

    @Test
    fun careersOpensWorkWithUs() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://automattic.com/work-with-us/", clickAndCaptureIntent(R.id.about_careers))
    }

    @Test
    fun privacyOpensPrivacyPolicy() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://automattic.com/privacy", clickAndCaptureIntent(R.id.about_privacy))
    }

    @Test
    fun termsOpensTerms() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent("https://simplenote.com/terms", clickAndCaptureIntent(R.id.about_terms))
    }

    @Test
    fun californiaOpensUsPrivacyLaws() {
        installHandler(BROWSER, "http", "https")
        assertViewIntent(
            "https://automattic.com/privacy/#us-privacy-laws",
            clickAndCaptureIntent(R.id.about_california),
        )
    }

    @Test
    fun linkRowWithoutBrowserShowsBrowserErrorDialog() {
        assertNull(clickAndCaptureIntent(R.id.about_blog))
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
    }

    @Test
    fun storeWithoutBrowserShowsBrowserErrorDialog() {
        installHandler(STORE, "market")

        assertNull(clickAndCaptureIntent(R.id.about_store))
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
    }

    // California is the one row that skips the browser check and toasts instead of the dialog.
    @Test
    fun californiaWithoutBrowserToasts() {
        assertNull(clickAndCaptureIntent(R.id.about_california))
        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(application.getString(R.string.no_browser_available), ShadowToast.getTextOfLatestToast())
    }

    private fun clickAndCaptureIntent(@IdRes rowId: Int): Intent? {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        activity.findViewById<View>(rowId).performClick()
        return shadowOf(application).nextStartedActivity
    }

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
