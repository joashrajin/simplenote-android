package com.automattic.simplenote;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.automattic.simplenote.utils.WordPressTokenStore;

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
        if (!WordPressTokenStore.from(this).prepareForBackup()) {
            throw new IOException("Unable to remove the legacy WordPress token before backup");
        }

        super.onFullBackup(data);
    }

    @Override
    public void onRestoreFinished() {
        super.onRestoreFinished();

        WordPressTokenStore tokenStore = WordPressTokenStore.from(this);
        if (!tokenStore.clearToken() && !tokenStore.clearToken()) {
            Log.e(TAG, "Unable to clear the restored WordPress token");
        }
    }
}
