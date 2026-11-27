package com.automattic.simplenote.utils

import android.content.Context
import android.content.res.Resources
import android.view.MenuItem
import com.automattic.simplenote.models.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class TagsAdapterTest {
    private lateinit var adapter: TagsAdapter

    @Before
    fun setUp() {
        val resources = mock<Resources> {
            on { getString(any()) } doReturn "special"
        }
        val context = mock<Context> {
            on { this.resources } doReturn resources
        }
        adapter = TagsAdapter(context)
    }

    @Test
    fun selectedCustomTagKeepsItsPositionByKeyAfterReordering() {
        val alpha = Tag("alpha")
        val beta = Tag("beta")
        adapter.submitList(listOf(alpha, beta))
        val selectedAlpha = adapter.getItem(2)

        adapter.submitList(listOf(beta, alpha))

        val update = adapter.rebindSelection(selectedAlpha)
        assertFalse(update.isMissing)
        assertFalse(update.isRenamed)
        assertEquals(TagsAdapter.FilterRefresh.NONE, update.getFilterRefresh(false))
        assertEquals(TagsAdapter.FilterRefresh.INITIAL, update.getFilterRefresh(true))
        assertEquals(3L, update.item.id)
        assertEquals("alpha", update.item.name)
    }

    @Test
    fun selectedCustomTagUsesItsStableKeyAcrossLexicalRenames() {
        val tag = Tag("stable-key")
        tag.name = "work"
        adapter.submitList(listOf(tag))
        val selected = adapter.getItem(2)

        tag.name = "Work"
        adapter.submitList(listOf(tag))

        val update = adapter.rebindSelection(selected)
        assertFalse(update.isMissing)
        assertTrue(update.isRenamed)
        assertEquals(TagsAdapter.FilterRefresh.AUTOMATIC, update.getFilterRefresh(false))
        assertEquals(2L, update.item.id)
        assertEquals("Work", update.item.name)
    }

    @Test
    fun removedCustomTagDoesNotResolveToAnotherTagAtItsOldPosition() {
        val alpha = Tag("alpha")
        val beta = Tag("beta")
        adapter.submitList(listOf(alpha, beta))
        val selectedAlpha = adapter.getItem(2)

        adapter.submitList(listOf(beta))

        val update = adapter.rebindSelection(selectedAlpha)
        assertTrue(update.isMissing)
        assertFalse(update.isRenamed)
        assertEquals(TagsAdapter.FilterRefresh.AUTOMATIC, update.getFilterRefresh(false))
        assertEquals(TagsAdapter.ALL_NOTES_ID.toLong(), update.item.id)
    }

    @Test
    fun untaggedSelectionIsUnavailableWithoutCustomTags() {
        val untagged = adapter.getItem(adapter.count - 1)

        val update = adapter.rebindSelection(untagged)
        assertTrue(update.isMissing)
        assertEquals(TagsAdapter.FilterRefresh.AUTOMATIC, update.getFilterRefresh(false))
        assertEquals(TagsAdapter.ALL_NOTES_ID.toLong(), update.item.id)
    }

    @Test
    fun customMenuSelectionKeepsTheTagKey() {
        val tag = Tag("stable-key")
        tag.name = "work"
        adapter.submitList(listOf(tag))
        val menuItem = mock<MenuItem> {
            on { itemId } doReturn 2
        }

        val selected = adapter.getTagFromItem(menuItem)

        assertEquals("stable-key", selected.key)
        assertEquals("work", selected.name)
    }
}
