package com.automattic.simplenote.widgets

import android.text.style.ClickableSpan
import android.view.View

class CheckableSpan : ClickableSpan() {
    var isChecked = false

    override fun onClick(view: View) {
        isChecked = !isChecked
        if (view is SimplenoteEditText) {
            try {
                view.toggleCheckbox(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
