package com.automattic.simplenote.utils

import com.automattic.simplenote.models.Note
import com.simperium.client.Bucket

object NoteListWidgetQuery {
    @JvmStatic
    fun hasActiveNotes(notesBucket: Bucket<Note>): Boolean = Note.all(notesBucket).count() > 0
}
