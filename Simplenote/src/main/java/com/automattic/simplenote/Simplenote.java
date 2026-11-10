package com.automattic.simplenote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.automattic.simplenote.analytics.AnalyticsTracker;
import com.automattic.simplenote.analytics.AnalyticsTrackerNosara;
import com.automattic.simplenote.models.Account;
import com.automattic.simplenote.models.Note;
import com.automattic.simplenote.models.Preferences;
import com.automattic.simplenote.models.Tag;
import com.automattic.simplenote.repositories.AccountStatus;
import com.automattic.simplenote.repositories.PreferencesRepository;
import com.automattic.simplenote.utils.AppLog;
import com.automattic.simplenote.utils.AppLog.Type;
import com.automattic.simplenote.utils.crashlogging.CrashLoggingInitializer;
import com.automattic.simplenote.utils.DisplayUtils;
import com.automattic.simplenote.utils.PrefUtils;
import com.automattic.simplenote.utils.SyncWorker;
import com.simperium.Simperium;
import com.simperium.android.WebSocketManager;
import com.simperium.client.Bucket;
import com.simperium.client.ChannelProvider.HeartbeatListener;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

import org.wordpress.passcodelock.AppLockManager;

import java.util.concurrent.TimeUnit;

import dagger.hilt.android.HiltAndroidApp;

import javax.inject.Inject;

@HiltAndroidApp
public class Simplenote extends Application implements HeartbeatListener {
    public static final String DELETED_NOTE_ID = "deletedNoteId";
    public static final String SELECTED_NOTE_ID = "selectedNoteId";
    public static final String SCROLL_POSITION_PREFERENCES = "scroll_position";
    public static final String SYNC_TIME_PREFERENCES = "sync_time";
    public static final String TAG = "Simplenote";
    public static final int INTENT_EDIT_NOTE = 2;
    public static final int INTENT_PREFERENCES = 1;
    public static final int ONE_MINUTE_MILLIS = 60 * 1000;  // 60 seconds
    public static final int TEN_SECONDS_MILLIS = 10 * 1000;  // 10 seconds
    public static final int TWENTY_SECONDS_MILLIS = 20 * 1000;  // 20 seconds

    private static final String TAG_SYNC = "sync";
    private static final long HEARTBEAT_TIMEOUT =  WebSocketManager.HEARTBEAT_INTERVAL * 2;

    private Activity mCurrentActivity;

    // The application instance lives for the whole process, so the static reference cannot leak.
    @SuppressLint("StaticFieldLeak")
    private static volatile Simplenote sInstance;

    private Handler mHeartbeatHandler;
    private Runnable mHeartbeatRunnable;
    private boolean mIsInBackground = true;

    // Do not remove: constructing the initializer starts Sentry. Its position before mSimperium,
    // plus providesSimperium's dependency on it, keeps crash logging live before any bucket work.
    @Inject
    CrashLoggingInitializer crashLoggingInitializer;

    // Field injection runs at the top of super.onCreate(), so the Simperium client and the bucket
    // graph are constructed there, on the main thread, before any activity, worker, or widget code
    // can run — the same guarantee the inline construction in onCreate used to give.
    @Inject
    Simperium mSimperium;

    @Inject
    SimperiumBuckets mBuckets;

    public void onCreate() {
        super.onCreate();
        sInstance = this;

        SimplenoteAppLock appLock = new SimplenoteAppLock(this);
        AppLockManager.getInstance().setCurrentAppLock(appLock);
        appLock.enable();

        mSimperium.addHeartbeatListener(this);

        mHeartbeatHandler = new Handler();
        mHeartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                AppLog.add(Type.NETWORK, "Heartbeat stopped");
                mHeartbeatHandler.removeCallbacks(mHeartbeatRunnable);
                mHeartbeatHandler.postDelayed(mHeartbeatRunnable, HEARTBEAT_TIMEOUT);
            }
        };

        // Replaces the AccountVerificationWatcher bucket listener; the collection delivers on
        // the main thread, where the listener's fragment transactions always belonged.
        AccountStreams.watchAccountStatus(AccountStreams.accountRepository(this), new VerificationListener());

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        ApplicationLifecycleMonitor applicationLifecycleMonitor = new ApplicationLifecycleMonitor();
        registerComponentCallbacks(applicationLifecycleMonitor);
        registerActivityLifecycleCallbacks(applicationLifecycleMonitor);

        AnalyticsTracker.registerTracker(new AnalyticsTrackerNosara(this));
        AnalyticsTracker.refreshMetadata(mSimperium.getUser().getEmail());

        AppLog.addHeader(Type.DEVICE, getDeviceInfo());
        AppLog.addHeader(Type.ACCOUNT, getAccountInfo());
        AppLog.add(Type.LAYOUT, DisplayUtils.getDisplaySizeAndOrientation(Simplenote.this));
    }

    @Override
    public void onBeat() {
        AppLog.add(Type.NETWORK, "Heartbeat received");
        mHeartbeatHandler.removeCallbacks(mHeartbeatRunnable);
        mHeartbeatHandler.postDelayed(mHeartbeatRunnable, HEARTBEAT_TIMEOUT);
    }

    public static boolean analyticsIsEnabled() {
        // Static callers can run before injection creates the buckets (or, in unit tests, without
        // an application at all). Preserve the legacy pre-init default of enabled analytics.
        Simplenote app = sInstance;
        if (app == null || app.mBuckets == null) {
            return true;
        }

        return EntryPointAccessors.fromApplication(app, PreferencesEntryPoint.class)
                .preferencesRepository()
                .analyticsEnabledSnapshot();
    }

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    interface PreferencesEntryPoint {
        PreferencesRepository preferencesRepository();
    }

    private String getAccountInfo() {
        String email = "Email: " + (mSimperium != null && mSimperium.getUser() != null ? mSimperium.getUser().getEmail() : "?");
        String notes = "Notes: " + (mBuckets != null ? mBuckets.getNotes().count() : "?");
        String tags = "Tags: " + (mBuckets != null ? mBuckets.getTags().count() : "?");
        return email + "\n" + notes + "\n" + tags + "\n\n";
    }

    private String getDeviceInfo() {
        String architecture = Build.DEVICE != null && Build.DEVICE.matches(".+_cheets|cheets_.+") ? "Chrome OS " : "Android ";
        String device = "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")";
        String system = "System: " + architecture + Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")";
        String app = "App: Simplenote " + PrefUtils.versionInfo();
        return device + "\n" + system + "\n" + app + "\n\n";
    }

    public Simperium getSimperium() {
        return mSimperium;
    }

    public Bucket<Note> getNotesBucket() {
        return mBuckets.getNotes();
    }

    public SyncTimes<Note> getNoteSyncTimes() {
        return mBuckets.getNoteSyncTimes();
    }

    public Bucket<Tag> getTagsBucket() {
        return mBuckets.getTags();
    }

    public Bucket<Preferences> getPreferencesBucket() {
        return mBuckets.getPreferences();
    }

    public Bucket<Account> getAccountBucket() {
        return mBuckets.getAccount();
    }

    // For callers that can run during application field injection, before mBuckets is assigned —
    // the crash-logging data provider is constructed there. The bucket getters above assume
    // injection has completed.
    public boolean bucketsAreInitialized() {
        return mBuckets != null;
    }

    public boolean isInBackground() {
        return mIsInBackground;
    }

    public boolean isLoggedIn() {
        return AccountStreams.accountRepository(this).isLoggedIn();
    }

    public String getUserEmail() {
        return AccountStreams.accountRepository(this).userEmail();
    }

    private void showUnverifiedAccount() {
        showReviewAccountOrVerifyEmail(mCurrentActivity, false);
    }

    private void showWaitingOnEmailConfirmation() {
        showReviewAccountOrVerifyEmail(mCurrentActivity, true);
    }

    private void dismissReviewAccountDialog() {
        FragmentManager fragmentManager;
        if (mCurrentActivity instanceof NotesActivity) {
            fragmentManager = ((NotesActivity) mCurrentActivity).getSupportFragmentManager();
        } else if (mCurrentActivity instanceof NoteEditorActivity) {
            fragmentManager = ((NoteEditorActivity) mCurrentActivity).getSupportFragmentManager();
        } else {
            return;
        }

        for (Fragment fragment : fragmentManager.getFragments()) {
            if (fragment instanceof FullScreenDialogFragment) {
                ((FullScreenDialogFragment) fragment).dismiss();

                return;
            }
        }
    }
    private void showReviewAccountOrVerifyEmail(final Activity activity, boolean hasSentEmail) {
        final FragmentManager fragmentManager;
        final @IdRes int container;

        if (activity instanceof NotesActivity) {
            fragmentManager = ((NotesActivity) activity).getSupportFragmentManager();
            container = R.id.drawer_layout;
        } else if (activity instanceof NoteEditorActivity) {
            fragmentManager = ((NoteEditorActivity) activity).getSupportFragmentManager();
            container = android.R.id.content;
        } else {
            return;
        }

        // If isStateSaved() is true, it means that the application is not in the foreground, thus, we cannot
        // show the dialog because it would cause an IllegalStateException (the same guard
        // FullScreenDialogFragment.dismiss already applies).
        if (activity.isFinishing() || activity.isDestroyed() || fragmentManager.isStateSaved()) {
            return;
        }

        final Bundle bundle = ReviewAccountVerifyEmailFragment.newBundle(hasSentEmail);

        new FullScreenDialogFragment.Builder(activity)
            .setContent(ReviewAccountVerifyEmailFragment.class, bundle)
            .setOnConfirmListener(null)
            .setOnDismissListener(null)
            .setToolbarElevation(0)
            .setViewContainer(container)
            .build()
            .show(fragmentManager, FullScreenDialogFragment.TAG);
    }

    private class ApplicationLifecycleMonitor implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
        // ComponentCallbacks
        @Override
        public void onTrimMemory(int level) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                mIsInBackground = true;

                // Give the buckets some time to finish sync, then stop them
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!mIsInBackground) {
                            return;
                        }

                        mBuckets.getAccount().stop();
                        AppLog.add(Type.SYNC, "Stopped account bucket (Simplenote)");

                        mBuckets.getNotes().stop();
                        AppLog.add(Type.SYNC, "Stopped note bucket (Simplenote)");

                        mBuckets.getTags().stop();
                        AppLog.add(Type.SYNC, "Stopped tag bucket (Simplenote)");

                        mBuckets.getPreferences().stop();
                        AppLog.add(Type.SYNC, "Stopped preference bucket (Simplenote)");
                    }
                }, TEN_SECONDS_MILLIS);

                PeriodicWorkRequest syncWorkRequest = new PeriodicWorkRequest.Builder(
                    SyncWorker.class,
                    PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.LINEAR, ONE_MINUTE_MILLIS, TimeUnit.MILLISECONDS)
                    .setInitialDelay(TWENTY_SECONDS_MILLIS, TimeUnit.MILLISECONDS)
                    .addTag(TAG_SYNC)
                    .build();
                WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                    TAG_SYNC,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    syncWorkRequest
                );
                Log.d("Simplenote.onTrimMemory", "Started worker");

                // Send analytics if app is in the background
                AnalyticsTracker.track(
                        AnalyticsTracker.Stat.APPLICATION_CLOSED,
                        AnalyticsTracker.CATEGORY_USER,
                        "application_closed"
                );
                AnalyticsTracker.flush();
                AppLog.add(Type.ACTION, "App closed");
            } else {
                mIsInBackground = false;
            }
        }

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            AppLog.add(Type.LAYOUT, DisplayUtils.getDisplaySizeAndOrientation(Simplenote.this));
        }

        @Override
        public void onLowMemory() {
        }

        // ActivityLifecycleCallbacks
        @SuppressLint("LongLogTag")
        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            if (mIsInBackground) {
                AnalyticsTracker.track(
                        AnalyticsTracker.Stat.APPLICATION_OPENED,
                        AnalyticsTracker.CATEGORY_USER,
                        "application_opened"
                );

                mIsInBackground = false;
                AppLog.add(Type.ACTION, "App opened");
                WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(TAG_SYNC);
                Log.d("Simplenote.onActivityResumed", "Stopped worker");
            }

            String activitySimpleName = activity.getClass().getSimpleName();

            mBuckets.getAccount().start();
            AppLog.add(Type.SYNC, "Started account bucket (" + activitySimpleName + ")");
            mBuckets.getPreferences().start();
            AppLog.add(Type.SYNC, "Started preference bucket (" + activitySimpleName + ")");
            mBuckets.getNotes().start();
            AppLog.add(Type.SYNC, "Started note bucket (" + activitySimpleName + ")");
            mBuckets.getTags().start();
            AppLog.add(Type.SYNC, "Started tag bucket (" + activitySimpleName + ")");
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            mCurrentActivity = activity;
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }

    private class VerificationListener implements Consumer<AccountStatus> {
        @Override
        public void accept(AccountStatus status) {
            if (status instanceof AccountStatus.Verified) {
                dismissReviewAccountDialog();
            } else if (status instanceof AccountStatus.Unverified) {
                if (((AccountStatus.Unverified) status).getHasSentEmail()) {
                    showWaitingOnEmailConfirmation();
                } else {
                    showUnverifiedAccount();
                }
            }
        }
    }
}
