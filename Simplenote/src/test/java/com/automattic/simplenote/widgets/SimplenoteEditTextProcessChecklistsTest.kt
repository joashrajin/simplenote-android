package com.automattic.simplenote.widgets

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.utils.ThemeUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the range gate of processChecklists(start, count): it only bails out when the paragraphs touched by the edit
 * contain no '['; otherwise ChecklistUtils converts every checklist in the note. Each note here has at most one
 * checked and one unchecked marker so that each regex pass has a single match (see ChecklistUtilsTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SimplenoteEditTextProcessChecklistsTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val context: Context = ContextThemeWrapper(app, ThemeUtils.getStyle(app))
    private val errors = mutableListOf<Throwable>()

    @After
    fun noChecklistErrors() {
        assertEquals(emptyList<Throwable>(), errors)
    }

    @Test
    fun editInParagraphWithoutBracketLeavesMarkdownElsewhere() {
        val editText = editText("Title\n- [ ] Task\nBody")

        editText.processChecklists(17, 4)

        assertEquals("Title\n- [ ] Task\nBody", editText.text.toString())
        assertEquals(emptyList<Pair<Int, Boolean>>(), checkboxes(editText))
    }

    @Test
    fun editInChecklistParagraphConvertsIt() {
        val editText = editText("Title\n- [ ] Task\nBody")

        editText.processChecklists(8, 0)

        assertEquals("Title\n\u00a0 Task\nBody", editText.text.toString())
        assertEquals(listOf(6 to false), checkboxes(editText))
    }

    @Test
    fun bracketInEditedParagraphConvertsChecklistsInOtherParagraphs() {
        val editText = editText("- [x] Done\nSee [link]")

        editText.processChecklists(15, 1)

        assertEquals("\u00a0 Done\nSee [link]", editText.text.toString())
        assertEquals(listOf(0 to true), checkboxes(editText))
    }

    @Test
    fun editSpanningParagraphsChecksEveryTouchedParagraph() {
        val editText = editText("Intro\nMore\n- [ ] Task")

        editText.processChecklists(2, 10)

        assertEquals("Intro\nMore\n\u00a0 Task", editText.text.toString())
        assertEquals(listOf(11 to false), checkboxes(editText))
    }

    @Test
    fun outOfRangeStartAndNegativeCountAreClamped() {
        val beyond = editText("- [ ] Task")
        beyond.processChecklists(100, -5)

        val before = editText("- [x] Task")
        before.processChecklists(-3, 2)

        assertEquals("\u00a0 Task", beyond.text.toString())
        assertEquals("\u00a0 Task", before.text.toString())
        assertEquals(listOf(0 to false), checkboxes(beyond))
        assertEquals(listOf(0 to true), checkboxes(before))
    }

    @Test
    fun emptyTextIsIgnored() {
        val editText = editText("")

        editText.processChecklists(0, 0)
        editText.processChecklists()

        assertEquals("", editText.text.toString())
    }

    @Test
    fun fullPassConvertsCheckedAndUncheckedMarkers() {
        val editText = editText("- [ ] Todo\n- [x] Done")

        editText.processChecklists()

        assertEquals("\u00a0 Todo\n\u00a0 Done", editText.text.toString())
        assertEquals(listOf(0 to false, 7 to true), checkboxes(editText))
    }

    @Test
    fun emptyBracketsAreConvertedByTheCheckedPassAsUnchecked() {
        val editText = editText("- [] Empty")

        editText.processChecklists()

        assertEquals("\u00a0 Empty", editText.text.toString())
        assertEquals(listOf(0 to false), checkboxes(editText))
    }

    private fun editText(content: String) = SimplenoteEditText(context).also {
        it.setText(content)
        it.setOnChecklistErrorListener { error -> errors += error }
    }

    private fun checkboxes(editText: SimplenoteEditText): List<Pair<Int, Boolean>> {
        val text = editText.text!!
        val images = text.getSpans(0, text.length, CenteredImageSpan::class.java).map { text.getSpanStart(it) }
        val checkboxes = text.getSpans(0, text.length, CheckableSpan::class.java)
            .map { text.getSpanStart(it) to it.isChecked }
            .sortedBy { it.first }
        assertEquals(checkboxes.map { it.first }, images.sorted())
        return checkboxes
    }
}
