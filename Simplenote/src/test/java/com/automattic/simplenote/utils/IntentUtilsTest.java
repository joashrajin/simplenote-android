package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.automattic.simplenote.NotesActivity;

import org.junit.Test;

public class IntentUtilsTest {
    private static final String PACKAGE_NAME = "com.automattic.simplenote.debug";

    private final Context mContext = mock(Context.class);
    private final PackageManager mPackageManager = mock(PackageManager.class);

    @Test
    public void missingLauncherFallsBackToNotesActivity() {
        when(mContext.getPackageName()).thenReturn(PACKAGE_NAME);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getLaunchIntentForPackage(PACKAGE_NAME)).thenReturn(null);

        assertEquals(NotesActivity.class.getName(), IntentUtils.getMainActivityClassName(mContext));
    }

    @Test
    public void availableLauncherClassIsPreserved() {
        String aliasClass = "com.automattic.simplenote.NotesActivitySustainerAlias";
        Intent launchIntent = mock(Intent.class);
        ComponentName component = mock(ComponentName.class);
        when(mContext.getPackageName()).thenReturn(PACKAGE_NAME);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getLaunchIntentForPackage(PACKAGE_NAME)).thenReturn(launchIntent);
        when(launchIntent.getComponent()).thenReturn(component);
        when(component.getClassName()).thenReturn(aliasClass);

        assertEquals(aliasClass, IntentUtils.getMainActivityClassName(mContext));
    }
}
