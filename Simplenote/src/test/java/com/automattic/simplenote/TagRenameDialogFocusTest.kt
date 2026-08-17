package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class TagRenameDialogFocusTest {
    @Test
    fun renameFieldOwnsFocusWhenDialogOpens() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val dialogContext = ContextThemeWrapper(activityController.get(), R.style.Dialog)
        val content = LayoutInflater.from(dialogContext).inflate(R.layout.edit_tag, null)
        val input = content.findViewById<TextInputLayout>(R.id.input_tag_name)
        val editText = input.editText!!
        val message = content.findViewById<View>(R.id.message)
        val dialog = AlertDialog.Builder(dialogContext)
            .setView(content)
            .setTitle(R.string.rename_tag)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            editText.setText("tag")
            message.visibility = View.GONE
            input.visibility = View.VISIBLE
        }

        try {
            dialog.show()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(View.VISIBLE, input.visibility)
            assertTrue(editText.hasFocus())
            assertSame(editText, dialog.currentFocus)
        } finally {
            dialog.dismiss()
            shadowOf(Looper.getMainLooper()).idle()
            activityController.pause().stop().destroy()
        }
    }
}
