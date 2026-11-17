package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import com.automattic.simplenote.widgets.CenteredImageSpan
import com.automattic.simplenote.widgets.CheckableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the multi-match conversion (positionAdjustment and the start/end rebasing) on the JVM. Every call gets an
 * [ArtMatcherInput], so its Matcher reads the text as it was when the call started, like ART's Matcher, while the
 * replacements and spans land on the live [SpannableStringBuilder].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ChecklistUtilsMultiMatchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun deviceTestInputGetsThreeCheckboxesAndKeepsTheMidLineMarker() {
        val live = SpannableStringBuilder("ToDo\n- [ ] Write code\n- [ ] Test it\n- [ ] Ship it - [x] not on a newline")

        addSpans(live, ChecklistUtils.CHECKLIST_REGEX_LINES, isList = false)

        assertEquals("ToDo\n  Write code\n  Test it\n  Ship it - [x] not on a newline", live.toString())
        assertCheckboxes(live, 5 to false, 18 to false, 28 to false)
    }

    @Test
    fun consecutiveLinesAreEachConvertedAtTheirShiftedOffsets() {
        val live = SpannableStringBuilder("- [ ] a\n- [ ] b\n- [x] c")

        addSpans(live, ChecklistUtils.CHECKLIST_REGEX_LINES)

        assertEquals("  a\n  b\n  c", live.toString())
        assertCheckboxes(live, 0 to false, 4 to false, 8 to true)
    }

    @Test
    fun leadingWhitespaceOfLaterMatchesStaysBeforeTheirCheckbox() {
        val live = SpannableStringBuilder("  - [x] a\n\t-\t[ ] b\n    -  [X] c\nd")

        addSpans(live, ChecklistUtils.CHECKLIST_REGEX_LINES)

        assertEquals("    a\n\t  b\n      c\nd", live.toString())
        assertCheckboxes(live, 2 to true, 7 to false, 15 to true)
    }

    @Test
    fun unanchoredRegexConvertsEveryMarkerOnTheLineAndAcrossNewlines() {
        val live = SpannableStringBuilder("a - [ ] b - [x] c\n- [X] d")

        addSpans(live, ChecklistUtils.CHECKLIST_REGEX)

        assertEquals("a   b   c\n  d", live.toString())
        assertCheckboxes(live, 2 to false, 6 to true, 10 to true)
    }

    @Test
    fun editorPassesConvertCheckedThenUncheckedLinesOfOneNote() {
        val live = SpannableStringBuilder("Title\n- [ ] one\n- [x] two\n  - [ ] three\n- [X] four\nend")

        addSpans(live, ChecklistUtils.CHECKLIST_REGEX_LINES_CHECKED, isList = false)
        assertEquals("Title\n- [ ] one\n  two\n  - [ ] three\n  four\nend", live.toString())
        assertCheckboxes(live, 16 to true, 36 to true)

        addSpans(live, ChecklistUtils.CHECKLIST_REGEX_LINES_UNCHECKED, isList = false)
        assertEquals("Title\n  one\n  two\n    three\n  four\nend", live.toString())
        assertCheckboxes(live, 6 to false, 12 to true, 20 to false, 28 to true)
    }

    @Test
    fun unicodeSpansConvertEveryMarkerAtItsShiftedOffset() {
        val live = SpannableStringBuilder("- [ ] a\n  - [x] b\n- [] c - [X] d")
        val input = ArtMatcherInput(live)

        assertSame(input, ChecklistUtils.addChecklistUnicodeSpansForRegex(input, ChecklistUtils.CHECKLIST_REGEX))
        assertEquals("\u2610 a\n  \u2611 b\n\u2610 c \u2611 d", live.toString())
        val spans = live.getSpans(0, live.length, Any::class.java).sortedBy { live.getSpanStart(it) }
        assertEquals(
            listOf(0 to false, 6 to true, 10 to false, 14 to true),
            spans.map { live.getSpanStart(it) to (it as CheckableSpan).isChecked }
        )
        for (span in spans) {
            assertEquals(live.getSpanStart(span) + 1, live.getSpanEnd(span))
            assertEquals(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE, live.getSpanFlags(span))
        }
    }

    private fun addSpans(live: SpannableStringBuilder, regex: String, isList: Boolean = true) {
        val input = ArtMatcherInput(live)
        val result = ChecklistUtils.addChecklistSpansForRegexAndColor(context, input, regex, R.color.blue_50, isList)
        assertSame(input, result)
    }

    private fun assertCheckboxes(live: Editable, vararg expected: Pair<Int, Boolean>) {
        val checkables = live.getSpans(0, live.length, CheckableSpan::class.java).sortedBy { live.getSpanStart(it) }
        val images = live.getSpans(0, live.length, CenteredImageSpan::class.java).sortedBy { live.getSpanStart(it) }
        assertEquals(2 * expected.size, live.getSpans(0, live.length, Any::class.java).size)
        assertEquals(expected.toList(), checkables.map { live.getSpanStart(it) to it.isChecked })
        assertEquals(expected.map { it.first }, images.map { live.getSpanStart(it) })
        for (span in checkables + images) {
            assertEquals(live.getSpanStart(span) + 1, live.getSpanEnd(span))
            assertEquals(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE, live.getSpanFlags(span))
            assertEquals(ChecklistUtils.CHAR_NO_BREAK_SPACE, live[live.getSpanStart(span)])
        }
    }

    /** Reads characters from a snapshot of [live] taken on creation and forwards everything else to [live]. */
    private class ArtMatcherInput(val live: SpannableStringBuilder) : Editable by live {
        private val snapshot = live.toString()

        override fun get(index: Int): Char = snapshot[index]

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            snapshot.subSequence(startIndex, endIndex)
    }
}
