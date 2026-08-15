package com.automattic.simplenote

import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.repositories.RevisionsResult
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.automattic.simplenote.viewmodels.NoteListViewModel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the detach guard the refresh path has carried since the Phase 0 lifecycle fixes: a
 * detached [NoteListFragment.refreshList] schedules nothing and throws nothing. The in-flight
 * cancellation half of the old AsyncTask contract now lives inside [NoteListViewModel] as
 * per-refresh job cancellation, covered by its JVM test.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class NoteListFragmentLifecycleTest {
    @Test
    fun refreshListDoesNotScheduleWorkWhenDetached() {
        val fragment = newDetachedFragment()
        val thrown = AtomicReference<RuntimeException>()

        assertFalse("Fixture must be detached", fragment.isAdded)
        assertNull("A never-attached fragment has no view model", viewModelOf(fragment))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                fragment.refreshList()
            } catch (exception: RuntimeException) {
                thrown.set(exception)
            }
        }

        assertNull("Detached refresh must return without throwing", thrown.get())
        assertNull("Detached refresh must not create refresh machinery", viewModelOf(fragment))
    }

    @Test
    fun detachedRefreshNeverReachesTheViewModel() {
        val fragment = newDetachedFragment()
        val repository = CountingNotesRepository()
        setViewModel(fragment, NoteListViewModel(repository, StubPreferencesRepository(), StubTagsRepository()))

        InstrumentationRegistry.getInstrumentation().runOnMainSync { fragment.refreshList() }

        assertEquals(
            "Detached refresh must not schedule a query through the view model",
            0,
            repository.searchCount.get()
        )
    }

    private fun newDetachedFragment(): NoteListFragment {
        val reference = AtomicReference<NoteListFragment>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            reference.set(NoteListFragment())
        }
        return reference.get()
    }

    private fun viewModelOf(fragment: NoteListFragment): Any? = viewModelField().get(fragment)

    private fun setViewModel(fragment: NoteListFragment, viewModel: NoteListViewModel) {
        viewModelField().set(fragment, viewModel)
    }

    private fun viewModelField() = NoteListFragment::class.java.getDeclaredField("mViewModel").apply {
        isAccessible = true
    }

    private class CountingNotesRepository : NotesRepository {
        val searchCount = AtomicInteger(0)

        override suspend fun search(request: NoteSearchRequest): NoteQueryResult {
            searchCount.incrementAndGet()
            return NoteQueryResult.InvalidQuery
        }

        override suspend fun getNote(key: String): Note? = null
        override fun observeNote(key: String): Flow<Note?> = flowOf(null)
        override suspend fun trashedNoteCount(): Int = 0
        override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult =
            NoteQueryResult.InvalidQuery

        override suspend fun referencesTo(key: String): List<NoteReference> = emptyList()
        override suspend fun hasUnsyncedNotes(): Boolean = false
        override suspend fun allNotesForExport(): List<Note> = emptyList()
        override suspend fun createNote(content: String, key: String?): Note = Note(key ?: "fixture")
        override suspend fun saveNote(note: Note) = Unit
        override suspend fun setTrashed(keys: List<String>, trashed: Boolean) = Unit
        override suspend fun emptyTrash() = Unit
        override suspend fun setPinned(keys: List<String>, pinned: Boolean) = Unit
        override suspend fun setPreviewEnabled(key: String, enabled: Boolean) = Unit
        override suspend fun setPublished(key: String, published: Boolean) = Unit
        override suspend fun getRevisions(key: String, max: Int): RevisionsResult = RevisionsResult.Failure
        override fun noteChanges(): Flow<NoteChange> = emptyFlow()
    }

    private class StubPreferencesRepository : PreferencesRepository {
        override suspend fun isAnalyticsEnabled(): Boolean = false
        override suspend fun setAnalyticsEnabled(enabled: Boolean) = Unit
        override fun analyticsEnabledSnapshot(): Boolean = false
        override suspend fun recentSearches(): List<String> = emptyList()
        override suspend fun addRecentSearch(query: String, index: Int) = Unit
        override suspend fun removeRecentSearch(query: String): Int = -1
        override fun preferencesChanged(): Flow<Unit> = emptyFlow()
        override suspend fun sortOrder(): SortOrder = SortOrder.MODIFIED_DESC
    }

    private class StubTagsRepository : TagsRepository {
        override fun saveTag(tagName: String): Boolean = false
        override fun isTagValid(tagName: String): Boolean = false
        override fun isTagMissing(tagName: String): Boolean = false
        override fun isTagConflict(tagName: String, oldTagName: String): Boolean = false
        override fun getCanonicalTagName(tagName: String): String = tagName
        override fun renameTag(tagName: String, oldTag: Tag): Boolean = false
        override suspend fun allTags(): List<TagItem> = emptyList()
        override suspend fun searchTags(query: String): List<TagItem> = emptyList()
        override suspend fun suggestTags(query: String): List<String> = emptyList()
        override suspend fun deleteTag(tag: Tag) = Unit
        override suspend fun tagsChanged(): Flow<Boolean> = emptyFlow()
    }
}
