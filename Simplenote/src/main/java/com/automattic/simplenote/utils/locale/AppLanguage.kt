package com.automattic.simplenote.utils.locale

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.Preference
import com.automattic.simplenote.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.xmlpull.v1.XmlPullParser
import java.text.Collator
import java.util.Locale

/**
 * The "App language" setting: Android's per-app language page on 33+, and an AppCompat-backed picker below it,
 * where `autoStoreLocales` in the manifest persists the choice across restarts.
 */
object AppLanguage {
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

    @JvmStatic
    fun bind(preference: Preference, activity: Activity) {
        preference.summary = currentLanguageName(activity)
        preference.setOnPreferenceClickListener {
            open(activity)
            true
        }
    }

    /** Returns the picker it shows, or null once the system page took over. */
    @JvmStatic
    fun open(activity: Activity): AlertDialog? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                activity.startActivity(systemSettingsIntent(activity))
                return null
            } catch (_: ActivityNotFoundException) {
                // Builds without the page fall back to the AppCompat picker, which works on every API level.
            }
        }
        return showPicker(activity)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal fun systemSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))

    internal fun currentLanguageName(context: Context): String {
        val current = AppCompatDelegate.getApplicationLocales()[0]
        return if (current == null) context.getString(R.string.app_language_system_default) else displayName(current)
    }

    /** The locales in `res/xml/locales_config.xml`, the same list Android 13+ shows, in the file's order. */
    internal fun supportedLocales(context: Context): List<Locale> {
        val tags = mutableListOf<String>()
        context.resources.getXml(R.xml.locales_config).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NAMESPACE, "name")?.let(tags::add)
                }
            }
        }
        return tags.map(Locale::forLanguageTag)
    }

    internal fun displayName(locale: Locale): String =
        locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }

    private fun showPicker(activity: Activity): AlertDialog {
        val collator = Collator.getInstance()
        val locales = listOf<Locale?>(null) + supportedLocales(activity).sortedWith { a, b ->
            collator.compare(displayName(a), displayName(b))
        }
        val names = locales.map { locale ->
            if (locale == null) activity.getString(R.string.app_language_system_default) else displayName(locale)
        }
        val current = AppCompatDelegate.getApplicationLocales()[0]
        val checked = locales.indexOfFirst { it?.toLanguageTag() == current?.toLanguageTag() }

        return MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_language)
            .setSingleChoiceItems(names.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                val chosen = locales[which]
                AppCompatDelegate.setApplicationLocales(
                    if (chosen == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.create(chosen)
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
