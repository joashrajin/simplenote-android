package com.automattic.simplenote;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.automattic.simplenote.utils.DrawableUtils;
import com.automattic.simplenote.utils.SystemBarUtils;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(DrawableUtils.tintDrawableWithResource(
                this, R.drawable.ic_cross_24dp, android.R.color.white
            ));
        }
        
        // The About screen is blue in both modes, so force light bar icons.
        SystemBarUtils.applyEdgeToEdge(this, false);
        SystemBarUtils.applyInsets(
            findViewById(R.id.main_parent_view),
            toolbar,
            findViewById(R.id.about_fragment)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
