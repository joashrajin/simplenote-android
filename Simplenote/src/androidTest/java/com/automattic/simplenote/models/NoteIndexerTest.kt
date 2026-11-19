package com.automattic.simplenote.models

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.simperium.client.BucketSchema.Index
import org.hamcrest.CoreMatchers.`is`
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class NoteIndexerTest {
    private lateinit var note: Note
    private lateinit var indexer: NoteIndexer

    @Before
    fun setUp() {
        val properties = JSONObject()
        properties.put(Note.CREATION_DATE_PROPERTY, DATE_DOUBLE)
        properties.put(Note.MODIFICATION_DATE_PROPERTY, DATE_DOUBLE)
        properties.put(Note.PUBLISH_URL_PROPERTY, "test.url.property")
        note = Note("test", properties)
        indexer = NoteIndexer()
    }

    @Test
    fun testIndexMillisecondDates() {
        val indexes = indexer.index(note)
        assertIndex(indexes, Note.MODIFIED_INDEX_NAME, DATE_LONG)
        assertIndex(indexes, Note.CREATED_INDEX_NAME, DATE_LONG)
    }


    @Test
    fun testConvertMillisToSeconds() {
        val date = Note.numberToDate(DATE_DOUBLE)
        assertThat(date.timeInMillis, `is`(DATE_LONG))
    }

    @Test
    fun testConvertNumberToDateString() {
        val dateConverted = Note.numberToDateString(DATE_NUMBER)
        assertThat(dateConverted, `is`(DATE_STRING))
    }

    @Test
    fun testGetCreationDate() {
        val calendar = note.creationDate
        assertThat(calendar.timeInMillis, `is`(DATE_LONG))
    }

    @Test
    fun testGetCreationDateString() {
        val date = Note.numberToDate(DATE_DOUBLE)
        note.creationDate = date
        assertThat(note.creationDateString, `is`(DATE_STRING))
    }

    @Test
    fun testGetModificationDate() {
        val calendar = note.modificationDate
        assertThat(calendar.timeInMillis, `is`(DATE_LONG))
    }

    @Test
    fun testGetModificationDateString() {
        val date = Note.numberToDate(DATE_DOUBLE)
        note.modificationDate = date
        assertThat(note.modificationDateString, `is`(DATE_STRING))
    }

    @Test
    fun testIsPublished() {
        note.isPublished = true
        assertThat(note.isPublished, `is`(true))
    }

    @Test
    fun testIsNotPublished() {
        note.isPublished = false
        assertThat(note.isPublished, `is`(false))
    }

    private fun assertIndex(indexes: List<Index>, name: String, expected: Any) {
        for (index in indexes) {
            if (index.name == name) {
                assertThat("Index $name had incorrect value", index.value, `is`(expected))
                return
            }
        }
        fail("Did not find a index with name: $name")
    }

    companion object {
        private const val DATE_STRING = "2013-09-12T20:47:03.000Z"
        private const val DATE_DOUBLE = 1379018823037.0
        private const val DATE_LONG = 1379018823000L
        private const val DATE_NUMBER = 1379018823L
    }
}
