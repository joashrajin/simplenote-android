package com.automattic.simplenote.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.repositories.CollaboratorsRepository
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.usecases.GetTagsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@ExperimentalCoroutinesApi
class TagsViewModelSearchTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val repository = ControllableTagsRepository()
    private val collaboratorsRepository = mock<CollaboratorsRepository> {
        on { isValidCollaborator(any()) } doReturn false
    }
    private val viewModel by lazy {
        TagsViewModel(repository, GetTagsUseCase(repository, collaboratorsRepository))
    }

    private fun runCurrent() = coroutinesTestRule.testDispatcher.scheduler.runCurrent()
    private fun advanceUntilIdle() = coroutinesTestRule.testDispatcher.scheduler.advanceUntilIdle()

    @Test
    fun olderSearchCannotReplaceNewerResults() {
        val olderResults = repository.enqueueSearch("a")
        val newerResults = repository.enqueueSearch("ab")

        viewModel.search("a")
        runCurrent()
        viewModel.search("ab")
        runCurrent()

        val expectedItems = listOf(tagItem("ab-result"))
        newerResults.complete(expectedItems)
        runCurrent()
        olderResults.complete(listOf(tagItem("a-result")))
        advanceUntilIdle()

        assertEquals("ab", viewModel.uiState.value?.searchQuery)
        assertEquals(listOf("ab-result"), currentTagNames())
        assertEquals(true, viewModel.uiState.value?.searchUpdate)
    }

    @Test
    fun closeSearchLoadsAllTagsAndRejectsPendingResults() {
        val initialResults = repository.enqueueSearch("a")
        viewModel.search("a")
        runCurrent()
        initialResults.complete(listOf(tagItem("a-result")))
        advanceUntilIdle()

        val pendingResults = repository.enqueueSearch("ab")
        val expectedItems = listOf(tagItem("all-result"))
        repository.allTagItems = expectedItems
        viewModel.search("ab")
        runCurrent()
        viewModel.closeSearch()
        runCurrent()

        assertNull(viewModel.uiState.value?.searchQuery)
        assertEquals(listOf("all-result"), currentTagNames())
        assertEquals(true, viewModel.uiState.value?.searchUpdate)

        pendingResults.complete(listOf(tagItem("ab-result")))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value?.searchQuery)
        assertEquals(listOf("all-result"), currentTagNames())
        assertEquals(true, viewModel.uiState.value?.searchUpdate)
    }

    @Test
    fun resultRefreshUsesPendingSearchQuery() {
        repository.allTagItems = listOf(tagItem("all-result"))
        viewModel.start()
        advanceUntilIdle()

        val pendingResults = repository.enqueueSearch("a")
        val refreshedItems = listOf(tagItem("refreshed-a-result"))
        repository.enqueueSearch("a").complete(refreshedItems)
        viewModel.search("a")
        runCurrent()
        viewModel.updateOnResult()
        runCurrent()

        assertEquals(listOf("a", "a"), repository.searchRequests)
        assertEquals("a", viewModel.uiState.value?.searchQuery)
        assertEquals(listOf("refreshed-a-result"), currentTagNames())
        assertEquals(true, viewModel.uiState.value?.searchUpdate)

        pendingResults.complete(listOf(tagItem("stale-a-result")))
        advanceUntilIdle()

        assertEquals("a", viewModel.uiState.value?.searchQuery)
        assertEquals(listOf("refreshed-a-result"), currentTagNames())
        assertEquals(true, viewModel.uiState.value?.searchUpdate)
    }

    @Test
    fun tagChangeRefreshUsesPendingSearchQuery() {
        repository.allTagItems = listOf(tagItem("all-result"))
        viewModel.start()
        viewModel.startListeningTagChanges()
        advanceUntilIdle()

        val pendingResults = repository.enqueueSearch("a")
        repository.enqueueSearch("a").complete(listOf(tagItem("refreshed-a-result")))
        viewModel.search("a")
        runCurrent()
        repository.tagChanges.tryEmit(true)
        runCurrent()

        assertEquals(listOf("a", "a"), repository.searchRequests)
        assertEquals("a", viewModel.uiState.value?.searchQuery)
        assertEquals(listOf("refreshed-a-result"), currentTagNames())
        assertEquals(true, viewModel.uiState.value?.searchUpdate)

        pendingResults.complete(listOf(tagItem("stale-a-result")))
        advanceUntilIdle()

        assertEquals("a", viewModel.uiState.value?.searchQuery)
        assertEquals(listOf("refreshed-a-result"), currentTagNames())
        viewModel.stopListeningTagChanges()
    }

    private fun currentTagNames() = requireNotNull(viewModel.uiState.value).tagItems.map { it.tag.name }

    private fun tagItem(name: String) = TagItem(Tag(name), 0)

    private class ControllableTagsRepository : TagsRepository {
        var allTagItems: List<TagItem> = emptyList()
        val searchRequests = mutableListOf<String>()
        val tagChanges = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
        private val searchResults = mutableMapOf<String, ArrayDeque<CompletableDeferred<List<TagItem>>>>()

        fun enqueueSearch(query: String): CompletableDeferred<List<TagItem>> {
            return CompletableDeferred<List<TagItem>>().also { result ->
                searchResults.getOrPut(query) { ArrayDeque() }.addLast(result)
            }
        }

        override suspend fun allTags(): List<TagItem> = allTagItems

        override suspend fun searchTags(query: String): List<TagItem> {
            searchRequests.add(query)
            val result = requireNotNull(searchResults[query]?.removeFirst())
            return try {
                result.await()
            } catch (exception: CancellationException) {
                withContext(NonCancellable) { result.await() }
            }
        }

        override suspend fun tagsChanged(): Flow<Boolean> = tagChanges
        override suspend fun deleteTag(tag: Tag) = Unit
        override suspend fun suggestTags(query: String): List<String> = emptyList()
        override fun saveTag(tagName: String): Boolean = false
        override fun isTagValid(tagName: String): Boolean = false
        override fun isTagMissing(tagName: String): Boolean = false
        override fun isTagConflict(tagName: String, oldTagName: String): Boolean = false
        override fun getCanonicalTagName(tagName: String): String = tagName
        override fun renameTag(tagName: String, oldTag: Tag): Boolean = false
    }
}
