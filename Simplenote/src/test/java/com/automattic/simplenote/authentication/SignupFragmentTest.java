package com.automattic.simplenote.authentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SignupFragmentTest {
    @Test
    public void detachedFragmentClosesDeliveredResponse() throws IOException {
        SignupFragment fragment = new SignupFragment();
        ResponseBody body = mock(ResponseBody.class);
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://app.simplenote.com/signup").build())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body(body)
                .build();
        Callback callback = ReflectionHelpers.callInstanceMethod(
                fragment,
                "buildCallback",
                ReflectionHelpers.ClassParameter.from(String.class, "test@example.com")
        );

        callback.onResponse(mock(Call.class), response);

        verify(body).close();
    }
}
