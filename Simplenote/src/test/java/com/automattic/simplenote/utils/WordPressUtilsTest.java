package com.automattic.simplenote.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.app.Application;
import android.net.Uri;

import com.automattic.simplenote.Simplenote;

import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class WordPressUtilsTest {
    private static final String AUTH_STATE = "app-test-state";
    private static final String USER_EMAIL = "user@example.com";
    private static final String SIMPERIUM_TOKEN = "simperium-token";

    @Test
    public void missingExpectedAndResponseStateIsRejected() {
        Simplenote application = mock(Simplenote.class);

        boolean result = WordPressUtils.processAuthResponse(
            application,
            authorizationResponse(null),
            null,
            true
        );

        assertFalse(result);
        verifyNoInteractions(application);
    }

    @Test
    public void matchingNonEmptyStateStillAuthorizesUser() {
        Simplenote application = mock(Simplenote.class);

        boolean result = WordPressUtils.processAuthResponse(
            application,
            authorizationResponse(AUTH_STATE),
            AUTH_STATE,
            true
        );

        assertTrue(result);
        verify(application).loginWithToken(USER_EMAIL, SIMPERIUM_TOKEN);
    }

    private AuthorizationResponse authorizationResponse(String state) {
        AuthorizationServiceConfiguration configuration = new AuthorizationServiceConfiguration(
            Uri.parse("https://public-api.wordpress.com/oauth2/authorize"),
            Uri.parse("https://public-api.wordpress.com/oauth2/token")
        );
        AuthorizationRequest request = new AuthorizationRequest.Builder(
            configuration,
            "test-client",
            ResponseTypeValues.CODE,
            Uri.parse("https://app.simplenote.com/wpcc")
        ).setState(state).build();

        AuthorizationResponse.Builder response = new AuthorizationResponse.Builder(request)
            .setAdditionalParameters(Map.of(
                "user", USER_EMAIL,
                "token", SIMPERIUM_TOKEN
            ));

        if (state != null) {
            response.setState(state);
        }

        return response.build();
    }
}
