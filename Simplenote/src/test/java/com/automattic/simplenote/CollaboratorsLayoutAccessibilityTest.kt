package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.widget.ImageButton
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CollaboratorsLayoutAccessibilityTest {
    @Test
    fun addButtonDescribesTheCollaboratorAction() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Default)
        val layout = LayoutInflater.from(context).inflate(R.layout.activity_collaborators, null)
        val button = layout.findViewById<ImageButton>(R.id.button_add_collaborator)

        assertEquals(context.getString(R.string.add_collaborator), button.contentDescription)
    }
}
