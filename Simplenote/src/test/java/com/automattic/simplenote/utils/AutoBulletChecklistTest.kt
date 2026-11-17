package com.automattic.simplenote.utils

import android.app.Application
import android.text.Editable
import android.text.Spannable
import com.automattic.simplenote.widgets.CheckableSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the AutoBullet paths AutoBulletTest (kept identical to upstream) does not cover: invalid input, checklist
 * lines, and the bullet characters only the editor produces. Blank checks use java.lang.String.trim(), which
 * keeps the no-break space that marks a converted checkbox.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AutoBulletChecklistTest {
    @Test
    fun nullEditableIsIgnored() {
        AutoBullet.apply(null, 0, 1)
    }

    @Test
    fun cursorBeyondTheTextIsIgnored() {
        val editable = editable("- a\n")

        AutoBullet.apply(editable, 3, 5)

        assertEquals("- a\n", editable.toString())
    }

    @Test
    fun checklistLineContinuesWithUncheckedMarkdown() {
        val editable = editable("\u00a0 Buy milk\n", checkboxAt = 0)

        AutoBullet.apply(editable, 10, 11)

        assertEquals("\u00a0 Buy milk\n- [ ] ", editable.toString())
    }

    @Test
    fun indentedChecklistLineKeepsItsIndentation() {
        val editable = editable("Title\n\t \u00a0 Buy\n", checkboxAt = 8)

        AutoBullet.apply(editable, 13, 14)

        assertEquals("Title\n\t \u00a0 Buy\n\t - [ ] ", editable.toString())
    }

    @Test
    fun checklistLineWithoutSpaceAfterCheckboxDropsIndentation() {
        val editable = editable("\t\u00a0Buy\n", checkboxAt = 1)

        AutoBullet.apply(editable, 5, 6)

        assertEquals("\t\u00a0Buy\n- [ ] ", editable.toString())
    }

    @Test
    fun emptyChecklistLineIsRemoved() {
        val editable = editable("\u00a0 Buy\n\u00a0\n", checkboxAt = 0)
        editable.setSpan(CheckableSpan(), 6, 7, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        AutoBullet.apply(editable, 7, 8)

        assertEquals("\u00a0 Buy\n", editable.toString())
    }

    @Test
    fun emptyChecklistLineWithSurroundingWhitespaceIsRemoved() {
        val editable = editable("  \u00a0 \n", checkboxAt = 2)

        AutoBullet.apply(editable, 4, 5)

        assertEquals("", editable.toString())
    }

    @Test
    fun noBreakSpaceAfterBulletIsNotAnEmptyBullet() {
        val editable = editable("- \u00a0\n")

        AutoBullet.apply(editable, 3, 4)

        assertEquals("- \u00a0\n- ", editable.toString())
    }

    @Test
    fun bulletCharacterContinues() {
        val editable = editable(" \u2022 a\n")

        AutoBullet.apply(editable, 4, 5)

        assertEquals(" \u2022 a\n \u2022 ", editable.toString())
    }

    @Test
    fun noBreakSpaceWithoutCheckboxContinuesAsBullet() {
        val editable = editable("\u00a0 a\n")

        AutoBullet.apply(editable, 3, 4)

        assertEquals("\u00a0 a\n\u00a0 ", editable.toString())
    }

    @Test
    fun emptyBulletCharacterLineIsRemoved() {
        val editable = editable("\u2022 a\n\u2022 \t\n")

        AutoBullet.apply(editable, 7, 8)

        assertEquals("\u2022 a\n", editable.toString())
    }

    @Test
    fun checkboxOnAnEarlierLineDoesNotMakeABullet() {
        val editable = editable("\u00a0 a\nplain\n", checkboxAt = 0)

        AutoBullet.apply(editable, 9, 10)

        assertEquals("\u00a0 a\nplain\n", editable.toString())
    }

    private fun editable(source: String, checkboxAt: Int = -1): Editable {
        val editable = Editable.Factory.getInstance().newEditable(source)
        if (checkboxAt >= 0) {
            editable.setSpan(CheckableSpan(), checkboxAt, checkboxAt + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return editable
    }
}
