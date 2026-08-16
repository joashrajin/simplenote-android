package com.automattic.simplenote.authentication

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import com.automattic.simplenote.authentication.magiclink.MagicLinkConfirmationFragment
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MagicLinkableFragmentTest {
    @Test
    fun loginConfirmationReplacesTheSignInViewAndKeepsBackNavigation() {
        val fragmentManager = mock<FragmentManager>()
        val transaction = mock<FragmentTransaction>()
        whenever(fragmentManager.beginTransaction()).thenReturn(transaction)
        whenever(transaction.add(any<Int>(), any<Fragment>(), any<String>())).thenReturn(transaction)
        whenever(transaction.replace(any<Int>(), any<Fragment>(), any<String>())).thenReturn(transaction)
        whenever(transaction.addToBackStack(null)).thenReturn(transaction)
        whenever(transaction.commit()).thenReturn(1)
        val fragment = spy(TestMagicLinkableFragment())
        doReturn(fragmentManager).whenever(fragment).parentFragmentManager

        fragment.showLoginConfirmation("user@example.com")

        val confirmation = argumentCaptor<Fragment>()
        verify(transaction).replace(
            eq(R.id.fragment_container),
            confirmation.capture(),
            eq(SimplenoteSignupActivity.SIGNUP_FRAGMENT_TAG),
        )
        assertTrue(confirmation.firstValue is MagicLinkConfirmationFragment)
        assertEquals(
            "user@example.com",
            confirmation.firstValue.requireArguments().getString(MagicLinkConfirmationFragment.PARAM_USERNAME),
        )
        verify(transaction, never()).add(any<Int>(), any<Fragment>(), any<String>())
        verify(transaction).addToBackStack(null)
        verify(transaction).commit()
    }

    @Test
    fun destroyedViewReleasesTheEmailField() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Simplestyle,
        )
        val email = EditText(context)
        val emailLayout = TextInputLayout(context).apply {
            id = R.id.input_email
            addView(email)
        }
        val root = FrameLayout(context).apply {
            addView(emailLayout)
            addView(Button(context).apply { id = R.id.button })
        }
        val fragment = TestMagicLinkableFragment().apply { nextView = root }

        fragment.onCreateView(LayoutInflater.from(context), null, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(email, fragment.currentEmailField())

        fragment.onDestroyView()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(fragment.currentEmailField())
    }

    private class TestMagicLinkableFragment : MagicLinkableFragment() {
        var nextView: View? = null

        override fun inflateLayout(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? = nextView

        override fun actionButtonText() = "Continue"

        override fun onActionButtonClicked(view: View, emailEditText: EditText) = Unit

        fun showLoginConfirmation(email: String) = showConfirmationScreen(email, false)

        fun currentEmailField(): EditText? = getEmailEditText()
    }
}
