package com.automattic.simplenote.widgets;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.transition.TransitionValues;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class MorphSetupTest {
    private Activity mActivity;
    private View mTarget;

    @Before
    public void setUp() {
        Intent intent = new Intent();
        intent.putExtra(MorphSetup.EXTRA_SHARED_ELEMENT_COLOR_END, Color.BLACK);
        intent.putExtra(MorphSetup.EXTRA_SHARED_ELEMENT_COLOR_START, Color.WHITE);
        mActivity = Robolectric.buildActivity(Activity.class, intent).setup().get();
        mTarget = new View(mActivity);
        mTarget.layout(0, 0, 100, 100);
    }

    @Test
    public void returnTransitionUsesConfiguredStartRadiusWithoutMorphBackground() {
        TransitionValues values = captureReturnStartValues(12);

        assertEquals(Integer.valueOf(12), values.values.get("radius"));
    }

    @Test
    public void returnTransitionUsesCurrentMorphBackgroundRadius() {
        mTarget.setBackground(new MorphDrawable(Color.BLACK, 7.6f));

        TransitionValues values = captureReturnStartValues(12);

        assertEquals(Integer.valueOf(8), values.values.get("radius"));
    }

    private TransitionValues captureReturnStartValues(int radius) {
        MorphSetup.setSharedElementTransitions(mActivity, mTarget, radius);
        MorphRectangleToCircle transition =
                (MorphRectangleToCircle) mActivity.getWindow().getSharedElementReturnTransition();
        TransitionValues values = new TransitionValues(mTarget);
        transition.captureStartValues(values);
        return values;
    }
}
