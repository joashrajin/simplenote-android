package org.wordpress.passcodelock

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35], qualifiers = "w360dp-h640dp-port-mdpi")
class PasscodeTextScalingTest {
    @Test
    fun keyboardButtonTextFollowsSystemFontScale() {
        val normal = inflateKeyboard(1f)
        val large = inflateKeyboard(largeFontScale())

        normal.digitButtons.forEach { button ->
            assertEquals(24f, button.textSize, TOLERANCE)
        }
        large.digitButtons.forEach { button ->
            assertEquals(sp(large.context, 24f), button.textSize, TOLERANCE)
            assertTrue(button.textSize > normal.digitButtons.first().textSize)
        }
    }

    @Test
    fun pinTextFollowsSystemFontScale() {
        val normal = inflateKeyboard(1f)
        val large = inflateKeyboard(largeFontScale())

        assertEquals(34f, normal.pin.textSize, TOLERANCE)
        assertEquals(sp(large.context, 34f), large.pin.textSize, TOLERANCE)
        assertTrue(large.pin.textSize > normal.pin.textSize)
    }

    @Test
    fun scaledPhoneLayoutKeepsPasscodeControlsContained() {
        assertScaledLayoutFits(inflateKeyboard(largeFontScale()), PHONE_WIDTH, PHONE_HEIGHT)
    }

    @Test
    @Config(sdk = [35], qualifiers = "sw600dp-w960dp-h600dp-land-mdpi")
    fun scaledTabletLandscapeLayoutKeepsPasscodeControlsContained() {
        val fixture = inflateKeyboard(2f)

        assertTrue(fixture.root.findViewById<View>(R.id.AppUnlockLinearLayout1) is RelativeLayout)
        assertScaledLayoutFits(fixture, TABLET_WIDTH, TABLET_HEIGHT)
    }

    private fun assertScaledLayoutFits(fixture: Fixture, width: Int, height: Int) {
        fixture.pin.text = "1234"

        fixture.root.measure(exactly(width), exactly(height))
        fixture.root.layout(0, 0, fixture.root.measuredWidth, fixture.root.measuredHeight)

        assertEquals(width, fixture.root.width)
        assertEquals(height, fixture.root.height)
        CONTROL_IDS.forEach { id -> assertContained(fixture.root.findViewById(id), fixture.root) }
        assertTextFits(fixture.pin)
        fixture.digitButtons.forEach { button ->
            assertTextFits(button)
        }
    }

    private fun inflateKeyboard(fontScale: Float): Fixture {
        val context = createContext(fontScale)
        val root = LayoutInflater.from(context).inflate(R.layout.app_passcode_keyboard, null) as ViewGroup
        val buttons = DIGIT_BUTTON_IDS.map { id -> root.findViewById<TextView>(id) }

        assertEquals(fontScale, context.resources.configuration.fontScale, 0f)
        return Fixture(context, root, root.findViewById(R.id.pin_field), buttons)
    }

    private fun createContext(fontScale: Float): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            this.fontScale = fontScale
        }
        return ContextThemeWrapper(
            application.createConfigurationContext(configuration),
            R.style.Theme_Simplestyle_Passcode,
        )
    }

    private fun assertContained(view: View, root: ViewGroup) {
        val bounds = Rect(0, 0, view.width, view.height)
        root.offsetDescendantRectToMyCoords(view, bounds)
        val name = root.resources.getResourceEntryName(view.id)

        assertTrue("$name should have a positive size", bounds.width() > 0 && bounds.height() > 0)
        assertTrue("$name starts outside the passcode viewport: $bounds", bounds.left >= 0 && bounds.top >= 0)
        assertTrue("$name ends outside the passcode viewport: $bounds", bounds.right <= root.width)
        assertTrue("$name ends outside the passcode viewport: $bounds", bounds.bottom <= root.height)
    }

    private fun assertTextFits(view: TextView) {
        val layout = view.layout
        val contentWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        val contentHeight = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
        val name = view.resources.getResourceEntryName(view.id)

        assertEquals("$name should remain on one line", 1, layout.lineCount)
        assertEquals(view.text.length, layout.getLineEnd(0))
        assertEquals(0, layout.getEllipsisCount(0))
        assertTrue("$name text exceeds its content width", layout.getLineWidth(0) <= contentWidth + TOLERANCE)
        assertTrue("$name text exceeds its content height", layout.height <= contentHeight)
    }

    private fun sp(context: Context, value: Float): Float {
        return TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
        }.textSize.roundToInt().toFloat()
    }

    private fun largeFontScale(): Float = if (Build.VERSION.SDK_INT >= 35) 2f else 1.3f

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private data class Fixture(
        val context: Context,
        val root: ViewGroup,
        val pin: TextView,
        val digitButtons: List<TextView>,
    )

    private companion object {
        const val PHONE_WIDTH = 360
        const val PHONE_HEIGHT = 640
        const val TABLET_WIDTH = 960
        const val TABLET_HEIGHT = 600
        const val TOLERANCE = 0.01f

        val DIGIT_BUTTON_IDS = intArrayOf(
            R.id.button0,
            R.id.button1,
            R.id.button2,
            R.id.button3,
            R.id.button4,
            R.id.button5,
            R.id.button6,
            R.id.button7,
            R.id.button8,
            R.id.button9,
        )

        val CONTROL_IDS = intArrayOf(
            R.id.passcodelock_prompt,
            R.id.AppUnlockLinearLayout1,
            R.id.pin_field,
            R.id.divider,
            R.id.tableLayout1,
            R.id.image_fingerprint,
            R.id.button_erase,
            *DIGIT_BUTTON_IDS,
        )
    }
}
