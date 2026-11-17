package com.automattic.simplenote.utils;

import android.app.Application;
import android.text.Editable;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ChecklistsTest {
    @Test
    public void testRegexMatching() {
        String checklistMarkdown = "ToDo\n- [ ] Write code\n- [ ] Test it\n- [ ] Ship it - [x] not on a newline";

        Pattern p = Pattern.compile(ChecklistUtils.CHECKLIST_REGEX_LINES, Pattern.MULTILINE);
        Matcher m = p.matcher(checklistMarkdown);

        int count = 0;
        while(m.find()) {
            count++;
        }

        assertThat(count, is(3));

        p = Pattern.compile(ChecklistUtils.CHECKLIST_REGEX, Pattern.MULTILINE);
        m = p.matcher(checklistMarkdown);

        count = 0;
        while(m.find()) {
            count++;
        }

        assertThat(count, is(4));
    }

    @Test
    public void testRegexMatchingNested() {
        String checklistMarkdown = "ToDo\n\n- [ ] New Feature\n    - [ ] Write code\n    - [ ] Test it\n    - [ ] Ship it";

        Pattern p = Pattern.compile(ChecklistUtils.CHECKLIST_REGEX_LINES, Pattern.MULTILINE);
        Matcher m = p.matcher(checklistMarkdown);

        int count = 0;
        while(m.find()) {
            count++;
        }

        assertThat(count, is(4));
    }

    @Test
    public void testNullBuilderPassed() {
        Editable editable = ChecklistUtils.addChecklistSpansForRegexAndColor(
            ApplicationProvider.getApplicationContext(),
            null,
            ChecklistUtils.CHECKLIST_REGEX_LINES,
            android.R.color.black,
            false
        );

        assertThat(editable.length(), is(0));
    }
}
