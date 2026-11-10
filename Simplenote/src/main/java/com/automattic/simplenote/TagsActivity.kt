package com.automattic.simplenote

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automattic.simplenote.compose.DeleteTagDialog
import com.automattic.simplenote.compose.SimplenoteTheme
import com.automattic.simplenote.compose.TagsScreen
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.utils.DisplayUtils
import com.automattic.simplenote.utils.toast
import com.automattic.simplenote.viewmodels.TagsEvent
import com.automattic.simplenote.viewmodels.TagsEvent.AddTagEvent
import com.automattic.simplenote.viewmodels.TagsEvent.DeleteTagEvent
import com.automattic.simplenote.viewmodels.TagsEvent.EditTagEvent
import com.automattic.simplenote.viewmodels.TagsEvent.FinishEvent
import com.automattic.simplenote.viewmodels.TagsEvent.LongAddTagEvent
import com.automattic.simplenote.viewmodels.TagsEvent.LongDeleteTagEvent
import com.automattic.simplenote.viewmodels.TagsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Second Compose screen conversion (the Collaborators screen set the pattern): the window is a
 * single ComposeView hosting [TagsScreen] under [SimplenoteTheme]. The ViewModel's uiState renders
 * through observeAsState() inside the screen, while the SingleLiveEvent stream stays observed
 * here, exactly as the View world did: toasts, finish, the AddTagActivity launch, the rename
 * [TagDialogFragment] (still a View DialogFragment), and the delete-confirmation dialog's
 * visibility, which composes over the screen from activity-held state.
 *
 * The FAB -> AddTagActivity MorphSetup shared-element morph is dropped (the design's accepted
 * loss): a Compose FAB cannot anchor a View scene-transition, so the launch is a plain
 * startActivityForResult and AddTagActivity's faux-dialog window simply appears.
 *
 * Edge to edge is unchanged: ThemedAppCompatActivity.onCreate applies
 * SystemBarUtils.applyEdgeToEdge, and the Scaffold inside [TagsScreen] consumes
 * WindowInsets.safeDrawing in place of the deleted SystemBarUtils.applyInsets wiring.
 */
@AndroidEntryPoint
class TagsActivity : ThemedAppCompatActivity() {
    private val viewModel: TagsViewModel by viewModels()

    // The delete-confirm dialog's visibility lives here so the SingleLiveEvent stream (whose
    // observers cannot be unregistered per-dialog) is observed exactly once, for the activity's
    // lifetime.
    private var tagToDelete by mutableStateOf<TagItem?>(null)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setObservers()
        viewModel.start()

        setContent {
            SimplenoteTheme {
                TagsScreen(viewModel)

                tagToDelete?.let { tagItem ->
                    DeleteTagDialog(
                        onConfirm = {
                            tagToDelete = null
                            viewModel.deleteTag(tagItem)
                        },
                        onDismiss = { tagToDelete = null },
                    )
                }
            }
        }
    }

    private fun setObservers() {
        viewModel.event.observe(this) { event: TagsEvent ->
            when (event) {
                is AddTagEvent -> startAddTagActivity()
                is LongAddTagEvent -> showLongPressToast(R.string.add_tag)
                is FinishEvent -> finish()
                is EditTagEvent -> showTagDialogFragment(event)
                is DeleteTagEvent -> tagToDelete = event.tagItem
                is LongDeleteTagEvent -> showLongPressToast(R.string.delete_tag)
            }
        }
    }

    private fun showTagDialogFragment(event: EditTagEvent) {
        val dialog = TagDialogFragment(event.tagItem.tag)
        dialog.show(supportFragmentManager.beginTransaction(), TagDialogFragment.DIALOG_TAG)
    }

    @Suppress("DEPRECATION")
    private fun startAddTagActivity() {
        startActivityForResult(Intent(this, AddTagActivity::class.java), REQUEST_ADD_TAG)
    }

    private fun showLongPressToast(@StringRes message: Int) {
        // The View world performed the haptic on the long-pressed view; the decor view is its
        // stand-in now that the event no longer carries one.
        if (window.decorView.isHapticFeedbackEnabled) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        toast(message)
    }

    override fun onResume() {
        super.onResume()
        DisplayUtils.disableScreenshotsIfLocked(this)
        viewModel.startListeningTagChanges()
    }

    public override fun onPause() {
        super.onPause()
        viewModel.stopListeningTagChanges()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && requestCode == REQUEST_ADD_TAG) {
            viewModel.updateOnResult()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val REQUEST_ADD_TAG = 9000
    }
}
