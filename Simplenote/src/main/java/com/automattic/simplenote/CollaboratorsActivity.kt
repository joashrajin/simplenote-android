package com.automattic.simplenote

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automattic.simplenote.compose.AddCollaboratorDialog
import com.automattic.simplenote.compose.CollaboratorsScreen
import com.automattic.simplenote.compose.RemoveCollaboratorDialog
import com.automattic.simplenote.compose.SimplenoteTheme
import com.automattic.simplenote.utils.IntentUtils
import com.automattic.simplenote.utils.toast
import com.automattic.simplenote.viewmodels.AddCollaboratorViewModel
import com.automattic.simplenote.viewmodels.CollaboratorsViewModel
import com.automattic.simplenote.viewmodels.CollaboratorsViewModel.Event
import com.automattic.simplenote.viewmodels.CollaboratorsViewModel.UiState
import dagger.hilt.android.AndroidEntryPoint

/**
 * First Compose screen conversion: the window is a single ComposeView hosting
 * [CollaboratorsScreen] under [SimplenoteTheme]. The frozen ViewModels are untouched — uiState
 * renders through observeAsState() inside the screen, while both SingleLiveEvent streams stay
 * observed here, exactly as the View world did: toasts, finish/navigate, and the visibility of
 * the two dialogs (add + remove-confirm), which compose over the screen from activity-held state.
 *
 * Edge to edge is unchanged: ThemedAppCompatActivity.onCreate applies
 * SystemBarUtils.applyEdgeToEdge, and the Scaffold inside [CollaboratorsScreen] consumes
 * WindowInsets.safeDrawing in place of the deleted SystemBarUtils.applyInsets wiring.
 */
@AndroidEntryPoint
class CollaboratorsActivity : ThemedAppCompatActivity() {
    private val viewModel: CollaboratorsViewModel by viewModels()
    private val addCollaboratorViewModel: AddCollaboratorViewModel by viewModels()

    // The AddCollaboratorFragment used to be scoped to its own show; its replacement dialog's
    // visibility and inline error live here so the SingleLiveEvent streams (whose observers
    // cannot be unregistered per-dialog) are observed exactly once, for the activity's lifetime.
    private var addCollaboratorDialog by mutableStateOf<AddCollaboratorDialogState?>(null)
    private var collaboratorToRemove by mutableStateOf<String?>(null)

    private data class AddCollaboratorDialogState(
        val noteId: String,
        @StringRes val errorRes: Int? = null,
        /**
         * True from Accept until the ViewModel answers. The valid path suspends on IO, so a late
         * CollaboratorAdded/Close/NoteDeleted/NoteInTrash could otherwise dismiss a fresh dialog
         * the user reopened after cancelling; a reopened dialog starts false, so stale
         * dismiss-class events are ignored.
         */
        val submitting: Boolean = false,
    )

    companion object {
        const val NOTE_ID_ARG = "note_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val noteId = intent.getStringExtra(NOTE_ID_ARG)
        if (noteId == null) {
            finish()
            return
        }

        setObservers()
        viewModel.loadCollaborators(noteId)

        setContent {
            SimplenoteTheme {
                CollaboratorsScreen(viewModel)

                addCollaboratorDialog?.let { dialog ->
                    AddCollaboratorDialog(
                        errorRes = dialog.errorRes,
                        onTextChanged = { clearAddCollaboratorError() },
                        onAddCollaborator = { collaborator ->
                            addCollaboratorDialog = addCollaboratorDialog?.copy(submitting = true)
                            addCollaboratorViewModel.addCollaborator(dialog.noteId, collaborator)
                        },
                        onDismiss = { addCollaboratorDialog = null },
                    )
                }

                collaboratorToRemove?.let { collaborator ->
                    RemoveCollaboratorDialog(
                        onConfirm = {
                            collaboratorToRemove = null
                            viewModel.removeCollaborator(collaborator)
                        },
                        onDismiss = { collaboratorToRemove = null },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.startListeningChanges()
    }

    override fun onPause() {
        super.onPause()

        viewModel.stopListeningChanges()
    }

    private fun setObservers() {
        viewModel.uiState.observe(this) { uiState ->
            when (uiState) {
                is UiState.NoteDeleted -> {
                    toast(R.string.collaborators_note_deleted, Toast.LENGTH_LONG)
                    navigateToNotesList()
                }
                is UiState.NoteInTrash -> {
                    toast(R.string.collaborators_note_trashed, Toast.LENGTH_LONG)
                    navigateToNotesList()
                }
                // Rendered by CollaboratorsScreen through observeAsState().
                is UiState.CollaboratorsList, is UiState.EmptyCollaborators -> Unit
            }
        }

        viewModel.event.observe(this) { event ->
            when (event) {
                is Event.AddCollaboratorEvent ->
                    addCollaboratorDialog = AddCollaboratorDialogState(event.noteId)
                is Event.LongAddCollaboratorEvent -> showLongPressToast(R.string.add_collaborator)
                is Event.LongRemoveCollaboratorEvent -> showLongPressToast(R.string.remove_collaborator)
                is Event.RemoveCollaboratorEvent -> collaboratorToRemove = event.collaborator
                Event.CloseCollaboratorsEvent -> finish()
            }
        }

        addCollaboratorViewModel.event.observe(this) { event ->
            when (event) {
                AddCollaboratorViewModel.Event.CollaboratorAdded,
                AddCollaboratorViewModel.Event.Close,
                AddCollaboratorViewModel.Event.NoteDeleted, // The uiState observer handles the note.
                AddCollaboratorViewModel.Event.NoteInTrash -> {
                    // Only a dialog with a submission in flight dismisses: a stale event arriving
                    // after cancel-and-reopen (submitting == false) must not close the new dialog.
                    if (addCollaboratorDialog?.submitting == true) {
                        addCollaboratorDialog = null
                    }
                }
                AddCollaboratorViewModel.Event.InvalidCollaborator ->
                    setAddCollaboratorError(R.string.invalid_collaborator)
                AddCollaboratorViewModel.Event.CollaboratorCurrentUser ->
                    setAddCollaboratorError(R.string.collaborator_is_current_user)
            }
        }
    }

    private fun setAddCollaboratorError(@StringRes errorRes: Int) {
        addCollaboratorDialog = addCollaboratorDialog?.copy(errorRes = errorRes, submitting = false)
    }

    private fun clearAddCollaboratorError() {
        addCollaboratorDialog = addCollaboratorDialog?.copy(errorRes = null)
    }

    private fun showLongPressToast(@StringRes message: Int) {
        // The View world performed the haptic on the FAB; the decor view is its stand-in.
        if (window.decorView.isHapticFeedbackEnabled) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        toast(message)
    }

    private fun navigateToNotesList() {
        val intent = IntentUtils.maybeAliasedIntent(applicationContext)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }
}
