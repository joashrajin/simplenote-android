package com.automattic.simplenote.search

sealed class NoteFilter {
    object AllNotes : NoteFilter()
    object Trash : NoteFilter()
    object Untagged : NoteFilter()
    data class InTag(val tagName: String) : NoteFilter()
}
