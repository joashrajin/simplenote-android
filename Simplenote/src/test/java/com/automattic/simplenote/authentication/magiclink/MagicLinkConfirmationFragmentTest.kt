package com.automattic.simplenote.authentication.magiclink

import android.app.Application
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.automattic.simplenote.utils.SimplenoteProgressDialogFragment
import com.automattic.simplenote.viewmodels.MagicLinkUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MagicLinkConfirmationFragmentTest {
    @Test
    fun loadingReusesTheRestoredProgressDialog() {
        val restoredDialog = mock<SimplenoteProgressDialogFragment>()
        val fragmentManager = mockFragmentManager(restoredDialog)
        val fragment = spy(MagicLinkConfirmationFragment())
        stubFragmentManager(fragment, fragmentManager)

        invokePrivate(fragment, "showProgressDialog", "Signing in")

        verify(fragmentManager).findFragmentByTag(SimplenoteProgressDialogFragment.TAG)
        verify(fragmentManager, never()).beginTransaction()
    }

    @Test
    fun terminalStateDismissesTheRestoredProgressDialog() {
        val restoredDialog = mock<SimplenoteProgressDialogFragment>()
        val fragmentManager = mockFragmentManager(restoredDialog)
        val fragment = spy(MagicLinkConfirmationFragment())
        stubFragmentManager(fragment, fragmentManager)

        invokePrivate(fragment, "hideDialogProgress")

        verify(restoredDialog).dismiss()
    }

    @Test
    fun waitingStateDismissesTheRestoredProgressDialog() {
        val restoredDialog = mock<SimplenoteProgressDialogFragment>()
        val fragmentManager = mockFragmentManager(restoredDialog)
        val fragment = spy(MagicLinkConfirmationFragment())
        stubFragmentManager(fragment, fragmentManager)

        invokePrivate(fragment, "handleState", MagicLinkUiState.Waiting)

        verify(restoredDialog).dismiss()
    }

    private fun mockFragmentManager(restoredDialog: SimplenoteProgressDialogFragment): FragmentManager {
        val transaction = mock<FragmentTransaction>()
        whenever(transaction.add(any(), eq(SimplenoteProgressDialogFragment.TAG))).thenReturn(transaction)
        whenever(transaction.commit()).thenReturn(1)

        return mock {
            on { findFragmentByTag(SimplenoteProgressDialogFragment.TAG) } doReturn restoredDialog
            on { beginTransaction() } doReturn transaction
        }
    }

    @Suppress("DEPRECATION")
    private fun stubFragmentManager(fragment: MagicLinkConfirmationFragment, fragmentManager: FragmentManager) {
        doReturn(fragmentManager).whenever(fragment).parentFragmentManager
        doReturn(fragmentManager).whenever(fragment).requireFragmentManager()
    }

    private fun invokePrivate(fragment: MagicLinkConfirmationFragment, methodName: String, vararg arguments: Any) {
        MagicLinkConfirmationFragment::class.java
            .declaredMethods
            .single { method -> method.name == methodName && method.parameterCount == arguments.size }
            .apply { isAccessible = true }
            .invoke(fragment, *arguments)
    }
}
