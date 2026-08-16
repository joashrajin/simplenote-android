package com.automattic.simplenote.authentication.magiclink

import com.automattic.simplenote.networking.SimpleHttp
import com.automattic.simplenote.repositories.MagicLinkResponseResult
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException

class OkHttpMagicLinkRepositoryTest {
    private val simpleHttp: SimpleHttp = mock()
    private val repository = OkHttpMagicLinkRepository(simpleHttp)

    @Test
    fun malformedSuccessfulResponseIsReportedAsIOException() = runTest {
        whenever(simpleHttp.firePostRequest(REQUEST_PATH, REQUEST_BODY)).thenReturn(response("{}"))

        val exception = runCatching {
            repository.completeLogin(USERNAME, AUTH_CODE)
        }.exceptionOrNull()

        assertEquals(IOException::class.java, exception?.javaClass)
        assertTrue(exception?.cause is JSONException)
    }

    @Test
    fun validSuccessfulResponseRetainsItsResult() = runTest {
        whenever(simpleHttp.firePostRequest(REQUEST_PATH, REQUEST_BODY)).thenReturn(
            response("""{"sync_token":"$SYNC_TOKEN","username":"$USERNAME"}""")
        )

        assertEquals(
            MagicLinkResponseResult.MagicLinkCompleteSuccess(USERNAME, SYNC_TOKEN),
            repository.completeLogin(USERNAME, AUTH_CODE)
        )
    }

    private fun response(body: String): Response = Response.Builder()
        .request(Request.Builder().url("https://app.simplenote.com/$REQUEST_PATH").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody())
        .build()

    private companion object {
        const val REQUEST_PATH = "account/complete-login"
        const val USERNAME = "person@example.com"
        const val AUTH_CODE = "auth-code"
        const val SYNC_TOKEN = "sync-token"
        val REQUEST_BODY = mapOf("username" to USERNAME, "auth_code" to AUTH_CODE)
    }
}
