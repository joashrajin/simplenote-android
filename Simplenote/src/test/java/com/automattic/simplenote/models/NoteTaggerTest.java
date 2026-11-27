package com.automattic.simplenote.models;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;

import org.junit.Before;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

public class NoteTaggerTest {
    private static final String NOTE_KEY = "note-key";

    private Bucket<Note> mNotesBucket;
    private Bucket<Tag> mTagsBucket;
    private NoteTagger mNoteTagger;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        mNotesBucket = mock(Bucket.class);
        mTagsBucket = mock(Bucket.class);
        mNoteTagger = new NoteTagger(mTagsBucket);
    }

    @Test
    public void remoteTagAdditionRefreshesTagObservers() throws Exception {
        Note note = new Note(NOTE_KEY);
        when(mNotesBucket.getObject(NOTE_KEY)).thenReturn(note);
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);

        assertNull(note.getProperty(Note.TAGS_PROPERTY));
        replaceTags(note, "alpha");
        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);

        verifyTagObserversRefreshed();
    }

    @Test
    public void sameSizeTagReplacementRefreshesTagObservers() throws Exception {
        Note note = noteWithTags("alpha");

        applyRemoteModification(note, "beta");

        verifyTagObserversRefreshed();
        clearInvocations(mNotesBucket, mTagsBucket);
        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);
        verifyNoInteractions(mNotesBucket, mTagsBucket);
    }

    @Test
    public void remoteTagRemovalRefreshesTagObservers() throws Exception {
        Note note = noteWithTags("alpha", "beta");

        applyRemoteModification(note, "alpha");

        verifyTagObserversRefreshed();
    }

    @Test
    public void remoteTaggedInsertRefreshesTagObservers() throws Exception {
        Note note = noteWithTags("alpha");
        when(mNotesBucket.getObject(NOTE_KEY)).thenReturn(note);

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.INSERT, NOTE_KEY);

        verifyTagObserversRefreshed();
    }

    @Test
    public void tagOrderAndDuplicatesDoNotRefreshTagObservers() throws Exception {
        Note note = noteWithTags("alpha", "", "beta", "alpha");

        applyRemoteModification(note, "beta", "alpha");

        verifyNoInteractions(mTagsBucket);
    }

    @Test
    public void contentOnlyChangeDoesNotRefreshTagObservers() throws Exception {
        Note note = noteWithTags("alpha");
        when(mNotesBucket.getObject(NOTE_KEY)).thenReturn(note);
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);
        note.setContent("Updated content");

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);

        verifyNoInteractions(mTagsBucket);
    }

    @Test
    public void missingTagPropertyIsNotMutatedOrRefreshed() throws Exception {
        Note note = new Note(NOTE_KEY);
        when(mNotesBucket.getObject(NOTE_KEY)).thenReturn(note);

        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);
        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);

        assertNull(note.getProperty(Note.TAGS_PROPERTY));
        verifyNoInteractions(mTagsBucket);
    }

    @Test
    public void untaggedInsertDoesNotRefreshTagObservers() throws Exception {
        Note note = noteWithTags();
        when(mNotesBucket.getObject(NOTE_KEY)).thenReturn(note);

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.INSERT, NOTE_KEY);

        verifyNoInteractions(mTagsBucket);
    }

    @Test
    public void malformedEmptyTagPropertyCanTransitionToTagged() throws Exception {
        Note note = new Note(NOTE_KEY);
        note.setProperty(Note.TAGS_PROPERTY, "");
        when(mNotesBucket.getObject(NOTE_KEY)).thenReturn(note);
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);
        replaceTags(note, "alpha");

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);

        verifyTagObserversRefreshed();
    }

    @Test
    public void unsupportedChangeDiscardsPendingSnapshot() throws Exception {
        Note note = noteWithTags("alpha");
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);
        replaceTags(note, "beta");

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.REMOVE, NOTE_KEY);
        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);

        verify(mNotesBucket, never()).getObject(anyString());
        verifyNoInteractions(mTagsBucket);
    }

    @Test
    public void keylessResetDiscardsPendingSnapshots() throws Exception {
        Note note = noteWithTags("alpha");
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);
        replaceTags(note, "beta");

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.RESET, null);
        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);

        verifyNoInteractions(mNotesBucket, mTagsBucket);
    }

    @Test
    public void keylessIndexPreservesPendingSnapshot() throws Exception {
        Note note = noteWithTags("alpha");
        when(mNotesBucket.getObject(NOTE_KEY)).thenReturn(note);
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);
        replaceTags(note, "beta");

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.INDEX, null);
        verifyNoInteractions(mNotesBucket, mTagsBucket);

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);
        verifyTagObserversRefreshed();
    }

    @Test
    public void interleavedUpdatesKeepSeparateSnapshots() throws Exception {
        Note firstNote = noteWithKeyAndTags("first-key", "alpha");
        Note secondNote = noteWithKeyAndTags("second-key", "beta");
        when(mNotesBucket.getObject("first-key")).thenReturn(firstNote);
        when(mNotesBucket.getObject("second-key")).thenReturn(secondNote);
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, firstNote);
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, secondNote);
        replaceTags(firstNote, "gamma");
        replaceTags(secondNote, "delta");

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, "second-key");
        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, "first-key");

        verify(mTagsBucket, times(2)).notifyOnNetworkChangeListeners(Bucket.ChangeType.INDEX);
    }

    @Test
    public void missingPostStoreNoteDoesNotLeavePendingSnapshot() throws Exception {
        Note note = noteWithTags("alpha");
        when(mNotesBucket.getObject(NOTE_KEY)).thenThrow(new BucketObjectMissingException());
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);
        replaceTags(note, "beta");

        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);
        clearInvocations(mNotesBucket);
        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);

        verify(mNotesBucket, never()).getObject(anyString());
        verifyNoInteractions(mTagsBucket);
    }

    private void applyRemoteModification(Note note, String... updatedTags) throws Exception {
        when(mNotesBucket.getObject(NOTE_KEY)).thenReturn(note);
        mNoteTagger.onBeforeUpdateObject(mNotesBucket, note);
        replaceTags(note, updatedTags);
        mNoteTagger.onNetworkChange(mNotesBucket, Bucket.ChangeType.MODIFY, NOTE_KEY);
    }

    private void replaceTags(Note note, String... tags) throws Exception {
        JSONObject properties = new JSONObject(note.getProperties().toString());
        properties.put(Note.TAGS_PROPERTY, new JSONArray(Arrays.asList(tags)));
        note.setProperties(properties);
    }

    private Note noteWithTags(String... tags) {
        return noteWithKeyAndTags(NOTE_KEY, tags);
    }

    private Note noteWithKeyAndTags(String key, String... tags) {
        Note note = new Note(key);
        note.setTags(Arrays.asList(tags));
        return note;
    }

    private void verifyTagObserversRefreshed() {
        verify(mTagsBucket).notifyOnNetworkChangeListeners(Bucket.ChangeType.INDEX);
    }
}
