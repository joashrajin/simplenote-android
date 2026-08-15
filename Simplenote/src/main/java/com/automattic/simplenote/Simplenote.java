package com.automattic.simplenote;

import static com.simperium.android.AsyncAuthClient.USER_ACCESS_TOKEN_PREFERENCE;
import static com.simperium.android.AsyncAuthClient.USER_EMAIL_PREFERENCE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.automattic.android.tracks.crashlogging.CrashLogging;
import com.automattic.simplenote.analytics.AnalyticsTracker;
import com.automattic.simplenote.analytics.AnalyticsTrackerNosara;
import com.automattic.simplenote.models.Account;
import com.automattic.simplenote.models.Note;
import com.automattic.simplenote.models.NoteCountIndexer;
import com.automattic.simplenote.models.NoteTagger;
import com.automattic.simplenote.models.Preferences;
import com.automattic.simplenote.models.Tag;
import com.automattic.simplenote.repositories.AccountRepository;
import com.automattic.simplenote.repositories.PreferencesRepository;
import com.automattic.simplenote.utils.AppLog;
import com.automattic.simplenote.utils.AppLog.Type;
import com.automattic.simplenote.utils.DisplayUtils;
import com.automattic.simplenote.utils.PrefUtils;
import com.automattic.simplenote.utils.SyncWorker;
import com.simperium.Simperium;
import com.simperium.android.AndroidClient;
import com.simperium.android.WebSocketManager;
import com.simperium.client.Bucket;
import com.simperium.client.BucketNameInvalid;
import com.simperium.client.ChannelProvider.HeartbeatListener;
import com.simperium.client.User;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

import org.wordpress.passcodelock.AppLockManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import dagger.Lazy;
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

    private static final String AUTH_PROVIDER = "simplenote.com";
    private static final String TAG_SYNC = "sync";
    private static final long HEARTBEAT_TIMEOUT =  WebSocketManager.HEARTBEAT_INTERVAL * 2;

    private WeakReference<FullScreenDialogFragment> mAccountVerificationDialog;
    private final List<Activity> mResumedActivities = new ArrayList<>();

    // The application instance lives for the whole process, so the static reference cannot leak.
    @SuppressLint("StaticFieldLeak")
    private static volatile Simplenote sInstance;

    private static Bucket<Account> mAccountBucket;
    private static Bucket<Preferences> mPreferencesBucket;

    private Bucket<Note> mNotesBucket;
    private Bucket<Tag> mTagsBucket;
    private SyncTimes<Note> mNoteSyncTimes;
    private Handler mHeartbeatHandler;
    private Runnable mHeartbeatRunnable;
    private Simperium mSimperium;
    private AccountVerificationCoordinator mAccountVerificationCoordinator;
    private boolean mIsInBackground = true;

    //Do not remove, it's needed for Sentry initialization
    @Inject
    CrashLogging crashLogging;

    @Inject
    Lazy<AccountRepository> accountRepository;

    public void onCreate() {
        super.onCreate();
        sInstance = this;
        crashLogging.initialize();

        SimplenoteAppLock appLock = new SimplenoteAppLock(this);
        AppLockManager.getInstance().setCurrentAppLock(appLock);
        appLock.enable();

        mSimperium = Simperium.newClient(
                BuildConfig.SIMPERIUM_APP_ID,
                BuildConfig.SIMPERIUM_APP_KEY,
                this
        );

        mSimperium.setAuthProvider(AUTH_PROVIDER);
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

        SyncTimePersister syncTimePersister = new SyncTimePersister();
        mNoteSyncTimes = new SyncTimes<>(syncTimePersister.load());
        mNoteSyncTimes.addListener(syncTimePersister);

        try {
            mNotesBucket = mSimperium.bucket(new Note.Schema());
            mNotesBucket.addListener(mNoteSyncTimes.bucketListener);
            Tag.Schema tagSchema = new Tag.Schema();
            tagSchema.addIndex(new NoteCountIndexer(mNotesBucket));
            mTagsBucket = mSimperium.bucket(tagSchema);
            mPreferencesBucket = mSimperium.bucket(new Preferences.Schema());
            mAccountBucket = mSimperium.bucket(new Account.Schema());
            // Every time a note changes or is deleted we need to reindex the tag counts
            mNotesBucket.addListener(new NoteTagger(mTagsBucket));
        } catch (BucketNameInvalid e) {
            throw new RuntimeException("Could not create bucket", e);
        }

        mAccountVerificationCoordinator = new AccountVerificationCoordinator(
                accountRepository.get(),
                this::getAuthorizedUserEmail,
                this::renderAccountVerificationPresentation,
                this::dismissAccountVerificationDialog
        );
        mAccountVerificationCoordinator.start();

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
        // Static callers can run before onCreate creates the buckets (or, in unit tests, without an
        // application at all). Preserve the legacy pre-init default of enabled analytics.
        Simplenote app = sInstance;
        if (app == null || mPreferencesBucket == null) {
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
        String notes = "Notes: " + (mNotesBucket != null ? mNotesBucket.count() : "?");
        String tags = "Tags: " + (mTagsBucket != null ? mTagsBucket.count() : "?");
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
        return mNotesBucket;
    }

    public SyncTimes getNoteSyncTimes() {
        return mNoteSyncTimes;
    }

    public Bucket<Tag> getTagsBucket() {
        return mTagsBucket;
    }

    public Bucket<Preferences> getPreferencesBucket() {
        return mPreferencesBucket;
    }

    public Bucket<Account> getAccountBucket() {
        return mAccountBucket;
    }

    public boolean isInBackground() {
        return mIsInBackground;
    }

    public void loginWithToken(String email, String spToken) {
        prepareForAuthenticationSessionChange();

        // Manually authorize the user with Simperium
        User user = mSimperium.getUser();
        user.setAccessToken(spToken);
        user.setEmail(email);
        user.setStatus(User.Status.AUTHORIZED);

        // Store the user data in Simperium shared preferences
        SharedPreferences.Editor editor = AndroidClient.sharedPreferences(this).edit();
        editor.putString(USER_ACCESS_TOKEN_PREFERENCE, user.getAccessToken());
        editor.putString(USER_EMAIL_PREFERENCE, user.getEmail());
        editor.apply();

    }

    public boolean isLoggedIn() {
        User user = mSimperium.getUser();
        return user != null && user.getStatus() == User.Status.AUTHORIZED;
    }

    public String getUserEmail() {
        User user = mSimperium.getUser();
        return user != null ? user.getEmail() : null;
    }

    private String getAuthorizedUserEmail() {
        return isLoggedIn() ? getUserEmail() : null;
    }

    private void markActivityResumed(Activity activity) {
        mResumedActivities.remove(activity);
        mResumedActivities.add(activity);
    }

    private void markActivityNotResumed(Activity activity) {
        if (!mResumedActivities.remove(activity)) {
            return;
        }

        if (!mResumedActivities.isEmpty()) {
            mAccountVerificationCoordinator.onHostAvailable();
        }
    }

    private Activity getCurrentVerificationHost() {
        for (int index = mResumedActivities.size() - 1; index >= 0; index--) {
            Activity activity = mResumedActivities.get(index);
            if ((activity instanceof NotesActivity || activity instanceof NoteEditorActivity) &&
                    getSafeVerificationFragmentManager(activity) != null) {
                return activity;
            }
        }
        return null;
    }

    public void prepareForAuthenticationSessionChange() {
        if (mAccountVerificationCoordinator != null) {
            mAccountVerificationCoordinator.onAuthenticationSessionChanging();
        }
    }

    public boolean isCurrentAccountVerificationPresentation(
            String email,
            int generation,
            long revision,
            String processNonce
    ) {
        return mAccountVerificationCoordinator != null &&
                mAccountVerificationCoordinator.isCurrentPresentation(email, generation, revision, processNonce);
    }

    private boolean renderAccountVerificationPresentation(
            AccountVerificationCoordinator.Presentation presentation
    ) {
        Activity activity = getCurrentVerificationHost();
        FragmentManager fragmentManager = getSafeVerificationFragmentManager(activity);
        if (fragmentManager == null) {
            return false;
        }

        try {
            switch (presentation.getUpdate().getStatus()) {
                case VERIFIED:
                    if (!dismissReviewAccountDialogs()) {
                        return false;
                    }
                    break;

                case SENT_EMAIL:
                    if (!dismissReviewAccountDialogs()) {
                        return false;
                    }
                    showReviewAccountOrVerifyEmail(activity, fragmentManager, true, presentation);
                    break;

                case UNVERIFIED:
                    if (!dismissReviewAccountDialogs()) {
                        return false;
                    }
                    showReviewAccountOrVerifyEmail(activity, fragmentManager, false, presentation);
                    break;
            }
            return true;
        } catch (IllegalStateException exception) {
            AppLog.add(
                    Type.SYNC,
                    "Unable to update account verification dialog (" +
                            exception.getClass().getSimpleName() + ")"
            );
            return false;
        }
    }

    private boolean dismissAccountVerificationDialog() {
        try {
            return dismissReviewAccountDialogs();
        } catch (IllegalStateException exception) {
            AppLog.add(
                    Type.SYNC,
                    "Unable to dismiss account verification dialog (" +
                            exception.getClass().getSimpleName() + ")"
            );
            return false;
        }
    }

    private FragmentManager getSafeVerificationFragmentManager(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }

        FragmentManager fragmentManager;
        if (activity instanceof NotesActivity) {
            fragmentManager = ((NotesActivity) activity).getSupportFragmentManager();
        } else if (activity instanceof NoteEditorActivity) {
            fragmentManager = ((NoteEditorActivity) activity).getSupportFragmentManager();
        } else {
            return null;
        }
        return fragmentManager.isStateSaved() ? null : fragmentManager;
    }

    private void dismissReviewAccountDialog(FragmentManager fragmentManager) {
        for (Fragment fragment : fragmentManager.getFragments()) {
            if (fragment instanceof FullScreenDialogFragment &&
                    ((FullScreenDialogFragment) fragment).getContent() instanceof ReviewAccountVerifyEmailFragment) {
                ((FullScreenDialogFragment) fragment).dismiss();
            }
        }
    }

    private boolean dismissReviewAccountDialogs() {
        FullScreenDialogFragment pendingDialog = mAccountVerificationDialog == null ? null :
                mAccountVerificationDialog.get();
        mAccountVerificationDialog = null;
        if (pendingDialog != null) {
            pendingDialog.dismiss();
        }

        boolean allHostsSafe = true;
        boolean foundSafeHost = false;
        for (Activity activity : mResumedActivities) {
            if (!(activity instanceof NotesActivity) && !(activity instanceof NoteEditorActivity)) {
                continue;
            }

            FragmentManager fragmentManager = getSafeVerificationFragmentManager(activity);
            if (fragmentManager == null) {
                allHostsSafe = false;
                continue;
            }

            foundSafeHost = true;
            dismissReviewAccountDialog(fragmentManager);
        }
        return foundSafeHost && allHostsSafe;
    }

    private void showReviewAccountOrVerifyEmail(
            final Activity activity,
            FragmentManager fragmentManager,
            boolean hasSentEmail,
            AccountVerificationCoordinator.Presentation presentation
    ) {
        final @IdRes int container;

        if (activity instanceof NotesActivity) {
            container = R.id.drawer_layout;
        } else if (activity instanceof NoteEditorActivity) {
            container = android.R.id.content;
        } else {
            return;
        }

        final Bundle bundle = ReviewAccountVerifyEmailFragment.newBundle(
                hasSentEmail,
                presentation.getUpdate().getEmail(),
                presentation.getSessionGeneration(),
                presentation.getRevision(),
                presentation.getProcessNonce()
        );

        FullScreenDialogFragment dialog = new FullScreenDialogFragment.Builder(activity)
            .setContent(ReviewAccountVerifyEmailFragment.class, bundle)
            .setOnConfirmListener(null)
            .setOnDismissListener(null)
            .setToolbarElevation(0)
            .setViewContainer(container)
            .build();
        mAccountVerificationDialog = new WeakReference<>(dialog);
        dialog.show(fragmentManager, FullScreenDialogFragment.TAG);
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

                        if (mAccountBucket != null) {
                            mAccountBucket.stop();
                            AppLog.add(Type.SYNC, "Stopped account bucket (Simplenote)");
                        }

                        if (mNotesBucket != null) {
                            mNotesBucket.stop();
                            AppLog.add(Type.SYNC, "Stopped note bucket (Simplenote)");
                        }

                        if (mTagsBucket != null) {
                            mTagsBucket.stop();
                            AppLog.add(Type.SYNC, "Stopped tag bucket (Simplenote)");
                        }

                        if (mPreferencesBucket != null) {
                            mPreferencesBucket.stop();
                            AppLog.add(Type.SYNC, "Stopped preference bucket (Simplenote)");
                        }
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
            markActivityResumed(activity);

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

            mAccountVerificationCoordinator.onHostAvailable();

            mAccountBucket.start();
            AppLog.add(Type.SYNC, "Started account bucket (" + activitySimpleName + ")");
            mPreferencesBucket.start();
            AppLog.add(Type.SYNC, "Started preference bucket (" + activitySimpleName + ")");
            mNotesBucket.start();
            AppLog.add(Type.SYNC, "Started note bucket (" + activitySimpleName + ")");
            mTagsBucket.start();
            AppLog.add(Type.SYNC, "Started tag bucket (" + activitySimpleName + ")");
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            markActivityNotResumed(activity);
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            markActivityNotResumed(activity);
        }
    }

    private class SyncTimePersister implements SyncTimes.SyncTimeListener {
        private final SharedPreferences mPreferences;

        public SyncTimePersister() {
            mPreferences = getSharedPreferences(SYNC_TIME_PREFERENCES, Context.MODE_PRIVATE);
        }

        public HashMap<String, Calendar> load() {
            HashMap<String, Calendar> syncTimes = new HashMap<>();

            //noinspection unchecked
            for (Map.Entry<String, Long> syncTime : ((Map<String, Long>) mPreferences.getAll()).entrySet()) {
                Calendar instant = Calendar.getInstance();
                instant.setTimeInMillis(syncTime.getValue());
                syncTimes.put(syncTime.getKey(), instant);
            }

            return syncTimes;
        }

        @Override
        public void onRemove(String entityId) {
            mPreferences.edit().remove(entityId).apply();
        }

        @Override
        public void onUpdate(String entityId, Calendar lastSyncTime, boolean isSynced) {
            if(lastSyncTime != null){
                mPreferences.edit().putLong(entityId, lastSyncTime.getTimeInMillis()).apply();
            }
        }
    }

}
