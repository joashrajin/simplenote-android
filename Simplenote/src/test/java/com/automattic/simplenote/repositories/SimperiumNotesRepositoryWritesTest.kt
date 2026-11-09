package com.automattic.simplenote.repositories

import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.search.SearchQueryBuilder
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import com.simperium.client.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Calendar

@ExperimentalCoroutinesApi
class SimperiumNotesRepositoryWritesTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private lateinit var notesBucket: Bucket<Note>
    private lateinit var cursor: Bucket.ObjectCursor<Note>
    private lateinit var repository: SimperiumNotesRepository

    @Before
    fun setUp() {
        notesBucket = mock()
        cursor = mock()
        whenever(notesBucket.query()).thenAnswer { Query<Note>(notesBucket) }
        whenever(notesBucket.searchObjects(any())).thenReturn(cursor)
        repository = SimperiumNotesRepository(notesBucket, SearchQueryBuilder(), coroutinesTestRule.testDispatcher)
    }

    @Test
    fun createNoteWithoutKeyReproducesTheSharedContentSequence() = runTest {
        val creationDate = Calendar.getInstance()
        val note = mock<Note>()
        whenever(note.creationDate).thenReturn(creationDate)
        whenever(notesBucket.newObject()).thenReturn(note)

        val created = repository.createNote("shared text")

        assertSame(note, created)
        inOrder(notesBucket, note) {
            verify(notesBucket).newObject()
            verify(note).setCreationDate(any())
            verify(note).setModificationDate(creationDate)
            verify(note).setContent("shared text")
            verify(note).save()
        }
        verify(note, never()).title
    }

    @Test
    fun createNoteWithKeyReproducesTheWelcomeNoteSequence() = runTest {
        val creationDate = Calendar.getInstance()
        val note = mock<Note>()
        whenever(note.creationDate).thenReturn(creationDate)
        whenever(notesBucket.newObject("welcome-android")).thenReturn(note)

        val created = repository.createNote(content = "welcome", key = "welcome-android")

        assertSame(note, created)
        inOrder(notesBucket, note) {
            verify(notesBucket).newObject("welcome-android")
            verify(note).setCreationDate(any())
            verify(note).setModificationDate(creationDate)
            verify(note).setContent("welcome")
            verify(note).title
            verify(note).save()
        }
    }

    @Test
    fun saveNotePersistsTheNote() = runTest {
        val note = mock<Note>()

        repository.saveNote(note)

        verify(note).save()
    }

    @Test
    fun setTrashedTrashesEachKeyWithTheLegacySequence() = runTest {
        val note1 = mock<Note>()
        val note2 = mock<Note>()
        whenever(notesBucket.get("key1")).thenReturn(note1)
        whenever(notesBucket.get("key2")).thenReturn(note2)

        repository.setTrashed(listOf("key1", "key2"), true)

        inOrder(notesBucket, note1, note2) {
            verify(notesBucket).get("key1")
            verify(note1).setDeleted(true)
            verify(note1).setModificationDate(any())
            verify(note1).save()
            verify(notesBucket).get("key2")
            verify(note2).setDeleted(true)
            verify(note2).setModificationDate(any())
            verify(note2).save()
        }
    }

    @Test
    fun setTrashedWithFalseRestoresTheNote() = runTest {
        val note = mock<Note>()
        whenever(note.isDeleted).thenReturn(true)
        whenever(notesBucket.get("key1")).thenReturn(note)

        repository.setTrashed(listOf("key1"), false)

        inOrder(notesBucket, note) {
            verify(notesBucket).get("key1")
            verify(note).setDeleted(false)
            verify(note).setModificationDate(any())
            verify(note).save()
        }
    }

    @Test
    fun setTrashedSkipsNotesAlreadyAtTheTargetState() = runTest {
        val note = mock<Note>()
        whenever(note.isDeleted).thenReturn(true)
        whenever(notesBucket.get("key1")).thenReturn(note)

        repository.setTrashed(listOf("key1"), true)

        verify(note, never()).setDeleted(any())
        verify(note, never()).save()
    }

    @Test
    fun setTrashedSkipsMissingKeys() = runTest {
        val note = mock<Note>()
        whenever(notesBucket.get("missing")).thenThrow(BucketObjectMissingException())
        whenever(notesBucket.get("key2")).thenReturn(note)
        objectStoreWith()

        repository.setTrashed(listOf("missing", "key2"), true)

        verify(note).setDeleted(true)
        verify(note).save()
    }

    @Test
    fun setTrashedMissingFromTheBucketFallsBackToTheObjectStoreScan() = runTest {
        whenever(notesBucket.get("key1")).thenThrow(BucketObjectMissingException())
        val ghostless = noteWithKey("key1")
        objectStoreWith(ghostless)

        repository.setTrashed(listOf("key1"), true)

        verify(ghostless).setDeleted(true)
        verify(ghostless).save()
    }

    @Test
    fun setPinnedPinsEachKeyWithTheLegacySequence() = runTest {
        val note1 = mock<Note>()
        val note2 = mock<Note>()
        whenever(notesBucket.get("key1")).thenReturn(note1)
        whenever(notesBucket.get("key2")).thenReturn(note2)

        repository.setPinned(listOf("key1", "key2"), true)

        inOrder(notesBucket, note1, note2) {
            verify(notesBucket).get("key1")
            verify(note1).setPinned(true)
            verify(note1).setModificationDate(any())
            verify(note1).save()
            verify(notesBucket).get("key2")
            verify(note2).setPinned(true)
            verify(note2).setModificationDate(any())
            verify(note2).save()
        }
    }

    @Test
    fun setPinnedSkipsNotesAlreadyAtTheTargetState() = runTest {
        val note = mock<Note>()
        whenever(note.isPinned).thenReturn(true)
        whenever(notesBucket.get("key1")).thenReturn(note)

        repository.setPinned(listOf("key1"), true)

        verify(note, never()).setPinned(any())
        verify(note, never()).save()
    }

    @Test
    fun setPinnedSkipsMissingKeys() = runTest {
        val note = mock<Note>()
        whenever(notesBucket.get("missing")).thenThrow(BucketObjectMissingException())
        whenever(notesBucket.get("key2")).thenReturn(note)
        objectStoreWith()

        repository.setPinned(listOf("missing", "key2"), true)

        verify(note).setPinned(true)
        verify(note).save()
    }

    @Test
    fun permanentlyDeleteDeletesTheBucketObject() = runTest {
        val note = mock<Note>()
        whenever(notesBucket.get("key1")).thenReturn(note)

        repository.permanentlyDelete("key1")

        verify(note).delete()
    }

    @Test
    fun permanentlyDeleteToleratesAMissingNote() = runTest {
        whenever(notesBucket.get("missing")).thenThrow(BucketObjectMissingException())
        objectStoreWith()

        repository.permanentlyDelete("missing")
    }

    @Test
    fun permanentlyDeleteMissingFromTheBucketFallsBackToTheObjectStoreScan() = runTest {
        whenever(notesBucket.get("key1")).thenThrow(BucketObjectMissingException())
        val ghostless = noteWithKey("key1")
        objectStoreWith(ghostless)

        repository.permanentlyDelete("key1")

        verify(ghostless).delete()
    }

    @Test
    fun setPreviewEnabledSetsThePropertyAndSavesWithoutTouchingTheModificationDate() = runTest {
        val note = mock<Note>()
        whenever(notesBucket.get("key1")).thenReturn(note)

        repository.setPreviewEnabled("key1", true)

        inOrder(notesBucket, note) {
            verify(notesBucket).get("key1")
            verify(note).setPreviewEnabled(true)
            verify(note).save()
        }
        verify(note, never()).setModificationDate(any())
    }

    @Test
    fun setPreviewEnabledToleratesAMissingNote() = runTest {
        whenever(notesBucket.get("missing")).thenThrow(BucketObjectMissingException())
        objectStoreWith()

        repository.setPreviewEnabled("missing", true)
    }

    @Test
    fun setPublishedSetsThePropertyAndSavesWithoutTouchingTheModificationDate() = runTest {
        val note = mock<Note>()
        whenever(notesBucket.get("key1")).thenReturn(note)

        repository.setPublished("key1", true)

        inOrder(notesBucket, note) {
            verify(notesBucket).get("key1")
            verify(note).setPublished(true)
            verify(note).save()
        }
        verify(note, never()).setModificationDate(any())
    }

    @Test
    fun setPublishedToleratesAMissingNote() = runTest {
        whenever(notesBucket.get("missing")).thenThrow(BucketObjectMissingException())
        objectStoreWith()

        repository.setPublished("missing", false)
    }

    @Test
    fun emptyTrashDeletesEachTrashedNoteAndClosesTheCursor() = runTest {
        val note1 = mock<Note>()
        val note2 = mock<Note>()
        whenever(cursor.moveToNext()).thenReturn(true, true, false)
        whenever(cursor.getObject()).thenReturn(note1, note2)

        repository.emptyTrash()

        verify(note1).delete()
        verify(note2).delete()
        verify(cursor).close()
        val captor = argumentCaptor<Query<Note>>()
        verify(notesBucket).searchObjects(captor.capture())
        val conditions = captor.firstValue.conditions.map { condition ->
            "${condition.key} ${condition.comparisonType.name} ${condition.subject}"
        }
        assertEquals(listOf("deleted EQUAL_TO true"), conditions)
    }

    @Test
    fun getRevisionsMapsOnCompleteToSuccessInMapOrder() = runTest {
        val note = mock<Note>()
        val older = mock<Note>()
        val newer = mock<Note>()
        whenever(notesBucket.get("key1")).thenReturn(note)

        val result = async(coroutinesTestRule.testDispatcher) { repository.getRevisions("key1", 30) }
        runCurrent()
        revisionCallbacks(note).onComplete(linkedMapOf(1 to older, 2 to newer))

        assertEquals(RevisionsResult.Success(listOf(older, newer)), result.await())
    }

    @Test
    fun getRevisionsMapsOnErrorToFailure() = runTest {
        val note = mock<Note>()
        whenever(notesBucket.get("key1")).thenReturn(note)

        val result = async(coroutinesTestRule.testDispatcher) { repository.getRevisions("key1", 30) }
        runCurrent()
        revisionCallbacks(note).onError(RuntimeException("request failed"))

        assertEquals(RevisionsResult.Failure, result.await())
    }

    @Test
    fun getRevisionsWithAMissingNoteReturnsFailureWithoutRequestingRevisions() = runTest {
        whenever(notesBucket.get("missing")).thenThrow(BucketObjectMissingException())
        objectStoreWith()

        assertEquals(RevisionsResult.Failure, repository.getRevisions("missing", 30))
        verify(notesBucket, never()).getRevisions(any<Note>(), any(), any())
    }

    @Test
    fun getRevisionsCancellationIgnoresLateCallbacks() = runTest {
        val note = mock<Note>()
        whenever(notesBucket.get("key1")).thenReturn(note)

        val result = async(coroutinesTestRule.testDispatcher) { repository.getRevisions("key1", 30) }
        runCurrent()
        val callbacks = revisionCallbacks(note)
        result.cancel()
        runCurrent()
        callbacks.onComplete(linkedMapOf())
        callbacks.onError(RuntimeException("request failed"))

        assertTrue(result.isCancelled)
    }

    @Test
    fun noteChangesMapsEachBucketListenerCallback() = runTest {
        val events = mutableListOf<NoteChange>()
        val job = launch(coroutinesTestRule.testDispatcher) { repository.noteChanges().collect { events.add(it) } }
        runCurrent()

        savedNoteListener().onSaveObject(notesBucket, noteWithKey("saved"))
        runCurrent()
        deletedNoteListener().onDeleteObject(notesBucket, noteWithKey("deleted"))
        runCurrent()
        networkChangeListener().onNetworkChange(notesBucket, Bucket.ChangeType.INDEX, "changed")
        runCurrent()

        assertEquals(
            listOf(
                NoteChange.Saved("saved"),
                NoteChange.Deleted("deleted"),
                NoteChange.NetworkChanged(Bucket.ChangeType.INDEX, "changed"),
            ),
            events
        )
        job.cancel()
    }

    @Test
    fun noteChangesCancellationRemovesAllThreeListeners() = runTest {
        val job = launch(coroutinesTestRule.testDispatcher) { repository.noteChanges().collect {} }
        runCurrent()
        val networkListener = networkChangeListener()
        val saveListener = savedNoteListener()
        val deleteListener = deletedNoteListener()

        job.cancel()
        advanceUntilIdle()

        verify(notesBucket).removeOnNetworkChangeListener(networkListener)
        verify(notesBucket).removeOnSaveObjectListener(saveListener)
        verify(notesBucket).removeOnDeleteObjectListener(deleteListener)
    }

    private fun noteWithKey(key: String): Note {
        val note = mock<Note>()
        whenever(note.simperiumKey).thenReturn(key)
        return note
    }

    // A missing-key write falls back to the object-store scan the way reads do; these stubs
    // model the store's contents for that scan.
    private fun objectStoreWith(vararg notes: Note): Bucket.ObjectCursor<Note> {
        val allCursor = mock<Bucket.ObjectCursor<Note>>()
        whenever(notesBucket.allObjects()).thenReturn(allCursor)
        val moves = notes.map { true } + false
        whenever(allCursor.moveToNext()).thenReturn(moves.first(), *moves.drop(1).toTypedArray())
        if (notes.isNotEmpty()) {
            whenever(allCursor.getObject()).thenReturn(notes.first(), *notes.drop(1).toTypedArray())
        }
        return allCursor
    }

    private fun revisionCallbacks(note: Note): Bucket.RevisionsRequestCallbacks<Note> =
        argumentCaptor<Bucket.RevisionsRequestCallbacks<Note>>().run {
            verify(notesBucket).getRevisions(eq(note), eq(30), capture())
            firstValue
        }

    private fun networkChangeListener(): Bucket.OnNetworkChangeListener<Note> =
        argumentCaptor<Bucket.OnNetworkChangeListener<Note>>().run {
            verify(notesBucket).addOnNetworkChangeListener(capture())
            firstValue
        }

    private fun savedNoteListener(): Bucket.OnSaveObjectListener<Note> =
        argumentCaptor<Bucket.OnSaveObjectListener<Note>>().run {
            verify(notesBucket).addOnSaveObjectListener(capture())
            firstValue
        }

    private fun deletedNoteListener(): Bucket.OnDeleteObjectListener<Note> =
        argumentCaptor<Bucket.OnDeleteObjectListener<Note>>().run {
            verify(notesBucket).addOnDeleteObjectListener(capture())
            firstValue
        }
}
