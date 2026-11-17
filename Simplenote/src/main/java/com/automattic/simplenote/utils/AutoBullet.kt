package com.automattic.simplenote.utils

import android.text.Editable
import com.automattic.simplenote.utils.ChecklistUtils.CHAR_BULLET
import com.automattic.simplenote.utils.ChecklistUtils.CHAR_NO_BREAK_SPACE
import com.automattic.simplenote.widgets.CheckableSpan
import java.util.regex.Pattern

object AutoBullet {
    private const val PATTERN_BULLET = "^([\\s]*)([-*+$CHAR_BULLET$CHAR_NO_BREAK_SPACE])[\\s]+(.*)$"
    private const val STR_SPACE = " "

    @JvmStatic
    fun apply(editable: Editable?, oldCursorPosition: Int, newCursorPosition: Int) {
        if (editable == null || !isValidCursorIncrement(editable, oldCursorPosition, newCursorPosition)) {
            return
        }

        val prevChar = editable[newCursorPosition - 1]
        if (prevChar == '\n') {
            val prevParagraphEnd = newCursorPosition - 1
            var prevParagraphStart = 0
            for (i in prevParagraphEnd - 1 downTo 0) {
                if (editable[i] == '\n') {
                    prevParagraphStart = i + 1
                    break
                }
            }
            val prevParagraph = editable.subSequence(prevParagraphStart, prevParagraphEnd).toString()
            val metadata = extractBulletMetadata(prevParagraph)
            // See if there's a CheckableSpan in the previous line
            val checkableSpans = editable.getSpans(prevParagraphStart, prevParagraphEnd, CheckableSpan::class.java)

            if (checkableSpans.isNotEmpty()) {
                if (prevParagraph.javaTrim().equals(CHAR_NO_BREAK_SPACE.toString(), ignoreCase = true)) {
                    // Empty checklist item, remove and place cursor at start of line
                    editable.replace(prevParagraphStart, newCursorPosition, "")
                } else {
                    // We can add a new checkbox!
                    val leadingWhitespace = metadata.leadingWhitespace ?: ""
                    editable.insert(
                        newCursorPosition,
                        leadingWhitespace + ChecklistUtils.UNCHECKED_MARKDOWN + STR_SPACE
                    )
                }

                return
            }

            if (metadata.isBullet) {
                if (!metadata.isEmptyBullet) {
                    editable.insert(newCursorPosition, buildBullet(metadata))
                } else {
                    editable.replace(prevParagraphStart, newCursorPosition, "")
                }
            }
        }
    }

    private fun isValidCursorIncrement(editable: Editable, oldCursorPosition: Int, newCursorPosition: Int): Boolean {
        return newCursorPosition > 0 && newCursorPosition <= editable.length && newCursorPosition > oldCursorPosition
    }

    private fun buildBullet(metadata: BulletMetadata): String {
        return "${metadata.leadingWhitespace}${metadata.bulletChar}$STR_SPACE"
    }

    private fun extractBulletMetadata(input: String): BulletMetadata {
        val metadata = BulletMetadata()

        val pattern = Pattern.compile(PATTERN_BULLET)
        val matcher = pattern.matcher(input)

        if (matcher.find()) {
            metadata.isBullet = true
            metadata.leadingWhitespace = matcher.group(1)
            metadata.bulletChar = matcher.group(2)
            metadata.isEmptyBullet = matcher.group(3)!!.javaTrim().isEmpty()
        }

        return metadata
    }

    // java.lang.String.trim(): Kotlin's trim() would also strip the no-break space that marks a checkbox.
    private fun String.javaTrim(): String = trim { it <= ' ' }

    private class BulletMetadata {
        var bulletChar: String? = null
        var leadingWhitespace: String? = null
        var isBullet = false
        var isEmptyBullet = false
    }
}
