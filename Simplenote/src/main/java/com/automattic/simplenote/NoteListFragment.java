package com.automattic.simplenote;

import static com.automattic.simplenote.analytics.AnalyticsTracker.CATEGORY_SEARCH;
import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.RECENT_SEARCH_TAPPED;
import static com.automattic.simplenote.models.Suggestion.Type.HISTORY;
import static com.automattic.simplenote.models.Suggestion.Type.QUERY;
import static com.automattic.simplenote.models.Suggestion.Type.TAG;
import static com.automattic.simplenote.search.SearchQueryBuilder.TAG_PREFIX;
import static com.automattic.simplenote.utils.PrefUtils.ALPHABETICAL_ASCENDING;
import static com.automattic.simplenote.utils.PrefUtils.ALPHABETICAL_DESCENDING;
import static com.automattic.simplenote.utils.PrefUtils.DATE_CREATED_ASCENDING;
import static com.automattic.simplenote.utils.PrefUtils.DATE_CREATED_DESCENDING;
import static com.automattic.simplenote.utils.PrefUtils.DATE_MODIFIED_ASCENDING;
import static com.automattic.simplenote.utils.PrefUtils.DATE_MODIFIED_DESCENDING;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.TextAppearanceSpan;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.ListFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.automattic.simplenote.analytics.AnalyticsTracker;
import com.automattic.simplenote.models.Note;
import com.automattic.simplenote.models.Suggestion;
import com.automattic.simplenote.repositories.NoteQueryResult;
import com.automattic.simplenote.utils.AppLog;
import com.automattic.simplenote.utils.AppLog.Type;
import com.automattic.simplenote.utils.BrowserUtils;
import com.automattic.simplenote.utils.ChecklistUtils;
import com.automattic.simplenote.utils.DateTimeUtils;
import com.automattic.simplenote.utils.DisplayUtils;
import com.automattic.simplenote.utils.DrawableUtils;
import com.automattic.simplenote.utils.NetworkUtils;
import com.automattic.simplenote.utils.PrefUtils;
import com.automattic.simplenote.utils.SearchSnippetFormatter;
import com.automattic.simplenote.utils.SimplenoteLinkify;
import com.automattic.simplenote.utils.StrUtils;
import com.automattic.simplenote.utils.TextHighlighter;
import com.automattic.simplenote.utils.ThemeUtils;
import com.automattic.simplenote.utils.WidgetUtils;
import com.automattic.simplenote.viewmodels.NoteListUpdate;
import com.automattic.simplenote.viewmodels.NoteListViewModel;
import com.automattic.simplenote.viewmodels.SuggestionsUpdate;
import com.automattic.simplenote.widgets.RobotoRegularTextView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.simperium.client.Bucket;
import com.simperium.client.Bucket.ObjectCursor;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * A list fragment representing a list of Notes. This fragment also supports
 * tablet devices by allowing list items to be given an 'activated' state upon
 * selection. This helps indicate which item is currently being viewed in a
 * {@link NoteEditorFragment}.
 * <p>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
@AndroidEntryPoint
public class NoteListFragment extends ListFragment implements AdapterView.OnItemLongClickListener, AbsListView.MultiChoiceModeListener {
    /**
     * The preferences key representing the activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";
    private static final int POPUP_MENU_FIRST_ITEM_POSITION = 0;
    public static final String ACTION_NEW_NOTE = "com.automattic.simplenote.NEW_NOTE";
    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sCallbacks = new Callbacks() {
        @Override
        public void onActionModeCreated() {
        }

        @Override
        public void onActionModeDestroyed() {
        }

        @Override
        public void onNoteSelected(String noteID, String matchOffsets, boolean isMarkdownEnabled, boolean isPreviewEnabled) {
        }
    };
    protected NotesCursorAdapter mNotesAdapter;
    private ActionMode mActionMode;
    private View mRootView;
    private RobotoRegularTextView mEmptyViewButton;
    private ImageView mEmptyViewImage;
    private TextView mEmptyViewText;
    private View mDividerLine;
    private FloatingActionButton mFloatingActionButton;
    private boolean mIsCondensedNoteList;
    private ListView mList;
    private RecyclerView mSuggestionList;
    private RelativeLayout mSuggestionLayout;
    private String mSelectedNoteId;
    private SuggestionAdapter mSuggestionAdapter;
    private NoteListViewModel mViewModel;
    /**
     * The search string the adapter's current cursor was built against — the delivered
     * NoteQueryResult.Notes searchSnapshot, never the live query text. Rendering decisions
     * keyed on it can never disagree with the columns the cursor actually carries (issue #142).
     */
    private String mRenderedSearchSnapshot;
    private int mTitleFontSize;
    private int mPreviewFontSize;
    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = sCallbacks;
    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public NoteListFragment() {
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        getListView().setItemChecked(position, true);

        if (mActionMode == null) {
            requireActivity().startActionMode(this);
        }

        return true;
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        mCallbacks.onActionModeCreated();
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.bulk_edit, menu);
        DrawableUtils.tintMenuWithAttribute(getActivity(), menu, R.attr.actionModeTextColor);
        mActionMode = actionMode;
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (getListView().getCheckedItemIds().length > 0) {
            switch (item.getItemId()) {
                case R.id.menu_link:
                    AnalyticsTracker.track(
                        AnalyticsTracker.Stat.INTERNOTE_LINK_COPIED,
                        AnalyticsTracker.CATEGORY_LINK,
                        "internote_link_copied_list"
                    );
                    BrowserUtils.copyToClipboard(requireContext(), getSelectedNoteLinks());
                    mode.finish();
                    break;
                case R.id.menu_trash:
                    trashSelectedNotes();
                    break;
                case R.id.menu_pin:
                    pinSelectedNotes();
                    break;
            }
        }

        return false;
    }

    /**
     * Replaces PinNotesTask: the legacy task toggled each checked note, so the selection is
     * partitioned by current pin state before delegating to the repository-backed view model.
     * The completion callback reproduces onPostExecute verbatim.
     */
    private void pinSelectedNotes() {
        SparseBooleanArray selectedRows = getListView().getCheckedItemPositions();
        List<String> notesToPin = new ArrayList<>();
        List<String> notesToUnpin = new ArrayList<>();

        for (int i = 0; i < selectedRows.size(); i++) {
            if (selectedRows.valueAt(i)) {
                Note note = mNotesAdapter.getItem(selectedRows.keyAt(i));

                if (note.isPinned()) {
                    notesToUnpin.add(note.getSimperiumKey());
                } else {
                    notesToPin.add(note.getSimperiumKey());
                }
            }
        }

        mViewModel.pinNotes(notesToPin, notesToUnpin, () -> {
            mActionMode.finish();
            refreshList();
        });
    }

    /**
     * Replaces TrashNotesTask with the same toggle partitioning as {@link #pinSelectedNotes()};
     * every selected key feeds the undo bar, exactly as the legacy task collected them.
     */
    private void trashSelectedNotes() {
        SparseBooleanArray selectedRows = getListView().getCheckedItemPositions();
        List<String> notesToTrash = new ArrayList<>();
        List<String> notesToRestore = new ArrayList<>();
        final List<String> deletedNoteIds = new ArrayList<>();

        for (int i = 0; i < selectedRows.size(); i++) {
            if (selectedRows.valueAt(i)) {
                Note note = mNotesAdapter.getItem(selectedRows.keyAt(i));
                deletedNoteIds.add(note.getSimperiumKey());

                if (note.isDeleted()) {
                    notesToRestore.add(note.getSimperiumKey());
                } else {
                    notesToTrash.add(note.getSimperiumKey());
                }
            }
        }

        mViewModel.trashNotes(notesToTrash, notesToRestore, () -> {
            NotesActivity notesActivity = (NotesActivity) getActivity();

            if (notesActivity != null) {
                notesActivity.showUndoBarWithNoteIds(deletedNoteIds);
            }

            if (!isDetached()) {
                updateSelectionAfterTrashAction();
                mActionMode.finish();
                refreshList();
            }
        });
    }

    private String getSelectedNoteLinks() {
        SparseBooleanArray checkedPositions = getListView().getCheckedItemPositions();
        StringBuilder links = new StringBuilder();

        for (int i = 0; i < checkedPositions.size(); i++) {
            if (checkedPositions.valueAt(i)) {
                Note note = mNotesAdapter.getItem(checkedPositions.keyAt(i));
                links.append(SimplenoteLinkify.getNoteLinkWithTitle(note.getTitle(), note.getSimperiumKey())).append("\n");
            }
        }

        return links.toString();
    }

    public List<Integer> getSelectedNotesPositions() {
        SparseBooleanArray checkedPositions = getListView().getCheckedItemPositions();
        ArrayList<Integer> positions = new ArrayList<>();

        for (int i = 0; i < checkedPositions.size(); i++) {
            if (checkedPositions.valueAt(i)) {
                positions.add(checkedPositions.keyAt(i) - mList.getHeaderViewsCount());
            }
        }

        return positions;
    }

    public void updateSelectionAfterTrashAction() {
        if (DisplayUtils.isLargeScreenLandscape(getActivity())) {
            // Try to find the nearest note to the first deleted item
            List<Integer> deletedNotesPositions = getSelectedNotesPositions();
            int firstDeletedNote = deletedNotesPositions.get(0);
            int positionToSelect = -1;
            // Loop through the notes below
            for (int i = firstDeletedNote + 1; i < mNotesAdapter.getCount(); i++) {
                if (!deletedNotesPositions.contains(i)) {
                    positionToSelect = i;
                    break;
                }
            }
            if (positionToSelect == -1) {
                // Loop through the above notes
                for (int i = firstDeletedNote - 1; i >= 0; i--) {
                    if (!deletedNotesPositions.contains(i)) {
                        positionToSelect = i;
                        break;
                    }
                }
            }

            if (positionToSelect != -1) {
                Note noteToSelect = mNotesAdapter.getItem(positionToSelect + mList.getHeaderViewsCount());
                mCallbacks.onNoteSelected(noteToSelect.getSimperiumKey(), null, noteToSelect.isMarkdownEnabled(), noteToSelect.isPreviewEnabled());
                // As we will trigger a list refresh later, save the selectedNoteId
                mSelectedNoteId = noteToSelect.getSimperiumKey();
            } else {
                // The list of notes is empty
                ((NotesActivity) requireActivity()).showDetailPlaceholder();
            }
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mCallbacks.onActionModeDestroyed();
        mActionMode = null;
        if (getActivity() != null) {
            NotesActivity notesActivity = (NotesActivity) getActivity();
            setActivateOnItemClick(DisplayUtils.isLargeScreenLandscape(notesActivity));
            if (mSelectedNoteId == null) {
                notesActivity.showDetailPlaceholder();
            }
        }
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode actionMode, int position, long id, boolean checked) {
        int checkedCount = getListView().getCheckedItemCount();

        if (checkedCount == 0) {
            actionMode.setTitle("");
        } else {
            actionMode.setTitle(getResources().getQuantityString(R.plurals.selected_notes, checkedCount, checkedCount));
        }

        actionMode.invalidate();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.add(Type.NETWORK, NetworkUtils.getNetworkInfo(requireContext()));
        AppLog.add(Type.SCREEN, "Created (NoteListFragment)");
        mViewModel = new ViewModelProvider(this).get(NoteListViewModel.class);

        if (savedInstanceState != null) {
            // The legacy search fields died with the fragment instance; the view model outlives
            // it across a recreation, so restore their fresh-instance defaults explicitly.
            // The sticky pre-rotation search delivery may render one stale search frame
            // before the resume refresh supersedes it; legacy showed an empty list here.
            mViewModel.stopSearching();
            mViewModel.clearSearchQuery();
        }
    }

    protected void getPrefs() {
        mIsCondensedNoteList = PrefUtils.getBoolPref(getActivity(), PrefUtils.PREF_CONDENSED_LIST, false);
        mTitleFontSize = PrefUtils.getFontSize(getActivity());
        mPreviewFontSize = mTitleFontSize - 2;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notes_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NotesActivity notesActivity = (NotesActivity) requireActivity();

        if (ACTION_NEW_NOTE.equals(notesActivity.getIntent().getAction()) &&
            !notesActivity.userIsUnauthorized()) {
            //if user tap on "app shortcut", create a new note
            createNewNote("", "new_note_shortcut");
        }

        mRootView = view.findViewById(R.id.list_root);

        LinearLayout emptyView = view.findViewById(android.R.id.empty);
        emptyView.setVisibility(View.GONE);
        mEmptyViewButton = emptyView.findViewById(R.id.button);
        mEmptyViewImage = emptyView.findViewById(R.id.image);
        mEmptyViewText = emptyView.findViewById(R.id.text);
        setEmptyListImage(R.drawable.ic_notes_24dp);
        setEmptyListMessage(getString(R.string.empty_notes_all));
        mDividerLine = view.findViewById(R.id.divider_line);

        if (DisplayUtils.isLargeScreenLandscape(notesActivity)) {
            setActivateOnItemClick(true);
            mDividerLine.setVisibility(View.VISIBLE);
        }

        mFloatingActionButton = view.findViewById(R.id.fab_button);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewNote("", "action_bar_button");
            }
        });
        mFloatingActionButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (v.isHapticFeedbackEnabled()) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }

                Toast.makeText(getContext(), requireContext().getString(R.string.new_note), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mSuggestionLayout = view.findViewById(R.id.suggestion_layout);
        mSuggestionList = view.findViewById(R.id.suggestion_list);
        mSuggestionAdapter = new SuggestionAdapter(new ArrayList<Suggestion>());
        mSuggestionList.setAdapter(mSuggestionAdapter);
        mSuggestionList.setLayoutManager(new LinearLayoutManager(requireContext()));

        mList = view.findViewById(android.R.id.list);

        mNotesAdapter = new NotesCursorAdapter(requireContext(), null, 0);
        setListAdapter(mNotesAdapter);

        mViewModel.getNoteList().observe(getViewLifecycleOwner(), this::onNoteListUpdated);
        mViewModel.getSuggestions().observe(getViewLifecycleOwner(), this::onSuggestionsUpdated);

        getListView().setOnItemLongClickListener(this);
        getListView().setMultiChoiceModeListener(this);
    }

    @Override
    public void onDestroyView() {
        if (mNotesAdapter != null) {
            mNotesAdapter.swapNoteCursor(null);
        }
        super.onDestroyView();
    }

    /**
     * Applies a suggestion feed exactly as the legacy inline queries did: getSearchItems
     * diffed the recent searches into whichever adapter was live (even the tag-suggestion one,
     * as the preference-bucket listeners fired), while getTagSuggestions installed a fresh
     * adapter.
     */
    private void onSuggestionsUpdated(SuggestionsUpdate update) {
        if (update.isRecentSearches()) {
            mSuggestionAdapter.updateItems(update.getSuggestions());
        } else {
            mSuggestionAdapter = new SuggestionAdapter(update.getSuggestions());
            mSuggestionList.setAdapter(mSuggestionAdapter);
        }
    }

    /**
     * Renders a refresh delivered by {@link NoteListViewModel}, running the exact callback
     * chain RefreshListTask.onPostExecute ran. The adapter borrows the ViewModel-owned cursor;
     * the invalid-query contract clears the list. On a sticky redelivery (view recreated, same
     * update instance) only the idempotent cursor swap repeats.
     */
    private void onNoteListUpdated(NoteListUpdate update) {
        int count;

        // Reproduces the legacy onPostExecute suppression: no swap or side effects while the
        // activity is gone or finishing. An unconsumed update's cursor is closed by the model.
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }

        if (update.getResult() instanceof NoteQueryResult.Notes) {
            NoteQueryResult.Notes notes = (NoteQueryResult.Notes) update.getResult();
            Bucket.ObjectCursor<Note> cursor = notes.getCursor();

            // Defensive since the search path joined the shared pipeline (nothing swaps
            // cursors around the model anymore): never hand the adapter a closed cursor.
            if (cursor.isClosed()) {
                if (mViewModel.isSearching()) {
                    mViewModel.refreshListForSearch();
                } else {
                    refreshList();
                }
                return;
            }

            mRenderedSearchSnapshot = notes.getSearchSnapshot();
            mNotesAdapter.swapNoteCursor(cursor);
            count = mNotesAdapter.getCount();
        } else {
            mRenderedSearchSnapshot = null;
            mNotesAdapter.swapNoteCursor(null);
            count = 0;
        }

        if (!update.consumeSideEffects()) {
            return;
        }

        NotesActivity notesActivity = (NotesActivity) getActivity();

        if (notesActivity != null) {
            if (update.isFromNavSelect() && DisplayUtils.isLargeScreenLandscape(notesActivity)) {
                if (count == 0) {
                    notesActivity.showDetailPlaceholder();
                } else {
                    // Select the first note
                    selectFirstNote();
                }
            }

            notesActivity.updateTrashMenuItem(true);
        }

        if (mSelectedNoteId != null) {
            setNoteSelected(mSelectedNoteId);
            mSelectedNoteId = null;
        }
    }

    public void showListPadding(boolean show) {
        mList.setPadding(
            mList.getPaddingLeft(),
            mList.getPaddingTop(),
            mList.getPaddingRight(),
            show ? (int) getResources().getDimension(R.dimen.note_list_item_padding_bottom_button) : 0
        );
    }

    public void createNewNote(String title, String label) {
        if (!isAdded()) {
            return;
        }

        addNote(title);
        AnalyticsTracker.track(
            AnalyticsTracker.Stat.LIST_NOTE_CREATED,
            AnalyticsTracker.CATEGORY_NOTE,
            label
        );
    }

    @Override
    public void onAttach(@NonNull Context activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onResume() {
        super.onResume();
        getPrefs();

        if (mViewModel.isSearching()) {
            mViewModel.refreshListForSearch();
        } else {
            refreshList();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        AppLog.add(Type.SCREEN, "Paused (NoteListFragment)");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sCallbacks;
    }

    public void setEmptyListButton(String message) {
        if (mEmptyViewButton != null) {
            if (!message.isEmpty()) {
                mEmptyViewButton.setVisibility(View.VISIBLE);
                mEmptyViewButton.setText(message);
            } else {
                mEmptyViewButton.setVisibility(View.GONE);
            }
        }
    }

    public void setEmptyListImage(@DrawableRes int image) {
        if (mEmptyViewImage != null) {
            if (image != -1) {
                mEmptyViewImage.setVisibility(View.VISIBLE);
                mEmptyViewImage.setImageResource(image);
            } else {
                mEmptyViewImage.setVisibility(View.GONE);
            }
        }
    }

    public void setEmptyListMessage(String message) {
        if (mEmptyViewText != null && message != null) {
            mEmptyViewText.setText(message);
        }
    }

    @Override
    public void onListItemClick(@NonNull ListView listView, @NonNull View view, int position, long id) {
        if (!isAdded()) return;
        super.onListItemClick(listView, view, position, id);

        NoteViewHolder holder = (NoteViewHolder) view.getTag();
        String noteID = holder.getNoteId();

        if (noteID != null) {
            Note note = mNotesAdapter.getItem(position);
            mCallbacks.onNoteSelected(noteID, holder.mMatchOffsets, note.isMarkdownEnabled(), note.isPreviewEnabled());
        }

        mActivatedPosition = position;
    }

    /**
     * Selects first row in the list if available
     */
    public void selectFirstNote() {
        if (mNotesAdapter.getCount() > 0) {
            Note selectedNote = mNotesAdapter.getItem(mList.getHeaderViewsCount());
            mCallbacks.onNoteSelected(selectedNote.getSimperiumKey(), null, selectedNote.isMarkdownEnabled(), selectedNote.isPreviewEnabled());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    public View getRootView() {
        return mRootView;
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        getListView().setChoiceMode(activateOnItemClick ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
    }

    public void setActivatedPosition(int position) {
        if (getListView() != null) {
            if (position == ListView.INVALID_POSITION) {
                getListView().setItemChecked(mActivatedPosition, false);
            } else {
                getListView().setItemChecked(position, true);
            }

            mActivatedPosition = position;
        }
    }

    public void setDividerVisible(boolean visible) {
        mDividerLine.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setFloatingActionButtonVisible(boolean visible) {
        if (mFloatingActionButton == null) return;

        if (visible) {
            mFloatingActionButton.show();
        } else {
            mFloatingActionButton.hide();
        }
    }

    public void refreshList() {
        refreshList(false);
    }

    public void refreshList(boolean fromNav) {
        // Detached-refresh contract, pinned by NoteListFragmentLifecycleTest: schedule nothing,
        // throw nothing. The guard must run before any view model access because a never-attached
        // fragment has no view model at all.
        Context context = getContext();
        if (context == null) {
            return;
        }

        NotesActivity notesActivity = (NotesActivity) getActivity();
        if (notesActivity == null) {
            return;
        }

        mViewModel.refreshList(notesActivity.getSelectedTag().getFilter(), fromNav);

        WidgetUtils.updateNoteWidgets(context.getApplicationContext());
    }

    public void refreshListFromNavSelect() {
        refreshList(true);
    }

    public void addNote(String title) {
        // Prevents jarring 'New note...' from showing in the list view when creating a new note
        NotesActivity notesActivity = (NotesActivity) requireActivity();

        if (!DisplayUtils.isLargeScreenLandscape(notesActivity)) {
            notesActivity.muteNoteChanges();
        }

        // Create & save new note
        Simplenote simplenote = (Simplenote) requireActivity().getApplication();
        Bucket<Note> notesBucket = simplenote.getNotesBucket();
        final Note note = notesBucket.newObject();
        note.setContent(title);
        note.setCreationDate(Calendar.getInstance());
        note.setModificationDate(note.getCreationDate());
        note.setMarkdownEnabled(PrefUtils.getBoolPref(getActivity(), PrefUtils.PREF_MARKDOWN_ENABLED, false));

        if (notesActivity.getSelectedTag() != null && notesActivity.getSelectedTag().name != null) {
            String tagName = notesActivity.getSelectedTag().name;

            if (!tagName.equals(getString(R.string.all_notes)) && !tagName.equals(getString(R.string.trash)) && !tagName.equals(getString(R.string.untagged_notes))) {
                note.setTagString(tagName);
            }
        }

        note.save();

        if (DisplayUtils.isLargeScreenLandscape(getActivity())) {
            // Hack: Simperium saves async so we add a small delay to ensure the new note is truly
            // saved before proceeding.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCallbacks.onNoteSelected(note.getSimperiumKey(), null, note.isMarkdownEnabled(), note.isPreviewEnabled());
                }
            }, 50);
        } else {
            Bundle arguments = new Bundle();
            arguments.putString(NoteEditorFragment.ARG_ITEM_ID, note.getSimperiumKey());
            arguments.putBoolean(NoteEditorFragment.ARG_NEW_NOTE, true);
            arguments.putBoolean(NoteEditorFragment.ARG_MARKDOWN_ENABLED, note.isMarkdownEnabled());
            arguments.putBoolean(NoteEditorFragment.ARG_PREVIEW_ENABLED, note.isPreviewEnabled());
            Intent editNoteIntent = new Intent(getActivity(), NoteEditorActivity.class);
            editNoteIntent.putExtras(arguments);

            requireActivity().startActivityForResult(editNoteIntent, Simplenote.INTENT_EDIT_NOTE);
        }
    }

    public void setNoteSelected(String selectedNoteID) {
        // Loop through notes and set note selected if found
        //noinspection unchecked
        ObjectCursor<Note> cursor = (ObjectCursor<Note>) mNotesAdapter.getCursor();
        if (cursor != null) {
            for (int i = 0; i < cursor.getCount(); i++) {
                cursor.moveToPosition(i);
                String noteKey = cursor.getSimperiumKey();
                if (noteKey != null && noteKey.equals(selectedNoteID)) {
                    setActivatedPosition(i + mList.getHeaderViewsCount());
                    return;
                }
            }
        }

        // Didn't find the note, let's try again after the cursor updates (see onNoteListUpdated)
        mSelectedNoteId = selectedNoteID;
    }

    /**
     * The search entry point NotesActivity's query listeners drive. State, suggestions, and
     * the submit refresh live in the view model; only the overlay visibility stays here (the
     * legacy method showed it, then hid it again in the same pass when submitting).
     */
    public void searchNotes(String searchString, boolean isSubmit) {
        mSuggestionLayout.setVisibility(isSubmit ? View.GONE : View.VISIBLE);
        mViewModel.searchNotes(searchString, isSubmit);
    }

    /**
     * Clear search and load all notes. The double refresh reproduces the legacy sequence: a
     * tag-filtered pass while the query is still set, then the final unfiltered pass — the
     * second superseding the first through the view model pipeline exactly as the legacy task
     * cancellation did.
     */
    public void clearSearch() {
        mViewModel.stopSearching();
        mSuggestionLayout.setVisibility(View.GONE);
        refreshList();

        if (mViewModel.hasSearchQuery()) {
            mViewModel.clearSearchQuery();
            refreshList();
        }
    }

    public void addSearchItem(String item, int index) {
        mViewModel.addRecentSearch(item, index);
    }

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when action mode is created.
         */
        void onActionModeCreated();

        /**
         * Callback for when action mode is destroyed.
         */
        void onActionModeDestroyed();

        /**
         * Callback for when a note has been selected.
         */
        void onNoteSelected(String noteID, String matchOffsets, boolean isMarkdownEnabled, boolean isPreviewEnabled);
    }

    // view holder for NotesCursorAdapter
    private static class NoteViewHolder {
        private ImageView mHasCollaborators;
        private ImageView mPinned;
        private ImageView mPublished;
        private TextView mContent;
        private TextView mDate;
        private TextView mTitle;
        private String mMatchOffsets;
        private String mNoteId;
        private View mStatus;

        public String getNoteId() {
            return mNoteId;
        }

        public void setNoteId(String noteId) {
            mNoteId = noteId;
        }
    }

    public class NotesCursorAdapter extends CursorAdapter {
        private ObjectCursor<Note> mCursor;

        private SearchSnippetFormatter.SpanFactory mSnippetHighlighter = new TextHighlighter(requireActivity(),
            R.attr.listSearchHighlightForegroundColor, R.attr.listSearchHighlightBackgroundColor);

        public NotesCursorAdapter(Context context, ObjectCursor<Note> c, int flags) {
            super(context, c, flags);
            mCursor = c;
        }

        public void swapNoteCursor(ObjectCursor<Note> cursor) {
            mCursor = cursor;
            super.swapCursor(cursor);
        }

        @Override
        public Note getItem(int position) {
            mCursor.moveToPosition(position - mList.getHeaderViewsCount());
            return mCursor.getObject();
        }

        /*
         *  nbradbury - implemented "holder pattern" to boost performance with large note lists
         */
        @Override
        @SuppressLint("Range")
        public View getView(final int position, View view, ViewGroup parent) {
            final NoteViewHolder holder;

            if (view == null) {
                view = View.inflate(requireContext(), R.layout.note_list_row, null);
                holder = new NoteViewHolder();
                holder.mTitle = view.findViewById(R.id.note_title);
                holder.mContent = view.findViewById(R.id.note_content);
                holder.mDate = view.findViewById(R.id.note_date);
                holder.mHasCollaborators = view.findViewById(R.id.note_shared);
                holder.mPinned = view.findViewById(R.id.note_pinned);
                holder.mPublished = view.findViewById(R.id.note_published);
                holder.mStatus = view.findViewById(R.id.note_status);
                view.setTag(holder);
            } else {
                holder = (NoteViewHolder) view.getTag();
            }

            if (holder.mTitle.getTextSize() != mTitleFontSize) {
                holder.mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTitleFontSize);
                holder.mContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, mPreviewFontSize);
                holder.mDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, mPreviewFontSize);
            }

            if (position == getListView().getCheckedItemPosition()) {
                view.setActivated(true);
            } else {
                view.setActivated(false);
            }

            // for performance reasons we are going to get indexed values
            // from the cursor instead of instantiating the entire bucket object
            boolean isSearching = mViewModel.isSearching();
            holder.mContent.setVisibility(mIsCondensedNoteList ? View.GONE : View.VISIBLE);
            mCursor.moveToPosition(position);
            holder.setNoteId(mCursor.getSimperiumKey());
            Calendar date = getDateByPreference(mCursor.getObject());
            holder.mDate.setText(date != null ? DateTimeUtils.getDateTextNumeric(date) : "");
            holder.mDate.setVisibility(isSearching && date != null ? View.VISIBLE : View.GONE);
            boolean hasCollaborators = mCursor.getObject().hasCollaborators();
            holder.mHasCollaborators.setVisibility(!hasCollaborators || isSearching ? View.GONE : View.VISIBLE);
            boolean isPinned = mCursor.getObject().isPinned();
            holder.mPinned.setVisibility(!isPinned || isSearching ? View.GONE : View.VISIBLE);
            boolean isPublished = !mCursor.getObject().getPublishedUrl().isEmpty();
            holder.mPublished.setVisibility(!isPublished || isSearching ? View.GONE : View.VISIBLE);
            boolean showIcons = isPinned || isPublished || hasCollaborators;
            boolean showDate = isSearching && date != null;
            holder.mStatus.setVisibility(showIcons || showDate ? View.VISIBLE : View.GONE);
            String title = mCursor.getString(mCursor.getColumnIndexOrThrow(Note.TITLE_INDEX_NAME));

            if (TextUtils.isEmpty(title)) {
                SpannableString newNoteString = new SpannableString(getString(R.string.new_note_list));
                newNoteString.setSpan(new TextAppearanceSpan(getActivity(), R.style.UntitledNoteAppearance),
                    0,
                    newNoteString.length(),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                newNoteString.setSpan(new AbsoluteSizeSpan(mTitleFontSize, true),
                    0,
                    newNoteString.length(),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                holder.mTitle.setText(newNoteString);
            } else {
                SpannableStringBuilder titleChecklistString = new SpannableStringBuilder(title);
                titleChecklistString = (SpannableStringBuilder) ChecklistUtils.addChecklistSpansForRegexAndColor(
                    getContext(),
                    titleChecklistString,
                    ChecklistUtils.CHECKLIST_REGEX,
                    ThemeUtils.getThemeTextColorId(getContext()),
                    true
                );
                holder.mTitle.setText(titleChecklistString);
            }

            holder.mMatchOffsets = null;
            int matchOffsetsIndex = -1;

            try {
                matchOffsetsIndex = mCursor.getColumnIndexOrThrow("match_offsets");
            } catch (IllegalArgumentException ignored) {}

            // Snippet rendering keys off the delivered search snapshot, never the live query
            // text, so it can only see columns the displayed cursor was actually built with.
            if (!TextUtils.isEmpty(mRenderedSearchSnapshot) && matchOffsetsIndex != -1) {
                title = mCursor.getString(mCursor.getColumnIndexOrThrow(Note.MATCHED_TITLE_INDEX_NAME));
                String snippet = mCursor.getString(mCursor.getColumnIndexOrThrow(Note.MATCHED_CONTENT_INDEX_NAME));
                holder.mMatchOffsets = mCursor.getString(matchOffsetsIndex);

                try {
                    holder.mContent.setText(SearchSnippetFormatter.formatString(
                        getContext(),
                        snippet,
                        mSnippetHighlighter,
                        R.color.text_title_disabled));
                    holder.mTitle.setText(SearchSnippetFormatter.formatString(
                        getContext(),
                        title,
                        mSnippetHighlighter, ThemeUtils.getThemeTextColorId(getContext())));
                } catch (NullPointerException e) {
                    title = StrUtils.notNullStr(mCursor.getString(mCursor.getColumnIndexOrThrow(Note.TITLE_INDEX_NAME)));
                    holder.mTitle.setText(title);
                    String matchedContentPreview = StrUtils.notNullStr(mCursor.getString(mCursor.getColumnIndexOrThrow(Note.CONTENT_PREVIEW_INDEX_NAME)));
                    holder.mContent.setText(matchedContentPreview);
                }
            } else if (!mIsCondensedNoteList) {
                String contentPreview = mCursor.getString(mCursor.getColumnIndexOrThrow(Note.CONTENT_PREVIEW_INDEX_NAME));

                if (title == null || title.equals(contentPreview) || title.equals(getString(R.string.new_note_list))) {
                    holder.mContent.setVisibility(View.GONE);
                } else {
                    holder.mContent.setText(contentPreview);
                    SpannableStringBuilder checklistString = new SpannableStringBuilder(contentPreview);
                    checklistString = (SpannableStringBuilder) ChecklistUtils.addChecklistSpansForRegexAndColor(
                        getContext(),
                        checklistString,
                        ChecklistUtils.CHECKLIST_REGEX,
                        R.color.text_title_disabled,
                        true
                    );
                    holder.mContent.setText(checklistString);
                }
            }

            // Add mouse right click support for showing a popup menu
            view.setOnTouchListener(new View.OnTouchListener() {
                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getButtonState() == MotionEvent.BUTTON_SECONDARY && event.getAction() == MotionEvent.ACTION_DOWN) {
                        showPopupMenuAtPosition(view, position);
                        return true;
                    }

                    return false;
                }
            });

            return view;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
            return null;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
        }
    }

    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.ViewHolder> {
        private final List<Suggestion> mSuggestions;

        private SuggestionAdapter(List<Suggestion> suggestions) {
            mSuggestions = new ArrayList<>(suggestions);
        }

        @Override
        public int getItemCount() {
            return mSuggestions.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mSuggestions.get(position).getType();
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
            switch (holder.mViewType) {
                case HISTORY:
                    holder.mSuggestionText.setText(mSuggestions.get(position).getName());
                    holder.mSuggestionIcon.setImageResource(R.drawable.ic_history_24dp);
                    holder.mButtonDelete.setVisibility(View.VISIBLE);
                    break;
                case QUERY:
                    holder.mSuggestionText.setText(mSuggestions.get(position).getName());
                    holder.mSuggestionIcon.setImageResource(R.drawable.ic_search_24dp);
                    holder.mButtonDelete.setVisibility(View.GONE);
                    break;
                case TAG:
                    holder.mSuggestionText.setText(TAG_PREFIX + mSuggestions.get(position).getName());
                    holder.mSuggestionIcon.setImageResource(R.drawable.ic_tag_24dp);
                    holder.mButtonDelete.setVisibility(View.GONE);
                    break;
            }

            holder.mButtonDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!isAdded()) {
                        return;
                    }

                    final String item = holder.mSuggestionText.getText().toString();
                    mViewModel.removeRecentSearch(item);
                    Snackbar
                        .make(getRootView(), R.string.snackbar_deleted_recent_search, Snackbar.LENGTH_LONG)
                        .setAction(
                            getString(R.string.undo),
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    mViewModel.restoreRemovedRecentSearch(item);
                                }
                            }
                        )
                        .show();
                }
            });
            holder.mButtonDelete.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (v.isHapticFeedbackEnabled()) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }

                    Toast.makeText(getContext(), requireContext().getString(R.string.description_delete_item), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ((NotesActivity) requireActivity()).submitSearch(holder.mSuggestionText.getText().toString());

                    if (holder.mViewType == HISTORY) {
                        AnalyticsTracker.track(
                            RECENT_SEARCH_TAPPED,
                            CATEGORY_SEARCH,
                            "recent_search_tapped"
                        );
                    }
                }
            });
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(requireContext()).inflate(R.layout.search_suggestion, parent, false), viewType);
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private ImageButton mButtonDelete;
            private ImageView mSuggestionIcon;
            private TextView mSuggestionText;
            private View mView;
            private int mViewType;

            private ViewHolder(View itemView, int viewType) {
                super(itemView);
                mView = itemView;
                mViewType = viewType;
                mSuggestionText = itemView.findViewById(R.id.suggestion_text);
                mSuggestionIcon = itemView.findViewById(R.id.suggestion_icon);
                mButtonDelete = itemView.findViewById(R.id.suggestion_delete);
            }
        }

        private void updateItems(List<Suggestion> suggestions) {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SuggestionDiffCallback(mSuggestions, suggestions));
            mSuggestions.clear();
            mSuggestions.addAll(suggestions);
            diffResult.dispatchUpdatesTo(this);
        }
    }

    private class SuggestionDiffCallback extends DiffUtil.Callback {
        private List<Suggestion> mListNew;
        private List<Suggestion> mListOld;

        private SuggestionDiffCallback(List<Suggestion> oldList, List<Suggestion> newList) {
            mListOld = oldList;
            mListNew = newList;
        }

        @Override
        public boolean areContentsTheSame(int itemPositionOld, int itemPositionNew) {
            Suggestion itemOld = mListOld.get(itemPositionOld);
            Suggestion itemNew = mListNew.get(itemPositionNew);
            return itemOld.getName().equalsIgnoreCase(itemNew.getName());
        }

        @Override
        public boolean areItemsTheSame(int itemPositionOld, int itemPositionNew) {
            Suggestion itemOld = mListOld.get(itemPositionOld);
            Suggestion itemNew = mListNew.get(itemPositionNew);
            return itemOld.getName().equalsIgnoreCase(itemNew.getName());
        }

        @Override
        public int getNewListSize() {
            return mListNew.size();
        }

        @Override
        public int getOldListSize() {
            return mListOld.size();
        }
    }

    private Calendar getDateByPreference(Note note) {
        switch (PrefUtils.getIntPref(requireContext(), PrefUtils.PREF_SORT_ORDER)) {
            case DATE_CREATED_ASCENDING:
            case DATE_CREATED_DESCENDING:
                return note.getCreationDate();
            case DATE_MODIFIED_ASCENDING:
            case DATE_MODIFIED_DESCENDING:
                return note.getModificationDate();
            case ALPHABETICAL_ASCENDING:
            case ALPHABETICAL_DESCENDING:
            default:
                return null;
        }
    }

    private void showPopupMenuAtPosition(View view, int position) {
        if (view.getContext() == null) {
            return;
        }

        final Note note = mNotesAdapter.getItem(position + mList.getHeaderViewsCount());
        if (note == null) {
            return;
        }

        PopupMenu popup = new PopupMenu(view.getContext(), view, Gravity.END);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.bulk_edit, popup.getMenu());

        if (!getListView().isLongClickable()) {
            // If viewing the trash, remove pin menu item and change trash menu title to 'Restore'
            popup.getMenu().removeItem(R.id.menu_pin);
            if (popup.getMenu().getItem(POPUP_MENU_FIRST_ITEM_POSITION) != null) {
                popup.getMenu().getItem(POPUP_MENU_FIRST_ITEM_POSITION).setTitle(R.string.restore);
            }
        } else if (popup.getMenu().getItem(POPUP_MENU_FIRST_ITEM_POSITION) != null) {
            // If not viewing the trash, set pin menu title based on note pin state
            int pinTitle = note.isPinned() ? R.string.unpin_from_top : R.string.pin_to_top;
            popup.getMenu().getItem(POPUP_MENU_FIRST_ITEM_POSITION).setTitle(pinTitle);
        }

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_pin:
                        note.setPinned(!note.isPinned());
                        note.setModificationDate(Calendar.getInstance());
                        note.save();
                        refreshList();
                        return true;
                    case R.id.menu_trash:
                        if (getActivity() != null) {
                            ((NotesActivity) getActivity()).trashNote(note);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        popup.show();
    }

}
