package com.automattic.simplenote

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = LoggedInTestApplication::class, sdk = [35])
class DeepLinkActivityTest {
    @Test
    fun signedInMagicLinkForwardsAlreadyLoggedInState() {
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://app.simplenote.com/login?email=dGVzdEBleGFtcGxlLmNvbQ==&auth_code=code")
        )
        val activity = Robolectric.buildActivity(DeepLinkActivity::class.java, deepLinkIntent)
            .create()
            .get()

        val startedIntent = shadowOf(activity).nextStartedActivity

        assertTrue(startedIntent.getBooleanExtra(NotesActivity.KEY_ALREADY_LOGGED_IN, false))
    }
}

class LoggedInTestApplication : Simplenote() {
    override fun onCreate() = Unit

    override fun isLoggedIn(): Boolean = true
}
