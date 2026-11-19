package com.automattic.simplenote.utils

import android.text.SpannableStringBuilder
import androidx.test.InstrumentationRegistry
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.widgets.CheckableSpan
import org.hamcrest.CoreMatchers.`is`
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stays on device: with several matches, ChecklistUtils shrinks the Editable while the Matcher iterates. ART's
 * Matcher works on a String snapshot; the JVM's reads the Editable lazily and throws IndexOutOfBoundsException.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class ChecklistsDeviceTest {
    @Test
    fun testCheckableSpanParsing() {
        val checklistMarkdown = "ToDo\n- [ ] Write code\n- [ ] Test it\n- [ ] Ship it - [x] not on a newline"

        val builder = SpannableStringBuilder(checklistMarkdown)

        val editable = ChecklistUtils.addChecklistSpansForRegexAndColor(
            InstrumentationRegistry.getTargetContext(),
            builder,
            ChecklistUtils.CHECKLIST_REGEX_LINES,
            android.R.color.black,
            false
        )

        // We should have 3 CheckableSpans
        val spans = editable.getSpans(0, editable.length, CheckableSpan::class.java)
        assertThat(spans.size, `is`(3))
    }
}
