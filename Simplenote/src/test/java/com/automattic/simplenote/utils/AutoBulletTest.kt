package com.automattic.simplenote.utils

import android.app.Application
import android.text.Editable
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AutoBulletTest {
    private fun buildEditable(source: CharSequence): Editable {
        return Editable.Factory.getInstance().newEditable(source)
    }

    @Test
    fun testLineBreakOnly() {
        val source = "\n"
        val target = "\n"
        val oldPos = 0
        val newPos = 1

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(target, `is`(editable.toString()))
    }

    @Test
    fun testMultipleLineBreakOnly() {
        val source = "\n\n\n"
        val target = "\n\n\n"
        val oldPos = 1
        val newPos = 2

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testBlankString() {
        val source = " "
        val target = " "
        val oldPos = 0
        val newPos = 1

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testEmptyStringNoIncrement() {
        val source = ""
        val target = ""
        val oldPos = 0
        val newPos = 0

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testNoIncrement() {
        val source = "Test"
        val target = "Test"
        val oldPos = 0
        val newPos = 0

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testLineBreakWithoutPreceedingList() {
        val source = "Hello\nWorld\n"
        val target = "Hello\nWorld\n"
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testNonLineBreakPosition() {
        val source = "Hello\nWorld\n- list"
        val target = "Hello\nWorld\n- list"
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testTopLevelBullet() {
        val source = "- Hello\n"
        val target = "- Hello\n- "
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testStarBullet() {
        val source = "* Hello\n"
        val target = "* Hello\n* "
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testPlusBullet() {
        val source = "+ Hello\n"
        val target = "+ Hello\n+ "
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testInvalidBulletChar() {
        val source = "# Hello\n"
        val target = source
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testSecondLevelBullet() {
        val source = "- Hello\n - World\n"
        val target = "- Hello\n - World\n - "
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testMultiLevelBullet() {
        val source = "- Hello\n - World\n  - Again\n"
        val target = "- Hello\n - World\n  - Again\n  - "
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testBulletBreak() {
        val source = "- Hello\n- \n"
        val target = "- Hello\n"
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testCursorMovedBackwardsToList() {
        val source = "- Hello\n"
        val target = "- Hello\n"
        val oldPos = source.length
        val newPos = source.length - 1

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testCursorMovedBackwardsToNonList() {
        val source = "Hello\n"
        val target = "Hello\n"
        val oldPos = source.length
        val newPos = source.length - 1

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testCursorMovedForwardMultipleToList() {
        val source = "Hello\n- list\n"
        val target = "Hello\n- list\n- "
        val oldPos = 6
        val newPos = 13

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testCursorMovedForwardMultipleToNonList() {
        val source = "Hello\nWorld"
        val target = "Hello\nWorld"
        val oldPos = 6
        val newPos = 11

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testBulletMiddleOfString() {
        val source = "Hello World\n- list\n\nHula Mundo"
        val target = "Hello World\n- list\n- \nHula Mundo"
        val oldPos = 18
        val newPos = 19 // index of the line break after "list"

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testEmptyTopLevelBullet() {
        val source = "- first\n- \n"
        val target = "- first\n"
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    // An empty nested bullet is removed, not outdented, the same as in the iOS and macOS apps.
    @Test
    fun testEmptySecondLevelBullet() {
        val source = "- first\n - second\n - \n"
        val target = "- first\n - second\n"
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testEmptyTabIndentedBulletIsRemoved() {
        val source = "- first\n\t- second\n\t- \n"
        val target = "- first\n\t- second\n"
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testEmptyMultiSpaceIndentedBulletIsRemoved() {
        val source = "- first\n  - second\n  - \n"
        val target = "- first\n  - second\n"
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }

    @Test
    fun testBulletNoSpace() {
        val source = "-first\n"
        val target = "-first\n"
        val oldPos = source.length - 1
        val newPos = source.length

        val editable = buildEditable(source)
        AutoBullet.apply(editable, oldPos, newPos)

        assertThat(editable.toString(), `is`(target))
    }
}
