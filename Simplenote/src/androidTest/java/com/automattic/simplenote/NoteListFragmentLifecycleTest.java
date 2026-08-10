package com.automattic.simplenote;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

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
            "Detached refresh scheduled work after throwing " + thrown.get(),
            getRefreshListTask(fragment)
        );
        assertNull("Detached refresh must return without throwing", thrown.get());
    }

    private static Object getRefreshListTask(NoteListFragment fragment) throws ReflectiveOperationException {
        Field field = NoteListFragment.class.getDeclaredField("mRefreshListTask");
        field.setAccessible(true);
        return field.get(fragment);
    }
}
