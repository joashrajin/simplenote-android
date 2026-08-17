package com.automattic.simplenote

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.StyleOverlayUtils
import com.automattic.simplenote.utils.ThemeUtils
import com.google.android.material.appbar.AppBarLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StyleOverlayTest {
    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    @Config(sdk = [35])
    fun overlayResourcesMirrorLegacyStyleBags() {
        assertOverlaySourceParity("values")
        assertOverlaySourceParity("values-night")
    }

    @Test
    @Config(sdk = [35])
    fun resolverMapsPremiumAndFallbackSelections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        STYLE_CASES.forEach { style ->
            assertEquals(style.overlay, StyleOverlayUtils.getStyleOverlay(style.index))

            preferences.edit()
                .putBoolean(PrefUtils.PREF_PREMIUM, true)
                .putInt(PrefUtils.PREF_STYLE_INDEX, style.index)
                .commit()
            assertEquals(style.overlay, StyleOverlayUtils.getStyleOverlay(context))

            preferences.edit().putBoolean(PrefUtils.PREF_PREMIUM, false).commit()
            assertEquals(DEFAULT_OVERLAY, StyleOverlayUtils.getStyleOverlay(context))
        }

        INVALID_STYLE_INDEXES.forEach { index ->
            assertEquals(DEFAULT_OVERLAY, StyleOverlayUtils.getStyleOverlay(index))
            preferences.edit()
                .putBoolean(PrefUtils.PREF_PREMIUM, true)
                .putInt(PrefUtils.PREF_STYLE_INDEX, index)
                .commit()
            assertEquals(DEFAULT_OVERLAY, StyleOverlayUtils.getStyleOverlay(context))
        }

        preferences.edit().clear().commit()
        assertEquals(DEFAULT_OVERLAY, StyleOverlayUtils.getStyleOverlay(context))
    }

    @Test
    @Config(sdk = [23, 35], qualifiers = "notnight")
    fun lightOverlayThemesMatchLegacyStyles() {
        assertResolvedThemeParity()
    }

    @Test
    @Config(sdk = [23, 35], qualifiers = "night")
    fun darkOverlayThemesMatchLegacyStyles() {
        assertResolvedThemeParity()
    }

    @Test
    @Config(sdk = [23, 35], qualifiers = "notnight")
    fun lightActivitiesApplyOverlayToActivityDecorAndComponents() {
        assertActivityMatrix(isNight = false)
    }

    @Test
    @Config(sdk = [23, 35], qualifiers = "night")
    fun darkActivitiesApplyOverlayToActivityDecorAndComponents() {
        assertActivityMatrix(isNight = true)
    }

    @Test
    @Config(sdk = [23, 35], qualifiers = "notnight")
    fun nonPremiumActivitiesUseTheDefaultOverlay() {
        STYLE_CASES.forEach { requestedStyle ->
            val controller = launchProbe(
                R.style.Theme_Simplestyle_Splash,
                requestedStyle,
                isNight = false,
                isPremium = false,
            )
            try {
                val activity = controller.get()
                val expected = newTheme(
                    activity.resources,
                    R.style.Theme_Simplestyle,
                    DEFAULT_OVERLAY,
                )
                assertAppliedStyle("non-premium ${requestedStyle.name}", activity, expected)
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    @Test
    @Config(sdk = [23, 35], qualifiers = "notnight")
    fun lightLifecycleReappliesTheCachedOverlay() {
        assertLifecycleReappliesCachedOverlay(isNight = false)
    }

    @Test
    @Config(sdk = [23, 35], qualifiers = "night")
    fun darkLifecycleReappliesTheCachedOverlay() {
        assertLifecycleReappliesCachedOverlay(isNight = true)
    }

    @Test
    @Config(sdk = [23, 35], qualifiers = "notnight")
    fun preferenceChangesRemainRecreationBased() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .clear()
            .putBoolean(PrefUtils.PREF_PREMIUM, true)
            .putInt(PrefUtils.PREF_STYLE_INDEX, ThemeUtils.STYLE_DEFAULT)
            .putString(PrefUtils.PREF_THEME, THEME_LIGHT)
            .commit()

        val controller = Robolectric.buildActivity(RecordingProbeActivity::class.java)
        controller.get().apply {
            beginThemeRecording()
            setTheme(R.style.Theme_Simplestyle_Splash)
        }
        controller.create().start().resume().visible()

        try {
            val activity = controller.get()
            val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
            preferences.edit().putInt(PrefUtils.PREF_STYLE_INDEX, ThemeUtils.STYLE_BLACK).commit()
            assertEquals("resumed style change", 1, activity.recreateCount)

            activity.recreateCount = 0
            controller.pause().stop()
            preferences.edit().putInt(PrefUtils.PREF_STYLE_INDEX, ThemeUtils.STYLE_SEPIA).commit()
            assertEquals("stopped style change", 0, activity.recreateCount)

            controller.restart()
            assertEquals("deferred style change", 1, activity.recreateCount)
            controller.start().resume()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun assertOverlaySourceParity(resourceDirectory: String) {
        val legacy = readStyles("src/main/res/$resourceDirectory/styles.xml", LEGACY_STYLE_NAMES)
        val overlays = readStyles("src/main/res/$resourceDirectory/style_overlays.xml", OVERLAY_STYLE_NAMES)

        assertEquals(OVERLAY_STYLE_NAMES, overlays.keys)
        STYLE_CASES.forEach { style ->
            val legacyItems = legacy[style.legacyName].orEmpty()
                .filterKeys { it !in SYSTEM_BAR_ITEM_NAMES }
            val overlayItems = overlays[style.overlayName].orEmpty()
            assertEquals("$resourceDirectory ${style.name}", legacyItems, overlayItems)
        }
    }

    private fun readStyles(path: String, names: Set<String>): Map<String, Map<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val styles = document.getElementsByTagName("style")
        val result = linkedMapOf<String, Map<String, String>>()

        for (index in 0 until styles.length) {
            val style = styles.item(index) as Element
            val name = style.getAttribute("name")
            if (name !in names) {
                continue
            }

            assertTrue("$path $name must declare parent=\"\"", style.hasAttribute("parent"))
            if (name.startsWith("ThemeOverlay.")) {
                assertEquals("$path $name", "", style.getAttribute("parent"))
            }

            val items = linkedMapOf<String, String>()
            val children = style.getElementsByTagName("item")
            for (itemIndex in 0 until children.length) {
                val item = children.item(itemIndex) as Element
                items[item.getAttribute("name")] = item.textContent.trim()
            }
            result[name] = items
        }

        return result
    }

    private fun assertResolvedThemeParity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        STYLE_CASES.forEach { style ->
            val legacy = newTheme(context.resources, style.legacy)
            val base = newTheme(context.resources, R.style.Theme_Simplestyle)
            val composed = newTheme(context.resources, R.style.Theme_Simplestyle, style.overlay)

            OWNED_ATTRIBUTES.forEach { attribute ->
                assertOwnedAttributeEquals(style.name, legacy, composed, attribute)
            }

            availableSystemBarAttributes().forEach { attribute ->
                assertAttributeEquals("${style.name} system bar", base, composed, attribute, resolveRefs = false)
                assertAttributeEquals("${style.name} system bar", base, composed, attribute, resolveRefs = true)
            }

            val overlayOnly = context.resources.newTheme().apply { applyStyle(style.overlay, true) }
            assertFalse(
                "${style.name} overlay must not inherit Theme.Simplestyle",
                overlayOnly.resolveAttribute(R.attr.noteTitleColor, TypedValue(), false),
            )
        }
    }

    private fun assertActivityMatrix(isNight: Boolean) {
        INITIAL_THEMES.forEach { initialTheme ->
            STYLE_CASES.forEach { style ->
                val controller = launchProbe(initialTheme, style, isNight)
                try {
                    val activity = controller.get()
                    val expected = newTheme(activity.resources, R.style.Theme_Simplestyle, style.overlay)

                    assertEquals(
                        "${style.name} must register Theme.Simplestyle exactly once",
                        1,
                        activity.recordedThemes.count { it == R.style.Theme_Simplestyle },
                    )
                    assertThemeEquals("${style.name} activity", expected, activity.theme)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        assertNotSame(
                            "${style.name} should exercise the DecorContext theme branch",
                            activity.theme,
                            activity.window.decorView.context.theme,
                        )
                    }
                    assertThemeEquals(
                        "${style.name} decor",
                        expected,
                        activity.window.decorView.context.theme,
                    )
                    assertColorDrawableEquals(
                        "${style.name} window background",
                        resolvedColor(expected, android.R.attr.windowBackground),
                        shadowOf(activity.window).backgroundDrawable,
                    )
                    assertToolbarAndActionMode(activity, expected, style.name)
                } finally {
                    controller.pause().stop().destroy()
                }
            }
        }
    }

    private fun launchProbe(
        initialTheme: Int,
        style: StyleCase,
        isNight: Boolean,
        isPremium: Boolean = true,
    ): ActivityController<ProbeActivity> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .clear()
            .putBoolean(PrefUtils.PREF_PREMIUM, isPremium)
            .putInt(PrefUtils.PREF_STYLE_INDEX, style.index)
            .putString(PrefUtils.PREF_THEME, if (isNight) THEME_DARK else THEME_LIGHT)
            .commit()

        val controller = Robolectric.buildActivity(ProbeActivity::class.java)
        controller.get().apply {
            beginThemeRecording()
            setTheme(initialTheme)
        }
        return controller.create().start().resume().visible()
    }

    private fun launchProbeWithCurrentPreferences(initialTheme: Int): ActivityController<ProbeActivity> {
        val controller = Robolectric.buildActivity(ProbeActivity::class.java)
        controller.get().apply {
            beginThemeRecording()
            setTheme(initialTheme)
        }
        return controller.create().start().resume().visible()
    }

    private fun assertLifecycleReappliesCachedOverlay(isNight: Boolean) {
        STYLE_CASES.forEachIndexed { index, style ->
            val controller = launchProbe(R.style.Theme_Simplestyle_Splash, style, isNight)
            val activity = controller.get()
            val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
            val nextStyle = STYLE_CASES[(index + 1) % STYLE_CASES.size]
            preferences.unregisterOnSharedPreferenceChangeListener(activity)
            preferences.edit().putInt(PrefUtils.PREF_STYLE_INDEX, nextStyle.index).commit()

            try {
                val expected = newTheme(activity.resources, R.style.Theme_Simplestyle, style.overlay)

                clobberAppliedTheme(activity)
                activity.onConfigurationChanged(Configuration(activity.resources.configuration))
                assertAppliedStyle("${style.name} configuration", activity, expected)

                controller.pause().stop()
                clobberAppliedTheme(activity)
                controller.restart().start().resume()
                assertAppliedStyle("${style.name} restart", activity, expected)
            } finally {
                controller.pause().stop().destroy()
            }

            val freshController = launchProbeWithCurrentPreferences(R.style.Theme_Simplestyle_Splash)
            try {
                val freshActivity = freshController.get()
                val expected = newTheme(
                    freshActivity.resources,
                    R.style.Theme_Simplestyle,
                    nextStyle.overlay,
                )
                assertAppliedStyle("${style.name} fresh activity", freshActivity, expected)
            } finally {
                freshController.pause().stop().destroy()
            }
        }
    }

    private fun clobberAppliedTheme(activity: ProbeActivity) {
        val base = newTheme(activity.resources, R.style.Theme_Simplestyle)
        activity.theme.setTo(base)
        val decorTheme = activity.window.decorView.context.theme
        if (decorTheme !== activity.theme) {
            decorTheme.setTo(base)
        }
        activity.window.setBackgroundDrawable(ColorDrawable(Color.MAGENTA))
    }

    private fun assertAppliedStyle(
        label: String,
        activity: ProbeActivity,
        expected: Resources.Theme,
    ) {
        assertThemeEquals("$label activity", expected, activity.theme)
        assertThemeEquals("$label decor", expected, activity.window.decorView.context.theme)
        assertColorDrawableEquals(
            "$label window background",
            resolvedColor(expected, android.R.attr.windowBackground),
            shadowOf(activity.window).backgroundDrawable,
        )
    }

    private fun assertToolbarAndActionMode(
        activity: ProbeActivity,
        expected: Resources.Theme,
        label: String,
    ) {
        assertColorDrawableEquals(
            "$label toolbar background",
            resolvedColor(expected, R.attr.toolbarColor),
            activity.toolbar.background,
        )
        assertEquals(
            "$label toolbar popup theme",
            resolvedResource(expected, R.attr.toolbarPopupTheme),
            activity.toolbar.popupTheme,
        )

        activity.toolbar.inflateMenu(R.menu.tags_list)
        val searchView = requireNotNull(
            activity.toolbar.menu.findItem(R.id.menu_search).actionView as? SearchView,
        ) { "$label search action view" }
        assertRepresentativeAttributes("$label search context", expected, searchView.context.theme)

        val actionMode = activity.startSupportActionMode(TestActionModeCallback())
        assertNotNull("$label action mode", actionMode)
        val actionModeBar = requireNotNull(
            activity.findViewById<View>(androidx.appcompat.R.id.action_mode_bar),
        ) { "$label action mode bar" }
        assertRepresentativeAttributes("$label action mode context", expected, actionModeBar.context.theme)
        assertColorDrawableEquals(
            "$label action mode background",
            resolvedColor(expected, R.attr.actionModeBackgroundColor),
            actionModeBar.background,
        )
        actionMode?.finish()
    }

    private fun assertThemeEquals(
        label: String,
        expected: Resources.Theme,
        actual: Resources.Theme,
    ) {
        OWNED_ATTRIBUTES.forEach { attribute ->
            assertOwnedAttributeEquals(label, expected, actual, attribute)
        }
    }

    private fun assertRepresentativeAttributes(
        label: String,
        expected: Resources.Theme,
        actual: Resources.Theme,
    ) {
        REPRESENTATIVE_COMPONENT_ATTRIBUTES.forEach { attribute ->
            assertOwnedAttributeEquals(label, expected, actual, attribute)
        }
    }

    private fun assertOwnedAttributeEquals(
        label: String,
        expected: Resources.Theme,
        actual: Resources.Theme,
        attribute: Int,
    ) {
        assertAttributeEquals(label, expected, actual, attribute, resolveRefs = false)
        if (attribute !in RESOURCE_ID_ATTRIBUTES) {
            assertAttributeEquals(label, expected, actual, attribute, resolveRefs = true)
        }
    }

    private fun availableSystemBarAttributes(): IntArray =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SYSTEM_BAR_ATTRIBUTES
        } else {
            SYSTEM_BAR_ATTRIBUTES.filter { it != android.R.attr.windowLightNavigationBar }.toIntArray()
        }

    private fun assertAttributeEquals(
        label: String,
        expected: Resources.Theme,
        actual: Resources.Theme,
        attribute: Int,
        resolveRefs: Boolean,
    ) {
        assertEquals(
            "$label attr=${attribute.toString(16)} resolveRefs=$resolveRefs",
            snapshot(expected, attribute, resolveRefs),
            snapshot(actual, attribute, resolveRefs),
        )
    }

    private fun snapshot(
        theme: Resources.Theme,
        attribute: Int,
        resolveRefs: Boolean,
    ): AttributeSnapshot {
        val value = TypedValue()
        val present = theme.resolveAttribute(attribute, value, resolveRefs)
        return if (present) {
            AttributeSnapshot(
                present = true,
                type = value.type,
                data = value.data,
                resourceId = value.resourceId,
                string = value.string?.toString(),
            )
        } else {
            AttributeSnapshot(present = false)
        }
    }

    private fun newTheme(resources: Resources, vararg styles: Int): Resources.Theme =
        resources.newTheme().apply {
            styles.forEach { applyStyle(it, true) }
        }

    private fun resolvedColor(theme: Resources.Theme, attribute: Int): Int {
        val values = theme.obtainStyledAttributes(intArrayOf(attribute))
        return try {
            values.getColor(0, Color.TRANSPARENT)
        } finally {
            values.recycle()
        }
    }

    private fun resolvedResource(theme: Resources.Theme, attribute: Int): Int {
        val values = theme.obtainStyledAttributes(intArrayOf(attribute))
        return try {
            values.getResourceId(0, 0)
        } finally {
            values.recycle()
        }
    }

    private fun assertColorDrawableEquals(label: String, expected: Int, actual: Any?) {
        assertTrue("$label must be a ColorDrawable but was $actual", actual is ColorDrawable)
        assertEquals(label, expected, (actual as ColorDrawable).color)
    }

    open class ProbeActivity : ThemedAppCompatActivity() {
        private var themeCalls: MutableList<Int>? = null
        val recordedThemes: List<Int>
            get() = themeCalls.orEmpty()

        lateinit var toolbar: Toolbar
            private set

        fun beginThemeRecording() {
            themeCalls = mutableListOf()
        }

        override fun setTheme(resid: Int) {
            themeCalls?.add(resid)
            super.setTheme(resid)
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val appBar = layoutInflater.inflate(R.layout.toolbar, null) as AppBarLayout
            setContentView(appBar)
            toolbar = appBar.findViewById(R.id.toolbar)
        }
    }

    class RecordingProbeActivity : ProbeActivity() {
        var recreateCount = 0

        override fun recreate() {
            recreateCount++
        }
    }

    private class TestActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.bulk_edit, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

    private data class StyleCase(
        val name: String,
        val index: Int,
        val legacy: Int,
        val legacyName: String,
        val overlay: Int,
        val overlayName: String,
    )

    private data class AttributeSnapshot(
        val present: Boolean,
        val type: Int = 0,
        val data: Int = 0,
        val resourceId: Int = 0,
        val string: String? = null,
    )

    companion object {
        private const val THEME_LIGHT = "0"
        private const val THEME_DARK = "1"

        private val STYLE_CASES = listOf(
            StyleCase(
                "Default",
                ThemeUtils.STYLE_DEFAULT,
                R.style.Style_Default,
                "Style.Default",
                R.style.ThemeOverlay_Simplestyle_Style_Default,
                "ThemeOverlay.Simplestyle.Style.Default",
            ),
            StyleCase(
                "Classic",
                ThemeUtils.STYLE_CLASSIC,
                R.style.Style_Classic,
                "Style.Classic",
                R.style.ThemeOverlay_Simplestyle_Style_Classic,
                "ThemeOverlay.Simplestyle.Style.Classic",
            ),
            StyleCase(
                "Black",
                ThemeUtils.STYLE_BLACK,
                R.style.Style_Black,
                "Style.Black",
                R.style.ThemeOverlay_Simplestyle_Style_Black,
                "ThemeOverlay.Simplestyle.Style.Black",
            ),
            StyleCase(
                "Matrix",
                ThemeUtils.STYLE_MATRIX,
                R.style.Style_Matrix,
                "Style.Matrix",
                R.style.ThemeOverlay_Simplestyle_Style_Matrix,
                "ThemeOverlay.Simplestyle.Style.Matrix",
            ),
            StyleCase(
                "Mono",
                ThemeUtils.STYLE_MONO,
                R.style.Style_Mono,
                "Style.Mono",
                R.style.ThemeOverlay_Simplestyle_Style_Mono,
                "ThemeOverlay.Simplestyle.Style.Mono",
            ),
            StyleCase(
                "Publication",
                ThemeUtils.STYLE_PUBLICATION,
                R.style.Style_Publication,
                "Style.Publication",
                R.style.ThemeOverlay_Simplestyle_Style_Publication,
                "ThemeOverlay.Simplestyle.Style.Publication",
            ),
            StyleCase(
                "Sepia",
                ThemeUtils.STYLE_SEPIA,
                R.style.Style_Sepia,
                "Style.Sepia",
                R.style.ThemeOverlay_Simplestyle_Style_Sepia,
                "ThemeOverlay.Simplestyle.Style.Sepia",
            ),
        )

        private val LEGACY_STYLE_NAMES = STYLE_CASES.mapTo(linkedSetOf()) { it.legacyName }
        private val OVERLAY_STYLE_NAMES = STYLE_CASES.mapTo(linkedSetOf()) { it.overlayName }
        private val DEFAULT_OVERLAY = R.style.ThemeOverlay_Simplestyle_Style_Default
        private val INVALID_STYLE_INDEXES = intArrayOf(-1, 7, Int.MAX_VALUE)
        private val INITIAL_THEMES = intArrayOf(
            R.style.Theme_Simplestyle_Splash,
            R.style.Style_Authentication,
        )

        private val SYSTEM_BAR_ITEM_NAMES = setOf(
            "android:navigationBarColor",
            "android:statusBarColor",
            "android:windowDrawsSystemBarBackgrounds",
            "android:windowLightNavigationBar",
            "android:windowLightStatusBar",
        )

        private val OWNED_ATTRIBUTES = intArrayOf(
            android.R.attr.alertDialogTheme,
            android.R.attr.windowBackground,
            R.attr.actionModeBackgroundColor,
            R.attr.actionModeCloseButtonStyle,
            R.attr.actionModeTextColor,
            R.attr.alertDialogTheme,
            R.attr.chipCheckedOffBackgroundColor,
            R.attr.chipCheckedOnBackgroundColor,
            R.attr.colorAccent,
            R.attr.colorPrimary,
            R.attr.colorPrimaryDark,
            R.attr.drawerBackgroundColor,
            R.attr.drawerBackgroundSelector,
            R.attr.editorSearchHighlightBackgroundColor,
            R.attr.editorSearchHighlightForegroundColor,
            R.attr.emptyImageColor,
            R.attr.fabColor,
            R.attr.fabIconColor,
            R.attr.iconTintColor,
            R.attr.listBackgroundSelector,
            R.attr.listSearchHighlightBackgroundColor,
            R.attr.listSearchHighlightForegroundColor,
            R.attr.mainBackgroundColor,
            R.attr.noteEditorTextColor,
            R.attr.sheetBackgroundColor,
            R.attr.styleFontFamily,
            R.attr.toolbarColor,
            R.attr.toolbarIconColor,
            R.attr.toolbarPopupTheme,
        )

        private val SYSTEM_BAR_ATTRIBUTES = intArrayOf(
            android.R.attr.navigationBarColor,
            android.R.attr.statusBarColor,
            android.R.attr.windowDrawsSystemBarBackgrounds,
            android.R.attr.windowLightNavigationBar,
            android.R.attr.windowLightStatusBar,
        )

        private val REPRESENTATIVE_COMPONENT_ATTRIBUTES = intArrayOf(
            R.attr.actionModeBackgroundColor,
            R.attr.actionModeCloseButtonStyle,
            R.attr.actionModeTextColor,
            R.attr.colorAccent,
            R.attr.toolbarColor,
            R.attr.toolbarIconColor,
            R.attr.toolbarPopupTheme,
        )

        private val RESOURCE_ID_ATTRIBUTES = intArrayOf(
            android.R.attr.alertDialogTheme,
            R.attr.actionModeCloseButtonStyle,
            R.attr.alertDialogTheme,
            R.attr.drawerBackgroundSelector,
            R.attr.listBackgroundSelector,
            R.attr.toolbarPopupTheme,
        )
    }
}
