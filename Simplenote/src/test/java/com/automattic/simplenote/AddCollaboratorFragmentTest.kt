package com.automattic.simplenote

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AddCollaboratorFragmentTest {
    @Test
    fun defaultFragmentFactoryCanInstantiateTheDialog() {
        val fragment = FragmentFactory().instantiate(
            AddCollaboratorFragment::class.java.classLoader!!,
            AddCollaboratorFragment::class.java.name,
        )

        assertTrue(fragment is AddCollaboratorFragment)
    }

    @Test
    fun noteIdSurvivesFactoryRecreationThroughArguments() {
        val original = AddCollaboratorFragment.newInstance("note-key")
        val restored = FragmentFactory().instantiate(
            AddCollaboratorFragment::class.java.classLoader!!,
            AddCollaboratorFragment::class.java.name,
        )
        restored.arguments = Bundle(original.requireArguments())

        assertEquals("note-key", restored.requireArguments().getString("note_id"))
    }
}
