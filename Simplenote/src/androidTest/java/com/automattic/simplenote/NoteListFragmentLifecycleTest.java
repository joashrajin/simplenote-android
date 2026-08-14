package com.automattic.simplenote;

import android.os.AsyncTask;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NoteListFragmentLifecycleTest {
    @Test
    public void refreshListDoesNotScheduleWorkWhenDetached() throws ReflectiveOperationException {
        AtomicReference<NoteListFragment> fragmentReference = new AtomicReference<>();
        AtomicReference<RuntimeException> thrown = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
            () -> fragmentReference.set(new NoteListFragment())
        );
        NoteListFragment fragment = fragmentReference.get();

        assertFalse("Fixture must be detached", fragment.isAdded());
        assertNull(getRefreshListTask(fragment));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                fragment.refreshList();
            } catch (RuntimeException exception) {
                thrown.set(exception);
            }
        });

        assertNull(
            "Detached refresh must not schedule replacement work (thrown=" + thrown.get() + ")",
            getRefreshListTask(fragment)
        );
        assertNull("Detached refresh must return without throwing", thrown.get());
    }

    @Test
    public void detachedRefreshStillCancelsInFlightTask() throws ReflectiveOperationException {
        AtomicReference<NoteListFragment> fragmentReference = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
            () -> fragmentReference.set(new NoteListFragment())
        );
        NoteListFragment fragment = fragmentReference.get();
        AsyncTask<?, ?, ?> seededTask = newRefreshListTask(fragment);
        setRefreshListTask(fragment, seededTask);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(fragment::refreshList);

        assertTrue("Detached refresh must cancel the in-flight task", seededTask.isCancelled());
        assertSame(
            "Detached refresh must not replace the cancelled task",
            seededTask,
            getRefreshListTask(fragment)
        );
    }

    private static Object getRefreshListTask(NoteListFragment fragment) throws ReflectiveOperationException {
        Field field = refreshListTaskField();
        return field.get(fragment);
    }

    private static void setRefreshListTask(NoteListFragment fragment, AsyncTask<?, ?, ?> task)
            throws ReflectiveOperationException {
        Field field = refreshListTaskField();
        field.set(fragment, task);
    }

    private static Field refreshListTaskField() throws NoSuchFieldException {
        Field field = NoteListFragment.class.getDeclaredField("mRefreshListTask");
        field.setAccessible(true);
        return field;
    }

    private static AsyncTask<?, ?, ?> newRefreshListTask(NoteListFragment fragment)
            throws ReflectiveOperationException {
        for (Class<?> inner : NoteListFragment.class.getDeclaredClasses()) {
            if ("RefreshListTask".equals(inner.getSimpleName())) {
                Constructor<?> constructor = inner.getDeclaredConstructor(NoteListFragment.class);
                constructor.setAccessible(true);
                return (AsyncTask<?, ?, ?>) constructor.newInstance(fragment);
            }
        }
        throw new NoSuchMethodException("RefreshListTask constructor not found");
    }
}
