package com.automattic.simplenote.utils

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.automattic.simplenote.R
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Magic-link and delete-account flows dismiss this dialog from network callbacks, which can land
 * after the host saved its state (app backgrounded) or was destroyed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SimplenoteProgressDialogFragmentTest {
    @Test
    fun dismissAfterStateSaveRemovesDialogWithoutThrowing() {
        val controller = launchHost()
        val fragmentManager = controller.get().supportFragmentManager
        val dialog = SimplenoteProgressDialogFragment.newInstance("Loading")
        dialog.showNow(fragmentManager, SimplenoteProgressDialogFragment.TAG)

        controller.saveInstanceState(Bundle())

        assertTrue(fragmentManager.isStateSaved)
        dialog.dismiss()
        fragmentManager.executePendingTransactions()
        assertNull(fragmentManager.findFragmentByTag(SimplenoteProgressDialogFragment.TAG))
    }

    @Test
    fun dismissAfterHostDestroyedDoesNotThrow() {
        val controller = launchHost()
        val dialog = SimplenoteProgressDialogFragment.newInstance("Loading")
        dialog.showNow(controller.get().supportFragmentManager, SimplenoteProgressDialogFragment.TAG)

        controller.pause().stop().destroy()

        dialog.dismiss()
    }

    private fun launchHost(): ActivityController<AppCompatActivity> {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_Simplestyle)
        return controller.setup()
    }
}
