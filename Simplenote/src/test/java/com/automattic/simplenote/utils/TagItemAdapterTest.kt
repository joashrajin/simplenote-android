package com.automattic.simplenote.utils

import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagItemAdapterTest {
    @Test
    fun sameTagLexicalRenameRequiresRowRebind() {
        val tag = Tag("work").apply { name = "Work" }
        val oldItem = TagItem(tag, 3)
        tag.name = "work"
        val newItem = TagItem(tag, 3)

        assertEquals("Work", oldItem.displayName)
        assertEquals("work", newItem.displayName)
        assertTrue(TagItemAdapter.DIFF_CALLBACK.areItemsTheSame(oldItem, newItem))
        assertFalse(TagItemAdapter.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem))
    }
}
