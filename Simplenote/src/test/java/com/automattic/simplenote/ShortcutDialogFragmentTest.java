package com.automattic.simplenote;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShortcutDialogFragmentTest {
    @Test
    public void defaultFactoryCanInstantiateFragment() {
        Fragment fragment = new FragmentFactory().instantiate(
                ShortcutDialogFragment.class.getClassLoader(),
                ShortcutDialogFragment.class.getName()
        );

        assertTrue(fragment instanceof ShortcutDialogFragment);
    }

    @Test
    public void previewArgumentSurvivesSavedArgumentTransfer() {
        ShortcutDialogFragment original = ShortcutDialogFragment.newInstance(true);
        ShortcutDialogFragment restored = (ShortcutDialogFragment) new FragmentFactory().instantiate(
                ShortcutDialogFragment.class.getClassLoader(),
                ShortcutDialogFragment.class.getName()
        );

        restored.setArguments(new Bundle(original.requireArguments()));

        assertTrue(restored.requireArguments().getBoolean("ARG_IS_PREVIEW"));
    }
}
