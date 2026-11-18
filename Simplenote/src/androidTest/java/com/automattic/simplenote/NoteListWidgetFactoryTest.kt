package com.automattic.simplenote

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.utils.PrefUtils
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class NoteListWidgetFactoryTest {
    // The factory reaches NotesRepository through an entry point (WidgetStreams), which resolves
    // against the TestDataModule buckets on HiltTestApplication.
    @JvmField
    @Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun getViewAtBeforeDataSetChangedReturnsEmptyRow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = NoteListWidgetFactory(context, Intent())

        assertEquals(0, factory.count)

        val views = factory.getViewAt(0)

        assertNotNull(views)
        assertEquals(context.packageName, views.`package`)
        assertEquals(PrefUtils.getLayoutWidgetListItem(context, true), views.layoutId)
    }

    @Test
    fun getViewAtAfterDestroyReturnsEmptyRow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = NoteListWidgetFactory(context, Intent())
        factory.onDataSetChanged()
        factory.onDestroy()

        assertEquals(0, factory.count)

        val views = factory.getViewAt(0)

        assertNotNull(views)
        assertEquals(context.packageName, views.`package`)
        assertEquals(PrefUtils.getLayoutWidgetListItem(context, true), views.layoutId)
    }

    @Test
    fun positionItemIdsAreReportedAsUnstable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = NoteListWidgetFactory(context, Intent())

        assertEquals(0L, factory.getItemId(0))
        assertEquals(7L, factory.getItemId(7))
        assertFalse(factory.hasStableIds())
    }
}
