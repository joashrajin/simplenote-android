package com.automattic.simplenote.utils;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.automattic.simplenote.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SimplenoteProgressDialogFragmentTest {
    @Test
    public void dismissAfterStateSaveRemovesDialogWithoutThrowing() {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup();
        FragmentManager fragmentManager = controller.get().getSupportFragmentManager();
        SimplenoteProgressDialogFragment dialog =
            SimplenoteProgressDialogFragment.newInstance("Loading");
        dialog.showNow(fragmentManager, SimplenoteProgressDialogFragment.TAG);

        controller.saveInstanceState(new Bundle());

        assertTrue(fragmentManager.isStateSaved());
        dialog.dismiss();
        fragmentManager.executePendingTransactions();
        assertNull(fragmentManager.findFragmentByTag(SimplenoteProgressDialogFragment.TAG));
    }

    @Test
    public void dismissAfterHostDestroyedDoesNotThrow() {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup();
        SimplenoteProgressDialogFragment dialog =
            SimplenoteProgressDialogFragment.newInstance("Loading");
        dialog.showNow(
            controller.get().getSupportFragmentManager(),
            SimplenoteProgressDialogFragment.TAG
        );

        controller.pause().stop().destroy();

        dialog.dismiss();
    }

    public static class TestActivity extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.Theme_Simplestyle);
            super.onCreate(savedInstanceState);
        }
    }
}
