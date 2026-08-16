package com.automattic.simplenote.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.SimperiumCollaboratorsRepository
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.usecases.GetTagsUseCase
import com.automattic.simplenote.usecases.ValidateTagUseCase
import com.automattic.simplenote.viewmodels.NoteEditorViewModel.NoteEditorEvent
import com.simperium.client.Bucket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class NoteEditorViewModelTest {
    @get:Rule
    val rule = InstantTaskExecutorRule()
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private val tagsRepository: TagsRepository = mock(TagsRepository::class.java)
    private val notesBucket = mock(Bucket::class.java) as Bucket<Note>
    private val collaboratorsRepository = SimperiumCollaboratorsRepository(
        notesBucket,
        coroutinesTestRule.testDispatcher
    )
    private val getTagsUseCase = GetTagsUseCase(tagsRepository, collaboratorsRepository)
    private val validateTagUseCase = ValidateTagUseCase(tagsRepository, collaboratorsRepository)
    private val viewModel = NoteEditorViewModel(getTagsUseCase, validateTagUseCase)

    private val note = Note("key1").also {
        it.content = "Hello World"
        it.tags = listOf("tag1", "tag2", "name@test.com")
        it.bucket = notesBucket
    }

    @Before
    fun setup() {
        whenever(tagsRepository.isTagValid(any())).thenReturn(true)
        whenever(tagsRepository.isTagMissing(any())).thenReturn(true)
    }

    @Test
    fun updateShouldUpdateUiState() {
        viewModel.update(note)

        assertEquals(listOf("tag1", "tag2"), viewModel.uiState.value?.tags)
        assertEquals(listOf("tag1", "tag2", "name@test.com"), note.tags)
    }

    @Test
    fun addTagShouldUpdateUiState() {
        viewModel.addTag("tag3", note)

        assertEquals(listOf("tag1", "tag2", "tag3"), viewModel.uiState.value?.tags)
        assertEquals(listOf("tag1", "tag2", "name@test.com", "tag3"), note.tags)
    }

    @Test
    fun addCollaboratorShouldNotUpdateUiState() {
        viewModel.update(note)

        viewModel.addTag("name@email.com", note)

        assertEquals(listOf("tag1", "tag2"), viewModel.uiState.value?.tags)
        assertEquals(NoteEditorEvent.TagAsCollaborator("name@email.com"), viewModel.event.value)
    }

    @Test
    fun addCollaboratorShouldNotUpdateNote() {
        viewModel.update(note)

        viewModel.addTag("name@email.com", note)

        assertEquals(listOf("tag1", "tag2", "name@test.com", "name@email.com"), note.tags)
        assertEquals(NoteEditorEvent.TagAsCollaborator("name@email.com"), viewModel.event.value)
    }

    @Test
    fun addInvalidTagShouldNotUpdateUiState() {
        viewModel.update(note)

        viewModel.addTag("test test1", note)

        assertEquals(listOf("tag1", "tag2"), viewModel.uiState.value?.tags)
        assertEquals(NoteEditorEvent.InvalidTag, viewModel.event.value)
    }

    @Test
    fun addInvalidTagShouldNotUpdateNote() {
        viewModel.update(note)

        viewModel.addTag("test test1", note)

        assertEquals(listOf("tag1", "tag2", "name@test.com"), note.tags)
        assertEquals(NoteEditorEvent.InvalidTag, viewModel.event.value)
    }

    @Test
    fun eventIsNotReplayedToReplacementLifecycleOwner() = runTest {
        val firstEvents = mutableListOf<NoteEditorEvent>()
        val firstOwner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(testScheduler))
        viewModel.event.observe(firstOwner) { firstEvents.add(it) }

        viewModel.addTag("test test1", note)

        assertEquals(listOf(NoteEditorEvent.InvalidTag), firstEvents)
        firstOwner.currentState = Lifecycle.State.DESTROYED

        val replacementEvents = mutableListOf<NoteEditorEvent>()
        val replacementOwner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(testScheduler))
        viewModel.event.observe(replacementOwner) { replacementEvents.add(it) }

        assertEquals(emptyList<NoteEditorEvent>(), replacementEvents)

        viewModel.addTag("name@email.com", note)

        assertEquals(
            listOf(NoteEditorEvent.TagAsCollaborator("name@email.com")),
            replacementEvents
        )
    }

    @Test
    fun removeTagShouldUpdateUiStateAndNote() {
        viewModel.removeTag("tag2", note)

        assertEquals(listOf("tag1"), viewModel.uiState.value?.tags)
        assertEquals(listOf("tag1", "name@test.com"), note.tags)
    }
}
