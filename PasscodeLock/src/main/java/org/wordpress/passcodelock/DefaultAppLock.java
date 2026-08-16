package org.wordpress.passcodelock;

import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;

public class DefaultAppLock extends AbstractAppLock {
    public static boolean isSupportedApi() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    private static final String UNLOCK_CLASS_NAME = PasscodeUnlockActivity.class.getName();
    private static final String OLD_PASSWORD_SALT = "sadasauidhsuyeuihdahdiauhs";

    private Application mCurrentApp;
    private PasscodePreferenceStore mPreferenceStore;
    private Date mLostFocusDate;

    public DefaultAppLock(Application app) {
        this(app, PasscodePreferenceStore.from(app));
    }

    DefaultAppLock(Application app, PasscodePreferenceStore preferenceStore) {
        super();
        mCurrentApp = app;
        mPreferenceStore = preferenceStore;
    }

    /** {@link PasscodeUnlockActivity} is always exempt. */
    @Override
    public boolean isExemptActivity(String activityName) {
        return UNLOCK_CLASS_NAME.equals(activityName) || super.isExemptActivity(activityName);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (!isExemptActivity(activity.getClass().getName())) mLostFocusDate = new Date();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (!isExemptActivity(activity.getClass().getName()) && shouldShowUnlockScreen()) {
            Intent i = new Intent(activity.getApplicationContext(), PasscodeUnlockActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.getApplication().startActivity(i);
        }
    }

    @Override public void onActivityCreated(Activity arg0, Bundle arg1) {}
    @Override public void onActivityDestroyed(Activity arg0) {}
    @Override public void onActivitySaveInstanceState(Activity arg0, Bundle arg1) {}
    @Override public void onActivityStarted(Activity arg0) {}
    @Override public void onActivityStopped(Activity arg0) {}

    public void enable() {
        if (!isPasswordLocked()) return;
        if (isSupportedApi()) {
            mCurrentApp.unregisterActivityLifecycleCallbacks(this);
            mCurrentApp.registerActivityLifecycleCallbacks(this);
        }
    }

    public void disable() {
        if (isSupportedApi()) {
            mCurrentApp.unregisterActivityLifecycleCallbacks(this);
        }
    }

    public boolean isPasswordLocked() {
        return mPreferenceStore.getPassword() != null;
    }

    public boolean setPassword(String password) {
        if (TextUtils.isEmpty(password)) {
            mPreferenceStore.clearPassword();
            disable();
        } else {
            mPreferenceStore.setPasswordHash(password.hashCode());
            enable();
        }
        return true;
    }

    @Override
    public boolean isFingerprintEnabled() {
        return mPreferenceStore.isFingerprintEnabled();
    }

    @Override
    public boolean enableFingerprint() {
        mPreferenceStore.setFingerprintEnabled(true);
        return true;
    }

    @Override
    public boolean disableFingerprint() {
        mPreferenceStore.setFingerprintEnabled(false);
        return true;
    }

    public void forcePasswordLock() {
        mLostFocusDate = null;
    }

    public boolean verifyPassword(String password) {
        if (TextUtils.isEmpty(password)) return false;

        // successful fingerprint scan bypasses PIN security
        if (isFingerprintPassword(password)) {
            mLostFocusDate = new Date();
            return true;
        }

        PasscodePreferenceStore.StoredPassword storedPassword = mPreferenceStore.getPassword();
        if (storedPassword == null) {
            return false;
        }

        boolean matches;
        switch (storedPassword.getFormat()) {
            case OLD_MD5:
                matches = storedPassword.getString().equalsIgnoreCase(legacyPasswordHash(password));
                break;
            case LEGACY_ENCRYPTED:
                String decryptedPassword = stripSalt(decryptPassword(storedPassword.getString()));
                matches = decryptedPassword.equalsIgnoreCase(password);
                if (matches && password.hashCode() != -1) {
                    mPreferenceStore.setPasswordHash(password.hashCode());
                }
                break;
            case HASH:
                matches = storedPassword.getHash() == password.hashCode();
                break;
            case INVALID:
            default:
                matches = false;
                break;
        }

        if (!matches) return false;

        mLostFocusDate = new Date();
        return true;
    }

    private String stripSalt(String saltedPassword) {
        if (TextUtils.isEmpty(saltedPassword) || saltedPassword.length() < 4) return "";
        int middle = saltedPassword.length() / 2;
        return saltedPassword.substring(middle - 2, middle + 2);
    }

    /** Show the unlock screen if there is a saved password and the timeout period has elapsed. */
    private boolean shouldShowUnlockScreen() {
        if(!isPasswordLocked()) return false;
        if(mLostFocusDate == null) return true;

        int currentTimeOut = getTimeout();
        setOneTimeTimeout(DEFAULT_TIMEOUT_S);

        if (timeSinceLocked() < currentTimeOut) return false;
        mLostFocusDate = null;
        return true;
    }

    private int timeSinceLocked() {
        return Math.abs((int) ((new Date().getTime() - mLostFocusDate.getTime()) / 1000));
    }

    //
    // Legacy methods for backwards compatibility of passwords stored using deprecated security
    //

    private String legacyPasswordHash(String rawPassword) {
        return StringUtils.getMd5Hash(OLD_PASSWORD_SALT + rawPassword + OLD_PASSWORD_SALT);
    }

    private String decryptPassword(String encryptedPwd) {
        try {
            DESKeySpec keySpec = new DESKeySpec(BuildConfig.PASSWORD_ENC_SECRET.getBytes("UTF-8"));
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey key = keyFactory.generateSecret(keySpec);

            byte[] encryptedWithoutB64 = Base64.decode(encryptedPwd, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("DES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] plainTextPwdBytes = cipher.doFinal(encryptedWithoutB64);
            return new String(plainTextPwdBytes);
        } catch (Exception e) {
        }
        return encryptedPwd;
    }
}
