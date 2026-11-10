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
 * Pins the resolved Material dialog-surface matrix: for each harness [AppStyle] x light/dark,
 * materialAlertDialogTheme must resolve to the expected ThemeOverlay.Simplestyle.Dialog variant,
 * and that overlay's android:colorBackground / colorSurfaceContainerHigh must equal the surface
 * the legacy R.style.Dialog overlays carried (the dialog sweeps change the builder, never the
 * surface color). Each cell is asserted twice: through the activity style (ThemeUtils.getStyle,
 * the path every screen dialog resolves through) and through the widget-configure dialog theme
 * (PrefUtils.getStyleWidgetDialog, the Theme.Transparent* wrapper used by
 * NoteWidgetLight/DarkConfigureActivity).
 *
 * In night, Black and Matrix assert against background_dark_black_24 - the surface their legacy
 * Dialog.Black overlay used - which only matches the base overlay's background_dark_24 because
 * the two colors are the same value; [blackNightSurfaceEqualsBaseNightSurface] freezes that
 * identity so a drift in either color fails loudly instead of silently splitting the styles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class DialogThemeMatrixTest {

    @Test
    fun lightDefault() =
        assertDialogSurface(AppStyle.DEFAULT, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.sn_surface)

    @Test
    fun lightClassic() =
        assertDialogSurface(AppStyle.CLASSIC, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.sn_surface)

    @Test
    fun lightBlack() =
        assertDialogSurface(AppStyle.BLACK, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.sn_surface)

    @Test
    fun lightSepia() =
        assertDialogSurface(AppStyle.SEPIA, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.background_light_sepia)

    @Test
    fun lightMatrix() =
        assertDialogSurface(AppStyle.MATRIX, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.sn_surface)

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkDefault() =
        assertDialogSurface(AppStyle.DEFAULT, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.background_dark_24)

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkClassic() =
        assertDialogSurface(AppStyle.CLASSIC, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.background_dark_24)

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkBlack() =
        assertDialogSurface(AppStyle.BLACK, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.background_dark_black_24)

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkSepia() = assertDialogSurface(
        AppStyle.SEPIA,
        R.style.ThemeOverlay_Simplestyle_Dialog_Sepia,
        R.color.background_dark_sepia_24
    )

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkMatrix() =
        assertDialogSurface(AppStyle.MATRIX, R.style.ThemeOverlay_Simplestyle_Dialog, R.color.background_dark_black_24)

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun blackNightSurfaceEqualsBaseNightSurface() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(app.getColor(R.color.background_dark_24), app.getColor(R.color.background_dark_black_24))
    }

    private fun assertDialogSurface(
        style: AppStyle,
        @StyleRes expectedOverlay: Int,
        @ColorRes legacyDialogSurface: Int,
    ) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putInt(PrefUtils.PREF_STYLE_INDEX, style.prefIndex)
            .commit()

        assertOverlayAndSurface(ContextThemeWrapper(app, ThemeUtils.getStyle(app)), expectedOverlay, legacyDialogSurface)
        assertOverlayAndSurface(
            ContextThemeWrapper(app, PrefUtils.getStyleWidgetDialog(app)),
            expectedOverlay,
            legacyDialogSurface
        )
    }

    private fun assertOverlayAndSurface(
        themed: Context,
        @StyleRes expectedOverlay: Int,
        @ColorRes legacyDialogSurface: Int,
    ) {
        val resolved = TypedValue()
        assertTrue(
            "materialAlertDialogTheme did not resolve",
            themed.theme.resolveAttribute(R.attr.materialAlertDialogTheme, resolved, true)
        )
        assertEquals(expectedOverlay, resolved.resourceId)

        val dialogContext = ContextThemeWrapper(themed, resolved.resourceId)
        val expectedColor = themed.getColor(legacyDialogSurface)
        assertEquals(expectedColor, resolveColor(dialogContext, android.R.attr.colorBackground))
        assertEquals(expectedColor, resolveColor(dialogContext, R.attr.colorSurfaceContainerHigh))
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
