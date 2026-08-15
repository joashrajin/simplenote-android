package com.automattic.simplenote.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.automattic.simplenote.R

/**
 * The single system-bar path. Activity windows go edge to edge via [applyEdgeToEdge] and derive
 * their status/navigation bar appearance from the resolved theme, replacing the per-API theme
 * forks (values-v27, values-night-v27, values-v35, values-night-v35) that used to carry
 * statusBarColor/navigationBarColor/windowLight* items per platform version. The bars are
 * transparent, so the window background — which every theme's bar color equaled — shows through.
 */
object SystemBarUtils {
    // androidx.activity's default dark scrim: navigation bar icons are always white before
    // API 26, so a light theme keeps a translucent dark scrim behind them there.
    private val NAVIGATION_BAR_DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

    /**
     * Goes edge to edge with transparent system bars whose icon appearance follows the theme:
     * dark icons on light themes, light icons on dark themes. Pass [lightSystemBars] explicitly
     * for windows whose bar area does not match the theme's lightness (e.g. the blue About
     * screen).
     */
    @JvmStatic
    @JvmOverloads
    fun applyEdgeToEdge(activity: ComponentActivity, lightSystemBars: Boolean = isLightTheme(activity)) {
        val style = if (lightSystemBars) {
            SystemBarStyle.light(Color.TRANSPARENT, NAVIGATION_BAR_DARK_SCRIM)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /**
     * Standard insets wiring for a toolbar screen: the root absorbs cutout/horizontal insets,
     * the toolbar drops below the status bar, and the content keeps clear of the navigation bar
     * and the IME. [keepContentBottomPadding] keeps the content view's own bottom padding as a
     * floor instead of overwriting it with the inset.
     */
    @JvmStatic
    @JvmOverloads
    fun applyInsets(
        rootView: View?,
        toolbar: Toolbar?,
        contentView: View?,
        keepContentBottomPadding: Boolean = false
    ) {
        rootView?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                // Horizontal insets only: the toolbar and content handle top and bottom.
                view.setPadding(systemBars.left, 0, systemBars.right, 0)
                windowInsets
            }
        }
        toolbar?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                (view.layoutParams as? MarginLayoutParams)?.let { params ->
                    params.topMargin = systemBars.top
                    view.layoutParams = params
                }
                windowInsets
            }
        }
        contentView?.let {
            val minimumBottomPadding = if (keepContentBottomPadding) it.paddingBottom else 0
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    maxOf(systemBars.bottom, ime.bottom, minimumBottomPadding)
                )
                // Pass the IME insets through so scrolling children can react to the keyboard.
                windowInsets.inset(0, 0, 0, systemBars.bottom)
            }
        }
    }

    /**
     * Bottom sheet windows are separate from the activity, so they reproduce the deleted sheet
     * themes' navigation bar directly: a sheet-colored bar with dark icons for light themes on
     * API 27-34 (values-night still paints it for dark themes, and API 35+ ignores bar colors).
     */
    @JvmStatic
    fun applyToSheet(dialog: Dialog) {
        val window = dialog.window ?: return
        val lightNavigationBar = isLightTheme(dialog.context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
        if (lightNavigationBar && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.navigationBarColor =
                ThemeUtils.getColorFromAttribute(dialog.context, R.attr.sheetBackgroundColor)
        }
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = lightNavigationBar
    }

    private fun isLightTheme(context: Context): Boolean {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(R.attr.isLightTheme, value, true)) {
            value.data != 0
        } else {
            ThemeUtils.isLightTheme(context)
        }
    }
}
