package com.automattic.simplenote;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.automattic.simplenote.utils.WordPressTokenStore;

import org.wordpress.passcodelock.PasscodePreferenceStore;

import java.io.IOException;

public class SimplenoteBackupAgent extends BackupAgent {
    private static final String TAG = "SimplenoteBackupAgent";

    @Override
    public void onBackup(
            ParcelFileDescriptor oldState,
            BackupDataOutput data,
            ParcelFileDescriptor newState
    ) {
    }

    @Override
    public void onRestore(
            BackupDataInput data,
            int appVersionCode,
            ParcelFileDescriptor newState
    ) {
    }

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        boolean tokenReady = prepareTokenForBackup();
        boolean passcodeReady = preparePasscodeForBackup();
        if (!tokenReady || !passcodeReady) {
            throw new IOException("Unable to remove legacy authentication state before backup");
        }

        super.onFullBackup(data);
    }

    @Override
    public void onRestoreFinished() {
        super.onRestoreFinished();

        clearTokenAfterRestore();
        clearPasscodeAfterRestore();
    }

    private boolean prepareTokenForBackup() {
        try {
            return WordPressTokenStore.from(this).prepareForBackup();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to prepare the WordPress token for backup", exception);
            return false;
        }
    }

    private boolean preparePasscodeForBackup() {
        try {
            return PasscodePreferenceStore.from(this).prepareForBackup();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to prepare passcode state for backup", exception);
            return false;
        }
    }

    private void clearTokenAfterRestore() {
        try {
            if (WordPressTokenStore.from(this).clearTokenWithRetry()) {
                return;
            }
            Log.e(TAG, "Unable to clear the restored WordPress token");
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to clear the restored WordPress token", exception);
        }
    }

    private void clearPasscodeAfterRestore() {
        try {
            if (PasscodePreferenceStore.from(this).clearPasscodeWithRetry()) {
                return;
            }
            Log.e(TAG, "Unable to clear the restored passcode state");
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to clear the restored passcode state", exception);
        }
    }
}
