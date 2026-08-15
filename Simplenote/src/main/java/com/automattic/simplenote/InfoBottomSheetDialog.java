package com.automattic.simplenote;

import android.app.Dialog;
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
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.automattic.simplenote.analytics.AnalyticsTracker;
import com.automattic.simplenote.models.Note;
import com.automattic.simplenote.repositories.NoteReference;
import com.automattic.simplenote.utils.DateTimeUtils;
import com.automattic.simplenote.utils.DisplayUtils;
import com.automattic.simplenote.utils.NoteUtils;
import com.automattic.simplenote.utils.SimplenoteLinkify;
import com.automattic.simplenote.viewmodels.InfoBottomSheetViewModel;
import com.automattic.simplenote.viewmodels.ReferenceState;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class InfoBottomSheetDialog extends BottomSheetDialogBase {
    public static final String TAG = InfoBottomSheetDialog.class.getSimpleName();

    private static final String ARG_CHARACTER_COUNT = "character_count";
    private static final String ARG_CREATED = "created";
    private static final String ARG_MODIFIED = "modified";
    private static final String ARG_NOTE_KEY = "note_key";
    private static final String ARG_WORD_COUNT = "word_count";
    private static final long NO_REQUEST = 0L;

    private final Observer<ReferenceState> mReferenceObserver = this::onReferenceStateChanged;

    private LinearLayout mDateTimeSyncedLayout;
    private LinearLayout mReferencesLayout;
    private RecyclerView mReferences;
    private TextView mCountCharacters;
    private TextView mCountWords;
    private TextView mDateTimeCreated;
    private TextView mDateTimeModified;
    private TextView mDateTimeSynced;
    private InfoBottomSheetViewModel mViewModel;
    private String mNoteKey;
    private long mReferenceRequest = NO_REQUEST;

    public InfoBottomSheetDialog() {
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        setRetainInstance(false);
        return dialog;
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
        mReferences.setLayoutManager(new LinearLayoutManager(requireContext()));

        if (getDialog() != null) {
            // Set peek height to half height of screen.
            getDialog().setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
                    FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

                    if (bottomSheet != null) {
                        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                        behavior.setPeekHeight(DisplayUtils.getDisplayPixelSize(requireContext()).y / 2);
                        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        behavior.setSkipCollapsed(true);
                    }
                }
            });
        }

        return infoView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle arguments = requireArguments();
        mNoteKey = arguments.getString(ARG_NOTE_KEY);
        mCountCharacters.setText(arguments.getString(ARG_CHARACTER_COUNT));
        mCountWords.setText(arguments.getString(ARG_WORD_COUNT));
        mDateTimeCreated.setText(DateTimeUtils.getDateTextString(
                requireContext(),
                calendarFromMillis(arguments.getLong(ARG_CREATED))
        ));
        mDateTimeModified.setText(DateTimeUtils.getDateTextString(
                requireContext(),
                calendarFromMillis(arguments.getLong(ARG_MODIFIED))
        ));

        Calendar sync = ((Simplenote) requireActivity().getApplication())
                .getNoteSyncTimes()
                .getLastSyncTime(mNoteKey);
        if (sync != null) {
            mDateTimeSynced.setText(DateTimeUtils.getDateTextString(requireContext(), sync));
            mDateTimeSyncedLayout.setVisibility(View.VISIBLE);
        } else {
            mDateTimeSyncedLayout.setVisibility(View.GONE);
        }

        mViewModel = new ViewModelProvider(requireActivity()).get(InfoBottomSheetViewModel.class);
        mReferenceRequest = mViewModel.loadReferences(mNoteKey);
        mViewModel.getReferenceState().observe(getViewLifecycleOwner(), mReferenceObserver);
    }

    public void show(FragmentManager manager, Note note) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_NOTE_KEY, note.getSimperiumKey());
        arguments.putString(ARG_CHARACTER_COUNT, NoteUtils.getCharactersCount(note.getContent()));
        arguments.putString(ARG_WORD_COUNT, NoteUtils.getWordCount(note.getContent()));
        arguments.putLong(ARG_CREATED, note.getCreationDate().getTimeInMillis());
        arguments.putLong(ARG_MODIFIED, note.getModificationDate().getTimeInMillis());
        setArguments(arguments);
        showNow(manager, TAG);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        stopReferenceLoad();
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroyView() {
        stopReferenceLoad();
        super.onDestroyView();
    }

    private void onReferenceStateChanged(ReferenceState state) {
        if (state instanceof ReferenceState.Loaded) {
            ReferenceState.Loaded loaded = (ReferenceState.Loaded) state;
            if (isCurrentRequest(loaded.getRequestId(), loaded.getNoteKey())) {
                showReferences(loaded.getReferences());
            }
        } else if (state instanceof ReferenceState.Loading) {
            ReferenceState.Loading loading = (ReferenceState.Loading) state;
            if (isCurrentRequest(loading.getRequestId(), loading.getNoteKey())) {
                hideReferences();
            }
        } else if (state instanceof ReferenceState.Error) {
            ReferenceState.Error error = (ReferenceState.Error) state;
            if (isCurrentRequest(error.getRequestId(), error.getNoteKey())) {
                hideReferences();
            }
        }
    }

    private void showReferences(List<NoteReference> references) {
        if (references.size() > 0) {
            mReferencesLayout.setVisibility(View.VISIBLE);
            ReferenceAdapter adapter = new ReferenceAdapter(references);
            mReferences.setAdapter(adapter);
        } else {
            hideReferences();
        }
    }

    private void hideReferences() {
        mReferencesLayout.setVisibility(View.GONE);
        mReferences.setAdapter(null);
    }

    private void stopReferenceLoad() {
        if (mViewModel != null) {
            mViewModel.getReferenceState().removeObserver(mReferenceObserver);
            if (mReferenceRequest != NO_REQUEST) {
                mViewModel.cancelReferences(mReferenceRequest);
            }
        }
        mReferenceRequest = NO_REQUEST;
        mNoteKey = null;
    }

    private boolean isCurrentRequest(long requestId, String noteKey) {
        return requestId == mReferenceRequest && noteKey.equals(mNoteKey);
    }

    private Calendar calendarFromMillis(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return calendar;
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
                        SimplenoteLinkify.openNote(requireActivity(), reference.getKey());
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
