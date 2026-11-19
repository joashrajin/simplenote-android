package com.automattic.simplenote.utils

import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.view.MotionEvent
import android.widget.TextView
import com.automattic.simplenote.widgets.CheckableSpan

// Only allows onClick events for CheckableSpans
class SimplenoteMovementMethod : ArrowKeyMovementMethod() {
    override fun onTouchEvent(textView: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        var x = event.x.toInt()
        var y = event.y.toInt()

        x -= textView.totalPaddingLeft
        y -= textView.totalPaddingTop

        x += textView.scrollX
        y += textView.scrollY

        val layout = textView.layout
        val line = layout.getLineForVertical(y)
        var off = layout.getOffsetForHorizontal(line, x.toFloat())
        val lineStart = layout.getLineStart(line)

        // Also toggle the checkbox if the user tapped the space next to the checkbox
        if (off == lineStart + 1) {
            off = lineStart
        }

        val checkableSpans = buffer.getSpans(off, off, CheckableSpan::class.java)

        if (checkableSpans.isNotEmpty()) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> if (!textView.hasFocus()) {
                    textView.isFocusableInTouchMode = false
                }
                MotionEvent.ACTION_UP -> {
                    checkableSpans[0].onClick(textView)
                    textView.isFocusableInTouchMode = true
                }
            }

            return true
        }

        return false
    }

    companion object {
        private var instance: SimplenoteMovementMethod? = null

        @JvmStatic
        fun getInstance(): SimplenoteMovementMethod {
            return instance ?: SimplenoteMovementMethod().also { instance = it }
        }
    }
}
