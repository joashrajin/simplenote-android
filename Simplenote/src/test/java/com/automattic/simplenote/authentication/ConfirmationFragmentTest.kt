package com.automattic.simplenote.authentication

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import com.automattic.simplenote.R
import com.automattic.simplenote.TagsActivity
import com.automattic.simplenote.screenshottest.AppStyle
import com.automattic.simplenote.screenshottest.ScreenshotHarness
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [ConfirmationFragment]: the argument keys newInstance writes, the bolded email in the sign-up or log-in
 * confirmation copy (sign-up when the flag is missing), and the support line with its mailto link made clickable.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class ConfirmationFragmentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun newInstanceWritesTheEmailAndSignUpFlagUnderFixedKeys() {
        val arguments = ConfirmationFragment.newInstance(EMAIL, false).requireArguments()

        assertEquals(setOf("CONFIRMATION_EMAIL_KEY", "IS_SIGNUP_KEY"), arguments.keySet())
        assertEquals(EMAIL, arguments.getString("CONFIRMATION_EMAIL_KEY"))
        assertEquals(false, arguments.getBoolean("IS_SIGNUP_KEY", true))
    }

    @Test
    fun signUpShowsTheEmailConfirmationCopyWithTheEmailInBold() {
        withShown(ConfirmationFragment.newInstance(EMAIL, true)) { view ->
            val text = view.emailConfirmation().text as Spanned

            assertEquals(
                "We've sent an email to $EMAIL. Please check your inbox and follow the instructions.",
                text.toString()
            )
            assertBold(text, EMAIL)
        }
    }

    @Test
    fun logInShowsTheMagicLinkCopyWithTheEmailInBold() {
        withShown(ConfirmationFragment.newInstance(EMAIL, false)) { view ->
            val text = view.emailConfirmation().text as Spanned

            assertEquals(
                "If an account exists, we've sent an email to $EMAIL containing a link that'll log you in.",
                text.toString()
            )
            assertBold(text, EMAIL)
        }
    }

    @Test
    fun missingSignUpFlagFallsBackToTheSignUpCopy() {
        val fragment = ConfirmationFragment()
        fragment.arguments = Bundle().apply { putString("CONFIRMATION_EMAIL_KEY", EMAIL) }

        withShown(fragment) { view ->
            assertTrue(view.emailConfirmation().text.startsWith("We've sent an email to $EMAIL."))
        }
    }

    @Test
    fun nullEmailIsRenderedAsTheTextNull() {
        withShown(ConfirmationFragment.newInstance(null, true)) { view ->
            val text = view.emailConfirmation().text as Spanned

            assertTrue(text.startsWith("We've sent an email to null."))
            assertBold(text, "null")
        }
    }

    @Test
    fun supportLineLinksTheSupportEmailAndIsClickable() {
        withShown(ConfirmationFragment.newInstance(EMAIL, true)) { view ->
            val support = view.findViewById<TextView>(R.id.support_text)
            val text = support.text as Spanned
            val links = text.getSpans(0, text.length, URLSpan::class.java)

            assertEquals(
                "Didn't get an email? You may already have an account. Contact support@simplenote.com for help.",
                text.toString()
            )
            assertEquals(listOf("mailto:support@simplenote.com"), links.map { it.url })
            assertEquals(
                "support@simplenote.com",
                text.subSequence(text.getSpanStart(links[0]), text.getSpanEnd(links[0])).toString()
            )
            assertTrue(support.movementMethod is LinkMovementMethod)
        }
    }

    private fun withShown(fragment: ConfirmationFragment, assertions: (android.view.View) -> Unit) {
        val scenario: ActivityScenario<TagsActivity> =
            ScreenshotHarness.launchSettled(TagsActivity::class.java, AppStyle.DEFAULT, darkMode = false)
        scenario.use {
            it.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commitNow()
                assertions(fragment.requireView())
            }
        }
    }

    private fun android.view.View.emailConfirmation(): TextView = findViewById(R.id.email_confirmation_text)

    private fun assertBold(text: Spanned, expected: String) {
        val bold = text.getSpans(0, text.length, StyleSpan::class.java).filter { it.style == Typeface.BOLD }
        assertEquals(1, bold.size)
        assertEquals(expected, text.subSequence(text.getSpanStart(bold[0]), text.getSpanEnd(bold[0])).toString())
    }

    private companion object {
        const val EMAIL = "ada@example.com"
    }
}
