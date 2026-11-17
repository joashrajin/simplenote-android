package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.BuildConfig
import com.automattic.simplenote.R
import com.automattic.simplenote.models.Note
import com.simperium.client.Query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterizes PrefUtils' Java-visible contract: preference keys, defaults, the string-backed int reads,
 * the ClassCastException fallbacks, and the style-to-resource tables.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PrefUtilsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun preferenceKeysAreStable() {
        assertEquals("pref_key_sort_order", PrefUtils.PREF_SORT_ORDER)
        assertEquals("pref_key_condensed_note_list", PrefUtils.PREF_CONDENSED_LIST)
        assertEquals("pref_key_sort_tags_alpha", PrefUtils.PREF_SORT_TAGS_ALPHA)
        assertEquals("pref_key_detect_links", PrefUtils.PREF_DETECT_LINKS)
        assertEquals("pref_key_first_launch", PrefUtils.PREF_FIRST_LAUNCH)
        assertEquals("pref_key_account_required", PrefUtils.PREF_ACCOUNT_REQUIRED)
        assertEquals("pref_key_markdown_enabled", PrefUtils.PREF_MARKDOWN_ENABLED)
        assertEquals("pref_key_style_index", PrefUtils.PREF_STYLE_INDEX)
        assertEquals("pref_key_theme", PrefUtils.PREF_THEME)
        assertEquals("pref_theme_modified", PrefUtils.PREF_THEME_MODIFIED)
        assertEquals("pref_key_wp_token", PrefUtils.PREF_WP_TOKEN)
        assertEquals("pref_key_wordpress_sites", PrefUtils.PREF_WORDPRESS_SITES)
        assertEquals("pref_key_note_widget_", PrefUtils.PREF_NOTE_WIDGET_NOTE)
    }

    @Test
    fun sortConstantsAreStable() {
        assertEquals("alphabetical_az", PrefUtils.ALPHABETICAL_ASCENDING_LABEL)
        assertEquals("alphabetical_za", PrefUtils.ALPHABETICAL_DESCENDING_LABEL)
        assertEquals("created_oldest", PrefUtils.DATE_CREATED_ASCENDING_LABEL)
        assertEquals("created_newest", PrefUtils.DATE_CREATED_DESCENDING_LABEL)
        assertEquals("modified_oldest", PrefUtils.DATE_MODIFIED_ASCENDING_LABEL)
        assertEquals("modified_newest", PrefUtils.DATE_MODIFIED_DESCENDING_LABEL)
        assertEquals(4, PrefUtils.ALPHABETICAL_ASCENDING)
        assertEquals(5, PrefUtils.ALPHABETICAL_DESCENDING)
        assertEquals(3, PrefUtils.DATE_CREATED_ASCENDING)
        assertEquals(2, PrefUtils.DATE_CREATED_DESCENDING)
        assertEquals(1, PrefUtils.DATE_MODIFIED_ASCENDING)
        assertEquals(0, PrefUtils.DATE_MODIFIED_DESCENDING)
        assertEquals(PrefUtils.ALPHABETICAL_DESCENDING, sortOf(PrefUtils.ALPHABETICAL_DESCENDING))
    }

    @Test
    fun getStringPrefDefaultsToEmptyString() {
        assertEquals("", PrefUtils.getStringPref(context, PrefUtils.PREF_WORDPRESS_SITES))
    }

    @Test
    fun getStringPrefReturnsGivenDefaultIncludingNull() {
        assertEquals("fallback", PrefUtils.getStringPref(context, PrefUtils.PREF_WORDPRESS_SITES, "fallback"))
        assertNull(PrefUtils.getStringPref(context, PrefUtils.PREF_WORDPRESS_SITES, null))
    }

    @Test
    fun getStringPrefReadsStoredString() {
        prefs.edit().putString(PrefUtils.PREF_NOTE_WIDGET_NOTE + 7, "note-key").commit()

        assertEquals("note-key", PrefUtils.getStringPref(context, PrefUtils.PREF_NOTE_WIDGET_NOTE + 7))
        assertEquals("note-key", PrefUtils.getStringPref(context, PrefUtils.PREF_NOTE_WIDGET_NOTE + 7, "x"))
    }

    @Test
    fun getStringPrefFallsBackToDefaultWhenStoredAsAnotherType() {
        prefs.edit().putInt(PrefUtils.PREF_WORDPRESS_SITES, 3).commit()

        assertEquals("", PrefUtils.getStringPref(context, PrefUtils.PREF_WORDPRESS_SITES))
        assertEquals("fallback", PrefUtils.getStringPref(context, PrefUtils.PREF_WORDPRESS_SITES, "fallback"))
        assertNull(PrefUtils.getStringPref(context, PrefUtils.PREF_WORDPRESS_SITES, null))
    }

    @Test(expected = NullPointerException::class)
    fun getStringPrefWithNullContextThrows() {
        PrefUtils.getStringPref(null, PrefUtils.PREF_WORDPRESS_SITES)
    }

    @Test
    fun getIntPrefDefaults() {
        assertEquals(0, PrefUtils.getIntPref(context, PrefUtils.PREF_SORT_ORDER))
        assertEquals(2, PrefUtils.getIntPref(context, PrefUtils.PREF_THEME, 2))
    }

    @Test
    fun getIntPrefParsesStoredString() {
        prefs.edit().putString(PrefUtils.PREF_SORT_ORDER, "5").commit()

        assertEquals(5, PrefUtils.getIntPref(context, PrefUtils.PREF_SORT_ORDER))
        assertEquals(5, PrefUtils.getIntPref(context, PrefUtils.PREF_SORT_ORDER, 3))
    }

    @Test
    fun getIntPrefFallsBackToDefaultForUnparseableString() {
        prefs.edit().putString(PrefUtils.PREF_THEME, "dark").commit()

        assertEquals(0, PrefUtils.getIntPref(context, PrefUtils.PREF_THEME))
        assertEquals(2, PrefUtils.getIntPref(context, PrefUtils.PREF_THEME, 2))
    }

    @Test
    fun getIntPrefIgnoresValueStoredAsInt() {
        prefs.edit().putInt(PrefUtils.PREF_SORT_ORDER, 3).commit()

        assertEquals(0, PrefUtils.getIntPref(context, PrefUtils.PREF_SORT_ORDER))
        assertEquals(4, PrefUtils.getIntPref(context, PrefUtils.PREF_SORT_ORDER, 4))
    }

    @Test(expected = NullPointerException::class)
    fun getIntPrefWithNullContextThrows() {
        PrefUtils.getIntPref(null, PrefUtils.PREF_SORT_ORDER)
    }

    @Test
    fun getBoolPrefDefaults() {
        assertFalse(PrefUtils.getBoolPref(context, PrefUtils.PREF_DETECT_LINKS))
        assertTrue(PrefUtils.getBoolPref(context, PrefUtils.PREF_FIRST_LAUNCH, true))
        assertFalse(PrefUtils.getBoolPref(context, PrefUtils.PREF_ACCOUNT_REQUIRED, false))
    }

    @Test
    fun getBoolPrefReadsStoredBoolean() {
        prefs.edit()
            .putBoolean(PrefUtils.PREF_MARKDOWN_ENABLED, true)
            .putBoolean(PrefUtils.PREF_FIRST_LAUNCH, false)
            .commit()

        assertTrue(PrefUtils.getBoolPref(context, PrefUtils.PREF_MARKDOWN_ENABLED))
        assertTrue(PrefUtils.getBoolPref(context, PrefUtils.PREF_MARKDOWN_ENABLED, false))
        assertFalse(PrefUtils.getBoolPref(context, PrefUtils.PREF_FIRST_LAUNCH, true))
    }

    @Test
    fun getBoolPrefFallsBackToDefaultWhenStoredAsAnotherType() {
        prefs.edit().putString(PrefUtils.PREF_CONDENSED_LIST, "true").commit()

        assertFalse(PrefUtils.getBoolPref(context, PrefUtils.PREF_CONDENSED_LIST))
        assertTrue(PrefUtils.getBoolPref(context, PrefUtils.PREF_CONDENSED_LIST, true))
    }

    @Test(expected = NullPointerException::class)
    fun getBoolPrefWithNullContextThrows() {
        PrefUtils.getBoolPref(null, PrefUtils.PREF_DETECT_LINKS, false)
    }

    @Test
    fun versionInfoIncludesBuildTypeAndHashInDebug() {
        val expected = if (BuildConfig.DEBUG) {
            BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") " + BuildConfig.BUILD_TYPE +
                " (" + BuildConfig.BUILD_HASH + ")"
        } else {
            BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
        }

        assertEquals(expected, PrefUtils.versionInfo().toString())
    }

    @Test
    fun styleIndexDefaultsToDefaultStyle() {
        assertEquals(ThemeUtils.STYLE_DEFAULT, PrefUtils.getStyleIndexSelected(context))
    }

    @Test
    fun setStyleIndexStoresIntUnderStyleKey() {
        PrefUtils.setStyleIndex(context, ThemeUtils.STYLE_SEPIA)

        assertEquals(ThemeUtils.STYLE_SEPIA, prefs.getInt(PrefUtils.PREF_STYLE_INDEX, -1))
        assertEquals(ThemeUtils.STYLE_SEPIA, PrefUtils.getStyleIndexSelected(context))
    }

    @Test
    fun styleIndexReadsRawIntWrite() {
        prefs.edit().putInt(PrefUtils.PREF_STYLE_INDEX, ThemeUtils.STYLE_MATRIX).commit()

        assertEquals(ThemeUtils.STYLE_MATRIX, PrefUtils.getStyleIndexSelected(context))
    }

    @Test(expected = ClassCastException::class)
    fun styleIndexStoredAsStringThrows() {
        prefs.edit().putString(PrefUtils.PREF_STYLE_INDEX, "3").commit()

        PrefUtils.getStyleIndexSelected(context)
    }

    @Test
    fun styleNameFromIndex() {
        val expected = mapOf(
            ThemeUtils.STYLE_DEFAULT to R.string.style_default,
            ThemeUtils.STYLE_CLASSIC to R.string.style_classic,
            ThemeUtils.STYLE_BLACK to R.string.style_black,
            ThemeUtils.STYLE_MATRIX to R.string.style_matrix,
            ThemeUtils.STYLE_MONO to R.string.style_mono,
            ThemeUtils.STYLE_PUBLICATION to R.string.style_publication,
            ThemeUtils.STYLE_SEPIA to R.string.style_sepia,
            UNKNOWN_STYLE to R.string.style_default,
            -1 to R.string.style_default,
        )

        expected.forEach { (index, name) ->
            assertEquals("index $index", context.getString(name), PrefUtils.getStyleNameFromIndex(context, index))
        }
    }

    @Test
    fun styleNameFromIndexSelected() {
        assertEquals(context.getString(R.string.style_default), PrefUtils.getStyleNameFromIndexSelected(context))

        PrefUtils.setStyleIndex(context, ThemeUtils.STYLE_PUBLICATION)

        assertEquals(context.getString(R.string.style_publication), PrefUtils.getStyleNameFromIndexSelected(context))
    }

    @Test
    fun layoutWidgetPerStyle() {
        assertLayouts(
            { light -> PrefUtils.getLayoutWidget(context, light) },
            ThemeUtils.STYLE_DEFAULT to (R.layout.note_widget_light to R.layout.note_widget_dark),
            ThemeUtils.STYLE_CLASSIC to (R.layout.note_widget_light to R.layout.note_widget_dark),
            ThemeUtils.STYLE_BLACK to (R.layout.note_widget_light to R.layout.note_widget_dark_black),
            ThemeUtils.STYLE_MATRIX to (R.layout.note_widget_light_mono to R.layout.note_widget_dark_matrix),
            ThemeUtils.STYLE_MONO to (R.layout.note_widget_light_mono to R.layout.note_widget_dark_mono),
            ThemeUtils.STYLE_PUBLICATION to
                (R.layout.note_widget_light_publication to R.layout.note_widget_dark_publication),
            ThemeUtils.STYLE_SEPIA to (R.layout.note_widget_light_sepia to R.layout.note_widget_dark_sepia),
            UNKNOWN_STYLE to (R.layout.note_widget_light to R.layout.note_widget_dark),
        )
    }

    @Test
    fun layoutWidgetListPerStyle() {
        assertLayouts(
            { light -> PrefUtils.getLayoutWidgetList(context, light) },
            ThemeUtils.STYLE_DEFAULT to
                (R.layout.note_list_widget_light_default to R.layout.note_list_widget_dark_default),
            ThemeUtils.STYLE_CLASSIC to
                (R.layout.note_list_widget_light_classic to R.layout.note_list_widget_dark_classic),
            ThemeUtils.STYLE_BLACK to (R.layout.note_list_widget_light_black to R.layout.note_list_widget_dark_black),
            ThemeUtils.STYLE_MATRIX to
                (R.layout.note_list_widget_light_matrix to R.layout.note_list_widget_dark_matrix),
            ThemeUtils.STYLE_MONO to (R.layout.note_list_widget_light_mono to R.layout.note_list_widget_dark_mono),
            ThemeUtils.STYLE_PUBLICATION to
                (R.layout.note_list_widget_light_publication to R.layout.note_list_widget_dark_publication),
            ThemeUtils.STYLE_SEPIA to (R.layout.note_list_widget_light_sepia to R.layout.note_list_widget_dark_sepia),
            UNKNOWN_STYLE to (R.layout.note_list_widget_light_default to R.layout.note_list_widget_dark_default),
        )
    }

    @Test
    fun layoutWidgetListItemPerStyle() {
        val plain = R.layout.note_list_widget_item_light to R.layout.note_list_widget_item_dark
        val monospace = R.layout.note_list_widget_item_light_monospace to R.layout.note_list_widget_item_dark_monospace
        assertLayouts(
            { light -> PrefUtils.getLayoutWidgetListItem(context, light) },
            ThemeUtils.STYLE_DEFAULT to plain,
            ThemeUtils.STYLE_CLASSIC to plain,
            ThemeUtils.STYLE_BLACK to plain,
            ThemeUtils.STYLE_MATRIX to monospace,
            ThemeUtils.STYLE_MONO to monospace,
            ThemeUtils.STYLE_PUBLICATION to
                (R.layout.note_list_widget_item_light_serif to R.layout.note_list_widget_item_dark_serif),
            ThemeUtils.STYLE_SEPIA to plain,
            UNKNOWN_STYLE to plain,
        )
    }

    @Test
    fun styleWidgetDialogPerStyle() {
        val expected = mapOf(
            ThemeUtils.STYLE_DEFAULT to R.style.Theme_Transparent,
            ThemeUtils.STYLE_CLASSIC to R.style.Theme_Transparent_Classic,
            ThemeUtils.STYLE_BLACK to R.style.Theme_Transparent_Black,
            ThemeUtils.STYLE_MATRIX to R.style.Theme_Transparent_Matrix,
            ThemeUtils.STYLE_MONO to R.style.Theme_Transparent_Mono,
            ThemeUtils.STYLE_PUBLICATION to R.style.Theme_Transparent_Publication,
            ThemeUtils.STYLE_SEPIA to R.style.Theme_Transparent_Sepia,
            UNKNOWN_STYLE to R.style.Theme_Transparent,
        )

        expected.forEach { (style, theme) ->
            PrefUtils.setStyleIndex(context, style)
            assertEquals("style $style", theme, PrefUtils.getStyleWidgetDialog(context))
        }
    }

    @Test
    fun fontSizeWithNullContextIsSixteen() {
        assertEquals(16, PrefUtils.getFontSize(null))
    }

    @Test
    fun fontSizeDefaultsToResourceValue() {
        assertEquals(context.resources.getInteger(R.integer.default_font_size), PrefUtils.getFontSize(context))
    }

    @Test
    fun fontSizeReadsStoredString() {
        prefs.edit().putString(FONT_SIZE_KEY, "24").commit()

        assertEquals(24, PrefUtils.getFontSize(context))
    }

    @Test
    fun fontSizeFallsBackToResourceValueForUnreadableValues() {
        val default = context.resources.getInteger(R.integer.default_font_size)

        prefs.edit().putString(FONT_SIZE_KEY, "large").commit()
        assertEquals(default, PrefUtils.getFontSize(context))

        prefs.edit().putInt(FONT_SIZE_KEY, 24).commit()
        assertEquals(default, PrefUtils.getFontSize(context))
    }

    @Test
    fun sortNoteQueryPerSortOrder() {
        val expected = mapOf(
            PrefUtils.DATE_MODIFIED_DESCENDING to (Note.MODIFIED_INDEX_NAME to Query.SortType.DESCENDING),
            PrefUtils.DATE_MODIFIED_ASCENDING to (Note.MODIFIED_INDEX_NAME to Query.SortType.ASCENDING),
            PrefUtils.DATE_CREATED_DESCENDING to (Note.CREATED_INDEX_NAME to Query.SortType.DESCENDING),
            PrefUtils.DATE_CREATED_ASCENDING to (Note.CREATED_INDEX_NAME to Query.SortType.ASCENDING),
            PrefUtils.ALPHABETICAL_ASCENDING to (Note.CONTENT_PROPERTY to Query.SortType.ASCENDING),
            PrefUtils.ALPHABETICAL_DESCENDING to (Note.CONTENT_PROPERTY to Query.SortType.DESCENDING),
        )

        expected.forEach { (sort, order) ->
            prefs.edit().putString(PrefUtils.PREF_SORT_ORDER, sort.toString()).commit()
            val query = mock<Query<Note>>()

            PrefUtils.sortNoteQuery(query, context, false)

            verify(query).order(order.first, order.second)
            verifyNoMoreInteractions(query)
        }
    }

    @Test
    fun sortNoteQueryPutsPinnedFirstWhenRequested() {
        prefs.edit().putString(PrefUtils.PREF_SORT_ORDER, PrefUtils.ALPHABETICAL_ASCENDING.toString()).commit()
        val query = mock<Query<Note>>()

        PrefUtils.sortNoteQuery(query, context, true)

        inOrder(query) {
            verify(query).order(Note.PINNED_INDEX_NAME, Query.SortType.DESCENDING)
            verify(query).order(Note.CONTENT_PROPERTY, Query.SortType.ASCENDING)
        }
        verifyNoMoreInteractions(query)
    }

    @Test
    fun sortNoteQueryDefaultsToModifiedDescending() {
        val query = mock<Query<Note>>()

        PrefUtils.sortNoteQuery(query, context, false)

        verify(query).order(Note.MODIFIED_INDEX_NAME, Query.SortType.DESCENDING)
        verifyNoMoreInteractions(query)
    }

    @Test
    fun sortNoteQueryAddsNoSortForUnknownOrder() {
        prefs.edit().putString(PrefUtils.PREF_SORT_ORDER, "9").commit()
        val query = mock<Query<Note>>()

        PrefUtils.sortNoteQuery(query, context, true)

        verify(query).order(Note.PINNED_INDEX_NAME, Query.SortType.DESCENDING)
        verifyNoMoreInteractions(query)
    }

    private fun assertLayouts(layout: (Boolean) -> Int, vararg expected: Pair<Int, Pair<Int, Int>>) {
        expected.forEach { (style, layouts) ->
            PrefUtils.setStyleIndex(context, style)
            assertEquals("style $style light", layouts.first, layout(true))
            assertEquals("style $style dark", layouts.second, layout(false))
        }
    }

    private fun sortOf(@PrefUtils.Sort sort: Int): Int = sort

    private companion object {
        const val UNKNOWN_STYLE = 42
        const val FONT_SIZE_KEY = "pref_key_font_size"
    }
}
