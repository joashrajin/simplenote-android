package com.automattic.simplenote;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Dialog;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.automattic.simplenote.models.Note;
import com.simperium.client.Bucket;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class HistoryBottomSheetDialogTest {
    private Fragment mHostFragment;
    private FragmentActivity mHostActivity;
    private HistoryBottomSheetDialog mSheet;

    @Before
    public void setUp() {
        mHostFragment = mock(Fragment.class);
        mHostActivity = mock(FragmentActivity.class);
        when(mHostFragment.isAdded()).thenReturn(true);
        when(mHostFragment.getActivity()).thenReturn(mHostActivity);
        when(mHostFragment.requireActivity()).thenReturn(mHostActivity);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(mHostActivity).runOnUiThread(any(Runnable.class));
        mSheet = new HistoryBottomSheetDialog(
                mHostFragment,
                mock(HistoryBottomSheetDialog.HistorySheetListener.class)
        );
    }

    @Test
    public void showInstallsNewNoteAndClearsOldRevisionsBeforeShowing() {
        HistoryBottomSheetDialog sheet = spy(mSheet);
        Note oldNote = mock(Note.class);
        Note newNote = mock(Note.class);
        ArrayList<Note> oldRevisions = new ArrayList<>();
        oldRevisions.add(mock(Note.class));
        ReflectionHelpers.setField(sheet, "mNote", oldNote);
        ReflectionHelpers.setField(sheet, "mNoteRevisionsList", oldRevisions);
        ReflectionHelpers.setField(sheet, "mLoadingView", mock(View.class));
        ReflectionHelpers.setField(sheet, "mSliderView", mock(View.class));

        AtomicReference<Note> noteAtShow = new AtomicReference<>();
        AtomicReference<ArrayList<Note>> revisionsAtShow = new AtomicReference<>();
        doAnswer(invocation -> {
            noteAtShow.set(ReflectionHelpers.getField(sheet, "mNote"));
            revisionsAtShow.set(ReflectionHelpers.getField(sheet, "mNoteRevisionsList"));
            ReflectionHelpers.setField(sheet, "mNoteRevisionsList", null);
            return null;
        }).when(sheet).showNow(any(FragmentManager.class), eq(HistoryBottomSheetDialog.TAG));

        sheet.show(mock(FragmentManager.class), newNote);

        assertSame(newNote, noteAtShow.get());
        assertNull(revisionsAtShow.get());
    }

    @Test
    public void lateOlderRequestCannotReplaceCurrentRevisions() {
        Note currentNote = mock(Note.class);
        Note olderRevision = mock(Note.class);
        Note currentRevision = mock(Note.class);
        ReflectionHelpers.setField(mSheet, "mNote", currentNote);
        Bucket.RevisionsRequestCallbacks<Note> olderCallbacks = mSheet.getRevisionsRequestCallbacks();
        Bucket.RevisionsRequestCallbacks<Note> currentCallbacks = mSheet.getRevisionsRequestCallbacks();

        currentCallbacks.onComplete(revisions(currentRevision));
        olderCallbacks.onComplete(revisions(olderRevision));

        ArrayList<Note> revisions = ReflectionHelpers.getField(mSheet, "mNoteRevisionsList");
        assertSame(currentRevision, revisions.get(0));
    }

    @Test
    public void completionQueuedBeforeNewRequestCannotApplyAfterIt() {
        Note currentNote = mock(Note.class);
        Note olderRevision = mock(Note.class);
        ArrayList<Runnable> uiTasks = new ArrayList<>();
        ReflectionHelpers.setField(mSheet, "mNote", currentNote);
        doAnswer(invocation -> {
            uiTasks.add(invocation.getArgument(0));
            return null;
        }).when(mHostActivity).runOnUiThread(any(Runnable.class));
        Bucket.RevisionsRequestCallbacks<Note> olderCallbacks = mSheet.getRevisionsRequestCallbacks();

        olderCallbacks.onComplete(revisions(olderRevision));
        mSheet.getRevisionsRequestCallbacks();
        uiTasks.get(0).run();

        assertNull(ReflectionHelpers.getField(mSheet, "mNoteRevisionsList"));
    }

    @Test
    public void errorWithoutDialogDoesNotTouchDestroyedViews() {
        mSheet.getRevisionsRequestCallbacks().onError(new RuntimeException("request failed"));
    }

    @Test
    public void currentVisibleErrorReplacesLoadingState() {
        Dialog dialog = mock(Dialog.class);
        View progress = mock(View.class);
        View error = mock(View.class);
        when(dialog.isShowing()).thenReturn(true);
        ReflectionHelpers.setField(mSheet, "mDialog", dialog);
        ReflectionHelpers.setField(mSheet, "mProgressBar", progress);
        ReflectionHelpers.setField(mSheet, "mErrorText", error);

        mSheet.getRevisionsRequestCallbacks().onError(new RuntimeException("request failed"));

        verify(progress).setVisibility(View.GONE);
        verify(error).setVisibility(View.VISIBLE);
    }

    private Map<Integer, Note> revisions(Note revision) {
        Map<Integer, Note> revisions = new LinkedHashMap<>();
        revisions.put(1, revision);
        return revisions;
    }
}
