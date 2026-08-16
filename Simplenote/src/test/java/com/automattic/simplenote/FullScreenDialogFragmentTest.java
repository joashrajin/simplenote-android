package com.automattic.simplenote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class FullScreenDialogFragmentTest {
    @Test
    public void dismissRemovesTheDialogBackStackEntry() {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        FullScreenDialogFragment dialog = showDialog(activity);

        dialog.dismiss();
        fragmentManager.executePendingTransactions();

        assertDialogDismissed(fragmentManager);
    }

    @Test
    public void dismissAfterRecreationRemovesTheDialogBackStackEntry() {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup();
        showDialog(controller.get());

        controller.recreate();

        FragmentManager fragmentManager = controller.get().getSupportFragmentManager();
        FullScreenDialogFragment dialog = (FullScreenDialogFragment) fragmentManager.findFragmentByTag(
            FullScreenDialogFragment.TAG
        );
        assertNotNull(dialog);
        assertEquals(1, fragmentManager.getBackStackEntryCount());

        dialog.dismiss();
        fragmentManager.executePendingTransactions();

        assertDialogDismissed(fragmentManager);
    }

    private FullScreenDialogFragment showDialog(TestActivity activity) {
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        FullScreenDialogFragment dialog = new FullScreenDialogFragment.Builder(activity)
            .setContent(TestContentFragment.class, null)
            .build();
        dialog.show(fragmentManager, FullScreenDialogFragment.TAG);
        fragmentManager.executePendingTransactions();

        assertNotNull(fragmentManager.findFragmentByTag(FullScreenDialogFragment.TAG));
        assertEquals(1, fragmentManager.getBackStackEntryCount());
        return dialog;
    }

    private void assertDialogDismissed(FragmentManager fragmentManager) {
        assertNull(fragmentManager.findFragmentByTag(FullScreenDialogFragment.TAG));
        assertEquals(0, fragmentManager.getBackStackEntryCount());
    }

    public static class TestActivity extends AppCompatActivity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            setTheme(R.style.Theme_Simplestyle);
            super.onCreate(savedInstanceState);
        }
    }

    public static class TestContentFragment extends Fragment implements
            FullScreenDialogFragment.FullScreenDialogContent {
        @Override
        public boolean onConfirmClicked(FullScreenDialogFragment.FullScreenDialogController controller) {
            return false;
        }

        @Override
        public boolean onDismissClicked(FullScreenDialogFragment.FullScreenDialogController controller) {
            return false;
        }

        @Override
        public void onViewCreated(FullScreenDialogFragment.FullScreenDialogController controller) {
        }
    }
}
