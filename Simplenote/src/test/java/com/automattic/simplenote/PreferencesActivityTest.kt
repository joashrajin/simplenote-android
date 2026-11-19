package com.automattic.simplenote

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.screenshottest.AppStyle
import com.automattic.simplenote.screenshottest.ScreenshotHarness
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.wordpress.passcodelock.PasscodePreferenceFragment
import org.wordpress.passcodelock.PasscodePreferenceFragmentCompat

/**
 * Pins what PreferencesActivity sets up in onCreate: its manifest entry, the toolbar as the up-enabled action bar
 * titled Settings, one transaction adding the settings fragment then the non-inflating passcode fragment into the
 * preferences container under fixed tags, and the edge-to-edge insets wiring of root, toolbar and container.
 *
 * Only the created state is driven: PreferencesFragment casts the application to Simplenote once its activity is
 * created, which HiltTestApplication is not, so onStart, onResume and the saved-state path are out of reach here.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class PreferencesActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ScreenshotHarness.applyStyle(AppStyle.DEFAULT, darkMode = false)
    }

    @Test
    fun manifestEntryKeepsItsConfigChangesParentAndTheme() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, PreferencesActivity::class.java), 0)

        val handled = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
            ActivityInfo.CONFIG_SCREEN_SIZE
        assertEquals(handled, info.configChanges and handled)
        assertEquals(NotesActivity::class.java.name, info.parentActivityName)
        assertEquals(R.style.Theme_Simplestyle, info.theme)
    }

    @Test
    fun toolbarIsTheUpEnabledActionBarTitledSettings() {
        val activity = create()

        val actionBar = activity.supportActionBar!!
        assertEquals(context.getString(R.string.settings), activity.title.toString())
        assertTrue(actionBar.displayOptions and ActionBar.DISPLAY_HOME_AS_UP != 0)
        assertSame(activity.findViewById<Toolbar>(R.id.toolbar).title, actionBar.title)
    }

    @Test
    fun addsTheSettingsThenTheNonInflatingPasscodeFragmentIntoTheContainer() {
        val activity = create()
        val fragmentManager = activity.supportFragmentManager
        fragmentManager.executePendingTransactions()

        val fragments = fragmentManager.fragments
        assertEquals(2, fragments.size)
        val preferences = fragments[0]
        val passcode = fragments[1]
        assertTrue(preferences is PreferencesFragment)
        assertTrue(passcode is PasscodePreferenceFragmentCompat)
        assertEquals("tag_preferences", preferences.tag)
        assertEquals("tag_passcode", passcode.tag)
        assertEquals(R.id.preferences_container, preferences.id)
        assertEquals(R.id.preferences_container, passcode.id)

        val passcodeArgs = passcode.requireArguments()
        assertEquals(setOf(PasscodePreferenceFragment.KEY_SHOULD_INFLATE), passcodeArgs.keySet())
        assertFalse(passcodeArgs.getBoolean(PasscodePreferenceFragment.KEY_SHOULD_INFLATE, true))
        assertEquals(null, preferences.arguments)
    }

    @Test
    fun insetsPadTheRootSidesTheToolbarTopAndTheContainerBottom() {
        val activity = create()
        val root = activity.findViewById<View>(R.id.main_parent_view)
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        val container = activity.findViewById<View>(R.id.preferences_container)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(11, 22, 33, 44))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertEquals(
            listOf(11, 0, 33, 0),
            listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        )
        assertEquals(22, (toolbar.layoutParams as MarginLayoutParams).topMargin)
        assertEquals(44, container.paddingBottom)
    }

    private fun create(): PreferencesActivity =
        Robolectric.buildActivity(PreferencesActivity::class.java).create().get()
}
