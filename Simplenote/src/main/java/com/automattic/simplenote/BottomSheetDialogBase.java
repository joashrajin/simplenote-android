package com.automattic.simplenote;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.automattic.simplenote.utils.SystemBarUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Base of the editor bottom sheets. The sheet dialog inherits the activity theme's Material 3
 * bottomSheetDialogTheme (surface, 28dp top corners, width clamp via the bottomSheetStyle's
 * android:maxWidth), so the base only keeps the retained-instance wiring and the system-bar
 * treatment of the sheet window.
 */
public class BottomSheetDialogBase extends BottomSheetDialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setRetainInstance(true);
        return super.onCreateDialog(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getDialog() != null) {
            SystemBarUtils.applyToSheet(getDialog());
        }
    }
}
