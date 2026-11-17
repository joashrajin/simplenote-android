package com.automattic.simplenote.utils;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.automattic.simplenote.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

public class DialogUtils {
    /**
     * Show an alert dialog with a link to the support@simplenote.com email address,
     * which can be tapped to launch the device's email app.
     *
     * @param context   {@link Context} from which to determine theme and resources.
     * @param message   {@link String} for the dialog message.
     */
    public static void showDialogWithEmail(Context context, String message) {
        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.error)
            .setMessage(HtmlCompat.fromHtml(String.format(
                message,
                context.getString(R.string.support_email),
                "<span style=\"color:#",
                Integer.toHexString(ThemeUtils.getColorFromAttribute(context, R.attr.colorAccent) & 0xffffff),
                "\">",
                "</span>"
            )))
            .setPositiveButton(android.R.string.ok, null)
            .show();
        ((TextView) Objects.requireNonNull(dialog.findViewById(android.R.id.message))).setMovementMethod(LinkMovementMethod.getInstance());
    }
}
