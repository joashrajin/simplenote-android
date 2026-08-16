package org.wordpress.passcodelock;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.Map;

public final class PasscodePreferenceStore {
    static final String FINGERPRINT_KEY = BuildConfig.FINGERPRINT_ENABLED_KEY;
    static final String MIGRATION_COMPLETE_KEY = "legacy_migration_complete";
    static final String OLD_PASSWORD_KEY = "wp_app_lock_password_key";
    static final String PASSWORD_KEY = BuildConfig.PASSWORD_PREFERENCE_KEY;
    static final String PREFERENCES_NAME = "passcode_lock";

    private static final Object PASSCODE_LOCK = new Object();
    private static boolean sLegacyCleanupPending;

    private final SharedPreferences mPreferences;
    private final SharedPreferences mLegacyPreferences;

    public static PasscodePreferenceStore from(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }

        return new PasscodePreferenceStore(
                applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
                PreferenceManager.getDefaultSharedPreferences(applicationContext)
        );
    }

    PasscodePreferenceStore(SharedPreferences preferences, SharedPreferences legacyPreferences) {
        mPreferences = preferences;
        mLegacyPreferences = legacyPreferences;
    }

    StoredPassword getPassword() {
        synchronized (PASSCODE_LOCK) {
            return getEffectiveStateLocked().getPassword();
        }
    }

    boolean setPasswordHash(int hash) {
        synchronized (PASSCODE_LOCK) {
            PasscodeState state = getEffectiveStateLocked().withPasswordHash(hash);
            return commitMutationLocked(state);
        }
    }

    boolean clearPassword() {
        synchronized (PASSCODE_LOCK) {
            PasscodeState state = getEffectiveStateLocked().withoutPassword();
            return commitMutationLocked(state);
        }
    }

    boolean isFingerprintEnabled() {
        synchronized (PASSCODE_LOCK) {
            return getEffectiveStateLocked().isFingerprintEnabled();
        }
    }

    boolean setFingerprintEnabled(boolean enabled) {
        synchronized (PASSCODE_LOCK) {
            PasscodeState state = getEffectiveStateLocked().withFingerprintEnabled(enabled);
            return commitMutationLocked(state);
        }
    }

    public boolean prepareForBackup() {
        synchronized (PASSCODE_LOCK) {
            Snapshot dedicated = readSnapshot(mPreferences);
            Snapshot legacy = readSnapshot(mLegacyPreferences);
            PasscodeState state = dedicated.migrationComplete
                    ? dedicated.state
                    : PasscodeState.merge(dedicated.state, legacy.state);

            if (!state.hasValidTypes()) {
                return false;
            }

            if (dedicated.migrationComplete
                    && !legacy.hasPasscodeValues
                    && !sLegacyCleanupPending) {
                return true;
            }

            return commitState(state) && removeLegacyState();
        }
    }

    public boolean clearPasscodeWithRetry() {
        synchronized (PASSCODE_LOCK) {
            return clearPasscodeLocked() || clearPasscodeLocked();
        }
    }

    private PasscodeState getEffectiveStateLocked() {
        Snapshot dedicated = readSnapshot(mPreferences);
        Snapshot legacy = readSnapshot(mLegacyPreferences);
        if (dedicated.migrationComplete) {
            persistDedicatedStateBeforeRemovingLegacy(
                    dedicated.state,
                    legacy.hasPasscodeValues || sLegacyCleanupPending
            );
            return dedicated.state;
        }

        PasscodeState effective = PasscodeState.merge(dedicated.state, legacy.state);
        if (effective.hasValidTypes() && commitState(effective)) {
            removeLegacyState();
        }
        return effective;
    }

    private boolean commitMutationLocked(PasscodeState state) {
        if (!state.hasValidTypes() || !commitState(state)) {
            return false;
        }

        removeLegacyState();
        return true;
    }

    private void persistDedicatedStateBeforeRemovingLegacy(
            PasscodeState state,
            boolean hasLegacyState
    ) {
        if (!hasLegacyState || !state.hasValidTypes()) {
            return;
        }

        if (commitState(state)) {
            removeLegacyState();
        }
    }

    private boolean clearPasscodeLocked() {
        boolean stateCommitted = commitState(PasscodeState.empty());
        boolean legacyRemoved = removeLegacyState();
        return stateCommitted && legacyRemoved;
    }

    private boolean commitState(PasscodeState state) {
        SharedPreferences.Editor editor = mPreferences.edit()
                .remove(OLD_PASSWORD_KEY)
                .remove(PASSWORD_KEY)
                .remove(FINGERPRINT_KEY);
        if (state.oldPasswordPresent) {
            editor.putString(OLD_PASSWORD_KEY, (String) state.oldPassword);
        }
        if (state.passwordPresent) {
            if (state.password instanceof Integer) {
                editor.putInt(PASSWORD_KEY, (Integer) state.password);
            } else {
                editor.putString(PASSWORD_KEY, (String) state.password);
            }
        }
        if (state.fingerprintPresent) {
            editor.putBoolean(FINGERPRINT_KEY, (Boolean) state.fingerprintEnabled);
        }
        editor.putBoolean(MIGRATION_COMPLETE_KEY, true);
        return editor.commit();
    }

    private boolean removeLegacyState() {
        Snapshot legacy = readSnapshot(mLegacyPreferences);
        if (!legacy.hasPasscodeValues && !sLegacyCleanupPending) {
            return true;
        }

        sLegacyCleanupPending = true;
        boolean removed = mLegacyPreferences.edit()
                .remove(OLD_PASSWORD_KEY)
                .remove(PASSWORD_KEY)
                .remove(FINGERPRINT_KEY)
                .commit();
        sLegacyCleanupPending = !removed;
        return removed;
    }

    private static Snapshot readSnapshot(SharedPreferences preferences) {
        Map<String, ?> values = preferences.getAll();
        if (values == null) {
            values = Collections.emptyMap();
        }
        PasscodeState state = new PasscodeState(
                values.containsKey(OLD_PASSWORD_KEY),
                values.get(OLD_PASSWORD_KEY),
                values.containsKey(PASSWORD_KEY),
                values.get(PASSWORD_KEY),
                values.containsKey(FINGERPRINT_KEY),
                values.get(FINGERPRINT_KEY)
        );
        return new Snapshot(
                state,
                Boolean.TRUE.equals(values.get(MIGRATION_COMPLETE_KEY)),
                state.hasPasscodeValues()
        );
    }

    enum PasswordFormat {
        OLD_MD5,
        LEGACY_ENCRYPTED,
        HASH,
        INVALID
    }

    static final class StoredPassword {
        private final PasswordFormat mFormat;
        private final String mString;
        private final int mHash;

        private StoredPassword(PasswordFormat format, String string, int hash) {
            mFormat = format;
            mString = string;
            mHash = hash;
        }

        PasswordFormat getFormat() {
            return mFormat;
        }

        String getString() {
            return mString;
        }

        int getHash() {
            return mHash;
        }
    }

    private static final class Snapshot {
        private final PasscodeState state;
        private final boolean migrationComplete;
        private final boolean hasPasscodeValues;

        private Snapshot(
                PasscodeState state,
                boolean migrationComplete,
                boolean hasPasscodeValues
        ) {
            this.state = state;
            this.migrationComplete = migrationComplete;
            this.hasPasscodeValues = hasPasscodeValues;
        }
    }

    private static final class PasscodeState {
        private final boolean oldPasswordPresent;
        private final Object oldPassword;
        private final boolean passwordPresent;
        private final Object password;
        private final boolean fingerprintPresent;
        private final Object fingerprintEnabled;

        private PasscodeState(
                boolean oldPasswordPresent,
                Object oldPassword,
                boolean passwordPresent,
                Object password,
                boolean fingerprintPresent,
                Object fingerprintEnabled
        ) {
            this.oldPasswordPresent = oldPasswordPresent;
            this.oldPassword = oldPassword;
            this.passwordPresent = passwordPresent;
            this.password = password;
            this.fingerprintPresent = fingerprintPresent;
            this.fingerprintEnabled = fingerprintEnabled;
        }

        private static PasscodeState empty() {
            return new PasscodeState(false, null, false, null, false, null);
        }

        private static PasscodeState merge(PasscodeState preferred, PasscodeState fallback) {
            return new PasscodeState(
                    preferred.oldPasswordPresent || fallback.oldPasswordPresent,
                    preferred.oldPasswordPresent ? preferred.oldPassword : fallback.oldPassword,
                    preferred.passwordPresent || fallback.passwordPresent,
                    preferred.passwordPresent ? preferred.password : fallback.password,
                    preferred.fingerprintPresent || fallback.fingerprintPresent,
                    preferred.fingerprintPresent
                            ? preferred.fingerprintEnabled
                            : fallback.fingerprintEnabled
            );
        }

        private PasscodeState withPasswordHash(int hash) {
            boolean validFingerprint = fingerprintPresent && fingerprintEnabled instanceof Boolean;
            return new PasscodeState(
                    false,
                    null,
                    true,
                    hash,
                    validFingerprint,
                    validFingerprint ? fingerprintEnabled : null
            );
        }

        private PasscodeState withoutPassword() {
            boolean validFingerprint = fingerprintPresent && fingerprintEnabled instanceof Boolean;
            return new PasscodeState(
                    false,
                    null,
                    false,
                    null,
                    validFingerprint,
                    validFingerprint ? fingerprintEnabled : null
            );
        }

        private PasscodeState withFingerprintEnabled(boolean enabled) {
            return new PasscodeState(
                    oldPasswordPresent,
                    oldPassword,
                    passwordPresent,
                    password,
                    true,
                    enabled
            );
        }

        private boolean hasPasscodeValues() {
            return oldPasswordPresent || passwordPresent || fingerprintPresent;
        }

        private boolean hasValidTypes() {
            return (!oldPasswordPresent || oldPassword instanceof String)
                    && (!passwordPresent || password instanceof String || password instanceof Integer)
                    && (!fingerprintPresent || fingerprintEnabled instanceof Boolean);
        }

        private StoredPassword getPassword() {
            if (oldPasswordPresent) {
                if (oldPassword instanceof String) {
                    return new StoredPassword(PasswordFormat.OLD_MD5, (String) oldPassword, 0);
                }
                return new StoredPassword(PasswordFormat.INVALID, null, 0);
            }
            if (!passwordPresent) {
                return null;
            }
            if (password instanceof String) {
                return new StoredPassword(PasswordFormat.LEGACY_ENCRYPTED, (String) password, 0);
            }
            if (password instanceof Integer) {
                return new StoredPassword(PasswordFormat.HASH, null, (Integer) password);
            }
            return new StoredPassword(PasswordFormat.INVALID, null, 0);
        }

        private boolean isFingerprintEnabled() {
            if (!fingerprintPresent) {
                return true;
            }
            return fingerprintEnabled instanceof Boolean && (Boolean) fingerprintEnabled;
        }
    }
}
