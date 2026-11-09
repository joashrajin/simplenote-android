package com.automattic.simplenote.utils;

import android.net.Uri;
import android.util.Base64;

import com.automattic.simplenote.AccountStreams;
import com.automattic.simplenote.Simplenote;
import com.automattic.simplenote.analytics.AnalyticsTracker;

import org.wordpress.passcodelock.AppLockManager;

import java.nio.charset.StandardCharsets;

public class AuthUtils {
    public static void logOut(Simplenote application) {
        // The data core — Simperium deauth, bucket reset/stop sequence, WordPress-token and
        // per-account preference clears — lives in AccountRepository.logout; the bridge runs it
        // to completion on the main thread before returning (see AccountStreams).
        AccountStreams.logOut(AccountStreams.accountRepository(application));

        // Resets analytics user back to 'anon' type
        AnalyticsTracker.refreshMetadata(null);

        // Remove Passcode Lock password
        AppLockManager.getInstance().getAppLock().setPassword("");

        WidgetUtils.updateNoteWidgets(application);
    }

    public static void magicLinkLogin(Simplenote application, Uri uri) {
        String userEmail = extractEmailFromMagicLink(uri);
        String spToken = uri.getQueryParameter("token");

        AccountStreams.logInWithToken(AccountStreams.accountRepository(application), userEmail, spToken);
    }

    public static String extractEmailFromMagicLink(Uri uri) {
        String userEmailEncoded = uri.getQueryParameter("email");
        return new String(Base64.decode(userEmailEncoded, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
