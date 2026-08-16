package com.automattic.simplenote.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AccountNetworkUtilsTest {
    private static final String REQUEST_URL = "https://app.simplenote.com/verify-email/test";
    private static final String RESPONSE_URL = "https://redirected.example/verify-email/test";

    @Test
    public void deleteSuccessfulResponseClosesResponse() throws IOException {
        DeleteAccountRequestHandler handler = mock(DeleteAccountRequestHandler.class);
        ResponseBody body = mock(ResponseBody.class);
        Response response = response(202, body);

        AccountNetworkUtils.buildDeleteAccountCallback(handler).onResponse(mock(Call.class), response);

        verify(handler).onSuccess();
        verifyNoMoreInteractions(handler);
        verify(body).close();
    }

    @Test
    public void deleteUnsuccessfulResponseClosesResponse() throws IOException {
        DeleteAccountRequestHandler handler = mock(DeleteAccountRequestHandler.class);
        ResponseBody body = mock(ResponseBody.class);
        Response response = response(503, body);

        AccountNetworkUtils.buildDeleteAccountCallback(handler).onResponse(mock(Call.class), response);

        verify(handler).onFailure();
        verifyNoMoreInteractions(handler);
        verify(body).close();
    }

    @Test
    public void verificationSuccessfulResponseClosesResponse() throws IOException {
        AccountVerificationEmailHandler handler = mock(AccountVerificationEmailHandler.class);
        ResponseBody body = mock(ResponseBody.class);
        Response response = response(200, body);

        AccountNetworkUtils.buildVerificationEmailCallback(handler).onResponse(call(), response);

        verify(handler).onSuccess(REQUEST_URL);
        verifyNoMoreInteractions(handler);
        verify(body).close();
    }

    @Test
    public void verificationNon200ResponseClosesResponse() throws IOException {
        AccountVerificationEmailHandler handler = mock(AccountVerificationEmailHandler.class);
        ResponseBody body = mock(ResponseBody.class);
        Response response = response(202, body);

        AccountNetworkUtils.buildVerificationEmailCallback(handler).onResponse(call(), response);

        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(handler).onFailure(failure.capture(), eq(REQUEST_URL));
        assertEquals("Error code: 202", failure.getValue().getMessage());
        verifyNoMoreInteractions(handler);
        verify(body).close();
    }

    @Test
    public void handlerFailureRemainsPrimaryWhenCloseAlsoFails() {
        DeleteAccountRequestHandler handler = mock(DeleteAccountRequestHandler.class);
        ResponseBody body = mock(ResponseBody.class);
        Response response = response(202, body);
        RuntimeException handlerFailure = new RuntimeException("handler");
        RuntimeException closeFailure = new RuntimeException("close");
        doThrow(handlerFailure).when(handler).onSuccess();
        doThrow(closeFailure).when(body).close();
        Callback callback = AccountNetworkUtils.buildDeleteAccountCallback(handler);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> callback.onResponse(mock(Call.class), response)
        );

        assertSame(handlerFailure, actual);
        assertArrayEquals(new Throwable[]{closeFailure}, actual.getSuppressed());
        verify(body).close();
    }

    private Call call() {
        Call call = mock(Call.class);
        when(call.request()).thenReturn(new Request.Builder().url(REQUEST_URL).build());
        return call;
    }

    private Response response(int code, ResponseBody body) {
        return new Response.Builder()
                .request(new Request.Builder().url(RESPONSE_URL).build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Test")
                .body(body)
                .build();
    }
}
