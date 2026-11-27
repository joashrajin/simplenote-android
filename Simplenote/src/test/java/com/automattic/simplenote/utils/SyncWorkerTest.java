package com.automattic.simplenote.utils;

import static com.automattic.simplenote.Simplenote.TEN_SECONDS_MILLIS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;

import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.automattic.simplenote.Simplenote;
import com.automattic.simplenote.models.Note;
import com.google.common.util.concurrent.ListenableFuture;
import com.simperium.client.Bucket;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class SyncWorkerTest {
    private Bucket<Note> noteBucket;
    private SyncWorker worker;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        Simplenote application = mock(Simplenote.class);
        noteBucket = mock(Bucket.class);

        when(application.getApplicationContext()).thenReturn(application);
        when(application.getNotesBucket()).thenReturn(noteBucket);
        when(application.isInBackground()).thenReturn(true);

        worker = new SyncWorker(application, mock(WorkerParameters.class));
    }

    @Test
    public void stoppedWorkDoesNotRunDelayedCompletion() {
        ListenableFuture<ListenableWorker.Result> future = worker.startWork();

        worker.onStopped();
        verify(noteBucket).stop();
        assertTrue(future.cancel(false));
        clearInvocations(noteBucket);
        shadowOf(Looper.getMainLooper()).idleFor(TEN_SECONDS_MILLIS + 1, TimeUnit.MILLISECONDS);

        verifyNoInteractions(noteBucket);
    }

    @Test
    public void runningWorkStillCompletesAfterDelay() {
        ListenableFuture<ListenableWorker.Result> future = worker.startWork();

        shadowOf(Looper.getMainLooper()).idleFor(TEN_SECONDS_MILLIS + 1, TimeUnit.MILLISECONDS);

        verify(noteBucket).start();
        verify(noteBucket).stop();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
    }
}
