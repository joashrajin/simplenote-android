package org.wordpress.passcodelock;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import androidx.core.os.CancellationSignal;

public class PasscodeUnlockActivity extends AbstractPasscodeKeyboardActivity {
    private OnBackInvokedCallback mBackInvokedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mBackInvokedCallback = this::handleBackPressed;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    mBackInvokedCallback
            );
        }
    }

	@SuppressLint("RestrictedApi")
    @Override
    public void onResume() {
        super.onResume();

        if (isFingerprintSupportedAndEnabled()) {
            mCancel = new CancellationSignal();
            mFingerprintManager.authenticate(null, 0, mCancel, getFingerprintCallback(), null);
            View view = findViewById(R.id.image_fingerprint);
            view.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mBackInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mBackInvokedCallback);
            mBackInvokedCallback = null;
        }
        super.onDestroy();
    }

    private void handleBackPressed() {
        getAppLock().forcePasswordLock();
        Intent i = new Intent();
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        startActivity(i);
        finish();
    }

    @Override
    protected void onPinLockInserted() {
        String passLock = mPinCodeField.getText().toString();
        if (getAppLock().verifyPassword(passLock)) {
            authenticationSucceeded();
        } else {
            authenticationFailed();
        }
    }

	@SuppressLint("RestrictedApi")
    @Override
    protected FingerprintManagerCompat.AuthenticationCallback getFingerprintCallback() {
        return new FingerprintManagerCompat.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
                // without the call to verifyPassword the unlock screen will show multiple times
                getAppLock().verifyPassword(AbstractAppLock.FINGERPRINT_VERIFICATION_BYPASS);
                authenticationSucceeded();
            }

            @Override
            public void onAuthenticationFailed() {
                authenticationFailed();
            }

            @Override public void onAuthenticationError(int errMsgId, CharSequence errString) { }
            @Override public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) { }
        };
    }

	@SuppressLint("RestrictedApi")
    private boolean isFingerprintSupportedAndEnabled() {
        return mFingerprintManager.isHardwareDetected() &&
               mFingerprintManager.hasEnrolledFingerprints() &&
               getAppLock().isFingerprintEnabled();
    }
}
