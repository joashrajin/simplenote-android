package com.automattic.simplenote

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.compose.StyleScreenTags
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Pins the style picker's list and selection behaviour: one row per [ThemeUtils.STYLE_ARRAY] entry, each
 * composed under its own Style_X theme with a style-specific link colour in the preview, and a tap on another
 * style writing [PrefUtils.PREF_STYLE_INDEX], which relaunches the screen with its scroll position. The screen is
 * tall enough that every row is laid out at once, except for the scroll test, which needs the list to scroll.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = StyleActivityTest.TALL)
class StyleActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun listsEveryStyleInOrder() {
        launch(ThemeUtils.STYLE_DEFAULT, darkMode = false)

        var previousTop = Float.NEGATIVE_INFINITY
        ThemeUtils.STYLE_ARRAY.forEachIndexed { position, style ->
            assertEquals(position, style)
            assertEquals(PrefUtils.getStyleNameFromIndex(context, style), title(style))
            val top = row(style).getUnclippedBoundsInRoot().top.value
            assertTrue("style $style is below the previous row", top > previousTop)
            previousTop = top
        }
        composeRule.onNodeWithTag(StyleScreenTags.LIST).onChildren().assertCountEquals(ThemeUtils.STYLE_ARRAY.size)
    }

    @Test
    fun onlyTheSelectedStyleRowIsSelected() {
        launch(ThemeUtils.STYLE_MATRIX, darkMode = false)

        ThemeUtils.STYLE_ARRAY.forEach { style ->
            if (style == ThemeUtils.STYLE_MATRIX) row(style).assertIsSelected() else row(style).assertIsNotSelected()
        }
    }

    @Test
    fun eachRowComposesUnderItsOwnStyleTheme() {
        val activity = launch(ThemeUtils.STYLE_DEFAULT, darkMode = false).get()

        ROW_THEMES.forEach { (style, theme) ->
            assertEquals(
                "style $style",
                Integer.toHexString(colorAccent(ContextThemeWrapper(activity, theme))),
                Integer.toHexString(linkColor(style)),
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
        val activity = launch(ThemeUtils.STYLE_DEFAULT, darkMode = false).get()

        row(ThemeUtils.STYLE_SEPIA).performClick()
        idle()

        assertEquals(ThemeUtils.STYLE_SEPIA, PrefUtils.getStyleIndexSelected(context))
        row(ThemeUtils.STYLE_SEPIA).assertIsSelected()
        row(ThemeUtils.STYLE_DEFAULT).assertIsNotSelected()
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
        val activity = launch(ThemeUtils.STYLE_CLASSIC, darkMode = false).get()

        row(ThemeUtils.STYLE_CLASSIC).performClick()
        idle()

        assertEquals(ThemeUtils.STYLE_CLASSIC, PrefUtils.getStyleIndexSelected(context))
        row(ThemeUtils.STYLE_CLASSIC).assertIsSelected()
        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    @Config(qualifiers = SHORT)
    fun relaunchRestoresTheScrollPositionFromTheExtra() {
        val controller = launch(ThemeUtils.STYLE_DEFAULT, darkMode = false)
        // The short screen can only scroll part of the list, so park the first visible row just past the top.
        val list = composeRule.onNodeWithTag(StyleScreenTags.LIST)
        list.performScrollToIndex(ThemeUtils.STYLE_CLASSIC)
        composeRule.waitForIdle()
        val classicAtRowStart = row(ThemeUtils.STYLE_CLASSIC).getUnclippedBoundsInRoot().top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, PARTIAL_SCROLL_PX) }
        composeRule.waitForIdle()
        val classicTop = row(ThemeUtils.STYLE_CLASSIC).getUnclippedBoundsInRoot().top
        assertTrue("the list is scrolled part way into a row", classicTop < classicAtRowStart)

        row(ThemeUtils.STYLE_BLACK).performClick()
        idle()
        val relaunch = shadowOf(controller.get()).nextStartedActivity
        assertNotNull(relaunch.getIntArrayExtra(EXTRA_SCROLL))
        controller.pause().stop().destroy()

        Robolectric.buildActivity(StyleActivity::class.java, relaunch).setup()
        idle()
        composeRule.waitForIdle()

        assertEquals(classicTop, row(ThemeUtils.STYLE_CLASSIC).getUnclippedBoundsInRoot().top)
    }

    @Test
    fun rowsAreRadioButtonsInASelectableGroup() {
        launch(ThemeUtils.STYLE_DEFAULT, darkMode = false)

        composeRule.onNodeWithTag(StyleScreenTags.LIST)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        ThemeUtils.STYLE_ARRAY.forEach { style ->
            row(style)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertTouchTargetIsAtLeastMinimum()
            // The row merges its title, so TalkBack announces the style name with the selected state.
            val merged = row(style).fetchSemanticsNode().config[SemanticsProperties.Text]
            assertTrue(merged.any { it.text == PrefUtils.getStyleNameFromIndex(context, style) })
        }
    }

    @Test
    fun navigateUpIsALabelledTouchTarget() {
        launch(ThemeUtils.STYLE_DEFAULT, darkMode = false)

        composeRule.onNodeWithContentDescription(context.getString(androidx.appcompat.R.string.abc_action_bar_up_description))
            .assertTouchTargetIsAtLeastMinimum()
    }

    @Test
    fun doubleFontScaleDoesNotClipAnyRow() {
        RuntimeEnvironment.setFontScale(2f)
        launch(ThemeUtils.STYLE_DEFAULT, darkMode = false)

        ThemeUtils.STYLE_ARRAY.forEach { style ->
            composeRule.onNodeWithTag(StyleScreenTags.LIST).performScrollToIndex(style)
            val rowBounds = row(style).getUnclippedBoundsInRoot()
            val titleBounds = node(StyleScreenTags.title(style)).getUnclippedBoundsInRoot()
            val contentBounds = node(StyleScreenTags.content(style)).getUnclippedBoundsInRoot()

            assertTrue("style $style title fits", rowBounds.contains(titleBounds))
            assertTrue("style $style preview fits", rowBounds.contains(contentBounds))
            assertTrue("style $style title sits above the preview", titleBounds.bottom <= contentBounds.top)
            assertTrue("style $style row is a touch target", rowBounds.height >= MIN_TOUCH_TARGET)
        }
    }

    private fun assertPreviewLinkColors(darkMode: Boolean, expected: Map<Int, Int>) {
        launch(ThemeUtils.STYLE_DEFAULT, darkMode)

        expected.forEach { (style, colorRes) ->
            val content = content(style)
            val color = content.spanStyles.filter { it.item.color != Color.Unspecified }.single()
            val underline = content.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }.single()

            assertEquals(underline.start, color.start)
            assertEquals(underline.end, color.end)
            assertEquals(
                "style $style",
                Integer.toHexString(ContextCompat.getColor(context, colorRes) or OPAQUE),
                Integer.toHexString(color.item.color.toArgb()),
            )
        }
    }

    private fun launch(style: Int, darkMode: Boolean): ActivityController<StyleActivity> {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(PrefUtils.PREF_STYLE_INDEX, style)
            .putString(PrefUtils.PREF_THEME, if (darkMode) THEME_DARK else THEME_LIGHT)
            .commit()
        val controller = Robolectric.buildActivity(StyleActivity::class.java).setup()
        idle()
        composeRule.waitForIdle()
        return controller
    }

    private fun row(style: Int) = composeRule.onNodeWithTag(StyleScreenTags.row(style))

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun title(style: Int): String =
        node(StyleScreenTags.title(style)).fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    private fun content(style: Int): AnnotatedString =
        node(StyleScreenTags.content(style)).fetchSemanticsNode().config[SemanticsProperties.Text].single()

    private fun linkColor(style: Int): Int =
        content(style).spanStyles.single { it.item.color != Color.Unspecified }.item.color.toArgb()

    private fun SemanticsNodeInteraction.assertTouchTargetIsAtLeastMinimum() {
        val touchBounds = fetchSemanticsNode().touchBoundsInRoot
        val minimumPx = with(composeRule.density) { MIN_TOUCH_TARGET.toPx() }
        assertTrue("touch height ${touchBounds.height}px", touchBounds.height >= minimumPx)
        assertTrue("touch width ${touchBounds.width}px", touchBounds.width >= minimumPx)
    }

    private fun DpRect.contains(other: DpRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    private fun colorAccent(themed: Context): Int {
        val value = TypedValue()
        check(themed.theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, value, true))
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

        private const val PARTIAL_SCROLL_PX = 37f

        private val MIN_TOUCH_TARGET = 48.dp

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
