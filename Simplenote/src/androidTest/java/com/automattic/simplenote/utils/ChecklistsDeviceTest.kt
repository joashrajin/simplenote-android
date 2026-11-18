package com.automattic.simplenote.utils;

import android.text.Editable;
import android.text.SpannableStringBuilder;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.automattic.simplenote.widgets.CheckableSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.matcher.ViewMatchers.assertThat;
import static org.hamcrest.CoreMatchers.is;

/**
 * Stays on device: with several matches, ChecklistUtils shrinks the Editable while the Matcher iterates. ART's
 * Matcher works on a String snapshot; the JVM's reads the Editable lazily and throws IndexOutOfBoundsException.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ChecklistsDeviceTest {
    @Test
    public void testCheckableSpanParsing() {
        String checklistMarkdown = "ToDo\n- [ ] Write code\n- [ ] Test it\n- [ ] Ship it - [x] not on a newline";

        SpannableStringBuilder builder = new SpannableStringBuilder(checklistMarkdown);

        Editable editable = ChecklistUtils.addChecklistSpansForRegexAndColor(
            InstrumentationRegistry.getTargetContext(),
            builder,
            ChecklistUtils.CHECKLIST_REGEX_LINES,
            android.R.color.black,
            false
        );

        // We should have 3 CheckableSpans
        CheckableSpan[] spans = editable.getSpans(0, editable.length(), CheckableSpan.class);
        assertThat(spans.length, is(3));
    }
}
