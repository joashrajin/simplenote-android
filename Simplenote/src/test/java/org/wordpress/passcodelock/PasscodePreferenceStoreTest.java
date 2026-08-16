package org.wordpress.passcodelock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wordpress.passcodelock.PasscodePreferenceStore.FINGERPRINT_KEY;
import static org.wordpress.passcodelock.PasscodePreferenceStore.MIGRATION_COMPLETE_KEY;
import static org.wordpress.passcodelock.PasscodePreferenceStore.OLD_PASSWORD_KEY;
import static org.wordpress.passcodelock.PasscodePreferenceStore.PASSWORD_KEY;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PasscodePreferenceStoreTest {
    private static final String OLD_PASSWORD_SALT = "sadasauidhsuyeuihdahdiauhs";

    private Application mApplication;
    private SharedPreferences mPreferences;
    private SharedPreferences mLegacyPreferences;
    private PasscodePreferenceStore mStore;

    @Before
    public void setUp() {
        mApplication = ApplicationProvider.getApplicationContext();
        mPreferences = mApplication.getSharedPreferences(
                PasscodePreferenceStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
        mLegacyPreferences = PreferenceManager.getDefaultSharedPreferences(mApplication);
        mPreferences.edit().clear().commit();
        mLegacyPreferences.edit().clear().commit();
        new PasscodePreferenceStore(mPreferences, mLegacyPreferences).clearPasscodeWithRetry();
        mPreferences.edit().clear().commit();
        mStore = new PasscodePreferenceStore(mPreferences, mLegacyPreferences);
    }

    @Test
    public void integerHashMigratesAndStillVerifies() {
        mLegacyPreferences.edit().putInt(PASSWORD_KEY, "1234".hashCode()).commit();

        PasscodePreferenceStore.StoredPassword storedPassword = mStore.getPassword();

        assertEquals(PasscodePreferenceStore.PasswordFormat.HASH, storedPassword.getFormat());
        assertEquals("1234".hashCode(), storedPassword.getHash());
        assertEquals("1234".hashCode(), mPreferences.getInt(PASSWORD_KEY, 0));
        assertTrue(mPreferences.getBoolean(MIGRATION_COMPLETE_KEY, false));
        assertFalse(mLegacyPreferences.contains(PASSWORD_KEY));
        DefaultAppLock appLock = new DefaultAppLock(mApplication, mStore);
        assertTrue(appLock.verifyPassword("1234"));
        assertFalse(appLock.verifyPassword("4321"));
    }

    @Test
    public void oldestMd5PasswordKeepsPriorityAndBothValuesMigrate() {
        String password = "2468";
        String oldHash = StringUtils.getMd5Hash(OLD_PASSWORD_SALT + password + OLD_PASSWORD_SALT);
        mLegacyPreferences.edit()
                .putString(OLD_PASSWORD_KEY, oldHash)
                .putInt(PASSWORD_KEY, "wrong".hashCode())
                .commit();

        PasscodePreferenceStore.StoredPassword storedPassword = mStore.getPassword();

        assertEquals(PasscodePreferenceStore.PasswordFormat.OLD_MD5, storedPassword.getFormat());
        assertEquals(oldHash, storedPassword.getString());
        assertEquals(oldHash, mPreferences.getString(OLD_PASSWORD_KEY, ""));
        assertEquals("wrong".hashCode(), mPreferences.getInt(PASSWORD_KEY, 0));
        assertTrue(new DefaultAppLock(mApplication, mStore).verifyPassword(password));
    }

    @Test
    public void encryptedPasswordMigratesAndSuccessfulVerificationUpgradesIt() throws Exception {
        String password = "1357";
        mLegacyPreferences.edit().putString(PASSWORD_KEY, encryptLegacyPassword(password)).commit();

        PasscodePreferenceStore.StoredPassword storedPassword = mStore.getPassword();

        assertEquals(PasscodePreferenceStore.PasswordFormat.LEGACY_ENCRYPTED, storedPassword.getFormat());
        assertTrue(new DefaultAppLock(mApplication, mStore).verifyPassword(password));
        assertEquals(password.hashCode(), mPreferences.getInt(PASSWORD_KEY, 0));
        assertFalse(mPreferences.contains(OLD_PASSWORD_KEY));
        assertFalse(mLegacyPreferences.contains(PASSWORD_KEY));
    }

    @Test
    public void explicitFalseFingerprintSettingMigrates() {
        mLegacyPreferences.edit().putBoolean(FINGERPRINT_KEY, false).commit();

        assertFalse(mStore.isFingerprintEnabled());

        assertTrue(mPreferences.contains(FINGERPRINT_KEY));
        assertFalse(mPreferences.getBoolean(FINGERPRINT_KEY, true));
        assertFalse(mLegacyPreferences.contains(FINGERPRINT_KEY));
    }

    @Test
    public void missingFingerprintSettingKeepsTheHistoricalDefault() {
        assertTrue(mStore.isFingerprintEnabled());
        assertFalse(mPreferences.contains(FINGERPRINT_KEY));
    }

    @Test
    public void dedicatedTombstoneWinsOverStaleLegacyPassword() {
        mPreferences.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).commit();
        mLegacyPreferences.edit().putInt(PASSWORD_KEY, "stale".hashCode()).commit();

        assertNull(mStore.getPassword());

        assertFalse(mPreferences.contains(PASSWORD_KEY));
        assertFalse(mLegacyPreferences.contains(PASSWORD_KEY));
    }

    @Test
    public void unmarkedDedicatedPasswordWinsOverLegacyPassword() {
        mPreferences.edit().putInt(PASSWORD_KEY, "new".hashCode()).commit();
        mLegacyPreferences.edit().putInt(PASSWORD_KEY, "old".hashCode()).commit();

        PasscodePreferenceStore.StoredPassword password = mStore.getPassword();

        assertEquals("new".hashCode(), password.getHash());
        assertTrue(mPreferences.getBoolean(MIGRATION_COMPLETE_KEY, false));
        assertFalse(mLegacyPreferences.contains(PASSWORD_KEY));
    }

    @Test
    public void passwordMutationsWriteOnlyDedicatedStorage() {
        mLegacyPreferences.edit()
                .putString(OLD_PASSWORD_KEY, "old")
                .putInt(PASSWORD_KEY, 17)
                .putBoolean(FINGERPRINT_KEY, false)
                .commit();

        assertTrue(mStore.setPasswordHash(42));

        assertEquals(42, mPreferences.getInt(PASSWORD_KEY, 0));
        assertFalse(mPreferences.contains(OLD_PASSWORD_KEY));
        assertFalse(mPreferences.getBoolean(FINGERPRINT_KEY, true));
        assertFalse(mLegacyPreferences.contains(OLD_PASSWORD_KEY));
        assertFalse(mLegacyPreferences.contains(PASSWORD_KEY));
        assertFalse(mLegacyPreferences.contains(FINGERPRINT_KEY));

        assertTrue(mStore.clearPassword());
        assertNull(mStore.getPassword());
        assertFalse(mPreferences.contains(OLD_PASSWORD_KEY));
        assertFalse(mPreferences.contains(PASSWORD_KEY));
        assertFalse(mPreferences.getBoolean(FINGERPRINT_KEY, true));
    }

    @Test
    public void malformedFingerprintStateDoesNotBlockPasswordMutations() {
        mLegacyPreferences.edit()
                .putInt(PASSWORD_KEY, 17)
                .putString(FINGERPRINT_KEY, "invalid")
                .commit();

        assertTrue(mStore.clearPassword());
        assertNull(mStore.getPassword());
        assertFalse(mPreferences.contains(FINGERPRINT_KEY));
        assertFalse(mLegacyPreferences.contains(FINGERPRINT_KEY));

        mPreferences.edit().putString(FINGERPRINT_KEY, "invalid").commit();
        assertTrue(mStore.setPasswordHash(42));
        assertEquals(42, mPreferences.getInt(PASSWORD_KEY, 0));
        assertFalse(mPreferences.contains(FINGERPRINT_KEY));
    }

    @Test
    public void malformedLegacyPasswordRemainsLockedAndBlocksBackup() {
        mLegacyPreferences.edit().putLong(PASSWORD_KEY, 7L).commit();

        PasscodePreferenceStore.StoredPassword password = mStore.getPassword();

        assertEquals(PasscodePreferenceStore.PasswordFormat.INVALID, password.getFormat());
        assertTrue(mLegacyPreferences.contains(PASSWORD_KEY));
        assertFalse(mPreferences.getBoolean(MIGRATION_COMPLETE_KEY, false));
        assertFalse(mStore.prepareForBackup());
        assertFalse(new DefaultAppLock(mApplication, mStore).verifyPassword("7"));
    }

    @Test
    public void prepareForBackupKeepsLegacyStateWhenDedicatedCommitFails() {
        MockPreferences stores = new MockPreferences();
        stores.legacyState.put(PASSWORD_KEY, 23);
        when(stores.editor.commit()).thenReturn(false);
        PasscodePreferenceStore store = new PasscodePreferenceStore(stores.preferences, stores.legacyPreferences);

        assertFalse(store.prepareForBackup());

        verify(stores.legacyPreferences, never()).edit();
    }

    @Test
    public void prepareForBackupFailsWhenLegacyStateCannotBeDeleted() {
        MockPreferences stores = new MockPreferences();
        stores.legacyState.put(PASSWORD_KEY, 23);
        when(stores.legacyEditor.commit()).thenReturn(false);
        PasscodePreferenceStore store = new PasscodePreferenceStore(stores.preferences, stores.legacyPreferences);

        assertFalse(store.prepareForBackup());

        InOrder order = inOrder(stores.editor, stores.legacyEditor);
        order.verify(stores.editor).commit();
        order.verify(stores.legacyEditor).commit();
    }

    @Test
    public void failedLegacyDeletionIsRetriedAfterTheInMemoryKeyDisappears() {
        MockPreferences stores = new MockPreferences();
        stores.legacyState.put(PASSWORD_KEY, 23);
        when(stores.legacyEditor.commit()).thenReturn(false, true);
        PasscodePreferenceStore store = new PasscodePreferenceStore(stores.preferences, stores.legacyPreferences);

        assertFalse(store.prepareForBackup());
        assertFalse(stores.legacyState.containsKey(PASSWORD_KEY));

        assertTrue(store.prepareForBackup());
        verify(stores.legacyEditor, times(2)).commit();
    }

    @Test
    public void failedInMemoryMigrationIsRecommittedBeforeLegacyDeletion() {
        MockPreferences stores = new MockPreferences();
        stores.legacyState.put(PASSWORD_KEY, 23);
        AtomicInteger commits = new AtomicInteger();
        when(stores.editor.commit()).thenAnswer(invocation -> commits.getAndIncrement() > 0);
        PasscodePreferenceStore store = new PasscodePreferenceStore(stores.preferences, stores.legacyPreferences);

        assertEquals(23, store.getPassword().getHash());
        assertTrue(stores.legacyState.containsKey(PASSWORD_KEY));

        assertTrue(store.prepareForBackup());
        assertEquals(2, commits.get());
        assertFalse(stores.legacyState.containsKey(PASSWORD_KEY));
    }

    @Test
    public void restoreClearRemovesPasscodeStateAndPreservesUnrelatedPreference() {
        mPreferences.edit()
                .putString(OLD_PASSWORD_KEY, "old")
                .putInt(PASSWORD_KEY, 17)
                .putBoolean(FINGERPRINT_KEY, false)
                .putBoolean(MIGRATION_COMPLETE_KEY, true)
                .commit();
        mLegacyPreferences.edit()
                .putString(OLD_PASSWORD_KEY, "legacy-old")
                .putInt(PASSWORD_KEY, 19)
                .putBoolean(FINGERPRINT_KEY, true)
                .putString("unrelated", "keep")
                .commit();

        assertTrue(mStore.clearPasscodeWithRetry());

        assertNull(mStore.getPassword());
        assertTrue(mStore.isFingerprintEnabled());
        assertFalse(mPreferences.contains(FINGERPRINT_KEY));
        assertFalse(mLegacyPreferences.contains(OLD_PASSWORD_KEY));
        assertFalse(mLegacyPreferences.contains(PASSWORD_KEY));
        assertFalse(mLegacyPreferences.contains(FINGERPRINT_KEY));
        assertEquals("keep", mLegacyPreferences.getString("unrelated", ""));
    }

    @Test
    public void restoreClearRetriesOnceAfterDedicatedCommitFailure() {
        MockPreferences stores = new MockPreferences();
        stores.dedicatedState.put(PASSWORD_KEY, 23);
        stores.legacyState.put(PASSWORD_KEY, 23);
        when(stores.editor.commit()).thenReturn(false, true);
        PasscodePreferenceStore store = new PasscodePreferenceStore(stores.preferences, stores.legacyPreferences);

        assertTrue(store.clearPasscodeWithRetry());

        verify(stores.editor, times(2)).commit();
    }

    @Test
    public void restoreClearReportsFailureWhenLegacyDeletionFailsTwice() {
        MockPreferences stores = new MockPreferences();
        stores.legacyState.put(PASSWORD_KEY, 23);
        when(stores.legacyEditor.commit()).thenReturn(false, false);
        PasscodePreferenceStore store = new PasscodePreferenceStore(stores.preferences, stores.legacyPreferences);

        assertFalse(store.clearPasscodeWithRetry());

        assertFalse(stores.legacyState.containsKey(PASSWORD_KEY));
        verify(stores.legacyEditor, times(2)).commit();
    }

    @Test(timeout = 10_000)
    public void separateStoreInstancesSerializeMigrationAndRestoreClear() throws Exception {
        MockPreferences migrating = new MockPreferences();
        migrating.legacyState.put(PASSWORD_KEY, 23);
        CountDownLatch migrationRead = new CountDownLatch(1);
        CountDownLatch continueMigration = new CountDownLatch(1);
        when(migrating.legacyPreferences.getAll()).thenAnswer(invocation -> {
            migrationRead.countDown();
            if (!continueMigration.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to continue migration");
            }
            return new HashMap<>(migrating.legacyState);
        });
        PasscodePreferenceStore first = new PasscodePreferenceStore(
                migrating.preferences,
                migrating.legacyPreferences
        );
        MockPreferences clearing = new MockPreferences();
        PasscodePreferenceStore second = new PasscodePreferenceStore(
                clearing.preferences,
                clearing.legacyPreferences
        );
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread migrationThread = new Thread(() -> runSafely(first::getPassword, failure), "passcode-migration");
        Thread clearThread = new Thread(
                () -> runSafely(second::clearPasscodeWithRetry, failure),
                "passcode-restore-clear"
        );

        migrationThread.start();
        assertTrue(migrationRead.await(5, TimeUnit.SECONDS));
        clearThread.start();
        try {
            assertEquals(Thread.State.BLOCKED, waitForBlockedOrTerminated(clearThread));
        } finally {
            continueMigration.countDown();
        }

        migrationThread.join(5_000);
        clearThread.join(5_000);
        assertFalse(migrationThread.isAlive());
        assertFalse(clearThread.isAlive());
        assertNull(failure.get());
    }

    @Test
    public void failedDiskCommitKeepsTheHistoricalPasswordLifecycleContract() {
        MockPreferences stores = new MockPreferences();
        when(stores.editor.commit()).thenReturn(false);
        PasscodePreferenceStore store = new PasscodePreferenceStore(stores.preferences, stores.legacyPreferences);
        Application application = mock(Application.class);
        DefaultAppLock appLock = new DefaultAppLock(application, store);

        assertTrue(appLock.setPassword("1234"));

        assertEquals("1234".hashCode(), stores.dedicatedState.get(PASSWORD_KEY));
        verify(application).unregisterActivityLifecycleCallbacks(appLock);
        verify(application).registerActivityLifecycleCallbacks(appLock);
    }

    private static String encryptLegacyPassword(String password) throws Exception {
        DESKeySpec keySpec = new DESKeySpec(BuildConfig.PASSWORD_ENC_SECRET.getBytes(StandardCharsets.UTF_8));
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey key = keyFactory.generateSecret(keySpec);
        Cipher cipher = Cipher.getInstance("DES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(("aa" + password + "bb").getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.DEFAULT);
    }

    private static void runSafely(Runnable runnable, AtomicReference<Throwable> failure) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }

    private static Thread.State waitForBlockedOrTerminated(Thread thread) {
        long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Thread.State state = thread.getState();
        while (state != Thread.State.BLOCKED && state != Thread.State.TERMINATED && System.nanoTime() < timeout) {
            Thread.yield();
            state = thread.getState();
        }
        return state;
    }

    private static class MockPreferences {
        private final SharedPreferences preferences = mock(SharedPreferences.class);
        private final SharedPreferences legacyPreferences = mock(SharedPreferences.class);
        private final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        private final SharedPreferences.Editor legacyEditor = mock(SharedPreferences.Editor.class);
        private final Map<String, Object> dedicatedState = new ConcurrentHashMap<>();
        private final Map<String, Object> legacyState = new ConcurrentHashMap<>();

        private MockPreferences() {
            when(preferences.getAll()).thenAnswer(invocation -> new HashMap<>(dedicatedState));
            when(legacyPreferences.getAll()).thenAnswer(invocation -> new HashMap<>(legacyState));
            when(preferences.edit()).thenReturn(editor);
            when(legacyPreferences.edit()).thenReturn(legacyEditor);
            when(editor.putBoolean(anyString(), anyBoolean())).thenAnswer(invocation -> {
                dedicatedState.put(invocation.getArgument(0), invocation.getArgument(1));
                return editor;
            });
            when(editor.putInt(anyString(), anyInt())).thenAnswer(invocation -> {
                dedicatedState.put(invocation.getArgument(0), invocation.getArgument(1));
                return editor;
            });
            when(editor.putString(anyString(), anyString())).thenAnswer(invocation -> {
                dedicatedState.put(invocation.getArgument(0), invocation.getArgument(1));
                return editor;
            });
            when(editor.remove(anyString())).thenAnswer(invocation -> {
                dedicatedState.remove(invocation.getArgument(0));
                return editor;
            });
            when(legacyEditor.remove(anyString())).thenAnswer(invocation -> {
                legacyState.remove(invocation.getArgument(0));
                return legacyEditor;
            });
            when(editor.commit()).thenReturn(true);
            when(legacyEditor.commit()).thenReturn(true);
        }
    }
}
