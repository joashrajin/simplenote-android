package com.automattic.simplenote.authentication

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.automattic.simplenote.R
import com.automattic.simplenote.utils.HtmlCompat

class ConfirmationFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_confirmation, container, false)
        initUi(view)
        return view
    }

    private fun initUi(view: View) {
        initEmailConfirmation(view.findViewById(R.id.email_confirmation_text)!!)
        initSupport(view.findViewById(R.id.support_text)!!)
    }

    private fun initEmailConfirmation(emailConfirmation: TextView) {
        val isSignup = requireArguments().getBoolean(IS_SIGNUP_KEY, true)
        val boldEmail = "<b>" + requireArguments().getString(CONFIRMATION_EMAIL_KEY) + "</b>"
        @StringRes val stringRes = if (isSignup) {
            R.string.email_confirmation_text
        } else {
            R.string.magic_link_request_login_dialog
        }
        val emailConfirmationText = HtmlCompat.fromHtml(String.format(getString(stringRes), boldEmail))
        emailConfirmation.text = emailConfirmationText
    }

    private fun initSupport(support: TextView) {
        val supportEmail = getString(R.string.support_email)
        val link = "<a href='mailto:$supportEmail'>$supportEmail</a>"
        val supportText = HtmlCompat.fromHtml(String.format(getString(R.string.support_text), link))
        support.text = supportText
        support.movementMethod = LinkMovementMethod.getInstance()
    }

    companion object {
        private const val CONFIRMATION_EMAIL_KEY = "CONFIRMATION_EMAIL_KEY"
        private const val IS_SIGNUP_KEY = "IS_SIGNUP_KEY"

        @JvmStatic
        fun newInstance(email: String?, isSignUp: Boolean): ConfirmationFragment {
            val confirmationFragment = ConfirmationFragment()
            val bundle = Bundle()
            bundle.putString(CONFIRMATION_EMAIL_KEY, email)
            bundle.putBoolean(IS_SIGNUP_KEY, isSignUp)
            confirmationFragment.arguments = bundle
            return confirmationFragment
        }
    }
}
