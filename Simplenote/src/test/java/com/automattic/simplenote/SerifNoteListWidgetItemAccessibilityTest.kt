package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SerifNoteListWidgetItemAccessibilityTest {
    @Test
    fun lightWidgetStatusIconsUseTheirOwnLabels() {
        assertStatusIconLabels(R.layout.note_list_widget_item_light_serif)
    }

    @Test
    fun darkWidgetStatusIconsUseTheirOwnLabels() {
        assertStatusIconLabels(R.layout.note_list_widget_item_dark_serif)
    }

    private fun assertStatusIconLabels(layoutId: Int) {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Default)
        val row = LayoutInflater.from(context).inflate(layoutId, null)
        val pinned = row.findViewById<ImageView>(R.id.note_pinned)
        val published = row.findViewById<ImageView>(R.id.note_published)

        assertEquals(context.getString(R.string.pin), pinned.contentDescription)
        assertEquals(context.getString(R.string.published), published.contentDescription)
    }
}
