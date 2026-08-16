package com.automattic.simplenote.authentication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.automattic.simplenote.R;
import com.automattic.simplenote.Simplenote;
import com.automattic.simplenote.analytics.AnalyticsTracker;
import com.automattic.simplenote.utils.IntentUtils;
import com.automattic.simplenote.utils.SimplenoteProgressDialogFragment;
import com.automattic.simplenote.utils.StrUtils;
import com.automattic.simplenote.utils.SystemBarUtils;
import com.automattic.simplenote.utils.WordPressUtils;
import com.automattic.simplenote.viewmodels.MagicLinkUiState;
import com.automattic.simplenote.viewmodels.CompleteMagicLinkViewModel;
import com.simperium.android.AuthenticationActivity;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;

import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SimplenoteAuthenticationActivity extends AuthenticationActivity {
    private static final String STATE_AUTH_STATE = "STATE_AUTH_STATE";

    public static final String KEY_IS_MAGIC_LINK = "KEY_IS_MAGIC_LINK";
    public static final String KEY_MAGIC_LINK_AUTH_KEY = "KEY_MAGIC_LINK_AUTH_KEY";
    public static final String KEY_MAGIC_LINK_AUTH_CODE = "KEY_MAGIC_LINK_AUTH_CODE";

    private String mAuthState;

    @Nullable
    private AlertDialog mPendingDialog;

    @Inject
    Simplenote simplenote;

    @Nullable
    CompleteMagicLinkViewModel completeMagicLinkViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Simperium owns this screen's layout, so it only takes the shared edge-to-edge path on
        // Android 15+, where the platform enforces it anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            SystemBarUtils.applyEdgeToEdge(this);

            // Keep the Simperium-owned layout inside system bars and display cutouts.
            applyAuthenticationInsets(findViewById(com.simperium.R.id.activity_authentication_root));
        }

        final Intent intent = getIntent();

        final boolean isMagicLink = intent.getBooleanExtra(KEY_IS_MAGIC_LINK, false);
        final String authKey = intent.getStringExtra(KEY_MAGIC_LINK_AUTH_KEY);
        final String authCode = intent.getStringExtra(KEY_MAGIC_LINK_AUTH_CODE);
        if (isMagicLink && authKey != null && authCode != null) {
            completeMagicLinkViewModel = new ViewModelProvider(this).get(CompleteMagicLinkViewModel.class);
            completeMagicLinkViewModel.getMagicLinkUiState().observe(this, state -> {
                if (state instanceof MagicLinkUiState.Success) {
                    hideDialog();
                    final MagicLinkUiState.Success stateResult = (MagicLinkUiState.Success) state;
                    simplenote.loginWithToken(stateResult.getEmail(), stateResult.getToken());

                    startNotesActivity(this, false);

                    AnalyticsTracker.track(
                            AnalyticsTracker.Stat.USER_CONFIRMED_LOGIN_LINK,
                            AnalyticsTracker.CATEGORY_USER,
                            "user_confirmed_login_link"
                    );

                    finish();
                } else if (state instanceof  MagicLinkUiState.Loading) {
                    showLoadingDialog(R.string.magic_link_complete_login_loading_message);
                } else if (state instanceof MagicLinkUiState.Error) {
                    hideDialog();
                    showDialogError(((MagicLinkUiState.Error) state).getMessageRes());
                }
            });
            completeMagicLinkViewModel.completeLogin(authKey, authCode, false);
        }
    }

    static void applyAuthenticationInsets(View view) {
        int initialLeft = view.getPaddingLeft();
        int initialTop = view.getPaddingTop();
        int initialRight = view.getPaddingRight();
        int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (insetView, windowInsets) -> {
            Insets safeDrawing = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            insetView.setPadding(
                initialLeft + safeDrawing.left,
                initialTop + safeDrawing.top,
                initialRight + safeDrawing.right,
                initialBottom + safeDrawing.bottom
            );
            return windowInsets.inset(
                safeDrawing.left,
                safeDrawing.top,
                safeDrawing.right,
                safeDrawing.bottom
            );
        });
    }

    public static void startNotesActivity(final Context context, final boolean showAnimation) {
        final Intent notesIntent = IntentUtils.maybeAliasedIntent(context.getApplicationContext());
        int flags;
        if (showAnimation) {
            flags = Intent.FLAG_ACTIVITY_NO_ANIMATION & (Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK;
        }
        notesIntent.addFlags(flags);
        context.startActivity(notesIntent);
    }

    @Override
    protected void onPause() {
        if (mPendingDialog != null) {
            mPendingDialog.dismiss();
        }
        super.onPause();
    }

    @Override
    protected void buttonSignupClicked() {
        Intent intent = new Intent(SimplenoteAuthenticationActivity.this, SimplenoteSignupActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != WordPressUtils.OAUTH_ACTIVITY_CODE || data == null) {
            return;
        }

        AuthorizationResponse authorizationResponse = AuthorizationResponse.fromIntent(data);
        AuthorizationException authorizationException = AuthorizationException.fromIntent(data);

        if (authorizationException != null) {
            Uri dataUri = data.getData();

            if (dataUri == null) {
                return;
            }

            if (StrUtils.isSameStr(dataUri.getQueryParameter("code"), "1")) {
                showDialogError(R.string.wpcom_log_in_error_unverified);
            } else {
                showDialogError(R.string.wpcom_log_in_error_generic);
            }
        } else if (authorizationResponse != null) {
            // Save token and finish activity.
            boolean authSuccess = WordPressUtils.processAuthResponse((Simplenote) getApplication(), authorizationResponse, mAuthState, true);

            if (!authSuccess) {
                showDialogError(R.string.wpcom_log_in_error_generic);
            } else {
                AnalyticsTracker.track(
                    AnalyticsTracker.Stat.WPCC_LOGIN_SUCCEEDED,
                    AnalyticsTracker.CATEGORY_USER,
                    "wpcc_login_succeeded_signin_activity"
                );

                finish();
            }
        }
    }

    @Override
    public void onLoginSheetCanceled() {
        super.onLoginSheetCanceled();
    }

    @Override
    public void onLoginSheetEmailClicked() {
        Intent intent = new Intent(SimplenoteAuthenticationActivity.this, SimplenoteSignupActivity.class);
        intent.putExtra(SimplenoteSignupActivity.KEY_IS_LOGIN, true);
        startActivity(intent);
        this.finish();
    }

    @Override
    protected void buttonLoginClicked() {
        onLoginSheetEmailClicked();
    }

    @Override
    public void onLoginSheetOtherClicked() {
        AuthorizationRequest.Builder authRequestBuilder = WordPressUtils.getWordPressAuthorizationRequestBuilder();

        // Set unique state value.
        mAuthState = "app-" + UUID.randomUUID();
        authRequestBuilder.setState(mAuthState);

        AuthorizationRequest request = authRequestBuilder.build();
        AuthorizationService authService = new AuthorizationService(SimplenoteAuthenticationActivity.this);
        Intent authIntent = authService.getAuthorizationRequestIntent(request);
        startActivityForResult(authIntent, WordPressUtils.OAUTH_ACTIVITY_CODE);

        AnalyticsTracker.track(
            AnalyticsTracker.Stat.WPCC_BUTTON_PRESSED,
            AnalyticsTracker.CATEGORY_USER,
            "wpcc_button_press_signin_activity"
        );
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_AUTH_STATE, mAuthState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.containsKey(STATE_AUTH_STATE)) {
            mAuthState = savedInstanceState.getString(STATE_AUTH_STATE);
        }
    }

    private void showLoadingDialog(@StringRes int stringRes) {
        final Fragment fragment = getSupportFragmentManager().findFragmentByTag(SimplenoteProgressDialogFragment.TAG);
        if (fragment == null) {
            final SimplenoteProgressDialogFragment progressDialogFragment = SimplenoteProgressDialogFragment.newInstance(getString(stringRes));
            progressDialogFragment.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.Simperium);
            progressDialogFragment.show(getSupportFragmentManager(), SimplenoteProgressDialogFragment.TAG);
        }
    }

    private void hideDialog() {
        final Fragment fragment = getSupportFragmentManager().findFragmentByTag(SimplenoteProgressDialogFragment.TAG);
        if (fragment != null) {
            try {
                final SimplenoteProgressDialogFragment progressDialogFragment = (SimplenoteProgressDialogFragment) fragment;
                if (!progressDialogFragment.isHidden()) {
                    progressDialogFragment.dismiss();
                }
            } catch (final ClassCastException e) {
                Log.e(TAG, "We have a class other than SimplenoteProgressDialogFragment", e);
            }
        }
    }

    private void showDialogError(@StringRes int message) {
        if (isFinishing() || message == 0) {
            return;
        }

        Context context = new ContextThemeWrapper(SimplenoteAuthenticationActivity.this, getTheme());
        mPendingDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.simperium_dialog_title_error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (completeMagicLinkViewModel != null) {
                        completeMagicLinkViewModel.resetState();
                    }
                })
                .setOnDismissListener(dialog -> mPendingDialog = null)
                .show();

        AnalyticsTracker.track(
            AnalyticsTracker.Stat.WPCC_LOGIN_FAILED,
            AnalyticsTracker.CATEGORY_USER,
            "wpcc_login_failed_signin_activity"
        );
    }
}
