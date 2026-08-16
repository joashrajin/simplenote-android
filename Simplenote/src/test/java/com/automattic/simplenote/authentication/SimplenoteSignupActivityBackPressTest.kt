package com.automattic.simplenote.authentication

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import com.automattic.simplenote.Simplenote
import com.automattic.simplenote.authentication.magiclink.MagicLinkConfirmationFragment
import com.automattic.simplenote.di.AppModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class SimplenoteSignupActivityBackPressTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun dispatcherBackReturnsToAuthentication() {
        launchSignup().use { scenario ->
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()

                assertEquals(
                    SimplenoteAuthenticationActivity::class.java.name,
                    shadowOf(activity).nextStartedActivity.component?.className,
                )
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun toolbarHomeRoutesThroughDispatcher() {
        launchSignup().use { scenario ->
            scenario.onActivity { activity ->
                var handled = false
                activity.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        handled = true
                    }
                })
                val homeItem = mock<MenuItem>()
                whenever(homeItem.itemId).thenReturn(android.R.id.home)

                assertTrue(activity.onOptionsItemSelected(homeItem))
                assertTrue(handled)
                assertNull(shadowOf(activity).nextStartedActivity)
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun magicLinkBackPopsBeforeReturningToAuthentication() {
        launchSignup(isLogin = true).use { scenario ->
            scenario.onActivity { activity ->
                val fragmentManager = activity.supportFragmentManager
                fragmentManager.executePendingTransactions()
                assertTrue(
                    fragmentManager.findFragmentByTag(SimplenoteSignupActivity.SIGNUP_FRAGMENT_TAG)
                        is SignInFragment,
                )
                fragmentManager.beginTransaction()
                    .add(
                        R.id.fragment_container,
                        MagicLinkConfirmationFragment.newInstance("person@example.com"),
                        SimplenoteSignupActivity.SIGNUP_FRAGMENT_TAG,
                    )
                    .addToBackStack(null)
                    .commit()
                fragmentManager.executePendingTransactions()

                activity.onBackPressedDispatcher.onBackPressed()

                assertTrue(
                    fragmentManager.findFragmentByTag(SimplenoteSignupActivity.SIGNUP_FRAGMENT_TAG)
                        is SignInFragment,
                )
                assertNull(shadowOf(activity).nextStartedActivity)
                assertFalse(activity.isFinishing)

                activity.onBackPressedDispatcher.onBackPressed()

                assertEquals(
                    SimplenoteAuthenticationActivity::class.java.name,
                    shadowOf(activity).nextStartedActivity.component?.className,
                )
                assertTrue(activity.isFinishing)
            }
        }
    }

    private fun launchSignup(isLogin: Boolean = false): ActivityScenario<SimplenoteSignupActivity> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, SimplenoteSignupActivity::class.java).apply {
            putExtra(SimplenoteSignupActivity.KEY_IS_LOGIN, isLogin)
        }
        return ActivityScenario.launch(intent)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestAppModule {
        @Provides
        fun provideSimplenote(): Simplenote = mock()

        @Provides
        fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)
    }
}
