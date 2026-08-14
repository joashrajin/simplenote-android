package com.automattic.simplenote.search

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.utils.PrefUtils

enum class SortOrder(val dateField: String?) {
    MODIFIED_DESC(Note.MODIFIED_INDEX_NAME),
    MODIFIED_ASC(Note.MODIFIED_INDEX_NAME),
    CREATED_DESC(Note.CREATED_INDEX_NAME),
    CREATED_ASC(Note.CREATED_INDEX_NAME),
    CONTENT_ASC(null),
    CONTENT_DESC(null);

    companion object {
        fun fromPreference(prefValue: Int): SortOrder = when (prefValue) {
            PrefUtils.DATE_MODIFIED_DESCENDING -> MODIFIED_DESC
            PrefUtils.DATE_MODIFIED_ASCENDING -> MODIFIED_ASC
            PrefUtils.DATE_CREATED_DESCENDING -> CREATED_DESC
            PrefUtils.DATE_CREATED_ASCENDING -> CREATED_ASC
            PrefUtils.ALPHABETICAL_ASCENDING -> CONTENT_ASC
            PrefUtils.ALPHABETICAL_DESCENDING -> CONTENT_DESC
            else -> MODIFIED_DESC
        }
    }
}
