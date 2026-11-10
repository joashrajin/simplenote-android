package com.automattic.simplenote.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.automattic.simplenote.R
import com.automattic.simplenote.viewmodels.AddCollaboratorViewModel

/**
 * Compose replacement for AddCollaboratorFragment: an email field with inline validation,
 * driven by the frozen [AddCollaboratorViewModel] through the hosting activity.
 *
 * The View dialog kept itself open on validation failure by overriding the positive button's
 * click listener in onShow; here Accept simply never dismisses — the activity closes the dialog
 * only on the ViewModel's CollaboratorAdded/Close/NoteDeleted/NoteInTrash events and feeds
 * InvalidCollaborator/CollaboratorCurrentUser back in as [errorRes]. Typing clears the error,
 * exactly like the old doAfterTextChanged hook.
 */
@Composable
fun AddCollaboratorDialog(
    @StringRes errorRes: Int?,
    onTextChanged: () -> Unit,
    onAddCollaborator: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var collaborator by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_collaborator)) },
        text = {
            OutlinedTextField(
                value = collaborator,
                onValueChange = { value ->
                    collaborator = value
                    onTextChanged()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.email_address)) },
                isError = errorRes != null,
                supportingText = errorRes?.let { error -> { Text(stringResource(error)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onAddCollaborator(collaborator) }) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
    // The fragment requested focus and raised the keyboard after a Handler delay; focusing the
    // field once the dialog is composed does the same without the timing hack.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
