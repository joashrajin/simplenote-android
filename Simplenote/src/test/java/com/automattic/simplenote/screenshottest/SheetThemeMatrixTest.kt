package com.automattic.simplenote.screenshottest

import android.app.Application
import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.ThemeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the resolved Material 3 bottom-sheet matrix that replaces the deleted per-style sheet
 * themes: for each harness [AppStyle] x light/dark, bottomSheetDialogTheme must resolve to the
 * expected ThemeOverlay.Simplestyle.BottomSheetDialog variant, and the sheet surface the overlay
 * carries (colorSurfaceContainerLow, the Material 3 modal sheet container color) must equal the
 * documented per-style surface. Light cells and the night Black/Matrix/Sepia overrides equal the
 * legacy sheetBackgroundColor exactly; the base night cells pin the deliberate one-step shift
 * from the legacy background_dark_8 to the shared ladder's background_dark_4.
 *
 * The width clamp the base fragment used to apply by hand is pinned too: every cell's
 * bottomSheetStyle must be Widget.Simplenote.BottomSheet, whose android:maxWidth is the
 * per-qualifier bottom_sheet_max_width (unclamped on the phone device profile; 360dp/600dp on
 * large portrait/landscape, the values the legacy clamp read from the library width_layout
 * dimension).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class SheetThemeMatrixTest {

    @Test
    fun lightDefault() =
        assertSheetSurface(AppStyle.DEFAULT, R.style.ThemeOverlay_Simplestyle_BottomSheetDialog, R.color.background_light)

    @Test
    fun lightClassic() =
        assertSheetSurface(AppStyle.CLASSIC, R.style.ThemeOverlay_Simplestyle_BottomSheetDialog, R.color.background_light)

    @Test
    fun lightBlack() =
        assertSheetSurface(AppStyle.BLACK, R.style.ThemeOverlay_Simplestyle_BottomSheetDialog, R.color.background_light)

    @Test
    fun lightSepia() = assertSheetSurface(
        AppStyle.SEPIA,
        R.style.ThemeOverlay_Simplestyle_BottomSheetDialog,
        R.color.background_light_sepia
    )

    @Test
    fun lightMatrix() =
        assertSheetSurface(AppStyle.MATRIX, R.style.ThemeOverlay_Simplestyle_BottomSheetDialog, R.color.background_light)

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkDefault() = assertSheetSurface(
        AppStyle.DEFAULT,
        R.style.ThemeOverlay_Simplestyle_BottomSheetDialog,
        R.color.background_dark_4
    )

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkClassic() = assertSheetSurface(
        AppStyle.CLASSIC,
        R.style.ThemeOverlay_Simplestyle_BottomSheetDialog,
        R.color.background_dark_4
    )

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkBlack() = assertSheetSurface(
        AppStyle.BLACK,
        R.style.ThemeOverlay_Simplestyle_BottomSheetDialog_Black,
        R.color.background_dark_black_8
    )

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkMatrix() = assertSheetSurface(
        AppStyle.MATRIX,
        R.style.ThemeOverlay_Simplestyle_BottomSheetDialog_Black,
        R.color.background_dark_black_8
    )

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkSepia() = assertSheetSurface(
        AppStyle.SEPIA,
        R.style.ThemeOverlay_Simplestyle_BottomSheetDialog_Sepia,
        R.color.background_dark_sepia_8
    )

    @Test
    fun phoneWidthUnclamped() = assertSheetMaxWidth(-1)

    @Test
    @Config(qualifiers = "w800dp-h1280dp-large-xhdpi")
    fun largePortraitWidthClamp() = assertSheetMaxWidth(360)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-large-land-xhdpi")
    fun largeLandscapeWidthClamp() = assertSheetMaxWidth(600)

    private fun assertSheetSurface(
        style: AppStyle,
        @StyleRes expectedOverlay: Int,
        @ColorRes expectedSheetSurface: Int,
    ) {
        val themed = themedContext(style)
        val resolved = TypedValue()
        assertTrue(
            "bottomSheetDialogTheme did not resolve",
            themed.theme.resolveAttribute(R.attr.bottomSheetDialogTheme, resolved, true)
        )
        assertEquals(expectedOverlay, resolved.resourceId)

        val sheetContext = ContextThemeWrapper(themed, resolved.resourceId)
        assertEquals(
            themed.getColor(expectedSheetSurface),
            resolveColor(sheetContext, R.attr.colorSurfaceContainerLow)
        )

        val sheetStyle = TypedValue()
        assertTrue(
            "bottomSheetStyle did not resolve",
            sheetContext.theme.resolveAttribute(R.attr.bottomSheetStyle, sheetStyle, true)
        )
        assertEquals(R.style.Widget_Simplenote_BottomSheet, sheetStyle.resourceId)
    }

    private fun assertSheetMaxWidth(expectedDp: Int) {
        val themed = themedContext(AppStyle.DEFAULT)
        val density = themed.resources.displayMetrics.density
        val expectedPixels = if (expectedDp < 0) expectedDp else (expectedDp * density).toInt()

        val attrs = themed.obtainStyledAttributes(
            R.style.Widget_Simplenote_BottomSheet,
            intArrayOf(android.R.attr.maxWidth)
        )
        try {
            assertEquals(expectedPixels, attrs.getDimensionPixelSize(0, 0))
        } finally {
            attrs.recycle()
        }
    }

    private fun themedContext(style: AppStyle): ContextThemeWrapper {
        val app = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putInt(PrefUtils.PREF_STYLE_INDEX, style.prefIndex)
            .commit()
        return ContextThemeWrapper(app, ThemeUtils.getStyle(app))
    }

    private fun resolveColor(context: Context, @AttrRes attr: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attr))
        try {
            return values.getColor(0, 1)
        } finally {
            values.recycle()
        }
    }
}
