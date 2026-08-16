package com.automattic.simplenote.utils;

import android.view.View;

import com.automattic.simplenote.R;
import com.google.android.material.snackbar.Snackbar;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UndoBarControllerTest {
    @Test
    public void eachUndoActionUsesTheIdsCapturedWhenShown() {
        View view = mock(View.class);
        Snackbar firstSnackbar = mock(Snackbar.class);
        Snackbar secondSnackbar = mock(Snackbar.class);
        ArgumentCaptor<View.OnClickListener> firstAction = ArgumentCaptor.forClass(View.OnClickListener.class);
        ArgumentCaptor<View.OnClickListener> secondAction = ArgumentCaptor.forClass(View.OnClickListener.class);
        RecordingUndoListener listener = new RecordingUndoListener();
        UndoBarController controller = new UndoBarController(listener);
        listener.controller = controller;

        try (MockedStatic<Snackbar> snackbar = mockStatic(Snackbar.class)) {
            snackbar.when(() -> Snackbar.make(view, "first", Snackbar.LENGTH_LONG)).thenReturn(firstSnackbar);
            snackbar.when(() -> Snackbar.make(view, "second", Snackbar.LENGTH_LONG)).thenReturn(secondSnackbar);
            when(firstSnackbar.setAction(eq(R.string.undo), firstAction.capture())).thenReturn(firstSnackbar);
            when(secondSnackbar.setAction(eq(R.string.undo), secondAction.capture())).thenReturn(secondSnackbar);

            controller.setDeletedNoteIds(new ArrayList<>(List.of("note-a")));
            controller.showUndoBar(view, "first");
            controller.setDeletedNoteIds(new ArrayList<>(List.of("note-b")));
            controller.showUndoBar(view, "second");

            firstAction.getValue().onClick(view);
            secondAction.getValue().onClick(view);

            assertEquals(List.of(List.of("note-a"), List.of("note-b")), listener.undoCalls);
            verify(firstSnackbar).show();
            verify(secondSnackbar).show();
        }
    }

    @Test
    public void undoActionSnapshotsSourceListWhenShown() {
        View view = mock(View.class);
        Snackbar snackbarMock = mock(Snackbar.class);
        ArgumentCaptor<View.OnClickListener> action = ArgumentCaptor.forClass(View.OnClickListener.class);
        RecordingUndoListener listener = new RecordingUndoListener();
        UndoBarController controller = new UndoBarController(listener);
        listener.controller = controller;
        List<String> deletedNoteIds = new ArrayList<>(List.of("note-a"));

        try (MockedStatic<Snackbar> snackbar = mockStatic(Snackbar.class)) {
            snackbar.when(() -> Snackbar.make(view, "deleted", Snackbar.LENGTH_LONG)).thenReturn(snackbarMock);
            when(snackbarMock.setAction(eq(R.string.undo), action.capture())).thenReturn(snackbarMock);

            controller.setDeletedNoteIds(deletedNoteIds);
            deletedNoteIds.add("note-b");
            controller.showUndoBar(view, "deleted");
            deletedNoteIds.add("note-c");
            action.getValue().onClick(view);

            assertEquals(List.of(List.of("note-a", "note-b")), listener.undoCalls);
        }
    }

    private static class RecordingUndoListener implements UndoBarController.UndoListener {
        private final List<List<String>> undoCalls = new ArrayList<>();
        private UndoBarController controller;

        @Override
        public void onUndo() {
            undoCalls.add(new ArrayList<>(controller.getDeletedNoteIds()));
        }
    }
}
