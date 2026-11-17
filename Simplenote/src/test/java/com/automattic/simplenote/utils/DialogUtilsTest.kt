package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Pins the Material 3 error dialog every "contact support" path shows: an "Error" title, the
 * message formatted with (support link, color-span open, accent hex, tag close, span close) and
 * parsed as HTML, a single OK button, and LinkMovementMethod on android.R.id.message so the
 * mailto link is tappable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DialogUtilsTest {
    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(ThemeUtils.getStyle(app))
        activity = controller.setup().get()
    }

    @Test
    fun showsErrorTitleSingleOkButtonAndLinkMovement() {
        val dialog = show(activity.getString(R.string.rename_tag_message))

        assertTrue(dialog.isShowing)
        assertEquals(
            activity.getString(R.string.error),
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()
        )
        val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertEquals(View.VISIBLE, positive.visibility)
        assertEquals(activity.getString(android.R.string.ok), positive.text.toString())
        assertEquals(View.GONE, dialog.getButton(DialogInterface.BUTTON_NEGATIVE).visibility)
        assertEquals(View.GONE, dialog.getButton(DialogInterface.BUTTON_NEUTRAL).visibility)
        // Robolectric hands out a fresh LinkMovementMethod per getInstance() call, so pin the class.
        assertEquals(LinkMovementMethod::class.java, messageView(dialog).movementMethod.javaClass)
    }

    @Test
    fun plainMaterialDialogMessageHasNoMovementMethod() {
        MaterialAlertDialogBuilder(activity).setMessage("x").show()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(messageView(ShadowDialog.getLatestDialog() as AlertDialog).movementMethod)
    }

    @Test
    fun supportEmailIsRenderedAsMailtoLink() {
        val dialog = show(activity.getString(R.string.rename_tag_message))

        val text = messageView(dialog).text as Spanned
        assertEquals(
            "An error occurred renaming the tag. Please, try again. " +
                "If the problem continues, contact us at support@simplenote.com for help.",
            text.toString()
        )
        val links = text.getSpans(0, text.length, URLSpan::class.java)
        assertEquals(1, links.size)
        assertEquals("mailto:support@simplenote.com", links[0].url)
        val linkText = text.subSequence(text.getSpanStart(links[0]), text.getSpanEnd(links[0]))
        assertEquals("support@simplenote.com", linkText.toString())
    }

    @Test
    fun colorArgumentsWrapTextInAccentColorSpan() {
        val dialog = show("%2\$s%3\$s%4\$sAccent%5\$s plain")

        val text = messageView(dialog).text as Spanned
        assertEquals("Accent plain", text.toString())
        val colors = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
        assertEquals(1, colors.size)
        assertEquals(0, text.getSpanStart(colors[0]))
        assertEquals(6, text.getSpanEnd(colors[0]))
        val accent = ThemeUtils.getColorFromAttribute(activity, R.attr.colorAccent)
        assertEquals(accent or OPAQUE, colors[0].foregroundColor)
    }

    @Test
    fun accentHexIsRgbOnlyWithoutAlpha() {
        val dialog = show("%3\$s")

        val accent = ThemeUtils.getColorFromAttribute(activity, R.attr.colorAccent)
        assertEquals(Integer.toHexString(accent and 0xffffff), messageView(dialog).text.toString())
        assertNotEquals(Integer.toHexString(accent), messageView(dialog).text.toString())
    }

    @Test
    fun messageWithoutFormatSpecifiersIsShownAsIs() {
        val dialog = show("Something went wrong.")

        assertEquals("Something went wrong.", messageView(dialog).text.toString())
    }

    private fun show(message: String): AlertDialog {
        DialogUtils.showDialogWithEmail(activity, message)
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun messageView(dialog: AlertDialog): TextView = dialog.findViewById(android.R.id.message)!!

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
    }
}
