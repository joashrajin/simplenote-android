package com.automattic.simplenote;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.automattic.simplenote.analytics.AnalyticsTracker;
import com.automattic.simplenote.models.Note;
import com.automattic.simplenote.repositories.NoteReference;
import com.automattic.simplenote.repositories.NotesRepository;
import com.automattic.simplenote.utils.DateTimeUtils;
import com.automattic.simplenote.utils.DisplayUtils;
import com.automattic.simplenote.utils.NoteUtils;
import com.automattic.simplenote.utils.SimplenoteLinkify;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class InfoBottomSheetDialog extends BottomSheetDialogBase {
    public static final String TAG = InfoBottomSheetDialog.class.getSimpleName();

    private final Fragment mFragment;

    @Inject NotesRepository notesRepository;
    @Inject SyncTimes<Note> noteSyncTimes;

    private LinearLayout mDateTimeSyncedLayout;
    private LinearLayout mReferencesLayout;
    private RecyclerView mReferences;
    private TextView mCountCharacters;
    private TextView mCountWords;
    private TextView mDateTimeCreated;
    private TextView mDateTimeModified;
    private TextView mDateTimeSynced;

    public InfoBottomSheetDialog(@NonNull Fragment fragment) {
        mFragment = fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View infoView = inflater.inflate(R.layout.bottom_sheet_info, null, false);
        mCountCharacters = infoView.findViewById(R.id.count_characters);
        mCountWords = infoView.findViewById(R.id.count_words);
        mDateTimeCreated = infoView.findViewById(R.id.date_time_created);
        mDateTimeModified = infoView.findViewById(R.id.date_time_modified);
        mDateTimeSynced = infoView.findViewById(R.id.date_time_synced);
        mDateTimeSyncedLayout = infoView.findViewById(R.id.date_time_synced_layout);
        mReferencesLayout = infoView.findViewById(R.id.references_layout);
        mReferences = infoView.findViewById(R.id.references);

        if (getDialog() != null) {
            // Set peek height to half height of screen.
            getDialog().setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) dialogInterface).getBehavior();
                    behavior.setPeekHeight(DisplayUtils.getDisplayPixelSize(requireContext()).y / 2);
                    behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    behavior.setSkipCollapsed(true);
                }
            });

            getDialog().setContentView(infoView);
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    public void show(FragmentManager manager, Note note) {
        if (mFragment.isAdded()) {
            showNow(manager, TAG);
            mCountCharacters.setText(NoteUtils.getCharactersCount(note.getContent()));
            mCountWords.setText(NoteUtils.getWordCount(note.getContent()));
            mDateTimeCreated.setText(DateTimeUtils.getDateTextString(requireContext(), note.getCreationDate()));
            mDateTimeModified.setText(DateTimeUtils.getDateTextString(requireContext(), note.getModificationDate()));
            Calendar sync = noteSyncTimes.getLastSyncTime(note.getSimperiumKey());

            if (sync != null) {
                mDateTimeSynced.setText(DateTimeUtils.getDateTextString(requireContext(), sync));
                mDateTimeSyncedLayout.setVisibility(View.VISIBLE);
            } else {
                mDateTimeSyncedLayout.setVisibility(View.GONE);
            }

            getReferences(note);
        }
    }

    private void getReferences(Note note) {
        // Hidden until the off-Main query lands, so an empty section never flashes.
        mReferencesLayout.setVisibility(View.GONE);
        NoteEditorStreams streams =
                new NoteEditorStreams(notesRepository, LifecycleOwnerKt.getLifecycleScope(this));
        streams.referencesTo(note.getSimperiumKey(), references -> {
            if (!isAdded()) {
                return;
            }

            if (references.size() > 0) {
                mReferencesLayout.setVisibility(View.VISIBLE);
                ReferenceAdapter adapter = new ReferenceAdapter(references);
                mReferences.setAdapter(adapter);
                mReferences.setLayoutManager(new LinearLayoutManager(requireContext()));
            } else {
                mReferencesLayout.setVisibility(View.GONE);
            }
        });
    }

    private class ReferenceAdapter extends RecyclerView.Adapter<ReferenceAdapter.ViewHolder> {
        private final List<NoteReference> mReferences;

        private ReferenceAdapter(List<NoteReference> references) {
            mReferences = new ArrayList<>(references);
        }

        @Override
        public int getItemCount() {
            return mReferences.size();
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
            final NoteReference reference = mReferences.get(position);
            holder.mTitle.setText(reference.getTitle());
            holder.mSubtitle.setText(
                getResources().getQuantityString(
                    R.plurals.references_count,
                    reference.getCount(),
                    reference.getCount(),
                    DateTimeUtils.getDateTextNumeric(reference.getDate())
                )
            );
            holder.mView.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AnalyticsTracker.track(
                            AnalyticsTracker.Stat.INTERNOTE_LINK_TAPPED,
                            AnalyticsTracker.CATEGORY_LINK,
                            "internote_link_tapped_info"
                        );
                        SimplenoteLinkify.openNote(mFragment.requireActivity(), reference.getKey());
                    }
                }
            );
        }

        @NonNull
        @Override
        public ReferenceAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(requireContext()).inflate(R.layout.reference_list_row, parent, false));
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView mSubtitle;
            private final TextView mTitle;
            private final View mView;

            private ViewHolder(View itemView) {
                super(itemView);
                mView = itemView;
                mTitle = itemView.findViewById(R.id.reference_title);
                mSubtitle = itemView.findViewById(R.id.reference_subtitle);
            }
        }
    }
}
