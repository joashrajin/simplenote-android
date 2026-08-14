package com.automattic.simplenote.utils;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.automattic.simplenote.utils.WordPressTokenStore.LEGACY_TOKEN_KEY;
import static com.automattic.simplenote.utils.WordPressTokenStore.MIGRATION_COMPLETE_KEY;
import static com.automattic.simplenote.utils.WordPressTokenStore.TOKEN_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WordPressTokenStoreTest {
    private SharedPreferences mPreferences;
    private SharedPreferences mLegacyPreferences;
    private SharedPreferences.Editor mEditor;
    private SharedPreferences.Editor mLegacyEditor;
    private WordPressTokenStore mStore;

    @Before
    public void setUp() {
        mPreferences = mock(SharedPreferences.class);
        mLegacyPreferences = mock(SharedPreferences.class);
        mEditor = mock(SharedPreferences.Editor.class);
        mLegacyEditor = mock(SharedPreferences.Editor.class);

        when(mPreferences.edit()).thenReturn(mEditor);
        when(mLegacyPreferences.edit()).thenReturn(mLegacyEditor);
        when(mEditor.putBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(mEditor);
        when(mEditor.putString(anyString(), anyString())).thenReturn(mEditor);
        when(mEditor.remove(anyString())).thenReturn(mEditor);
        when(mLegacyEditor.remove(anyString())).thenReturn(mLegacyEditor);
        when(mEditor.commit()).thenReturn(true);
        when(mLegacyEditor.commit()).thenReturn(true);

        mStore = new WordPressTokenStore(mPreferences, mLegacyPreferences);
    }

    @Test
    public void getTokenMigratesLegacyTokenBeforeDeletingIt() {
        setStoredState("", false, "legacy-token");

        assertEquals("legacy-token", mStore.getToken());

        InOrder order = inOrder(mEditor, mLegacyPreferences, mLegacyEditor);
        order.verify(mEditor).putString(TOKEN_KEY, "legacy-token");
        order.verify(mEditor).putBoolean(MIGRATION_COMPLETE_KEY, true);
        order.verify(mEditor).commit();
        order.verify(mLegacyPreferences).edit();
        order.verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
        order.verify(mLegacyEditor).commit();
    }

    @Test
    public void getTokenKeepsLegacyTokenWhenDedicatedWriteFails() {
        setStoredState("", false, "legacy-token");
        when(mEditor.commit()).thenReturn(false);

        assertEquals("legacy-token", mStore.getToken());

        verify(mLegacyPreferences, never()).edit();
    }

    @Test
    public void getTokenRetriesFailedInMemoryMigrationBeforeDeletingLegacyToken() {
        FailedCommitState state = configureFailedCommitState();

        assertEquals("legacy-token", mStore.getToken());
        assertFalse(state.migrationPersisted.get());
        assertTrue(state.legacyTokenPresent.get());

        assertEquals("legacy-token", mStore.getToken());
        assertEquals("legacy-token", state.persistedToken.get());
        assertTrue(state.migrationPersisted.get());
        assertFalse(state.legacyTokenPresent.get());
    }

    @Test
    public void getTokenDoesNotReimportLegacyTokenAfterMigration() {
        setStoredState("", true, "legacy-token");

        assertEquals("", mStore.getToken());

        verify(mEditor, never()).putString(TOKEN_KEY, "legacy-token");
        verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
    }

    @Test
    public void getTokenPrefersDedicatedToken() {
        setStoredState("new-token", false, "legacy-token");

        assertEquals("new-token", mStore.getToken());

        verify(mEditor).putString(TOKEN_KEY, "new-token");
        verify(mEditor).putBoolean(MIGRATION_COMPLETE_KEY, true);
        verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
    }

    @Test
    public void setTokenCommitsDedicatedTokenBeforeDeletingLegacyToken() {
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenReturn(true);

        assertTrue(mStore.setToken("new-token"));

        InOrder order = inOrder(mEditor, mLegacyPreferences, mLegacyEditor);
        order.verify(mEditor).putString(TOKEN_KEY, "new-token");
        order.verify(mEditor).putBoolean(MIGRATION_COMPLETE_KEY, true);
        order.verify(mEditor).commit();
        order.verify(mLegacyPreferences).edit();
        order.verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
        order.verify(mLegacyEditor).commit();
    }

    @Test
    public void setTokenKeepsLegacyTokenWhenDedicatedWriteFails() {
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenReturn(true);
        when(mEditor.commit()).thenReturn(false);

        assertFalse(mStore.setToken("new-token"));

        verify(mLegacyPreferences, never()).edit();
    }

    @Test
    public void setTokenSucceedsWhenOnlyLegacyDeletionFails() {
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenReturn(true);
        when(mLegacyEditor.commit()).thenReturn(false);

        assertTrue(mStore.setToken("new-token"));

        verify(mEditor).putString(TOKEN_KEY, "new-token");
        verify(mEditor).putBoolean(MIGRATION_COMPLETE_KEY, true);
        verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
        verify(mLegacyEditor).commit();
    }

    @Test
    public void clearTokenCommitsTombstoneBeforeDeletingLegacyToken() {
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenReturn(true);

        assertTrue(mStore.clearToken());

        InOrder order = inOrder(mEditor, mLegacyPreferences, mLegacyEditor);
        order.verify(mEditor).remove(TOKEN_KEY);
        order.verify(mEditor).putBoolean(MIGRATION_COMPLETE_KEY, true);
        order.verify(mEditor).commit();
        order.verify(mLegacyPreferences).edit();
        order.verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
        order.verify(mLegacyEditor).commit();
    }

    @Test
    public void clearTokenAttemptsLegacyDeletionWhenTombstoneWriteFails() {
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenReturn(true);
        when(mEditor.commit()).thenReturn(false);

        assertFalse(mStore.clearToken());

        verify(mEditor).remove(TOKEN_KEY);
        verify(mEditor).putBoolean(MIGRATION_COMPLETE_KEY, true);
        verify(mEditor).commit();
        verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
        verify(mLegacyEditor).commit();
    }

    @Test
    public void tombstonePreventsLegacyTokenReimportWhenDeletionFails() {
        AtomicBoolean migrationComplete = new AtomicBoolean();
        when(mPreferences.getString(TOKEN_KEY, "")).thenReturn("");
        when(mPreferences.getBoolean(MIGRATION_COMPLETE_KEY, false)).thenAnswer(
                invocation -> migrationComplete.get());
        when(mEditor.putBoolean(MIGRATION_COMPLETE_KEY, true)).thenAnswer(invocation -> {
            migrationComplete.set(true);
            return mEditor;
        });
        when(mLegacyPreferences.getString(LEGACY_TOKEN_KEY, "")).thenReturn("legacy-token");
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenReturn(true);
        when(mLegacyEditor.commit()).thenReturn(false, true);

        assertFalse(mStore.clearToken());
        assertTrue(migrationComplete.get());

        clearInvocations(mEditor, mLegacyPreferences, mLegacyEditor);

        assertEquals("", mStore.getToken());

        verify(mEditor, never()).putString(TOKEN_KEY, "legacy-token");
        verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
        verify(mLegacyEditor).commit();
    }

    @Test(timeout = 10_000)
    public void clearTokenWaitsForConcurrentLegacyMigration() throws Exception {
        setStoredState("", false, "legacy-token");
        CountDownLatch migrationReadLegacy = new CountDownLatch(1);
        CountDownLatch continueMigration = new CountDownLatch(1);
        AtomicReference<String> migratedToken = new AtomicReference<>();
        AtomicReference<Boolean> clearedToken = new AtomicReference<>();
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        when(mLegacyPreferences.getString(LEGACY_TOKEN_KEY, "")).thenAnswer(invocation -> {
            migrationReadLegacy.countDown();
            if (!continueMigration.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to continue token migration");
            }
            return "legacy-token";
        });

        SharedPreferences secondPreferences = mock(SharedPreferences.class);
        SharedPreferences secondLegacyPreferences = mock(SharedPreferences.class);
        SharedPreferences.Editor secondEditor = mock(SharedPreferences.Editor.class);
        when(secondPreferences.edit()).thenReturn(secondEditor);
        when(secondEditor.remove(anyString())).thenReturn(secondEditor);
        when(secondEditor.putBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(secondEditor);
        when(secondEditor.commit()).thenReturn(true);
        WordPressTokenStore secondStore = new WordPressTokenStore(secondPreferences, secondLegacyPreferences);
        Thread migrationThread = new Thread(() -> {
            try {
                migratedToken.set(mStore.getToken());
            } catch (Throwable throwable) {
                threadFailure.compareAndSet(null, throwable);
            }
        }, "wordpress-token-migration");
        Thread clearThread = new Thread(() -> {
            try {
                clearedToken.set(secondStore.clearToken());
            } catch (Throwable throwable) {
                threadFailure.compareAndSet(null, throwable);
            }
        }, "wordpress-token-clear");

        migrationThread.start();
        assertTrue(migrationReadLegacy.await(5, TimeUnit.SECONDS));
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
        assertNull(threadFailure.get());
        assertEquals("legacy-token", migratedToken.get());
        assertEquals(Boolean.TRUE, clearedToken.get());
    }

    @Test
    public void prepareForBackupFailsWhenLegacyTokenCannotBeDeleted() {
        setStoredState("", false, "legacy-token");
        when(mLegacyEditor.commit()).thenReturn(false);

        assertFalse(mStore.prepareForBackup());
    }

    @Test
    public void prepareForBackupKeepsLegacyTokenWhenDedicatedWriteFails() {
        setStoredState("", false, "legacy-token");
        when(mEditor.commit()).thenReturn(false);

        assertFalse(mStore.prepareForBackup());

        verify(mLegacyPreferences, never()).edit();
    }

    @Test
    public void prepareForBackupRetriesFailedInMemoryMigrationBeforeDeletingLegacyToken() {
        FailedCommitState state = configureFailedCommitState();

        assertEquals("legacy-token", mStore.getToken());
        assertFalse(state.migrationPersisted.get());
        assertTrue(state.legacyTokenPresent.get());

        assertTrue(mStore.prepareForBackup());
        assertEquals("legacy-token", state.persistedToken.get());
        assertTrue(state.migrationPersisted.get());
        assertFalse(state.legacyTokenPresent.get());
    }

    @Test
    public void prepareForBackupFailsWhenTombstonedLegacyTokenCannotBeDeleted() {
        setStoredState("", true, "legacy-token");
        when(mLegacyEditor.commit()).thenReturn(false);

        assertFalse(mStore.prepareForBackup());
    }

    @Test
    public void getTokenScrubsMalformedLegacyValue() {
        when(mPreferences.getString(TOKEN_KEY, "")).thenReturn("");
        when(mPreferences.getBoolean(MIGRATION_COMPLETE_KEY, false)).thenReturn(false);
        when(mLegacyPreferences.getString(LEGACY_TOKEN_KEY, "")).thenThrow(ClassCastException.class);
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenReturn(true);

        assertEquals("", mStore.getToken());

        verify(mEditor).putBoolean(MIGRATION_COMPLETE_KEY, true);
        verify(mLegacyEditor).remove(LEGACY_TOKEN_KEY);
    }

    private void setStoredState(String token, boolean migrationComplete, String legacyToken) {
        when(mPreferences.getString(TOKEN_KEY, "")).thenReturn(token);
        when(mPreferences.getBoolean(MIGRATION_COMPLETE_KEY, false)).thenReturn(migrationComplete);
        when(mLegacyPreferences.getString(LEGACY_TOKEN_KEY, "")).thenReturn(legacyToken);
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenReturn(true);
    }

    private FailedCommitState configureFailedCommitState() {
        FailedCommitState state = new FailedCommitState();
        AtomicReference<String> inMemoryToken = new AtomicReference<>("");
        AtomicBoolean inMemoryMigrationComplete = new AtomicBoolean();
        AtomicInteger commitCount = new AtomicInteger();

        when(mPreferences.getString(TOKEN_KEY, "")).thenAnswer(invocation -> inMemoryToken.get());
        when(mPreferences.getBoolean(MIGRATION_COMPLETE_KEY, false)).thenAnswer(
                invocation -> inMemoryMigrationComplete.get());
        when(mEditor.putString(TOKEN_KEY, "legacy-token")).thenAnswer(invocation -> {
            inMemoryToken.set("legacy-token");
            return mEditor;
        });
        when(mEditor.putBoolean(MIGRATION_COMPLETE_KEY, true)).thenAnswer(invocation -> {
            inMemoryMigrationComplete.set(true);
            return mEditor;
        });
        when(mEditor.commit()).thenAnswer(invocation -> {
            if (commitCount.getAndIncrement() == 0) {
                return false;
            }

            state.persistedToken.set(inMemoryToken.get());
            state.migrationPersisted.set(inMemoryMigrationComplete.get());
            return true;
        });
        when(mLegacyPreferences.getString(LEGACY_TOKEN_KEY, "")).thenAnswer(
                invocation -> state.legacyTokenPresent.get() ? "legacy-token" : "");
        when(mLegacyPreferences.contains(LEGACY_TOKEN_KEY)).thenAnswer(
                invocation -> state.legacyTokenPresent.get());
        when(mLegacyEditor.commit()).thenAnswer(invocation -> {
            state.legacyTokenPresent.set(false);
            return true;
        });

        return state;
    }

    private static class FailedCommitState {
        private final AtomicBoolean legacyTokenPresent = new AtomicBoolean(true);
        private final AtomicBoolean migrationPersisted = new AtomicBoolean();
        private final AtomicReference<String> persistedToken = new AtomicReference<>("");
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
}
