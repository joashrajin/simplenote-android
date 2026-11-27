package com.automattic.simplenote.usecases

import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.SimperiumCollaboratorsRepository
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.usecases.ValidateTagUseCase.TagValidationResult
import com.simperium.client.Bucket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ValidateTagUseCaseTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())
    private val notesBucket = Mockito.mock(Bucket::class.java) as Bucket<Note>
    private val tagsRepository: TagsRepository = Mockito.mock(TagsRepository::class.java)
    private val collaboratorsRepository = SimperiumCollaboratorsRepository(
        notesBucket,
        coroutinesTestRule.testDispatcher
    )
    private val validateTagUseCase = ValidateTagUseCase(tagsRepository, collaboratorsRepository)

    @Test
    fun emptyTagShouldReturnTagEmpty() {
        val result = validateTagUseCase.isTagValid("")

        assertEquals(TagValidationResult.TagEmpty, result)
    }

    @Test
    fun tagWithSpaceShouldReturnTagWithSpaces() {
        val result = validateTagUseCase.isTagValid("tag space")

        assertEquals(TagValidationResult.TagWithSpaces, result)
    }

    @Test
    fun tagsWithOtherWhitespaceShouldReturnTagWithSpaces() {
        val results = listOf(
            "tag\tname",
            "tag\nname",
            "tag\u00A0name",
            "tag\u2003name"
        ).map(validateTagUseCase::isTagValid)

        assertEquals(List(4) { TagValidationResult.TagWithSpaces }, results)
        Mockito.verifyNoInteractions(tagsRepository)
    }

    @Test
    fun tagWithLongNameShouldReturnTagTooLong() {
        whenever(tagsRepository.isTagValid(any())).thenReturn(false)

        val result = validateTagUseCase.isTagValid("taglong")

        assertEquals(TagValidationResult.TagTooLong, result)
    }

    @Test
    fun tagAlreadyInDatabaseShouldReturnTagExists() {
        whenever(tagsRepository.isTagValid(any())).thenReturn(true)
        whenever(tagsRepository.isTagMissing(any())).thenReturn(false)

        val result = validateTagUseCase.isTagValid("taglong")

        assertEquals(TagValidationResult.TagExists, result)
    }

    @Test
    fun validTagShouldReturnTagValid() {
        whenever(tagsRepository.isTagValid(any())).thenReturn(true)
        whenever(tagsRepository.isTagMissing(any())).thenReturn(true)

        val result = validateTagUseCase.isTagValid("tag1")

        assertEquals(TagValidationResult.TagValid, result)
    }

    @Test
    fun emailShouldReturnTagIsCollaborator() {
        whenever(tagsRepository.isTagValid(any())).thenReturn(true)
        whenever(tagsRepository.isTagMissing(any())).thenReturn(true)

        val result = validateTagUseCase.isTagValid("admin@test.com")

        assertEquals(TagValidationResult.TagIsCollaborator, result)
    }

    @Test
    fun emailAlreadyExistsAsTagShouldReturnTagIsCollaborator() {
        whenever(tagsRepository.isTagValid(any())).thenReturn(true)
        whenever(tagsRepository.isTagMissing(any())).thenReturn(false)

        val result = validateTagUseCase.isTagValid("admin@test.com")

        assertEquals(TagValidationResult.TagIsCollaborator, result)
    }
}
