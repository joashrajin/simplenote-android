package com.automattic.simplenote.utils.locale

import android.app.Application
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.automattic.simplenote.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [32])
class AppLanguageTest {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).get().apply {
        setTheme(R.style.Theme_Simplestyle)
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun supportedLocalesMatchTheGeneratedLocaleConfig() {
        val tags = AppLanguage.supportedLocales(activity).map(Locale::toLanguageTag)

        // toLanguageTag reports the modern codes for the iw and in folders.
        val folderTags = LocaleFoldersTest.EXPECTED_FOLDER_TAGS.map { mapOf("iw" to "he", "in" to "id")[it] ?: it }
        assertEquals(listOf("en-US") + folderTags.sorted(), listOf(tags.first()) + tags.drop(1).sorted())
    }

    @Test
    fun belowAndroid13OpensAPickerOfSystemDefaultThenEachLanguageInItsOwnName() {
        val dialog = openPicker()

        val names = itemNames(dialog)
        assertEquals("System default", names.first())
        assertEquals(1 + AppLanguage.supportedLocales(activity).size, names.size)
        listOf("Français", "Deutsch", "עברית", "English (United States)").forEach { name ->
            assertEquals(name, 1, names.count { it == name })
        }
        assertEquals(0, dialog.listView.checkedItemPosition)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun pickingALanguageAppliesItThroughAppCompat() {
        val dialog = openPicker()

        clickItem(dialog, itemNames(dialog).indexOf("Français"))

        assertEquals("fr", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertFalse(dialog.isShowing)
    }

    @Test
    fun pickingSystemDefaultClearsTheAppLanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("de"))
        val dialog = openPicker()
        assertEquals(itemNames(dialog).indexOf("Deutsch"), dialog.listView.checkedItemPosition)

        clickItem(dialog, 0)

        assertEquals(true, AppCompatDelegate.getApplicationLocales().isEmpty)
    }

    @Test
    fun summaryNamesTheCurrentChoice() {
        assertEquals("System default", AppLanguage.currentLanguageName(activity))

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("pt-BR"))

        assertEquals("Português (Brasil)", AppLanguage.currentLanguageName(activity))
    }

    @Test
    @Config(sdk = [33, 35])
    fun fromAndroid13OpensTheSystemPerAppLanguagePage() {
        assertNull(AppLanguage.open(activity))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_LOCALE_SETTINGS, started.action)
        assertEquals(Uri.fromParts("package", activity.packageName, null), started.data)
    }

    private fun openPicker(): AlertDialog {
        val dialog = AppLanguage.open(activity)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(dialog)
        return dialog!!
    }

    private fun itemNames(dialog: AlertDialog): List<String> {
        val adapter = dialog.listView.adapter
        return (0 until adapter.count).map { adapter.getItem(it).toString() }
    }

    private fun clickItem(dialog: AlertDialog, position: Int) {
        val list = dialog.listView
        list.performItemClick(list.adapter.getView(position, null, list), position, list.adapter.getItemId(position))
        shadowOf(Looper.getMainLooper()).idle()
    }
}
