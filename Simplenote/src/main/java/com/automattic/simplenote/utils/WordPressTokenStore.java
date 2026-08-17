package com.automattic.simplenote.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public final class WordPressTokenStore {
    static final String LEGACY_TOKEN_KEY = PrefUtils.PREF_WP_TOKEN;
    static final String MIGRATION_COMPLETE_KEY = "legacy_migration_complete";
    static final String PREFERENCES_NAME = "wordpress_auth";
    static final String TOKEN_KEY = "access_token";
    private static final Object TOKEN_LOCK = new Object();

    private final SharedPreferences mPreferences;
    private final SharedPreferences mLegacyPreferences;

    public static WordPressTokenStore from(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }

        return new WordPressTokenStore(
            applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        );
    }

    WordPressTokenStore(SharedPreferences preferences, SharedPreferences legacyPreferences) {
        mPreferences = preferences;
        mLegacyPreferences = legacyPreferences;
    }

    // Migration and tombstone-scrub states commit synchronously so the result is trustworthy;
    // steady state (tombstoned, no legacy key) is write-free, making this a one-time cost per
    // transition even on main-thread callers.
    public String getToken() {
        synchronized (TOKEN_LOCK) {
            String token = getString(mPreferences, TOKEN_KEY);
            if (getBoolean(mPreferences, MIGRATION_COMPLETE_KEY)) {
                persistStateBeforeRemovingLegacyToken(token);
                return token;
            }

            String legacyToken = getString(mLegacyPreferences, LEGACY_TOKEN_KEY);
            String tokenToStore = token.isEmpty() ? legacyToken : token;
            if (commitState(tokenToStore)) {
                removeLegacyToken();
            }

            return tokenToStore;
        }
    }

    public boolean setToken(String token) {
        synchronized (TOKEN_LOCK) {
            String tokenToStore = emptyIfBlank(token);
            if (tokenToStore.isEmpty()) {
                return clearTokenLocked();
            }

            if (!commitState(tokenToStore)) {
                return false;
            }

            removeLegacyToken();
            return true;
        }
    }

    public boolean clearToken() {
        synchronized (TOKEN_LOCK) {
            return clearTokenLocked();
        }
    }

    public boolean clearTokenWithRetry() {
        return clearToken() || clearToken();
    }

    public boolean prepareForBackup() {
        synchronized (TOKEN_LOCK) {
            if (getBoolean(mPreferences, MIGRATION_COMPLETE_KEY)) {
                return persistStateBeforeRemovingLegacyToken(getString(mPreferences, TOKEN_KEY));
            }

            String token = getString(mPreferences, TOKEN_KEY);
            String legacyToken = getString(mLegacyPreferences, LEGACY_TOKEN_KEY);
            String tokenToStore = token.isEmpty() ? legacyToken : token;

            return commitState(tokenToStore) && removeLegacyToken();
        }
    }

    private boolean clearTokenLocked() {
        boolean stateCommitted = commitState("");
        boolean legacyRemoved = removeLegacyToken();
        return stateCommitted && legacyRemoved;
    }

    private boolean persistStateBeforeRemovingLegacyToken(String token) {
        if (!mLegacyPreferences.contains(LEGACY_TOKEN_KEY)) {
            return true;
        }

        return commitState(token) && removeLegacyToken();
    }

    private boolean commitState(String token) {
        SharedPreferences.Editor editor = mPreferences.edit();
        if (token.isEmpty()) {
            editor.remove(TOKEN_KEY);
        } else {
            editor.putString(TOKEN_KEY, token);
        }
        editor.putBoolean(MIGRATION_COMPLETE_KEY, true);
        return editor.commit();
    }

    private boolean removeLegacyToken() {
        if (!mLegacyPreferences.contains(LEGACY_TOKEN_KEY)) {
            return true;
        }

        return mLegacyPreferences.edit().remove(LEGACY_TOKEN_KEY).commit();
    }

    private static boolean getBoolean(SharedPreferences preferences, String key) {
        try {
            return preferences.getBoolean(key, false);
        } catch (ClassCastException exception) {
            return false;
        }
    }

    private static String getString(SharedPreferences preferences, String key) {
        try {
            String value = preferences.getString(key, "");
            return emptyIfBlank(value);
        } catch (ClassCastException exception) {
            return "";
        }
    }

    private static String emptyIfBlank(String value) {
        return value == null || value.trim().isEmpty() ? "" : value;
    }
}
