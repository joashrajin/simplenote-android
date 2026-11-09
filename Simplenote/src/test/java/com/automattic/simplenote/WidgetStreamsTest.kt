package com.automattic.simplenote

import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.SortOrder
import com.automattic.simplenote.utils.PrefUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStreamsTest {

    @Test
    fun widgetListRequestReproducesTheLegacyWidgetQueryShape() {
        val request = WidgetStreams.widgetListRequest(PrefUtils.DATE_MODIFIED_DESCENDING)

        assertEquals(NoteFilter.AllNotes, request.filter)
        assertNull(request.rawSearch)
        assertEquals(SortOrder.MODIFIED_DESC, request.sort)
        assertTrue(request.pinnedFirst)
    }

    @Test
    fun widgetListRequestMapsEverySortPreference() {
        assertEquals(SortOrder.MODIFIED_DESC, WidgetStreams.widgetListRequest(PrefUtils.DATE_MODIFIED_DESCENDING).sort)
        assertEquals(SortOrder.MODIFIED_ASC, WidgetStreams.widgetListRequest(PrefUtils.DATE_MODIFIED_ASCENDING).sort)
        assertEquals(SortOrder.CREATED_DESC, WidgetStreams.widgetListRequest(PrefUtils.DATE_CREATED_DESCENDING).sort)
        assertEquals(SortOrder.CREATED_ASC, WidgetStreams.widgetListRequest(PrefUtils.DATE_CREATED_ASCENDING).sort)
        assertEquals(SortOrder.CONTENT_ASC, WidgetStreams.widgetListRequest(PrefUtils.ALPHABETICAL_ASCENDING).sort)
        assertEquals(SortOrder.CONTENT_DESC, WidgetStreams.widgetListRequest(PrefUtils.ALPHABETICAL_DESCENDING).sort)
    }
}
