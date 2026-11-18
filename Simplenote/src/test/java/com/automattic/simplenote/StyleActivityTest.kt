package com.automattic.simplenote

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.ThemeUtils
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Pins the style picker's list and selection behaviour: one row per [ThemeUtils.STYLE_ARRAY] entry, each
 * inflated under its own Style_X theme with a style-specific link colour in the preview, and a tap on another
 * style writing [PrefUtils.PREF_STYLE_INDEX], which relaunches the screen with its scroll position. The screen is
 * tall enough that every row is laid out at once, except for the scroll test, which needs the list to scroll.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = StyleActivityTest.TALL)
class StyleActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun listsEveryStyleInOrderWithOneViewTypePerRow() {
        val list = launch(ThemeUtils.STYLE_DEFAULT, darkMode = false).list()
        val adapter = list.adapter!!

        assertEquals(ThemeUtils.STYLE_ARRAY.size, adapter.itemCount)
        ThemeUtils.STYLE_ARRAY.forEachIndexed { position, style ->
            assertEquals(position, adapter.getItemViewType(position))
            assertEquals(PrefUtils.getStyleNameFromIndex(context, style), list.row(position).title().text.toString())
        }
    }

    @Test
    fun onlyTheSelectedStyleRowIsActivated() {
        val list = launch(ThemeUtils.STYLE_MATRIX, darkMode = false).list()

        ThemeUtils.STYLE_ARRAY.forEach { style ->
            assertEquals(style == ThemeUtils.STYLE_MATRIX, list.row(style).isActivated)
        }
    }

    @Test
    fun eachRowInflatesUnderItsOwnStyleTheme() {
        val activity = launch(ThemeUtils.STYLE_DEFAULT, darkMode = false)
        val list = activity.list()

        ROW_THEMES.forEach { (style, theme) ->
            assertEquals(
                "style $style",
                colorPrimary(ContextThemeWrapper(list.context, theme)),
                colorPrimary(list.row(style).context),
            )
        }
    }

    @Test
    fun previewLinkUsesTheStyleLightLinkColor() {
        assertPreviewLinkColors(darkMode = false, LIGHT_LINK_COLORS)
    }

    @Test
    @Config(qualifiers = "+night")
    fun previewLinkUsesTheStyleDarkLinkColor() {
        assertPreviewLinkColors(darkMode = true, DARK_LINK_COLORS)
    }

    @Test
    fun selectingAnotherStyleWritesThePrefAndRelaunchesWithTheScrollPosition() {
        val controller = launchController(ThemeUtils.STYLE_DEFAULT, darkMode = false)
        val activity = controller.get()
        val list = activity.list()

        list.row(ThemeUtils.STYLE_SEPIA).performClick()
        idle()

        assertEquals(ThemeUtils.STYLE_SEPIA, PrefUtils.getStyleIndexSelected(context))
        assertTrue(list.row(ThemeUtils.STYLE_SEPIA).isActivated)
        assertFalse(list.row(ThemeUtils.STYLE_DEFAULT).isActivated)
        assertTrue(activity.isFinishing)

        val relaunch = shadowOf(activity).nextStartedActivity
        assertEquals(StyleActivity::class.java.name, relaunch.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, relaunch.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertTrue(relaunch.hasExtra(EXTRA_SCROLL))
        assertEquals(android.R.anim.fade_in, shadowOf(activity).pendingTransitionEnterAnimationResourceId)
        assertEquals(android.R.anim.fade_out, shadowOf(activity).pendingTransitionExitAnimationResourceId)
    }

    @Test
    fun selectingTheCurrentStyleKeepsTheScreen() {
        val activity = launch(ThemeUtils.STYLE_CLASSIC, darkMode = false)

        activity.list().row(ThemeUtils.STYLE_CLASSIC).performClick()
        idle()

        assertEquals(ThemeUtils.STYLE_CLASSIC, PrefUtils.getStyleIndexSelected(context))
        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    @Config(qualifiers = SHORT)
    fun relaunchRestoresTheScrollPositionFromTheExtra() {
        val activity = launch(ThemeUtils.STYLE_DEFAULT, darkMode = false)
        val list = activity.list()
        val layoutManager = list.layoutManager as LinearLayoutManager
        layoutManager.scrollToPositionWithOffset(ThemeUtils.STYLE_MATRIX, 0)
        list.layoutNow()
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleTop = layoutManager.findViewByPosition(firstVisible)!!.top
        assertTrue(firstVisible > 0)

        list.row(ThemeUtils.STYLE_MONO).performClick()
        idle()
        val relaunch = shadowOf(activity).nextStartedActivity
        assertNotNull(relaunch.getParcelableExtra(EXTRA_SCROLL))

        val restored = Robolectric.buildActivity(StyleActivity::class.java, relaunch).setup().get()
        idle()
        val restoredLayoutManager = restored.list().layoutManager as LinearLayoutManager

        assertEquals(firstVisible, restoredLayoutManager.findFirstVisibleItemPosition())
        assertEquals(firstVisibleTop, restoredLayoutManager.findViewByPosition(firstVisible)!!.top)
    }

    private fun assertPreviewLinkColors(darkMode: Boolean, expected: Map<Int, Int>) {
        val list = launch(ThemeUtils.STYLE_DEFAULT, darkMode).list()

        expected.forEach { (style, colorRes) ->
            val content = list.row(style).findViewById<TextView>(R.id.preview_content).text as Spanned
            val color = content.getSpans(0, content.length, ForegroundColorSpan::class.java).single()
            val underline = content.getSpans(0, content.length, UnderlineSpan::class.java).single()

            assertEquals(content.getSpanStart(underline), content.getSpanStart(color))
            assertEquals(content.getSpanEnd(underline), content.getSpanEnd(color))
            assertEquals(
                "style $style",
                Integer.toHexString(ContextCompat.getColor(context, colorRes) or OPAQUE),
                Integer.toHexString(color.foregroundColor),
            )
        }
    }

    private fun launch(style: Int, darkMode: Boolean): StyleActivity = launchController(style, darkMode).get()

    private fun launchController(style: Int, darkMode: Boolean): ActivityController<StyleActivity> {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(PrefUtils.PREF_STYLE_INDEX, style)
            .putString(PrefUtils.PREF_THEME, if (darkMode) THEME_DARK else THEME_LIGHT)
            .commit()
        val controller = Robolectric.buildActivity(StyleActivity::class.java).setup()
        idle()
        return controller
    }

    private fun StyleActivity.list(): RecyclerView = findViewById<RecyclerView>(R.id.list).also { it.layoutNow() }

    private fun RecyclerView.layoutNow() {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        layout(left, top, right, bottom)
    }

    private fun RecyclerView.row(position: Int): View =
        checkNotNull(findViewHolderForAdapterPosition(position)) { "row $position is not laid out" }.itemView

    private fun View.title(): TextView = findViewById(R.id.preview_title)

    private fun colorPrimary(themed: Context): Int {
        val value = TypedValue()
        check(themed.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true))
        return value.data
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    companion object {
        const val TALL = "w360dp-h2000dp"
        const val SHORT = "w360dp-h640dp"

        private const val EXTRA_SCROLL = "EXTRA_SCROLL"

        // ThemeUtils.THEME_LIGHT / THEME_DARK are private; PREF_THEME is read through getStringPref.
        private const val THEME_LIGHT = "0"
        private const val THEME_DARK = "1"

        private const val OPAQUE = 0xff000000.toInt()

        private val ROW_THEMES = mapOf(
            ThemeUtils.STYLE_DEFAULT to R.style.Style_Default,
            ThemeUtils.STYLE_CLASSIC to R.style.Style_Classic,
            ThemeUtils.STYLE_BLACK to R.style.Style_Black,
            ThemeUtils.STYLE_MATRIX to R.style.Style_Matrix,
            ThemeUtils.STYLE_MONO to R.style.Style_Mono,
            ThemeUtils.STYLE_PUBLICATION to R.style.Style_Publication,
            ThemeUtils.STYLE_SEPIA to R.style.Style_Sepia,
        )

        private val LIGHT_LINK_COLORS: Map<Int, Int> = mapOf(
            ThemeUtils.STYLE_DEFAULT to R.color.simplenote_blue_50,
            ThemeUtils.STYLE_CLASSIC to R.color.blue_50,
            ThemeUtils.STYLE_BLACK to android.R.color.black,
            ThemeUtils.STYLE_MATRIX to R.color.green_50,
            ThemeUtils.STYLE_MONO to R.color.gray_50,
            ThemeUtils.STYLE_PUBLICATION to R.color.red_50,
            ThemeUtils.STYLE_SEPIA to R.color.orange_50,
        )

        private val DARK_LINK_COLORS: Map<Int, Int> = mapOf(
            ThemeUtils.STYLE_DEFAULT to R.color.simplenote_blue_20,
            ThemeUtils.STYLE_CLASSIC to R.color.blue_20,
            ThemeUtils.STYLE_BLACK to android.R.color.white,
            ThemeUtils.STYLE_MATRIX to R.color.green_20,
            ThemeUtils.STYLE_MONO to R.color.gray_20,
            ThemeUtils.STYLE_PUBLICATION to R.color.red_20,
            ThemeUtils.STYLE_SEPIA to R.color.orange_20,
        )
    }
}
