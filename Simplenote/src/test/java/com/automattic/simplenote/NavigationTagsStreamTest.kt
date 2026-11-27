package com.automattic.simplenote

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.repositories.CollaboratorsRepository
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.usecases.GetTagsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class NavigationTagsStreamTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    @Test
    fun collectsOnlyWhileResumedAndMarksOnlyTheFirstDeliveredSnapshotAsInitial() = runTest {
        val repository = FakeTagsRepository()
        val stream = stream(repository, backgroundScope)
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(testScheduler))
        val received = mutableListOf<String>()

        stream.start(owner.lifecycle, { false }) { tags, isInitial ->
            received.add("${tags.single().name}:$isInitial")
        }
        runCurrent()
        assertEquals(emptyList<Boolean>(), repository.sortRequests)

        owner.currentState = Lifecycle.State.RESUMED
        runCurrent()
        repository.snapshots.emit(listOf(Tag("first")))
        runCurrent()
        repository.snapshots.emit(listOf(Tag("second")))
        runCurrent()

        owner.currentState = Lifecycle.State.STARTED
        runCurrent()
        repository.snapshots.emit(listOf(Tag("paused")))
        runCurrent()

        assertEquals(listOf(false), repository.sortRequests)
        assertEquals(listOf("first:true", "second:false"), received)
    }

    @Test
    fun resumingRereadsTheSortPreferenceAndStartsANewInitialSnapshot() = runTest {
        val repository = FakeTagsRepository()
        val stream = stream(repository, backgroundScope)
        val owner = TestLifecycleOwner(Lifecycle.State.CREATED, UnconfinedTestDispatcher(testScheduler))
        val received = mutableListOf<Boolean>()
        var sortAlphabetically = false

        stream.start(owner.lifecycle, { sortAlphabetically }) { _, isInitial ->
            received.add(isInitial)
        }
        owner.currentState = Lifecycle.State.RESUMED
        runCurrent()
        repository.snapshots.emit(listOf(Tag("manual")))
        runCurrent()

        owner.currentState = Lifecycle.State.STARTED
        runCurrent()
        sortAlphabetically = true
        owner.currentState = Lifecycle.State.RESUMED
        runCurrent()
        repository.snapshots.emit(listOf(Tag("alphabetical")))
        runCurrent()

        assertEquals(listOf(false, true), repository.sortRequests)
        assertEquals(listOf(true, true), received)
    }

    private fun stream(repository: FakeTagsRepository, scope: CoroutineScope): NavigationTagsStream {
        val collaboratorsRepository = mock<CollaboratorsRepository>()
        whenever(collaboratorsRepository.isValidCollaborator(any())).thenReturn(false)
        return NavigationTagsStream(
            GetTagsUseCase(repository, collaboratorsRepository),
            scope,
        )
    }

    private class FakeTagsRepository : TagsRepository {
        val snapshots = MutableSharedFlow<List<Tag>>()
        val sortRequests = mutableListOf<Boolean>()

        override fun navigationTags(sortAlphabetically: Boolean): Flow<List<Tag>> {
            sortRequests.add(sortAlphabetically)
            return snapshots
        }

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
