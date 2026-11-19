package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ChecklistsTest {
    @Test
    fun testRegexMatching() {
        val checklistMarkdown = "ToDo\n- [ ] Write code\n- [ ] Test it\n- [ ] Ship it - [x] not on a newline"

        var p = Pattern.compile(ChecklistUtils.CHECKLIST_REGEX_LINES, Pattern.MULTILINE)
        var m = p.matcher(checklistMarkdown)

        var count = 0
        while (m.find()) {
            count++
        }

        assertThat(count, `is`(3))

        p = Pattern.compile(ChecklistUtils.CHECKLIST_REGEX, Pattern.MULTILINE)
        m = p.matcher(checklistMarkdown)

        count = 0
        while (m.find()) {
            count++
        }

        assertThat(count, `is`(4))
    }

    @Test
    fun testRegexMatchingNested() {
        val checklistMarkdown = "ToDo\n\n- [ ] New Feature\n    - [ ] Write code\n    - [ ] Test it\n    - [ ] Ship it"

        val p = Pattern.compile(ChecklistUtils.CHECKLIST_REGEX_LINES, Pattern.MULTILINE)
        val m = p.matcher(checklistMarkdown)

        var count = 0
        while (m.find()) {
            count++
        }

        assertThat(count, `is`(4))
    }

    @Test
    fun testNullBuilderPassed() {
        val editable = ChecklistUtils.addChecklistSpansForRegexAndColor(
            ApplicationProvider.getApplicationContext<Context>(),
            null,
            ChecklistUtils.CHECKLIST_REGEX_LINES,
            android.R.color.black,
            false
        )

        assertThat(editable.length, `is`(0))
    }
}
