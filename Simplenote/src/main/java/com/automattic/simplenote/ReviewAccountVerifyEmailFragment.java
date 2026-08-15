package com.automattic.simplenote;

import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwnerKt;

import com.automattic.simplenote.FullScreenDialogFragment.FullScreenDialogContent;
import com.automattic.simplenote.FullScreenDialogFragment.FullScreenDialogController;
import com.automattic.simplenote.analytics.AnalyticsTracker;
import com.automattic.simplenote.repositories.AccountRepository;
import com.automattic.simplenote.utils.AccountNetworkUtils;
import com.automattic.simplenote.utils.AccountVerificationEmailHandler;
import com.automattic.simplenote.utils.AppLog;
import com.automattic.simplenote.utils.BrowserUtils;
import com.automattic.simplenote.utils.NetworkUtils;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * A {@link FullScreenDialogFragment} for reviewing an account and verifying an email address.  When
 * an account has not been confirmed through a verification email link, the review account interface
 * is shown.  If a verification email has been sent, the verify email interface is shown.
 */
@AndroidEntryPoint
public class ReviewAccountVerifyEmailFragment extends Fragment implements FullScreenDialogContent {
    public static final String EXTRA_ACCOUNT_EMAIL = "EXTRA_ACCOUNT_EMAIL";
    public static final String EXTRA_PRESENTATION_REVISION = "EXTRA_PRESENTATION_REVISION";
    public static final String EXTRA_PROCESS_NONCE = "EXTRA_PROCESS_NONCE";
    public static final String EXTRA_SESSION_GENERATION = "EXTRA_SESSION_GENERATION";
    public static final String EXTRA_SENT_EMAIL = "EXTRA_SENT_EMAIL";

    private static final String URL_SETTINGS_REDIRECT = "https://app.simplenote.com/settings/";
    private static final String URL_VERIFY_EMAIL = "https://app.simplenote.com/account/verify-email/";
    private static final int TIMEOUT_SECONDS = 30;

    private AppCompatButton mButtonPrimary;
    private AppCompatButton mButtonSecondary;
    private FullScreenDialogController mDialogController;
    private ImageView mImageIcon;
    private String mEmail;
    private long mPresentationRevision = -1;
    private String mProcessNonce;
    private int mSessionGeneration = -1;
    private TextView mTextSubtitle;
    private TextView mTextTitle;
    private boolean mHasSentEmail;

    @Inject AccountRepository mAccountRepository;

    @Override
    public boolean onConfirmClicked(FullScreenDialogController controller) {
        if (!isForCurrentAccount()) {
            dismissStaleDialog();
            return false;
        }

        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), R.string.error_network_required, Toast.LENGTH_LONG).show();
            return false;
        }

        if (mHasSentEmail) {
            Toast.makeText(requireContext(), R.string.toast_email_sent, Toast.LENGTH_SHORT).show();
        } else {
            showVerifyEmail();
        }

        sendVerificationEmail();
        return false;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_review_account_verify_email, container, false);
        mHasSentEmail = getArguments() != null && getArguments().getBoolean(EXTRA_SENT_EMAIL);
        mEmail = getArguments() == null ? null : getArguments().getString(EXTRA_ACCOUNT_EMAIL);
        mPresentationRevision = getArguments() == null ? -1 :
                getArguments().getLong(EXTRA_PRESENTATION_REVISION, -1);
        mProcessNonce = getArguments() == null ? null : getArguments().getString(EXTRA_PROCESS_NONCE);
        mSessionGeneration = getArguments() == null ? -1 : getArguments().getInt(EXTRA_SESSION_GENERATION, -1);
        String displayEmail = mEmail == null ? "" : mEmail;

        mImageIcon = layout.findViewById(R.id.image);
        mImageIcon.setImageResource(mHasSentEmail ? R.drawable.ic_mail_24dp : R.drawable.ic_warning_24dp);
        mImageIcon.setContentDescription(getString(mHasSentEmail ? R.string.description_mail : R.string.description_warning));

        @StringRes int title = mHasSentEmail ? R.string.fullscreen_verify_email_title : R.string.fullscreen_review_account_title;
        mTextTitle = layout.findViewById(R.id.text_title);
        mTextTitle.setText(title);

        @StringRes int subtitle = mHasSentEmail ? R.string.fullscreen_verify_email_subtitle : R.string.fullscreen_review_account_subtitle;
        mTextSubtitle = layout.findViewById(R.id.text_subtitle);
        mTextSubtitle.setText(Html.fromHtml(String.format(getResources().getString(subtitle), displayEmail)));

        mButtonPrimary = layout.findViewById(R.id.button_primary);
        mButtonPrimary.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isForCurrentAccount()) {
                        dismissStaleDialog();
                        return;
                    }

                    AnalyticsTracker.track(
                        AnalyticsTracker.Stat.VERIFICATION_CONFIRM_BUTTON_TAPPED,
                        AnalyticsTracker.CATEGORY_USER,
                        "verification_confirm"
                    );
                    onConfirmClicked(mDialogController);
                }
            }
        );
        mButtonPrimary.setVisibility(mHasSentEmail ? View.GONE : View.VISIBLE);

        mButtonSecondary = layout.findViewById(R.id.button_secondary);
        mButtonSecondary.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isForCurrentAccount()) {
                        dismissStaleDialog();
                        return;
                    }

                    if (mHasSentEmail) {
                        AnalyticsTracker.track(
                            AnalyticsTracker.Stat.VERIFICATION_RESEND_EMAIL_BUTTON_TAPPED,
                            AnalyticsTracker.CATEGORY_USER,
                            "verification_resend_email"
                        );
                        onConfirmClicked(mDialogController);
                    } else {
                        AnalyticsTracker.track(
                            AnalyticsTracker.Stat.VERIFICATION_CHANGE_EMAIL_BUTTON_TAPPED,
                            AnalyticsTracker.CATEGORY_USER,
                            "verification_change_email"
                        );
                        BrowserUtils.launchBrowserOrShowError(requireContext(), URL_SETTINGS_REDIRECT);
                    }
                }
            }
        );
        mButtonSecondary.setText(mHasSentEmail ? R.string.fullscreen_verify_email_button_secondary : R.string.fullscreen_review_account_button_secondary);

        return layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!isForCurrentAccount()) {
            dismissStaleDialog();
            return;
        }

        new AccountVerificationResumeCheck(
            mAccountRepository,
            LifecycleOwnerKt.getLifecycleScope(getViewLifecycleOwner())
        ).start(getViewLifecycleOwner().getLifecycle(), mEmail, this::dismissIfVerified);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isForCurrentAccount()) {
            dismissStaleDialog();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public boolean onDismissClicked(FullScreenDialogController controller) {
        if (!isForCurrentAccount()) {
            return false;
        }

        AnalyticsTracker.track(
            AnalyticsTracker.Stat.VERIFICATION_DISMISSED,
            AnalyticsTracker.CATEGORY_USER,
            "verification_dismissed"
        );
        return false;
    }

    @Override
    public void onViewCreated(FullScreenDialogController controller) {
        mDialogController = controller;
    }

    private void dismissIfVerified() {
        if (isDetached() || isRemoving() || mDialogController == null) {
            return;
        }

        mDialogController.dismiss();
    }

    public static Bundle newBundle(
            boolean hasSentEmail,
            String email,
            int sessionGeneration,
            long presentationRevision,
            String processNonce
    ) {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_ACCOUNT_EMAIL, email);
        bundle.putLong(EXTRA_PRESENTATION_REVISION, presentationRevision);
        bundle.putString(EXTRA_PROCESS_NONCE, processNonce);
        bundle.putInt(EXTRA_SESSION_GENERATION, sessionGeneration);
        bundle.putBoolean(EXTRA_SENT_EMAIL, hasSentEmail);
        return bundle;
    }

    private boolean isForCurrentAccount() {
        Simplenote application = (Simplenote) requireActivity().getApplication();
        return application.isCurrentAccountVerificationPresentation(
                mEmail,
                mSessionGeneration,
                mPresentationRevision,
                mProcessNonce
        );
    }

    private void dismissStaleDialog() {
        if (mDialogController != null) {
            mDialogController.dismiss();
        } else if (getParentFragment() instanceof FullScreenDialogFragment) {
            ((FullScreenDialogFragment) getParentFragment()).dismiss();
        }
    }

    private void sendVerificationEmail() {
        AccountNetworkUtils.makeSendVerificationEmailRequest(mEmail, new AccountVerificationEmailHandler() {
            @Override
            public void onSuccess(@NonNull String url) {
                AppLog.add(AppLog.Type.AUTH, "Email sent (200 - " + url + ")");
            }

            @Override
            public void onFailure(@NonNull Exception e, @NonNull String url) {
                AppLog.add(AppLog.Type.AUTH, "Verification email error (" + e.getMessage() + " - " + url + ")");
            }
        });

        mHasSentEmail = true;
    }

    private void showVerifyEmail() {
        mImageIcon.setImageResource(R.drawable.ic_mail_24dp);
        mTextTitle.setText(R.string.fullscreen_verify_email_title);
        mTextSubtitle.setText(Html.fromHtml(String.format(getResources().getString(R.string.fullscreen_verify_email_subtitle), mEmail)));
        mButtonPrimary.setVisibility(View.GONE);
        mButtonSecondary.setText(R.string.fullscreen_verify_email_button_secondary);
    }
}
