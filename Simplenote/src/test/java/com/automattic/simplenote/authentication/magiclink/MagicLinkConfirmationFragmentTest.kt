package com.automattic.simplenote.authentication.magiclink

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.automattic.simplenote.HiltTestActivity
import com.automattic.simplenote.R
import com.automattic.simplenote.screenshottest.AppStyle
import com.automattic.simplenote.screenshottest.ScreenshotHarness
import com.automattic.simplenote.utils.SimplenoteProgressDialogFragment
import com.automattic.simplenote.viewmodels.CompleteMagicLinkViewModel
import com.automattic.simplenote.viewmodels.MagicLinkUiState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The progress dialog is retained across a configuration change while the recreated confirmation fragment
 * replays the view model's Loading state, so the fragment has to adopt the retained dialog instead of
 * stacking a second one that hides the first from its terminal-state dismissal.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class MagicLinkConfirmationFragmentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<HiltTestActivity>

    @Before
    fun setUp() {
        scenario = ScreenshotHarness.launchSettled(HiltTestActivity::class.java, AppStyle.DEFAULT, darkMode = false)
        scenario.onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, MagicLinkConfirmationFragment.newInstance(EMAIL), FRAGMENT_TAG)
                .commitNow()
        }
        setState(MagicLinkUiState.Loading(R.string.magic_link_complete_login_loading_message))
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun recreationDuringLoadingKeepsASingleProgressDialog() {
        scenario.recreate()
        ScreenshotHarness.settle()

        scenario.onActivity { assertEquals(1, progressDialogs(it.supportFragmentManager).size) }
    }

    @Test
    fun errorAfterRecreationDismissesTheRetainedProgressDialog() {
        scenario.recreate()
        ScreenshotHarness.settle()

        setState(MagicLinkUiState.Error(messageRes = R.string.magic_link_general_error))

        scenario.onActivity { assertTrue(progressDialogs(it.supportFragmentManager).isEmpty()) }
    }

    private fun setState(state: MagicLinkUiState) {
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)!!
            val viewModel = ViewModelProvider(fragment)[CompleteMagicLinkViewModel::class.java]
            uiState(viewModel).value = state
        }
        ScreenshotHarness.settle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun uiState(viewModel: CompleteMagicLinkViewModel): MutableLiveData<MagicLinkUiState> =
        CompleteMagicLinkViewModel::class.java.getDeclaredField("_magicLinkUiState")
            .apply { isAccessible = true }
            .get(viewModel) as MutableLiveData<MagicLinkUiState>

    private fun progressDialogs(fragmentManager: FragmentManager) =
        fragmentManager.fragments.filterIsInstance<SimplenoteProgressDialogFragment>()

    private companion object {
        const val EMAIL = "user@example.com"
        const val FRAGMENT_TAG = "magic_link_confirmation"
    }
}
