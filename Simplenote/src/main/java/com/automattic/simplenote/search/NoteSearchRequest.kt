package com.automattic.simplenote.search

data class NoteSearchRequest(
    val filter: NoteFilter,
    val rawSearch: String?,
    val sort: SortOrder,
    val pinnedFirst: Boolean,
)
