package com.automattic.simplenote.utils

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.automattic.simplenote.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Objects

object DialogUtils {
    /**
     * Show an alert dialog with a link to the support@simplenote.com email address,
     * which can be tapped to launch the device's email app.
     *
     * @param context   [Context] from which to determine theme and resources.
     * @param message   [String] for the dialog message.
     */
    @JvmStatic
    fun showDialogWithEmail(context: Context, message: String) {
        val dialog: AlertDialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.error)
            .setMessage(
                HtmlCompat.fromHtml(
                    String.format(
                        message,
                        context.getString(R.string.support_email),
                        "<span style=\"color:#",
                        Integer.toHexString(ThemeUtils.getColorFromAttribute(context, R.attr.colorAccent) and 0xffffff),
                        "\">",
                        "</span>"
                    )
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
        Objects.requireNonNull<TextView>(dialog.findViewById(android.R.id.message)).movementMethod =
            LinkMovementMethod.getInstance()
    }
}
