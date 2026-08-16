package com.automattic.simplenote.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.automattic.simplenote.NotesActivity;

public class IntentUtils {
    public static String getMainActivityClassName(Context context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null && launchIntent.getComponent() != null) {
            ComponentName componentName = launchIntent.getComponent();
            return componentName.getClassName();
        }

        return NotesActivity.class.getName();
    }

    public static Intent maybeAliasedIntent(Context context) {
        String packageName = context.getPackageName();

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, getMainActivityClassName(context)));

        return intent;
    }
}
