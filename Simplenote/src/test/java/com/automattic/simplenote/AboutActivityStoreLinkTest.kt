package com.automattic.simplenote

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AboutActivityStoreLinkTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun storeLinkAttemptsMarketWithoutBrowser() {
        val activity = Robolectric.buildActivity(StoreTestActivity::class.java).setup().get()

        clickStore(activity)

        assertEquals(1, activity.startedIntents.size)
        assertEquals("market://details?id=${activity.packageName}", activity.startedIntents[0].dataString)
    }

    @Test
    fun storeLinkFallsBackToHttpsWhenMarketUnavailable() {
        val activity = Robolectric.buildActivity(StoreTestActivity::class.java).setup().get()
        registerBrowser(activity)
        activity.rejectMarket = true

        clickStore(activity)

        assertEquals(2, activity.startedIntents.size)
        assertEquals("market://details?id=${activity.packageName}", activity.startedIntents[0].dataString)
        assertEquals(
            "https://play.google.com/store/apps/details?id=${activity.packageName}",
            activity.startedIntents[1].dataString,
        )
    }

    private fun clickStore(activity: StoreTestActivity) {
        composeRule.onNodeWithText(activity.getString(R.string.rate_us)).performScrollTo().performClick()
    }

    private fun registerBrowser(activity: StoreTestActivity) {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                applicationInfo = ApplicationInfo().apply { packageName = "browser" }
                name = "BrowserActivity"
            }
        }
        shadowOf(activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.simperium_url))),
            resolveInfo,
        )
    }

    class StoreTestActivity : AboutActivity() {
        val startedIntents = mutableListOf<Intent>()
        var rejectMarket = false

        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_Simplestyle_About)
            super.onCreate(savedInstanceState)
        }

        override fun startActivity(intent: Intent) {
            recordIntent(intent)
        }

        override fun startActivity(intent: Intent, options: Bundle?) {
            recordIntent(intent)
        }

        @Deprecated("Deprecated in Java")
        override fun startActivityForResult(intent: Intent, requestCode: Int) {
            recordIntent(intent)
        }

        @Deprecated("Deprecated in Java")
        override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
            recordIntent(intent)
        }

        private fun recordIntent(intent: Intent) {
            startedIntents.add(intent)
            if (rejectMarket && intent.data?.scheme == "market") {
                throw ActivityNotFoundException()
            }
        }
    }
}
