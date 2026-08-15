package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.view.MenuItem;

import com.automattic.simplenote.models.Tag;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class TagsAdapterTest {
    private TagsAdapter adapter;

    @Before
    public void setUp() {
        Context context = mock(Context.class);
        Resources resources = mock(Resources.class);
        when(context.getResources()).thenReturn(resources);
        when(resources.getString(anyInt())).thenReturn("special");
        adapter = new TagsAdapter(context);
    }

    @Test
    public void selectedCustomTagKeepsItsPositionByKeyAfterReordering() {
        Tag alpha = new Tag("alpha");
        Tag beta = new Tag("beta");
        adapter.submitList(Arrays.asList(alpha, beta));
        TagsAdapter.TagMenuItem selectedAlpha = adapter.getItem(2);

        adapter.submitList(Arrays.asList(beta, alpha));

        TagsAdapter.SelectionUpdate update = adapter.rebindSelection(selectedAlpha);
        assertFalse(update.isMissing);
        assertFalse(update.isRenamed);
        assertEquals(TagsAdapter.FilterRefresh.NONE, update.getFilterRefresh(false));
        assertEquals(TagsAdapter.FilterRefresh.INITIAL, update.getFilterRefresh(true));
        assertEquals(3, update.item.id);
        assertEquals("alpha", update.item.name);
    }

    @Test
    public void selectedCustomTagUsesItsStableKeyAcrossLexicalRenames() {
        Tag tag = new Tag("stable-key");
        tag.setName("work");
        adapter.submitList(Arrays.asList(tag));
        TagsAdapter.TagMenuItem selected = adapter.getItem(2);

        tag.setName("Work");
        adapter.submitList(Arrays.asList(tag));

        TagsAdapter.SelectionUpdate update = adapter.rebindSelection(selected);
        assertFalse(update.isMissing);
        assertTrue(update.isRenamed);
        assertEquals(TagsAdapter.FilterRefresh.AUTOMATIC, update.getFilterRefresh(false));
        assertEquals(2, update.item.id);
        assertEquals("Work", update.item.name);
    }

    @Test
    public void removedCustomTagDoesNotResolveToAnotherTagAtItsOldPosition() {
        Tag alpha = new Tag("alpha");
        Tag beta = new Tag("beta");
        adapter.submitList(Arrays.asList(alpha, beta));
        TagsAdapter.TagMenuItem selectedAlpha = adapter.getItem(2);

        adapter.submitList(Arrays.asList(beta));

        TagsAdapter.SelectionUpdate update = adapter.rebindSelection(selectedAlpha);
        assertTrue(update.isMissing);
        assertFalse(update.isRenamed);
        assertEquals(TagsAdapter.FilterRefresh.AUTOMATIC, update.getFilterRefresh(false));
        assertEquals(TagsAdapter.ALL_NOTES_ID, update.item.id);
    }

    @Test
    public void untaggedSelectionIsUnavailableWithoutCustomTags() {
        TagsAdapter.TagMenuItem untagged = adapter.getItem(adapter.getCount() - 1);

        TagsAdapter.SelectionUpdate update = adapter.rebindSelection(untagged);
        assertTrue(update.isMissing);
        assertEquals(TagsAdapter.FilterRefresh.AUTOMATIC, update.getFilterRefresh(false));
        assertEquals(TagsAdapter.ALL_NOTES_ID, update.item.id);
    }

    @Test
    public void customMenuSelectionKeepsTheTagKey() {
        Tag tag = new Tag("stable-key");
        tag.setName("work");
        adapter.submitList(Arrays.asList(tag));
        MenuItem menuItem = mock(MenuItem.class);
        when(menuItem.getItemId()).thenReturn(2);

        TagsAdapter.TagMenuItem selected = adapter.getTagFromItem(menuItem);

        assertEquals("stable-key", selected.key);
        assertEquals("work", selected.name);
    }
}
