package com.automattic.simplenote.search

import com.automattic.simplenote.utils.PrefUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SortOrderTest {

    @Test
    fun fromPreferenceMapsAllSixSortModes() {
        assertEquals(SortOrder.MODIFIED_DESC, SortOrder.fromPreference(PrefUtils.DATE_MODIFIED_DESCENDING))
        assertEquals(SortOrder.MODIFIED_ASC, SortOrder.fromPreference(PrefUtils.DATE_MODIFIED_ASCENDING))
        assertEquals(SortOrder.CREATED_DESC, SortOrder.fromPreference(PrefUtils.DATE_CREATED_DESCENDING))
        assertEquals(SortOrder.CREATED_ASC, SortOrder.fromPreference(PrefUtils.DATE_CREATED_ASCENDING))
        assertEquals(SortOrder.CONTENT_ASC, SortOrder.fromPreference(PrefUtils.ALPHABETICAL_ASCENDING))
        assertEquals(SortOrder.CONTENT_DESC, SortOrder.fromPreference(PrefUtils.ALPHABETICAL_DESCENDING))
    }

    @Test
    fun fromPreferenceMapsTheStoredPreferenceValuesExactly() {
        assertEquals(SortOrder.MODIFIED_DESC, SortOrder.fromPreference(0))
        assertEquals(SortOrder.MODIFIED_ASC, SortOrder.fromPreference(1))
        assertEquals(SortOrder.CREATED_DESC, SortOrder.fromPreference(2))
        assertEquals(SortOrder.CREATED_ASC, SortOrder.fromPreference(3))
        assertEquals(SortOrder.CONTENT_ASC, SortOrder.fromPreference(4))
        assertEquals(SortOrder.CONTENT_DESC, SortOrder.fromPreference(5))
    }

    @Test
    fun fromPreferenceFallsBackToModifiedDescLikeTheUnsetPreference() {
        assertEquals(SortOrder.MODIFIED_DESC, SortOrder.fromPreference(99))
        assertEquals(SortOrder.MODIFIED_DESC, SortOrder.fromPreference(-1))
    }

    @Test
    fun dateFieldMatchesGetDateByPreference() {
        assertEquals("modified", SortOrder.MODIFIED_DESC.dateField)
        assertEquals("modified", SortOrder.MODIFIED_ASC.dateField)
        assertEquals("created", SortOrder.CREATED_DESC.dateField)
        assertEquals("created", SortOrder.CREATED_ASC.dateField)
        assertNull(SortOrder.CONTENT_ASC.dateField)
        assertNull(SortOrder.CONTENT_DESC.dateField)
    }
}
