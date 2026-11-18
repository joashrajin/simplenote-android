package com.automattic.simplenote

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.screenshottest.ScreenshotHarness
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * Semantics of the Compose About screen (labels, roles, touch targets, large-font reflow), its press
 * feedback, and the touch-only logo Easter egg.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class AboutScreenAccessibilityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private var controller: ActivityController<AboutActivity>? = null

    // This class shares the screenshot tests' sandbox config, and an activity left running here
    // breaks their Espresso root lookup (NoMatchingViewException on the decor view).
    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
    }

    @Test
    fun closeIsAnnouncedAsCloseAndFinishes() {
        val activity = launch()

        composeRule.onNodeWithContentDescription(application.getString(R.string.description_close))
            .assertHasClickAction()
            .performClick()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun eachRowIsOneButtonLabelledByTitleAndSubtitle() {
        launch()

        ROWS.forEach { (title, subtitle) ->
            composeRule.onNode(hasText(application.getString(title)) and hasClickAction())
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertTextEquals(application.getString(title), application.getString(subtitle))
        }
    }

    // Close, six rows, three legal links: the logo's Easter egg adds no clickable or labelled node.
    @Test
    fun logoIsNotAFocusStop() {
        launch()

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(10)
        composeRule.onAllNodesWithContentDescription(application.getString(R.string.logo)).assertCountEquals(0)
    }

    @Test
    fun holdingTheLogoSpinsUpToTheEasterEggDialog() {
        launch()
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput { down(Offset(centerX, LOGO_CENTER_Y.toPx())) }
        composeRule.mainClock.advanceTimeBy(LOGO_HOLD_MS + LOGO_SPIN_UP_MS)

        assertTrue(ShadowDialog.getLatestDialog().isShowing)
        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun releasingTheLogoBeforeFullSpeedShowsNoDialog() {
        launch()
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput { down(Offset(centerX, LOGO_CENTER_Y.toPx())) }
        composeRule.mainClock.advanceTimeBy(LOGO_HOLD_MS)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(LOGO_SPIN_UP_MS)

        assertNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun legalLinksHaveMinimumTouchTargetsWithCentredText() {
        launch()

        LINKS.forEach { label ->
            val link = composeRule.onNodeWithText(link(label)).performScrollTo()
                .assertHasClickAction()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            val text = composeRule.onNodeWithText(link(label), useUnmergedTree = true)
            assertEquals(link.bounds().center.y, text.bounds().center.y, 0.5f)
        }
    }

    @Test
    fun privacyAndTermsWrapInsteadOfClippingAtLargeFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        launch()

        val rootWidth = composeRule.onRoot().bounds().width
        val privacy = composeRule.onNodeWithText(link(R.string.link_privacy), useUnmergedTree = true)
            .performScrollTo()
        val terms = composeRule.onNodeWithText(link(R.string.link_terms), useUnmergedTree = true)

        listOf(privacy, terms).forEach { text ->
            val bounds = text.bounds()
            val layout = text.textLayout()
            assertTrue(bounds.left >= 0f && bounds.right <= rootWidth)
            assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
            for (line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineRight(line) - layout.getLineLeft(line) <= bounds.width + 1f)
            }
        }
        assertTrue(privacy.bounds().right <= terms.bounds().left)
        assertTrue(terms.textLayout().lineCount > 1)
    }

    // Without a MaterialTheme or LocalIndication, plain clickables get Compose's debug indication: a
    // 30% black overlay while pressed. Robolectric does not paint the platform RippleDrawable that the
    // M3 ripple uses, so this can only pin that the overlay is gone.
    @Test
    fun pressingARowOrLegalLinkDrawsNoDebugOverlay() {
        val activity = launch()

        listOf(
            composeRule.onNode(hasText(application.getString(R.string.blog)) and hasClickAction()),
            composeRule.onNodeWithText(link(R.string.link_california)),
        ).forEach { node ->
            node.performScrollTo()
            val resting = activity.pixelNearLeftEdgeOf(node)
            node.performTouchInput { down(Offset(PRESS_INSET.toPx(), centerY)) }
            composeRule.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
            val pressed = activity.pixelNearLeftEdgeOf(node)
            node.performTouchInput { up() }

            assertEquals(Integer.toHexString(resting), Integer.toHexString(pressed))
        }
    }

    private fun launch(): AboutActivity =
        Robolectric.buildActivity(AboutActivity::class.java).setup().also { controller = it }.get()

    private fun link(@StringRes label: Int): String = application.getString(label, "", "", "", "")

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    // captureToImage() times out waiting for a redraw under Robolectric, so draw the window directly.
    private fun AboutActivity.pixelNearLeftEdgeOf(node: SemanticsNodeInteraction): Int {
        composeRule.waitForIdle()
        val decor = window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val inset = with(composeRule.density) { PRESS_INSET.toPx() }
        return bitmap.getPixel((bounds.left + inset).toInt(), bounds.center.y.toInt())
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    private companion object {
        // Below the 56dp top bar and the logo's 16dp top padding, at the 56dp logo's centre.
        val LOGO_CENTER_Y = 100.dp
        const val LOGO_HOLD_MS = 1_000L
        const val LOGO_SPIN_UP_MS = 850L

        // Inside the links' 4dp padding and the rows' 16dp text inset, so the sample is never a glyph.
        val PRESS_INSET = 2.dp
        const val PRESS_SETTLE_MS = 1_000L

        val ROWS = listOf(
            R.string.blog to R.string.simplenote_blog_url,
            R.string.twitter to R.string.about_twitter_handle,
            R.string.about_help_title to R.string.about_help_summary,
            R.string.rate_us to R.string.about_play_store,
            R.string.about_contribute_title to R.string.about_contribute_subtitle,
            R.string.about_hiring_title to R.string.about_hiring,
        )
        val LINKS = listOf(R.string.link_privacy, R.string.link_terms, R.string.link_california)
    }
}
