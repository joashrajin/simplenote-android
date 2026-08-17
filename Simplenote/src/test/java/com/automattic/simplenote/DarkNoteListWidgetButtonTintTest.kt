package com.automattic.simplenote

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.utils.PrefUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class DarkNoteListWidgetButtonTintTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PrefUtils.PREF_PREMIUM, false)
            .commit()
    }

    @Test
    fun defaultDarkWidgetAppliesGrayTintThroughRemoteViews() {
        val layout = PrefUtils.getLayoutWidgetList(context, false)
        assertEquals(R.layout.note_list_widget_dark_default, layout)

        val root = RemoteViews(context.packageName, layout).apply(context, FrameLayout(context))
        val button = root.findViewById<ImageView>(R.id.widget_button)
        val tint = button.imageTintList

        assertTrue(button.javaClass == ImageView::class.java)
        assertNotNull(tint)
        assertEquals(context.getColor(R.color.gray_100), tint!!.defaultColor)
    }

    @Test
    fun defaultDarkWidgetDeclaresTintInAndroidNamespace() {
        assertEquals(R.color.gray_100, widgetButtonTintResource())
    }

    private fun widgetButtonTintResource(): Int {
        val parser = context.resources.getXml(R.layout.note_list_widget_dark_default)
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                    parser.getAttributeResourceValue(ANDROID_NAMESPACE, "id", 0) == R.id.widget_button
                ) {
                    return parser.getAttributeResourceValue(ANDROID_NAMESPACE, "tint", 0)
                }
                parser.next()
            }
        } finally {
            parser.close()
        }
        fail("Missing widget_button in the default dark widget layout")
        return 0
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
