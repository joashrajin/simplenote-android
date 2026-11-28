package com.automattic.simplenote.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.automattic.simplenote.Simplenote;
import com.automattic.simplenote.di.AppEntryPoint;
import com.automattic.simplenote.models.Account;
import com.automattic.simplenote.models.Note;
import com.automattic.simplenote.models.Preferences;
import com.automattic.simplenote.models.Tag;
import com.automattic.simplenote.utils.AppLog.Type;
import com.google.common.util.concurrent.ListenableFuture;
import com.simperium.client.Bucket;

import dagger.hilt.android.EntryPointAccessors;

import static com.automattic.simplenote.Simplenote.TEN_SECONDS_MILLIS;

public class SyncWorker extends ListenableWorker {
    private final Bucket<Note> mBucketNote;
    private final Bucket<Preferences> mBucketPreference;
    private final Bucket<Account> mBucketAccount;
    private final Bucket<Tag> mBucketTag;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        AppEntryPoint entryPoint = EntryPointAccessors.fromApplication(
            context.getApplicationContext(),
            AppEntryPoint.class
        );
        mBucketNote = entryPoint.notesBucket();
        mBucketTag = entryPoint.tagsBucket();
        mBucketPreference = entryPoint.preferencesBucket();
        mBucketAccount = entryPoint.accountBucket();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        stopBuckets("onStopped");
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return CallbackToFutureAdapter.getFuture(
            new CallbackToFutureAdapter.Resolver<Result>() {
                @Nullable
                @Override
                public Object attachCompleter(@NonNull final CallbackToFutureAdapter.Completer<Result> completer) {
                    AppLog.add(Type.NETWORK, NetworkUtils.getNetworkInfo(getApplicationContext()));

                    if (mBucketAccount != null) {
                        mBucketAccount.start();
                        AppLog.add(Type.SYNC, "Started account bucket (SyncWorker)");
                    }

                    if (mBucketNote != null) {
                        mBucketNote.start();
                        AppLog.add(Type.SYNC, "Started note bucket (SyncWorker)");
                    }

                    if (mBucketTag != null) {
                        mBucketTag.start();
                        AppLog.add(Type.SYNC, "Started tag bucket (SyncWorker)");
                    }

                    if (mBucketPreference != null) {
                        mBucketPreference.start();
                        AppLog.add(Type.SYNC, "Started preference bucket (SyncWorker)");
                    }

                    Log.d("SyncWorker.startWork", "Started buckets");

                    new Handler(Looper.getMainLooper()).postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                stopBuckets("startWork");
                                completer.set(Result.success());
                            }
                        },
                        TEN_SECONDS_MILLIS
                    );

                    return null;
                }
            }
        );
    }

    private void stopBuckets(String method) {
        if (((Simplenote) getApplicationContext()).isInBackground()) {
            if (mBucketAccount != null) {
                mBucketAccount.stop();
                AppLog.add(Type.SYNC, "Stopped account bucket (SyncWorker)");
            }

            if (mBucketNote != null) {
                mBucketNote.stop();
                AppLog.add(Type.SYNC, "Stopped note bucket (SyncWorker)");
            }

            if (mBucketTag != null) {
                mBucketTag.stop();
                AppLog.add(Type.SYNC, "Stopped tag bucket (SyncWorker)");
            }

            if (mBucketPreference != null) {
                mBucketPreference.stop();
                AppLog.add(Type.SYNC, "Stopped preference bucket (SyncWorker)");
            }

            Log.d("SyncWorker." + method, "Stopped buckets");
        }
    }
}
