package com.automattic.simplenote.models

import com.simperium.client.FullTextIndex

class NoteFullTextIndexer : FullTextIndex.Indexer<Note> {
    override fun index(keys: Array<String>, note: Note): Map<String, String> {
        val values = HashMap<String, String>(keys.size)
        values[INDEXES[0]] = note.title
        values[INDEXES[1]] = note.content
        return values
    }

    companion object {
        @JvmField
        val INDEXES: Array<String> = Note.FULL_TEXT_INDEXES
    }
}
