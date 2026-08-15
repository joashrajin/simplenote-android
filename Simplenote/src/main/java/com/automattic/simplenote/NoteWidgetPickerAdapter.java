package com.automattic.simplenote;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.automattic.simplenote.usecases.WidgetNotePickerItem;
import com.automattic.simplenote.utils.ChecklistUtils;
import com.automattic.simplenote.utils.PrefUtils;
import com.automattic.simplenote.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.List;

final class NoteWidgetPickerAdapter extends BaseAdapter {
    private final Context mContext;
    private final List<WidgetNotePickerItem> mItems = new ArrayList<>();

    NoteWidgetPickerAdapter(Context context, List<WidgetNotePickerItem> items) {
        mContext = context;
        submitList(items);
    }

    void submitList(List<WidgetNotePickerItem> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public WidgetNotePickerItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(mContext).inflate(
                    PrefUtils.getLayoutWidgetListItem(mContext, ThemeUtils.isLightTheme(mContext)),
                    parent,
                    false
            );
        }

        WidgetNotePickerItem item = getItem(position);
        TextView titleTextView = view.findViewById(R.id.note_title);
        TextView contentTextView = view.findViewById(R.id.note_content);
        titleTextView.setText(item.getTitle());
        SpannableStringBuilder preview = new SpannableStringBuilder(item.getPreview());
        preview = (SpannableStringBuilder) ChecklistUtils.addChecklistSpansForRegexAndColor(
                mContext,
                preview,
                ChecklistUtils.CHECKLIST_REGEX,
                R.color.text_title_disabled,
                true
        );
        contentTextView.setText(preview);
        return view;
    }
}
