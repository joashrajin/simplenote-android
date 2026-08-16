package com.automattic.simplenote.authentication

import android.app.Application
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SignInFragmentTest {
    @Test
    fun oauthStateIsSavedForRestoration() {
        val savedState = Bundle()
        val fragment = SignInFragment()
        ReflectionHelpers.setField(fragment, "authState", AUTH_STATE)

        fragment.onSaveInstanceState(savedState)

        assertEquals(AUTH_STATE, savedState.getString(STATE_AUTH_STATE))
    }

    private companion object {
        const val AUTH_STATE = "app-fixed-oauth-state"
        const val STATE_AUTH_STATE = "STATE_AUTH_STATE"
    }
}
