package com.automattic.simplenote.utils

import android.content.Context
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import androidx.core.content.ContextCompat
import com.automattic.simplenote.R
import com.automattic.simplenote.widgets.CenteredImageSpan
import com.automattic.simplenote.widgets.CheckableSpan
import java.util.regex.Pattern

object ChecklistUtils {
    const val CHAR_BALLOT_BOX = '\u2610'
    const val CHAR_BALLOT_BOX_CHECK = '\u2611'
    const val CHAR_BULLET = '\u2022'
    const val CHAR_NO_BREAK_SPACE = '\u00a0'
    const val CHAR_VECTOR_CROSS_PRODUCT = '\u2a2f'
    const val CHECKLIST_OFFSET = 3

    const val CHECKLIST_REGEX = "(\\s+)?(-[ \\t]+\\[[xX\\s]?\\])"
    const val CHECKLIST_REGEX_LINES = "^(\\s+)?(-[ \\t]+\\[[xX\\s]?\\])"
    const val CHECKLIST_REGEX_LINES_CHECKED = "^(\\s+)?(-[ \\t]+\\[[xX]?\\])"
    const val CHECKLIST_REGEX_LINES_UNCHECKED = "^(\\s+)?(-[ \\t]+\\[[\\s]?\\])"
    const val CHECKED_MARKDOWN_PREVIEW = "- [$CHAR_VECTOR_CROSS_PRODUCT]"
    const val CHECKED_MARKDOWN = "- [x]"
    const val UNCHECKED_MARKDOWN = "- [ ]"

    /**
     * Replaces each markdown checklist matched by [regex] (CHECKLIST_REGEX or one of the CHECKLIST_REGEX_LINES
     * variants) with a no-break space carrying a [CenteredImageSpan] checkbox tinted with the [color] resource and a
     * [CheckableSpan]. [context] is only read once a checklist matches.
     */
    @JvmStatic
    fun addChecklistSpansForRegexAndColor(
        context: Context?,
        editable: Editable?,
        regex: String,
        color: Int,
        isList: Boolean
    ): Editable {
        if (editable == null) {
            return SpannableStringBuilder("")
        }

        val m = Pattern.compile(regex, Pattern.MULTILINE).matcher(editable)
        var positionAdjustment = 0

        while (m.find()) {
            var start = m.start() - positionAdjustment
            val end = m.end() - positionAdjustment

            // Safety first!
            if (end > editable.length) {
                continue
            }

            val leadingSpaces: String? = m.group(1)
            val match: String? = m.group(2)

            if (!leadingSpaces.isNullOrEmpty()) {
                start += leadingSpaces.length
            }

            if (match == null) {
                continue
            }

            val checkableSpan = CheckableSpan()
            checkableSpan.isChecked = match.contains("x") || match.contains("X")
            editable.replace(start, end, CHAR_NO_BREAK_SPACE.toString())

            var iconDrawable = ContextCompat.getDrawable(
                context!!,
                when {
                    checkableSpan.isChecked && isList -> R.drawable.ic_checkbox_list_checked_24dp
                    checkableSpan.isChecked -> R.drawable.ic_checkbox_editor_checked_24dp
                    isList -> R.drawable.ic_checkbox_list_unchecked_24dp
                    else -> R.drawable.ic_checkbox_editor_unchecked_24dp
                }
            )
            iconDrawable = DrawableUtils.tintDrawableWithResource(context, iconDrawable, color)
            val iconSize = DisplayUtils.getChecklistIconSize(context, isList)
            iconDrawable!!.setBounds(0, 0, iconSize, iconSize)

            val imageSpan = CenteredImageSpan(context, iconDrawable)
            editable.setSpan(imageSpan, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            editable.setSpan(checkableSpan, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            positionAdjustment += (end - start) - 1
        }

        return editable
    }

    /**
     * Replaces each markdown checklist matched by [regex] (CHECKLIST_REGEX or one of the CHECKLIST_REGEX_LINES
     * variants) with a ballot box character carrying a [CheckableSpan].
     */
    @JvmStatic
    fun addChecklistUnicodeSpansForRegex(editable: Editable?, regex: String): Editable {
        if (editable == null) {
            return SpannableStringBuilder("")
        }

        val m = Pattern.compile(regex, Pattern.MULTILINE).matcher(editable)

        var positionAdjustment = 0
        while (m.find()) {
            var start = m.start() - positionAdjustment
            val end = m.end() - positionAdjustment

            // Safety first!
            if (end > editable.length) {
                continue
            }

            val leadingSpaces: String? = m.group(1)
            if (!leadingSpaces.isNullOrEmpty()) {
                start += leadingSpaces.length
            }

            val match: String? = m.group(2)
            if (match == null) {
                continue
            }

            val checkableSpan = CheckableSpan()
            checkableSpan.isChecked = match.contains("x") || match.contains("X")
            editable.replace(
                start,
                end,
                if (checkableSpan.isChecked) CHAR_BALLOT_BOX_CHECK.toString() else CHAR_BALLOT_BOX.toString()
            )
            editable.setSpan(checkableSpan, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            positionAdjustment += (end - start) - 1
        }

        return editable
    }
}
