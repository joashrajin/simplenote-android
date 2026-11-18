package com.automattic.simplenote.utils;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.app.Application;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;

import com.automattic.simplenote.R;
import com.automattic.simplenote.widgets.CenteredImageSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/**
 * Pins what Java callers get when they pass null to the leaf utilities whose Java bodies
 * dereference the argument unconditionally: a NullPointerException, before any observable effect.
 * Written in Java so it keeps compiling (and passing null) whatever nullability the classes declare.
 * The null-tolerant paths are covered by the per-class tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class LeafUtilsJavaNullContractTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void drawableUtilsThrowOnNullDereferencedArguments() {
        Drawable drawable = new ColorDrawable();
        Menu menu = mock(Menu.class);
        MenuItem item = mock(MenuItem.class);

        assertThrows(NullPointerException.class, () -> DrawableUtils.setMenuItemAlpha(null, 0.3));
        assertThrows(NullPointerException.class,
            () -> DrawableUtils.tintDrawableWithResource(null, R.drawable.ic_cross_24dp, R.color.red_50));
        assertThrows(NullPointerException.class,
            () -> DrawableUtils.tintDrawable(null, R.drawable.ic_cross_24dp, 0));
        assertThrows(NullPointerException.class,
            () -> DrawableUtils.tintDrawableWithResource(null, drawable, R.color.red_50));
        assertThrows(NullPointerException.class,
            () -> DrawableUtils.tintDrawableWithAttribute(null, R.drawable.ic_cross_24dp, R.attr.colorPrimary));
        assertThrows(NullPointerException.class, () -> DrawableUtils.getColor(null, R.attr.colorPrimary));
        assertThrows(NullPointerException.class,
            () -> DrawableUtils.tintMenuWithAttribute(null, menu, R.attr.colorPrimary));
        assertThrows(NullPointerException.class,
            () -> DrawableUtils.tintMenuWithAttribute(mContext, null, R.attr.colorPrimary));
        assertThrows(NullPointerException.class, () -> DrawableUtils.tintMenu(null, 0));
        assertThrows(NullPointerException.class, () -> DrawableUtils.tintMenuItem(null, 0));
        assertThrows(NullPointerException.class,
            () -> DrawableUtils.tintMenuItemWithAttribute(null, item, R.attr.colorPrimary));
        assertThrows(NullPointerException.class,
            () -> DrawableUtils.tintMenuItemWithAttribute(mContext, null, R.attr.colorPrimary));
    }

    @Test
    public void networkUtilsSpeedGettersThrowOnNullContext() {
        assertThrows(NullPointerException.class, () -> NetworkUtils.getNetworkSpeed(null));
        assertThrows(NullPointerException.class, () -> NetworkUtils.getNetworkSpeedWifi(null));
    }

    @Test
    public void dialogUtilsThrowsOnNullArgumentsWithoutShowingDialog() {
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class);
        controller.get().setTheme(ThemeUtils.getStyle(mContext));
        AppCompatActivity activity = controller.setup().get();

        assertThrows(NullPointerException.class, () -> DialogUtils.showDialogWithEmail(null, "message"));
        assertThrows(NullPointerException.class, () -> DialogUtils.showDialogWithEmail(activity, null));
        assertNull(ShadowDialog.getLatestDialog());
    }

    @Test
    public void centeredImageSpanThrowsOnNullContext() {
        assertThrows(NullPointerException.class, () -> new CenteredImageSpan(null, new ColorDrawable()));
    }

    @Test
    public void spaceTokenizerThrowsOnNullTextWhereItReadsIt() {
        SpaceTokenizer tokenizer = new SpaceTokenizer();

        assertThrows(NullPointerException.class, () -> tokenizer.findTokenStart(null, 1));
        assertThrows(NullPointerException.class, () -> tokenizer.findTokenEnd(null, 0));
        assertThrows(NullPointerException.class, () -> tokenizer.terminateToken(null));
    }

    @Test
    public void htmlCompatThrowsOnNullSource() {
        assertThrows(NullPointerException.class, () -> HtmlCompat.fromHtml(null));
    }

    @Test
    public void simplenoteMovementMethodThrowsOnNullArguments() {
        SimplenoteMovementMethod method = SimplenoteMovementMethod.getInstance();
        TextView textView = new TextView(mContext);
        SpannableString buffer = new SpannableString("");
        MotionEvent event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 0f, 0f, 0);

        assertThrows(NullPointerException.class, () -> method.onTouchEvent(null, buffer, event));
        assertThrows(NullPointerException.class, () -> method.onTouchEvent(textView, buffer, null));
        event.recycle();
    }
}
