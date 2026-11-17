package com.automattic.simplenote.utils

/*
 *  misc. routines for Simplenote preferences
 *  added 01-Apr-2013 by Nick Bradbury
 */

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.IntDef
import androidx.preference.PreferenceManager
import com.automattic.simplenote.BuildConfig
import com.automattic.simplenote.R
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.utils.ThemeUtils.STYLE_BLACK
import com.automattic.simplenote.utils.ThemeUtils.STYLE_CLASSIC
import com.automattic.simplenote.utils.ThemeUtils.STYLE_DEFAULT
import com.automattic.simplenote.utils.ThemeUtils.STYLE_MATRIX
import com.automattic.simplenote.utils.ThemeUtils.STYLE_MONO
import com.automattic.simplenote.utils.ThemeUtils.STYLE_PUBLICATION
import com.automattic.simplenote.utils.ThemeUtils.STYLE_SEPIA
import com.simperium.client.Query

@Suppress("unused")
object PrefUtils {
    // key names for various preferences - note that these same key names are hard-coded in Preferences.xml

    // integer, determines note sort order
    const val PREF_SORT_ORDER = "pref_key_sort_order"

    // boolean, determines # of preview lines
    const val PREF_CONDENSED_LIST = "pref_key_condensed_note_list"

    // boolean, determines whether to sort the tags list alphabetically
    const val PREF_SORT_TAGS_ALPHA = "pref_key_sort_tags_alpha"

    // boolean, determines whether dates are shown
    const val PREF_SHOW_DATES = "pref_key_show_dates"

    // int, preferred font size
    private const val PREF_FONT_SIZE = "pref_key_font_size"

    // boolean, determines linkifying content in the editor
    const val PREF_DETECT_LINKS = "pref_key_detect_links"

    // boolean, set on first launch
    const val PREF_FIRST_LAUNCH = "pref_key_first_launch"

    // boolean, set to require an account to access the app
    const val PREF_ACCOUNT_REQUIRED = "pref_key_account_required"

    // boolean, set on when user taps to just try the app in the welcome view
    const val PREF_APP_TRIAL = "pref_key_app_trial"

    // boolean, allow notes to preview markdown
    const val PREF_MARKDOWN_ENABLED = "pref_key_markdown_enabled"

    // string. index style to use
    const val PREF_STYLE_INDEX = "pref_key_style_index"

    // string. determines theme to use
    const val PREF_THEME = "pref_key_theme"

    // boolean, determines if the theme was ever changed
    const val PREF_THEME_MODIFIED = "pref_theme_modified"

    // string. WordPress.com access token
    const val PREF_WP_TOKEN = "pref_key_wp_token"

    // boolean. determines if analytics is enabled
    const val PREF_ANALYTICS_ENABLED = "pref_key_analytics_enabled"

    // string. json array of sites used to publish to WordPress
    const val PREF_WORDPRESS_SITES = "pref_key_wordpress_sites"

    // string. Store notes linked to note widget instances.
    const val PREF_NOTE_WIDGET_NOTE = "pref_key_note_widget_"

    const val ALPHABETICAL_ASCENDING_LABEL = "alphabetical_az"
    const val ALPHABETICAL_DESCENDING_LABEL = "alphabetical_za"
    const val DATE_CREATED_ASCENDING_LABEL = "created_oldest"
    const val DATE_CREATED_DESCENDING_LABEL = "created_newest"
    const val DATE_MODIFIED_ASCENDING_LABEL = "modified_oldest"
    const val DATE_MODIFIED_DESCENDING_LABEL = "modified_newest"
    const val ALPHABETICAL_ASCENDING = 4
    const val ALPHABETICAL_DESCENDING = 5
    const val DATE_CREATED_ASCENDING = 3
    const val DATE_CREATED_DESCENDING = 2
    const val DATE_MODIFIED_ASCENDING = 1
    const val DATE_MODIFIED_DESCENDING = 0

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        DATE_MODIFIED_DESCENDING,
        DATE_MODIFIED_ASCENDING,
        DATE_CREATED_DESCENDING,
        DATE_CREATED_ASCENDING,
        ALPHABETICAL_ASCENDING,
        ALPHABETICAL_DESCENDING
    )
    annotation class Sort

    // Java callers pass Fragment.getActivity(); a null context fails here with the same NPE as before.
    private fun getPrefs(context: Context?): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context!!)

    @JvmStatic
    @JvmOverloads
    fun getStringPref(context: Context?, prefKey: String, defaultValue: String? = ""): String? {
        return try {
            getPrefs(context).getString(prefKey, defaultValue)
        } catch (e: ClassCastException) {
            defaultValue
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getIntPref(context: Context?, prefKey: String, defaultValue: Int = 0): Int {
        // read as string preference, then convert to int
        val strPref = getStringPref(context, prefKey, defaultValue.toString())
        return StrUtils.strToInt(strPref, defaultValue)
    }

    @JvmStatic
    @JvmOverloads
    fun getBoolPref(context: Context?, prefKey: String, defaultValue: Boolean = false): Boolean {
        return try {
            getPrefs(context).getBoolean(prefKey, defaultValue)
        } catch (e: ClassCastException) {
            defaultValue
        }
    }

    @JvmStatic
    fun versionInfo(): CharSequence {
        return if (BuildConfig.DEBUG) {
            BuildConfig.VERSION_NAME +
                " (" + BuildConfig.VERSION_CODE + ") " +
                BuildConfig.BUILD_TYPE +
                " (" + BuildConfig.BUILD_HASH + ")"
        } else {
            BuildConfig.VERSION_NAME +
                " (" + BuildConfig.VERSION_CODE + ")"
        }
    }

    @JvmStatic
    fun getLayoutWidget(context: Context, isLight: Boolean): Int {
        return when (getStyleIndexSelected(context)) {
            STYLE_BLACK -> if (isLight) R.layout.note_widget_light else R.layout.note_widget_dark_black
            STYLE_MATRIX -> if (isLight) R.layout.note_widget_light_mono else R.layout.note_widget_dark_matrix
            STYLE_MONO -> if (isLight) R.layout.note_widget_light_mono else R.layout.note_widget_dark_mono
            STYLE_PUBLICATION ->
                if (isLight) R.layout.note_widget_light_publication else R.layout.note_widget_dark_publication
            STYLE_SEPIA -> if (isLight) R.layout.note_widget_light_sepia else R.layout.note_widget_dark_sepia
            else -> if (isLight) R.layout.note_widget_light else R.layout.note_widget_dark
        }
    }

    @JvmStatic
    fun getLayoutWidgetList(context: Context, isLight: Boolean): Int {
        return when (getStyleIndexSelected(context)) {
            STYLE_BLACK ->
                if (isLight) R.layout.note_list_widget_light_black else R.layout.note_list_widget_dark_black
            STYLE_CLASSIC ->
                if (isLight) R.layout.note_list_widget_light_classic else R.layout.note_list_widget_dark_classic
            STYLE_MATRIX ->
                if (isLight) R.layout.note_list_widget_light_matrix else R.layout.note_list_widget_dark_matrix
            STYLE_MONO ->
                if (isLight) R.layout.note_list_widget_light_mono else R.layout.note_list_widget_dark_mono
            STYLE_PUBLICATION ->
                if (isLight) {
                    R.layout.note_list_widget_light_publication
                } else {
                    R.layout.note_list_widget_dark_publication
                }
            STYLE_SEPIA ->
                if (isLight) R.layout.note_list_widget_light_sepia else R.layout.note_list_widget_dark_sepia
            else ->
                if (isLight) R.layout.note_list_widget_light_default else R.layout.note_list_widget_dark_default
        }
    }

    @JvmStatic
    fun getLayoutWidgetListItem(context: Context, isLight: Boolean): Int {
        return when (getStyleIndexSelected(context)) {
            STYLE_PUBLICATION ->
                if (isLight) R.layout.note_list_widget_item_light_serif else R.layout.note_list_widget_item_dark_serif
            STYLE_MATRIX, STYLE_MONO ->
                if (isLight) {
                    R.layout.note_list_widget_item_light_monospace
                } else {
                    R.layout.note_list_widget_item_dark_monospace
                }
            else -> if (isLight) R.layout.note_list_widget_item_light else R.layout.note_list_widget_item_dark
        }
    }

    @JvmStatic
    fun getStyleIndexSelected(context: Context): Int {
        return getPrefs(context).getInt(PREF_STYLE_INDEX, STYLE_DEFAULT)
    }

    @JvmStatic
    fun getStyleNameFromIndex(context: Context, index: Int): String {
        return when (index) {
            STYLE_BLACK -> context.getString(R.string.style_black)
            STYLE_CLASSIC -> context.getString(R.string.style_classic)
            STYLE_MATRIX -> context.getString(R.string.style_matrix)
            STYLE_MONO -> context.getString(R.string.style_mono)
            STYLE_PUBLICATION -> context.getString(R.string.style_publication)
            STYLE_SEPIA -> context.getString(R.string.style_sepia)
            else -> context.getString(R.string.style_default)
        }
    }

    @JvmStatic
    fun getStyleNameFromIndexSelected(context: Context): String {
        return getStyleNameFromIndex(context, getStyleIndexSelected(context))
    }

    @JvmStatic
    fun getStyleWidgetDialog(context: Context): Int {
        return when (getStyleIndexSelected(context)) {
            STYLE_BLACK -> R.style.Theme_Transparent_Black
            STYLE_CLASSIC -> R.style.Theme_Transparent_Classic
            STYLE_MATRIX -> R.style.Theme_Transparent_Matrix
            STYLE_MONO -> R.style.Theme_Transparent_Mono
            STYLE_SEPIA -> R.style.Theme_Transparent_Sepia
            STYLE_PUBLICATION -> R.style.Theme_Transparent_Publication
            else -> R.style.Theme_Transparent
        }
    }

    @JvmStatic
    fun setStyleIndex(context: Context, index: Int) {
        getPrefs(context).edit().putInt(PREF_STYLE_INDEX, index).apply()
    }

    @JvmStatic
    fun getFontSize(context: Context?): Int {
        // Just in case
        if (context == null) {
            return 16
        }

        // Get default value for normal font size (differs based on screen/dpi size)
        val defaultFontSize = context.resources.getInteger(R.integer.default_font_size)

        return getIntPref(context, PREF_FONT_SIZE, defaultFontSize)
    }

    @JvmStatic
    fun sortNoteQuery(query: Query<Note>, context: Context, includePinnedOrdering: Boolean) {
        if (includePinnedOrdering) {
            query.order(Note.PINNED_INDEX_NAME, Query.SortType.DESCENDING)
        }

        when (getIntPref(context, PREF_SORT_ORDER)) {
            DATE_MODIFIED_DESCENDING -> query.order(Note.MODIFIED_INDEX_NAME, Query.SortType.DESCENDING)
            DATE_MODIFIED_ASCENDING -> query.order(Note.MODIFIED_INDEX_NAME, Query.SortType.ASCENDING)
            DATE_CREATED_DESCENDING -> query.order(Note.CREATED_INDEX_NAME, Query.SortType.DESCENDING)
            DATE_CREATED_ASCENDING -> query.order(Note.CREATED_INDEX_NAME, Query.SortType.ASCENDING)
            ALPHABETICAL_ASCENDING -> query.order(Note.CONTENT_PROPERTY, Query.SortType.ASCENDING)
            ALPHABETICAL_DESCENDING -> query.order(Note.CONTENT_PROPERTY, Query.SortType.DESCENDING)
        }
    }
}
