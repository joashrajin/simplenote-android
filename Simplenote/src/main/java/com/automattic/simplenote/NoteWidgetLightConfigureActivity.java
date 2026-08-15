package com.automattic.simplenote;

import static com.automattic.simplenote.analytics.AnalyticsTracker.CATEGORY_WIDGET;
import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_WIDGET_NOTE_NOT_FOUND_TAPPED;
import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_WIDGET_NOTE_TAPPED;
import static com.automattic.simplenote.utils.WidgetUtils.KEY_WIDGET_CLICK;
import static com.automattic.simplenote.viewmodels.NoteWidgetPickerViewModelKt.observeNoteWidgetPickerSelectionState;
import static com.automattic.simplenote.viewmodels.NoteWidgetPickerViewModelKt.observeNoteWidgetPickerUiState;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.automattic.simplenote.analytics.AnalyticsTracker;
import com.automattic.simplenote.usecases.WidgetNotePickerItem;
import com.automattic.simplenote.usecases.WidgetNotePickerSelection;
import com.automattic.simplenote.utils.ChecklistUtils;
import com.automattic.simplenote.utils.NoteUtils;
import com.automattic.simplenote.utils.PrefUtils;
import com.automattic.simplenote.viewmodels.NoteWidgetPickerSelectionState;
import com.automattic.simplenote.viewmodels.NoteWidgetPickerUiState;
import com.automattic.simplenote.viewmodels.NoteWidgetPickerViewModel;
import com.simperium.Simperium;
import com.simperium.client.User;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class NoteWidgetLightConfigureActivity extends AppCompatActivity {
    @Inject
    Simperium mSimperium;

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private AlertDialog mDialog;
    private ListView mListView;
    private NoteWidgetPickerAdapter mNotesAdapter;
    private NoteWidgetPickerViewModel mViewModel;
    private AppWidgetManager mWidgetManager;
    private RemoteViews mRemoteViews;
    private boolean mCompleting;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.note_widget_configure);

        User user = mSimperium.getUser();
        if (user.getStatus().equals(User.Status.NOT_AUTHORIZED)) {
            Toast.makeText(this, R.string.log_in_add_widget, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        mWidgetManager = AppWidgetManager.getInstance(this);
        mRemoteViews = new RemoteViews(getPackageName(), PrefUtils.getLayoutWidget(this, true));
        mViewModel = new ViewModelProvider(this).get(NoteWidgetPickerViewModel.class);
        observeNoteWidgetPickerUiState(mViewModel.getUiState(), this, this::renderUiState);
        observeNoteWidgetPickerSelectionState(
                mViewModel.getSelectionState(),
                this,
                this::renderSelectionState
        );
        mViewModel.load();

        if (savedInstanceState == null && intent.hasExtra(KEY_WIDGET_CLICK) &&
                intent.getSerializableExtra(KEY_WIDGET_CLICK) == NOTE_WIDGET_NOTE_NOT_FOUND_TAPPED) {
            AnalyticsTracker.track(
                    NOTE_WIDGET_NOTE_NOT_FOUND_TAPPED,
                    CATEGORY_WIDGET,
                    "note_widget_note_not_found_tapped"
            );
        }
    }

    private void renderUiState(NoteWidgetPickerUiState state) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (state instanceof NoteWidgetPickerUiState.Content) {
            showDialog(((NoteWidgetPickerUiState.Content) state).getItems());
        } else if (state instanceof NoteWidgetPickerUiState.Failed) {
            finishCanceled();
        }
    }

    private void renderSelectionState(NoteWidgetPickerSelectionState state) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (mListView != null) {
            mListView.setEnabled(state instanceof NoteWidgetPickerSelectionState.Idle);
        }

        if (state instanceof NoteWidgetPickerSelectionState.Selected) {
            NoteWidgetPickerSelectionState.Selected selected =
                    (NoteWidgetPickerSelectionState.Selected) state;
            mViewModel.consumeSelection(selected.getRequestId());
            completeWidget(selected.getSelection());
        } else if (state instanceof NoteWidgetPickerSelectionState.Missing) {
            NoteWidgetPickerSelectionState.Missing missing =
                    (NoteWidgetPickerSelectionState.Missing) state;
            mViewModel.consumeSelection(missing.getRequestId());
            finishCanceled();
        } else if (state instanceof NoteWidgetPickerSelectionState.Failed) {
            NoteWidgetPickerSelectionState.Failed failed =
                    (NoteWidgetPickerSelectionState.Failed) state;
            mViewModel.consumeSelection(failed.getRequestId());
            finishCanceled();
        }
    }

    @SuppressLint("InflateParams")
    private void showDialog(List<WidgetNotePickerItem> items) {
        if (mDialog != null) {
            mNotesAdapter.submitList(items);
            return;
        }

        ContextThemeWrapper context = new ContextThemeWrapper(this, PrefUtils.getStyleWidgetDialog(this));
        View layout = LayoutInflater.from(context).inflate(R.layout.note_widget_configure_list, null);
        mListView = layout.findViewById(R.id.list);
        mNotesAdapter = new NoteWidgetPickerAdapter(this, items);
        mListView.setAdapter(mNotesAdapter);
        mListView.setOnItemClickListener((parent, view, position, id) ->
                mViewModel.selectNote(mNotesAdapter.getItem(position).getKey())
        );
        mListView.setEnabled(
                mViewModel.getSelectionState().getValue() instanceof NoteWidgetPickerSelectionState.Idle
        );

        mDialog = new AlertDialog.Builder(context)
                .setView(layout)
                .setTitle(R.string.select_note)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mDialog.setOnDismissListener(dialog -> {
            mDialog = null;
            mListView = null;
            if (!mCompleting && !isChangingConfigurations()) {
                mCompleting = true;
                finish();
            }
        });
        mDialog.show();
    }

    private void completeWidget(WidgetNotePickerSelection note) {
        if (mCompleting) {
            return;
        }
        mCompleting = true;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putString(PrefUtils.PREF_NOTE_WIDGET_NOTE + mAppWidgetId, note.getKey()).apply();

        Bundle arguments = new Bundle();
        arguments.putBoolean(NoteEditorFragment.ARG_IS_FROM_WIDGET, true);
        arguments.putString(NoteEditorFragment.ARG_ITEM_ID, note.getKey());
        arguments.putBoolean(NoteEditorFragment.ARG_MARKDOWN_ENABLED, note.getMarkdownEnabled());
        arguments.putBoolean(NoteEditorFragment.ARG_PREVIEW_ENABLED, note.getPreviewEnabled());

        Intent intent = new Intent(this, NoteEditorActivity.class);
        intent.putExtras(arguments);
        intent.putExtra(KEY_WIDGET_CLICK, NOTE_WIDGET_NOTE_TAPPED);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                mAppWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = note.getTitle();
        String content = NoteUtils.getContentWithoutTitle(note.getContent(), title);
        mRemoteViews.setOnClickPendingIntent(R.id.widget_layout, pendingIntent);
        mRemoteViews.setTextViewText(R.id.widget_text, title);
        mRemoteViews.setTextColor(R.id.widget_text, getColor(R.color.text_title_light));
        mRemoteViews.setTextViewText(R.id.widget_text_title, title);
        mRemoteViews.setTextColor(R.id.widget_text_title, getColor(R.color.text_title_light));
        SpannableStringBuilder contentSpan = new SpannableStringBuilder(content);
        contentSpan = (SpannableStringBuilder) ChecklistUtils.addChecklistUnicodeSpansForRegex(
                contentSpan,
                ChecklistUtils.CHECKLIST_REGEX
        );
        mRemoteViews.setTextViewText(R.id.widget_text_content, contentSpan);
        mWidgetManager.updateAppWidget(mAppWidgetId, mRemoteViews);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        dismissDialogWithoutFinishing();
        finish();
    }

    private void finishCanceled() {
        mCompleting = true;
        dismissDialogWithoutFinishing();
        finish();
    }

    private void dismissDialogWithoutFinishing() {
        if (mDialog != null) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
            mDialog = null;
            mListView = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (isChangingConfigurations()) {
            dismissDialogWithoutFinishing();
        }
        super.onDestroy();
    }
}
