package com.automattic.simplenote;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import android.app.Application;

import com.automattic.simplenote.utils.WordPressUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class WordPressDialogFragmentTest {
    @Test
    public void detachedFetchSitesCallbackClosesDeliveredResponse() throws IOException {
        WordPressDialogFragment fragment = new WordPressDialogFragment();
        AtomicReference<Callback> capturedCallback = new AtomicReference<>();

        try (MockedStatic<WordPressUtils> wordPressUtils = mockStatic(WordPressUtils.class)) {
            wordPressUtils.when(() -> WordPressUtils.getSites(isNull(), any(Callback.class)))
                    .thenAnswer(invocation -> {
                        capturedCallback.set(invocation.getArgument(1));
                        return null;
                    });

            ReflectionHelpers.callInstanceMethod(fragment, "fetchSitesFromAPI");
        }

        Callback callback = capturedCallback.get();
        assertNotNull(callback);
        ResponseBody body = mock(ResponseBody.class);
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://public-api.wordpress.com/rest/v1.1/me/sites").build())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body(body)
                .build();

        callback.onResponse(mock(Call.class), response);

        verify(body).close();
    }
}
