package com.automattic.simplenote.authentication.magiclink

import android.app.Application
import android.text.InputType
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.view.ContextThemeWrapper
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MagicLinkCodeLayoutTest {
    @Test
    fun codeFieldRestrictsInputToSixAsciiAlphanumericCharacters() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Default)
        val layout = LayoutInflater.from(context).inflate(R.layout.fragment_magic_link_code, null)
        val codeField = layout.findViewById<EditText>(R.id.confirmation_code_textfield)

        checkNotNull(codeField.onCreateInputConnection(EditorInfo())).commitText("aB-é12", 1)
        assertEquals("aB12", codeField.text.toString())

        codeField.text?.clear()
        checkNotNull(codeField.onCreateInputConnection(EditorInfo())).commitText("aB12C3D4", 1)
        assertEquals("aB12C3", codeField.text.toString())
        assertEquals(InputType.TYPE_CLASS_TEXT, codeField.inputType and InputType.TYPE_MASK_CLASS)
        assertEquals(
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            codeField.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        )
        assertNotEquals(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            codeField.inputType and InputType.TYPE_MASK_VARIATION,
        )
    }
}
