package com.automattic.simplenote.screenshottest

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.ThemeUtils
import org.robolectric.Shadows.shadowOf
import org.wordpress.passcodelock.AppLockManager
import org.wordpress.passcodelock.DefaultAppLock
import java.time.Duration

/**
 * The five user-facing app styles, mapped to the [PrefUtils.PREF_STYLE_INDEX] values that
 * [ThemeUtils.getStyle] resolves to a full activity theme.
 */
enum class AppStyle(val prefIndex: Int) {
    DEFAULT(ThemeUtils.STYLE_DEFAULT),
    CLASSIC(ThemeUtils.STYLE_CLASSIC),
    BLACK(ThemeUtils.STYLE_BLACK),
    SEPIA(ThemeUtils.STYLE_SEPIA),
    MATRIX(ThemeUtils.STYLE_MATRIX),
}

/**
 * Style-aware launcher for Roborazzi screenshot tests.
 *
 * Screenshot tests live in this package, isolated from the plain-JVM suite by package: they are
 * plain Robolectric JUnit tests picked up by testDebugUnitTest (where Roborazzi captures are
 * no-ops), and only record/verify goldens when run through recordRoborazziDebug or
 * verifyRoborazziDebug. Goldens live in Simplenote/src/test/screenshots.
 *
 * Test classes must be annotated with @GraphicsMode(GraphicsMode.Mode.NATIVE) and
 * @Config(qualifiers = [DEVICE]); dark-mode test methods add @Config(qualifiers = [NIGHT]) so the
 * Robolectric configuration matches the persisted in-app theme preference.
 */
object ScreenshotHarness {
    /** Fixed portrait-phone configuration so goldens are stable across host machines. */
    const val DEVICE = "w360dp-h740dp-xhdpi"

    /** Appends the night qualifier to [DEVICE]; use on dark-mode test methods. */
    const val NIGHT = "+night"

    // Values of ThemeUtils.THEME_LIGHT / THEME_DARK (private there). PREF_THEME is written as a
    // string because PrefUtils.getIntPref round-trips through getStringPref.
    private const val THEME_LIGHT = "0"
    private const val THEME_DARK = "1"

    private const val GOLDEN_DIR = "src/test/screenshots"

    /** Deterministic tag fixture served by [ScreenshotTagsRepository]. */
    val SAMPLE_TAG_ITEMS: List<TagItem> = listOf(
        TagItem(tag("recipes", "Recipes"), 12),
        TagItem(tag("travel", "Travel"), 4),
        TagItem(tag("work", "Work"), 0),
    )

    /** Path of a golden image, relative to the module directory the test JVM runs in. */
    fun goldenFilePath(goldenName: String): String = "$GOLDEN_DIR/$goldenName.png"

    /**
     * Persists the app style and light/dark theme exactly the way the app reads them on activity
     * launch (ThemedAppCompatActivity -> ThemeUtils.setTheme + ThemeUtils.getStyle), so any
     * activity launched afterwards inflates under that style.
     */
    fun applyStyle(style: AppStyle, darkMode: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PrefUtils.PREF_PREMIUM, true)
            .putInt(PrefUtils.PREF_STYLE_INDEX, style.prefIndex)
            .putString(PrefUtils.PREF_THEME, if (darkMode) THEME_DARK else THEME_LIGHT)
            .commit()
        // DisplayUtils.disableScreenshotsIfLocked (called from onResume in themed activities)
        // NPEs unless an app lock is installed; Simplenote.onCreate normally installs one.
        AppLockManager.getInstance().setCurrentAppLock(DefaultAppLock(context as Application))
    }

    /** Launches [activityClass] under [style] x [darkMode] and settles rendering. */
    fun <A : Activity> launchSettled(
        activityClass: Class<A>,
        style: AppStyle,
        darkMode: Boolean,
    ): ActivityScenario<A> {
        applyStyle(style, darkMode)
        val scenario = ActivityScenario.launch(activityClass)
        settle()
        return scenario
    }

    /**
     * Runs the main looper with the Robolectric clock advancing so pending coroutines, LiveData
     * dispatches, and view animations (e.g. RecyclerView item-appearance animations) complete
     * deterministically before capture.
     */
    fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
    }

    private fun tag(key: String, name: String): Tag = Tag(key).apply { setName(name) }
}
