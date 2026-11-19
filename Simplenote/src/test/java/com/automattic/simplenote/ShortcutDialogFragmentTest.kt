package com.automattic.simplenote

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ShortcutDialogFragmentTest {
    @Test
    fun defaultFactoryCanInstantiateFragment() {
        val fragment = instantiate()

        assertTrue(fragment is ShortcutDialogFragment)
    }

    @Test
    fun previewArgumentSurvivesSavedArgumentTransfer() {
        val original = ShortcutDialogFragment.newInstance(true)
        val restored = instantiate()

        restored.arguments = Bundle(original.requireArguments())

        assertTrue(restored.requireArguments().getBoolean("ARG_IS_PREVIEW"))
    }

    private fun instantiate() = FragmentFactory().instantiate(
        ShortcutDialogFragment::class.java.classLoader!!,
        ShortcutDialogFragment::class.java.name,
    )
}
