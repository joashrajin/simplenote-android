package com.automattic.simplenote.utils

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.models.Note
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class TestBucketTest {
    private lateinit var notesBucket: TestBucket<Note>

    @Before
    fun setUp() {
        notesBucket = object : TestBucket<Note>("notes") {
            override fun build(key: String?): Note = Note(key)
        }
    }

    @Test
    fun executeAppliesEveryCondition() {
        createNote("active-match", "needle", emptyList(), false)
        createNote("deleted-match", "needle", emptyList(), true)
        createNote("active-other", "haystack", emptyList(), false)

        val matchingKeys = mutableListOf<String>()
        Note.search(notesBucket, "needle").execute().use { cursor ->
            while (cursor.moveToNext()) {
                matchingKeys.add(cursor.simperiumKey)
            }
        }

        assertEquals(listOf("active-match"), matchingKeys)
    }

    @Test
    fun countAppliesEveryCondition() {
        createNote("active-target", "content", listOf("target"), false)
        createNote("deleted-target", "content", listOf("target"), true)
        createNote("active-other", "content", listOf("other"), false)

        assertEquals(1, Note.allInTag(notesBucket, "target").count())
    }

    private fun createNote(key: String, content: String, tags: List<String>, deleted: Boolean) {
        notesBucket.newObject(key).apply {
            setContent(content)
            setTags(tags)
            setDeleted(deleted)
        }
    }
}
