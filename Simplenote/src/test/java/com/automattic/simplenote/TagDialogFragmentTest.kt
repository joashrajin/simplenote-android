package com.automattic.simplenote

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentFactory
import com.automattic.simplenote.models.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TagDialogFragmentTest {
    @Test
    fun defaultFragmentFactoryCanInstantiateTheDialog() {
        val fragment = FragmentFactory().instantiate(
            TagDialogFragment::class.java.classLoader!!,
            TagDialogFragment::class.java.name,
        )

        assertTrue(fragment is TagDialogFragment)
    }

    @Test
    fun tagKeySurvivesFactoryRecreationThroughArguments() {
        val original = TagDialogFragment(Tag("tag-key"))
        val savedArguments = Bundle(original.requireArguments())
        val restored = FragmentFactory().instantiate(
            TagDialogFragment::class.java.classLoader!!,
            TagDialogFragment::class.java.name,
        )
        restored.arguments = savedArguments

        assertEquals("tag-key", restored.requireArguments().getString("tag_key"))
    }
}
