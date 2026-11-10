package com.automattic.simplenote

import android.content.Context
import android.content.SharedPreferences
import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.NoteCountIndexer
import com.automattic.simplenote.models.NoteTagger
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Tag
import com.simperium.Simperium
import com.simperium.client.Bucket
import com.simperium.client.BucketNameInvalid
import com.simperium.client.BucketSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Pins the invariant [SimperiumBuckets] exists for: the exact Simplenote.onCreate wiring order of
 * the notes/tags bucket graph — notes before the tag schema indexes it, the NoteTagger only after
 * the tags bucket exists — plus the BucketNameInvalid contract.
 */
class SimperiumBucketsTest {
    private val sharedPreferences = mock<SharedPreferences>()
    private val context = mock<Context>()
    private val simperium = mock<Simperium>()
    private val notesBucket = mock<Bucket<Note>>()
    private val tagsBucket = mock<Bucket<Tag>>()
    private val preferencesBucket = mock<Bucket<Preferences>>()
    private val accountBucket = mock<Bucket<Account>>()

    @Before
    fun setUp() {
        whenever(sharedPreferences.all).thenReturn(emptyMap<String, Any>())
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(simperium.bucket(any<Note.Schema>())).thenReturn(notesBucket)
        whenever(simperium.bucket(any<Tag.Schema>())).thenReturn(tagsBucket)
        whenever(simperium.bucket(any<Preferences.Schema>())).thenReturn(preferencesBucket)
        whenever(simperium.bucket(any<Account.Schema>())).thenReturn(accountBucket)
    }

    @Test
    fun constructionReproducesTheOnCreateWiringOrder() {
        val buckets = SimperiumBuckets(context, simperium)

        val inOrder = inOrder(simperium, notesBucket)
        inOrder.verify(simperium).bucket(any<Note.Schema>())
        inOrder.verify(notesBucket).addListener(buckets.noteSyncTimes.bucketListener)
        inOrder.verify(simperium).bucket(any<Tag.Schema>())
        inOrder.verify(simperium).bucket(any<Preferences.Schema>())
        inOrder.verify(simperium).bucket(any<Account.Schema>())
        inOrder.verify(notesBucket).addListener(any<NoteTagger>())
        inOrder.verifyNoMoreInteractions()

        assertSame(notesBucket, buckets.notes)
        assertSame(tagsBucket, buckets.tags)
        assertSame(preferencesBucket, buckets.preferences)
        assertSame(accountBucket, buckets.account)
    }

    @Test
    fun tagSchemaIndexesTheConstructedNotesBucket() {
        SimperiumBuckets(context, simperium)

        // Mockito 5 captors are type-aware: of the four bucket() calls, only the Tag.Schema one
        // matches this captor.
        val schemaCaptor = argumentCaptor<Tag.Schema>()
        verify(simperium).bucket(schemaCaptor.capture())
        val tagSchema = schemaCaptor.firstValue

        val indexers = BucketSchema::class.java.getDeclaredField("indexers").let { field ->
            field.isAccessible = true
            field.get(tagSchema) as List<*>
        }
        val noteCountIndexer = indexers.filterIsInstance<NoteCountIndexer>().single()

        val indexedBucket = NoteCountIndexer::class.java.getDeclaredField("mNotesBucket").let { field ->
            field.isAccessible = true
            field.get(noteCountIndexer)
        }
        assertSame(notesBucket, indexedBucket)
    }

    @Test
    fun bucketNameInvalidBecomesARuntimeException() {
        whenever(simperium.bucket(any<Note.Schema>())).thenThrow(BucketNameInvalid("notes"))

        val exception = assertThrows(RuntimeException::class.java) {
            SimperiumBuckets(context, simperium)
        }

        assertEquals("Could not create bucket", exception.message)
        assertTrue(exception.cause is BucketNameInvalid)
    }
}
