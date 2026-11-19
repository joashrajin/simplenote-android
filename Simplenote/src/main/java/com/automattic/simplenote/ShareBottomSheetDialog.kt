package com.automattic.simplenote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.utils.NetworkUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class ShareBottomSheetDialog(
    private val fragment: Fragment,
    private val listener: ShareSheetListener
) : BottomSheetDialogBase() {
    private lateinit var publishButton: TextView
    private lateinit var unpublishButton: TextView
    private lateinit var wordPressButton: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.let { dialog ->
            dialog.setOnDismissListener { listener.onShareDismissed() }

            // Expand fully so the default peek height does not clip the action labels in landscape.
            dialog.setOnShowListener { dialogInterface ->
                val behavior = (dialogInterface as BottomSheetDialog).behavior
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }

            dialog.setContentView(R.layout.bottom_sheet_share)
            val collaborateButton = dialog.findViewById<TextView>(R.id.share_collaborate_button)!!
            val shareOtherButton = dialog.findViewById<TextView>(R.id.share_other_button)!!
            publishButton = dialog.findViewById(R.id.share_publish_button)!!
            unpublishButton = dialog.findViewById(R.id.share_unpublish_button)!!
            wordPressButton = dialog.findViewById(R.id.share_wp_post)!!

            collaborateButton.setOnClickListener { listener.onShareCollaborateClicked() }

            publishButton.setOnClickListener {
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    Toast.makeText(requireContext(), R.string.error_network_required, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                listener.onSharePublishClicked()
            }

            unpublishButton.setOnClickListener {
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    Toast.makeText(requireContext(), R.string.error_network_required, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                listener.onShareUnpublishClicked()
            }

            wordPressButton.setOnClickListener { listener.onWordPressPostClicked() }

            shareOtherButton.setOnClickListener {
                listener.onShareOtherClicked()
                dismiss()
            }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    fun show(manager: FragmentManager, note: Note?) {
        if (fragment.isAdded) {
            showNow(manager, TAG)

            if (note!!.isPublished) {
                publishButton.visibility = View.GONE
                unpublishButton.visibility = View.VISIBLE
            } else {
                publishButton.visibility = View.VISIBLE
                unpublishButton.visibility = View.GONE
            }
        }
    }

    interface ShareSheetListener {
        fun onShareCollaborateClicked()
        fun onShareDismissed()
        fun onShareOtherClicked()
        fun onSharePublishClicked()
        fun onShareUnpublishClicked()
        fun onWordPressPostClicked()
    }

    companion object {
        @JvmField
        val TAG: String = ShareBottomSheetDialog::class.java.simpleName
    }
}
