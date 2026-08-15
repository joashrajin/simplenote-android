package com.automattic.simplenote.utils;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.automattic.simplenote.R;
import com.automattic.simplenote.models.Tag;
import com.automattic.simplenote.search.NoteFilter;

import java.util.List;

public class TagsAdapter extends BaseAdapter {
    public static final int DEFAULT_ITEM_POSITION = 0;
    public static final int ALL_NOTES_ID = -1;
    public static final int TRASH_ID = -2;
    public static final int SETTINGS_ID = -3;
    public static final int TAGS_ID = -4;
    public static final int UNTAGGED_NOTES_ID = -5;

    private static final int mMinimumItemsPrimary = new int[] {R.string.all_notes, R.string.trash}.length;
    private static final int mMinimumItemsSecondary = new int[] {R.string.untagged_notes}.length;

    private Context mContext;
    private List<Tag> tags;
    private TagMenuItem mAllNotesItem;
    private TagMenuItem mTrashItem;
    private TagMenuItem mUntaggedNotesItem;

    public TagsAdapter(Context context) {
        this(context, null);
    }

    private TagsAdapter(Context context, List<Tag> tags) {
        mContext = context;
        mAllNotesItem = new TagMenuItem(ALL_NOTES_ID, R.string.all_notes, NoteFilter.AllNotes.INSTANCE);
        mTrashItem = new TagMenuItem(TRASH_ID, R.string.trash, NoteFilter.Trash.INSTANCE);
        mUntaggedNotesItem = new TagMenuItem(UNTAGGED_NOTES_ID, R.string.untagged_notes, NoteFilter.Untagged.INSTANCE);

        submitList(tags);
    }


    public void submitList(List<Tag> tags) {
        this.tags = tags;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mMinimumItemsPrimary + mMinimumItemsSecondary + getCountCustom();
    }

    public int getCountCustom() {

        return tags == null ? 0 : tags.size();
    }

    public TagMenuItem getDefaultItem() {
        return getItem(DEFAULT_ITEM_POSITION);
    }

    @Override
    public TagMenuItem getItem(int i) {
        if (i == 0) {
            return mAllNotesItem;
        } else if (i == 1) {
            return mTrashItem;
        } else if (i == this.getCount() - 1) {
            return mUntaggedNotesItem;
        } else {
            return new TagMenuItem(i, tags.get(i - mMinimumItemsPrimary));
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return null;
    }

    public int getPosition(TagMenuItem mSelectedTag) {
        if (mSelectedTag.id == ALL_NOTES_ID) return 0;
        if (mSelectedTag.id == TRASH_ID) return 1;
        if (mSelectedTag.id == UNTAGGED_NOTES_ID) {
            return getCountCustom() == 0 ? -1 : this.getCount() - 1;
        }
        if (tags == null) return -1;

        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).getSimperiumKey().equals(mSelectedTag.key)) {
                return i + mMinimumItemsPrimary;
            }
        }
        return -1;
    }

    public SelectionUpdate rebindSelection(TagMenuItem selectedTag) {
        int position = selectedTag == null ? -1 : getPosition(selectedTag);
        if (position == -1) {
            return new SelectionUpdate(getDefaultItem(), true, false);
        }

        TagMenuItem reboundTag = getItem(position);
        boolean isRenamed = selectedTag.key != null && !selectedTag.name.equals(reboundTag.name);
        return new SelectionUpdate(reboundTag, false, isRenamed);
    }

    public TagMenuItem getTagFromItem(MenuItem item) {
        switch (item.getItemId()) {
            case ALL_NOTES_ID:
                return mAllNotesItem;
            case TRASH_ID:
                return mTrashItem;
            case UNTAGGED_NOTES_ID:
                return mUntaggedNotesItem;
            default:
                return getItem(item.getItemId());
        }
    }

    public class TagMenuItem {
        public String key;
        public String name;
        public long id;
        private final NoteFilter filter;

        private TagMenuItem(long id, Tag tag) {
            this(id, tag.getSimperiumKey(), tag.getName(), new NoteFilter.InTag(tag.getName()));
        }

        private TagMenuItem(long id, String key, String name, NoteFilter filter) {
            this.id = id;
            this.key = key;
            this.name = name;
            this.filter = filter;
        }

        private TagMenuItem(long id, int resourceId, NoteFilter filter) {
            this(id, null, mContext.getResources().getString(resourceId), filter);
        }

        public NoteFilter getFilter() {
            return filter;
        }
    }

    public class SelectionUpdate {
        public final TagMenuItem item;
        public final boolean isMissing;
        public final boolean isRenamed;

        private SelectionUpdate(TagMenuItem item, boolean isMissing, boolean isRenamed) {
            this.item = item;
            this.isMissing = isMissing;
            this.isRenamed = isRenamed;
        }

        public FilterRefresh getFilterRefresh(boolean isInitial) {
            if (isInitial) {
                return FilterRefresh.INITIAL;
            }
            if (isMissing || isRenamed) {
                return FilterRefresh.AUTOMATIC;
            }
            return FilterRefresh.NONE;
        }
    }

    public enum FilterRefresh {
        NONE,
        INITIAL,
        AUTOMATIC
    }
}
