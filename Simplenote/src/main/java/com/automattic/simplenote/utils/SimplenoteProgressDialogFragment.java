package com.automattic.simplenote.utils;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.automattic.simplenote.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SimplenoteProgressDialogFragment extends DialogFragment {
    public static final String TAG = com.simperium.android.ProgressDialogFragment.class.getSimpleName();

    private static final String KEY_MESSAGE = "KEY_MESSAGE";

    public static SimplenoteProgressDialogFragment newInstance(String message) {
        SimplenoteProgressDialogFragment fragment = new SimplenoteProgressDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString(KEY_MESSAGE, message);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setCancelable(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use custom view for progress bar dialog
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View progressBar = inflater.inflate(R.layout.progressbar_dialog, null);
        TextView messageView = progressBar.findViewById(R.id.message);
        messageView.setText(getArguments() != null ? getArguments().getString(KEY_MESSAGE) : "");

        return new MaterialAlertDialogBuilder(requireContext()).setView(progressBar).create();
    }
}
