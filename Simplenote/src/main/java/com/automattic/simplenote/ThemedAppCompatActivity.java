package com.automattic.simplenote;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;

import com.automattic.simplenote.utils.PrefUtils;
import com.automattic.simplenote.utils.StyleOverlayUtils;
import com.automattic.simplenote.utils.SystemBarUtils;
import com.automattic.simplenote.utils.ThemeUtils;

/**
 * Abstract class to apply theme based on {@link PrefUtils#PREF_STYLE_INDEX}
 * to any {@link AppCompatActivity} that extends it.
 */
abstract public class ThemedAppCompatActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    @StyleRes
    private int mStyleOverlay;
    private Boolean mThemeChanged = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_Simplestyle);
        mStyleOverlay = StyleOverlayUtils.getStyleOverlay(this);
        applyStyleOverlay();
        SystemBarUtils.applyEdgeToEdge(this);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        applyStyleOverlay();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyStyleOverlay();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mThemeChanged) {
            recreate();
            mThemeChanged = false;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PrefUtils.PREF_THEME) || key.equals(PrefUtils.PREF_STYLE_INDEX)) {
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                recreate();
            } else {
                mThemeChanged = true;
            }
        }
    }

    @Override
    public void recreate() {
        Intent intent = new Intent(this, getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    private void applyStyleOverlay() {
        if (mStyleOverlay == 0) {
            return;
        }

        Resources.Theme activityTheme = getTheme();
        activityTheme.applyStyle(mStyleOverlay, true);

        Resources.Theme decorTheme = getWindow().getDecorView().getContext().getTheme();
        if (decorTheme != activityTheme) {
            decorTheme.applyStyle(mStyleOverlay, true);
        }

        TypedValue backgroundValue = new TypedValue();
        if (!activityTheme.resolveAttribute(android.R.attr.windowBackground, backgroundValue, true)) {
            return;
        }

        Drawable background = null;
        if (backgroundValue.resourceId != 0) {
            background = ResourcesCompat.getDrawable(getResources(), backgroundValue.resourceId, activityTheme);
        } else if (backgroundValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                backgroundValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            background = new ColorDrawable(backgroundValue.data);
        }

        if (background != null) {
            getWindow().setBackgroundDrawable(background);
        }
    }
}
