package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.DynamicDrawableSpan
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import com.automattic.simplenote.widgets.CenteredImageSpan
import com.automattic.simplenote.widgets.CheckableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.regex.Pattern

/**
 * Pins ChecklistUtils on inputs with a single checklist marker. ART's Matcher works on a String snapshot, but the
 * JVM's reads the Editable lazily while ChecklistUtils shrinks it and keeps scanning up to the original length. So
 * multi-match conversions run through ChecklistUtilsMultiMatchTest's snapshot input or on device (ChecklistsDeviceTest,
 * SimplenoteEditTextChecklistLayoutTest), and the unanchored CHECKLIST_REGEX inputs here end at most three characters
 * after their marker.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class ChecklistUtilsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun constantsKeepTheirValues() {
        assertEquals('\u2610', ChecklistUtils.CHAR_BALLOT_BOX)
        assertEquals('\u2611', ChecklistUtils.CHAR_BALLOT_BOX_CHECK)
        assertEquals('\u2022', ChecklistUtils.CHAR_BULLET)
        assertEquals('\u00a0', ChecklistUtils.CHAR_NO_BREAK_SPACE)
        assertEquals('\u2a2f', ChecklistUtils.CHAR_VECTOR_CROSS_PRODUCT)
        assertEquals(3, ChecklistUtils.CHECKLIST_OFFSET)
        assertEquals("""(\s+)?(-[ \t]+\[[xX\s]?\])""", ChecklistUtils.CHECKLIST_REGEX)
        assertEquals("""^(\s+)?(-[ \t]+\[[xX\s]?\])""", ChecklistUtils.CHECKLIST_REGEX_LINES)
        assertEquals("""^(\s+)?(-[ \t]+\[[xX]?\])""", ChecklistUtils.CHECKLIST_REGEX_LINES_CHECKED)
        assertEquals("""^(\s+)?(-[ \t]+\[[\s]?\])""", ChecklistUtils.CHECKLIST_REGEX_LINES_UNCHECKED)
        assertEquals("- [\u2a2f]", ChecklistUtils.CHECKED_MARKDOWN_PREVIEW)
        assertEquals("- [x]", ChecklistUtils.CHECKED_MARKDOWN)
        assertEquals("- [ ]", ChecklistUtils.UNCHECKED_MARKDOWN)
    }

    @Test
    fun lineRegexesSplitCheckedAndUncheckedMarkersWithEmptyBracketsInBoth() {
        val checked = ChecklistUtils.CHECKLIST_REGEX_LINES_CHECKED
        val unchecked = ChecklistUtils.CHECKLIST_REGEX_LINES_UNCHECKED

        assertEquals(listOf("- [x]", "- [X]", "-\t[x]", "-  [X]", "- []"), MARKERS.filter { finds(checked, it) })
        assertEquals(listOf("- [ ]", "- []", "- [\t]"), MARKERS.filter { finds(unchecked, it) })
        assertEquals(
            MARKERS.filter { finds(checked, it) || finds(unchecked, it) },
            MARKERS.filter { finds(ChecklistUtils.CHECKLIST_REGEX_LINES, it) }
        )
    }

    @Test
    fun onlyTheUnanchoredRegexFindsMarkersMidLine() {
        assertTrue(finds(ChecklistUtils.CHECKLIST_REGEX, "Ship it - [x]"))
        assertFalse(finds(ChecklistUtils.CHECKLIST_REGEX_LINES, "Ship it - [x]"))
        assertTrue(finds(ChecklistUtils.CHECKLIST_REGEX_LINES, "Ship it\n  - [x]"))
    }

    @Test
    fun spansForNullEditableReturnNewEmptyBuilderWithoutTouchingContext() {
        val first = ChecklistUtils.addChecklistSpansForRegexAndColor(
            null, null, ChecklistUtils.CHECKLIST_REGEX, R.color.blue_50, true
        )
        val second = ChecklistUtils.addChecklistSpansForRegexAndColor(
            null, null, ChecklistUtils.CHECKLIST_REGEX, R.color.blue_50, true
        )

        assertTrue(first is SpannableStringBuilder)
        assertEquals(0, first.length)
        assertNotSame(first, second)
    }

    @Test
    fun spansWithoutMatchReturnSameEditableUntouchedWithoutTouchingContext() {
        val editable = SpannableStringBuilder("Plain [x] text\n-[ ] no space")

        val result = ChecklistUtils.addChecklistSpansForRegexAndColor(
            null, editable, ChecklistUtils.CHECKLIST_REGEX, R.color.blue_50, true
        )

        assertSame(editable, result)
        assertEquals("Plain [x] text\n-[ ] no space", result.toString())
        assertEquals(0, result.getSpans(0, result.length, Any::class.java).size)
    }

    @Test
    fun spansWithMatchAndNullContextThrowNullPointerException() {
        val editable = SpannableStringBuilder("- [ ] Task")

        assertThrows(NullPointerException::class.java) {
            ChecklistUtils.addChecklistSpansForRegexAndColor(
                null, editable, ChecklistUtils.CHECKLIST_REGEX, R.color.blue_50, true
            )
        }
    }

    @Test
    fun uncheckedMarkerBecomesNoBreakSpaceWithImageThenCheckableSpan() {
        val editable = SpannableStringBuilder("- [ ] Task")

        val result = addSpans(editable, ChecklistUtils.CHECKLIST_REGEX_LINES, isList = false)

        assertSame(editable, result)
        assertEquals("\u00a0 Task", result.toString())
        assertSpans(result, 0, checked = false)
    }

    @Test
    fun lowerAndUpperCaseXAreChecked() {
        assertSpans(addSpans(SpannableStringBuilder("- [x] Done"), ChecklistUtils.CHECKLIST_REGEX_LINES), 0, true)
        assertSpans(addSpans(SpannableStringBuilder("- [X] Done"), ChecklistUtils.CHECKLIST_REGEX_LINES), 0, true)
    }

    @Test
    fun emptyBracketsMatchedByTheCheckedRegexStayUnchecked() {
        val result = addSpans(SpannableStringBuilder("- [] Todo"), ChecklistUtils.CHECKLIST_REGEX_LINES_CHECKED)

        assertEquals("\u00a0 Todo", result.toString())
        assertSpans(result, 0, checked = false)
    }

    @Test
    fun leadingWhitespaceStaysBeforeTheCheckbox() {
        val result = addSpans(SpannableStringBuilder("Title\n\t  - [x] Task"), ChecklistUtils.CHECKLIST_REGEX_LINES)

        assertEquals("Title\n\t  \u00a0 Task", result.toString())
        assertSpans(result, 9, checked = true)
    }

    @Test
    fun unanchoredRegexConvertsMidLineMarkerAfterItsLeadingSpace() {
        val result = addSpans(SpannableStringBuilder("Ship it -\t[x] go"), ChecklistUtils.CHECKLIST_REGEX)

        assertEquals("Ship it \u00a0 go", result.toString())
        assertSpans(result, 8, checked = true)
    }

    @Test
    fun spansAreAddedNextToExistingSpans() {
        val editable = SpannableStringBuilder("- [ ] Task")
        val existing = Any()
        editable.setSpan(existing, 6, 10, Spannable.SPAN_INCLUSIVE_INCLUSIVE)

        val result = addSpans(editable, ChecklistUtils.CHECKLIST_REGEX_LINES)

        assertEquals(2, result.getSpanStart(existing))
        assertEquals(6, result.getSpanEnd(existing))
        assertSpans(result, 0, checked = false, others = 1)
    }

    @Test
    fun iconSizeFollowsTheFontSizeWithExtraOutsideLists() {
        val list = addSpans(SpannableStringBuilder("- [ ] a"), ChecklistUtils.CHECKLIST_REGEX, isList = true)
        val editor = addSpans(SpannableStringBuilder("- [ ] a"), ChecklistUtils.CHECKLIST_REGEX, isList = false)

        // mdpi with the default font size: 16dp in lists, 16 + 12dp in the editor.
        assertEquals(Rect(0, 0, 16, 16), imageSpan(list).drawable.bounds)
        assertEquals(Rect(0, 0, 28, 28), imageSpan(editor).drawable.bounds)
        assertEquals(DisplayUtils.getChecklistIconSize(context, true), imageSpan(list).drawable.bounds.right)
        assertEquals(DisplayUtils.getChecklistIconSize(context, false), imageSpan(editor).drawable.bounds.right)
    }

    @Test
    fun imageSpanIsCenteredAndSizedByItsDrawable() {
        val result = addSpans(SpannableStringBuilder("- [x] a"), ChecklistUtils.CHECKLIST_REGEX, isList = false)
        val span = imageSpan(result)

        assertEquals(DynamicDrawableSpan.ALIGN_BOTTOM, span.verticalAlignment)
        assertEquals(28, span.getSize(Paint(), "a", 0, 1, null))
    }

    @Test
    fun iconIsTheMatchingCheckboxDrawableTintedWithTheColorResource() {
        assertIcon("- [ ] a", isList = true, R.drawable.ic_checkbox_list_unchecked_24dp, R.color.blue_50)
        assertIcon("- [x] a", isList = true, R.drawable.ic_checkbox_list_checked_24dp, R.color.red_50)
        assertIcon("- [ ] a", isList = false, R.drawable.ic_checkbox_editor_unchecked_24dp, R.color.red_50)
        assertIcon("- [X] a", isList = false, R.drawable.ic_checkbox_editor_checked_24dp, R.color.blue_50)
        assertFalse(
            render(tinted(R.drawable.ic_checkbox_list_checked_24dp, R.color.blue_50))
                .sameAs(render(tinted(R.drawable.ic_checkbox_list_unchecked_24dp, R.color.blue_50)))
        )
    }

    @Test
    fun unicodeSpansForNullEditableReturnNewEmptyBuilder() {
        val first = ChecklistUtils.addChecklistUnicodeSpansForRegex(null, ChecklistUtils.CHECKLIST_REGEX)
        val second = ChecklistUtils.addChecklistUnicodeSpansForRegex(null, ChecklistUtils.CHECKLIST_REGEX)

        assertTrue(first is SpannableStringBuilder)
        assertEquals(0, first.length)
        assertNotSame(first, second)
    }

    @Test
    fun unicodeSpansWithoutMatchReturnSameEditableUntouched() {
        val editable = SpannableStringBuilder("[ ] Task")

        val result = ChecklistUtils.addChecklistUnicodeSpansForRegex(editable, ChecklistUtils.CHECKLIST_REGEX)

        assertSame(editable, result)
        assertEquals("[ ] Task", result.toString())
        assertEquals(0, result.getSpans(0, result.length, Any::class.java).size)
    }

    @Test
    fun unicodeSpansReplaceUncheckedMarkerWithBallotBox() {
        val editable = SpannableStringBuilder("- [ ] Go")

        val result = ChecklistUtils.addChecklistUnicodeSpansForRegex(editable, ChecklistUtils.CHECKLIST_REGEX)

        assertSame(editable, result)
        assertEquals("\u2610 Go", result.toString())
        assertCheckableOnly(result, 0, checked = false)
    }

    @Test
    fun unicodeSpansReplaceCheckedMarkerWithCheckedBallotBox() {
        val result = ChecklistUtils.addChecklistUnicodeSpansForRegex(
            SpannableStringBuilder("  - [X] Task"),
            ChecklistUtils.CHECKLIST_REGEX_LINES
        )

        assertEquals("  \u2611 Task", result.toString())
        assertCheckableOnly(result, 2, checked = true)
    }

    @Test
    fun unicodeSpansConvertMidLineMarkerWithUnanchoredRegex() {
        val result = ChecklistUtils.addChecklistUnicodeSpansForRegex(
            SpannableStringBuilder("Buy - [x] it"),
            ChecklistUtils.CHECKLIST_REGEX
        )

        assertEquals("Buy \u2611 it", result.toString())
        assertCheckableOnly(result, 4, checked = true)
    }

    private fun addSpans(editable: Editable, regex: String, isList: Boolean = true): Editable =
        ChecklistUtils.addChecklistSpansForRegexAndColor(context, editable, regex, R.color.blue_50, isList)

    private fun assertSpans(editable: Editable, at: Int, checked: Boolean, others: Int = 0) {
        val spans = editable.getSpans(0, editable.length, Any::class.java)
        assertEquals(2 + others, spans.size)
        val image = editable.getSpans(0, editable.length, CenteredImageSpan::class.java).single()
        val checkable = editable.getSpans(0, editable.length, CheckableSpan::class.java).single()
        assertTrue(spans.indexOf(image) < spans.indexOf(checkable))
        for (span in listOf(image, checkable)) {
            assertEquals(at, editable.getSpanStart(span))
            assertEquals(at + 1, editable.getSpanEnd(span))
            assertEquals(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE, editable.getSpanFlags(span))
        }
        assertEquals(ChecklistUtils.CHAR_NO_BREAK_SPACE, editable[at])
        assertEquals(checked, checkable.isChecked)
    }

    private fun assertCheckableOnly(editable: Editable, at: Int, checked: Boolean) {
        val span = editable.getSpans(0, editable.length, Any::class.java).single() as CheckableSpan
        assertEquals(at, editable.getSpanStart(span))
        assertEquals(at + 1, editable.getSpanEnd(span))
        assertEquals(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE, editable.getSpanFlags(span))
        assertEquals(checked, span.isChecked)
    }

    private fun imageSpan(editable: Editable): CenteredImageSpan =
        editable.getSpans(0, editable.length, CenteredImageSpan::class.java).single()

    private fun assertIcon(source: String, isList: Boolean, @DrawableRes expected: Int, @ColorRes color: Int) {
        val result = ChecklistUtils.addChecklistSpansForRegexAndColor(
            context, SpannableStringBuilder(source), ChecklistUtils.CHECKLIST_REGEX, color, isList
        )

        assertTrue("$source isList=$isList", render(imageSpan(result).drawable).sameAs(render(tinted(expected, color))))
    }

    private fun tinted(@DrawableRes drawable: Int, @ColorRes color: Int): Drawable =
        DrawableUtils.tintDrawableWithResource(context, drawable, color)!!

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val bounds = drawable.copyBounds()
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(Canvas(bitmap))
        drawable.bounds = bounds
        return bitmap
    }

    private fun finds(regex: String, text: String) = Pattern.compile(regex, Pattern.MULTILINE).matcher(text).find()

    private companion object {
        const val SIZE = 48
        val MARKERS = listOf(
            "- [ ]", "- [x]", "- [X]", "-\t[x]", "-  [X]", "- []", "- [\t]", "-[ ]", "- [y]", "* [ ]", "- [ x]"
        )
    }
}
