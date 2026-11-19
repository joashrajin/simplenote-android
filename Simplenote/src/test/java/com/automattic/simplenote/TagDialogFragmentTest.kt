package com.automattic.simplenote

import android.view.WindowManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.screenshottest.AppStyle
import com.automattic.simplenote.screenshottest.ScreenshotHarness
import com.google.android.material.textfield.TextInputLayout
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.listeners.MockCreationListener
import org.mockito.mock.MockCreationSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the rename dialog across activity recreation: the restored fragment looks its tag up by key in the
 * injected tags bucket, shows the tag's name in a focused field with the keyboard requested, and dismisses
 * itself when the tag is no longer in the bucket.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [23, 35])
class TagDialogFragmentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val tag = Tag(TAG_KEY).apply { setName("Recipes") }
    private var tagInBucket = true
    private lateinit var scenario: ActivityScenario<TagsActivity>

    // ScreenshotDataModule provides Bucket<Tag> as an unscoped mock, so every injection gets a fresh one.
    private val bucketStubber = object : MockCreationListener {
        override fun onMockCreated(mock: Any, settings: MockCreationSettings<*>) {
            if (mock !is Bucket<*>) return
            Mockito.doAnswer { invocation ->
                if (invocation.getArgument<String>(0) == TAG_KEY && tagInBucket) {
                    tag
                } else {
                    throw BucketObjectMissingException()
                }
            }.`when`(mock).getObject(Mockito.anyString())
        }
    }

    @Before
    fun setUp() {
        Mockito.framework().addListener(bucketStubber)
        scenario = ScreenshotHarness.launchSettled(TagsActivity::class.java, AppStyle.DEFAULT, darkMode = false)
        scenario.onActivity {
            TagDialogFragment(tag).show(it.supportFragmentManager, TagDialogFragment.DIALOG_TAG)
        }
        ScreenshotHarness.settle()
    }

    @After
    fun tearDown() {
        scenario.close()
        Mockito.framework().removeListener(bucketStubber)
    }

    @Test
    fun opensWithTheTagNameInAFocusedFieldAndRequestsTheKeyboard() {
        scenario.onActivity { activity ->
            val fragment = dialogFragment(activity)!!
            val dialog = fragment.requireDialog()
            val field = nameField(fragment)

            assertTrue(dialog.isShowing)
            assertEquals("Recipes", field.text.toString())
            assertTrue(field.hasFocus())
            assertEquals(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
                dialog.window!!.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE,
            )
        }
    }

    @Test
    fun recreatedDialogLooksTheTagUpByKeyAndShowsItsName() {
        var original: TagDialogFragment? = null
        scenario.onActivity { original = dialogFragment(it) }

        scenario.recreate()
        ScreenshotHarness.settle()

        scenario.onActivity { activity ->
            val restored = dialogFragment(activity)!!
            assertNotSame(original, restored)
            assertTrue(restored.requireDialog().isShowing)
            assertEquals("Recipes", nameField(restored).text.toString())
            assertTrue(nameField(restored).hasFocus())
        }
    }

    @Test
    fun recreatedDialogDismissesItselfWhenTheTagIsGone() {
        tagInBucket = false

        scenario.recreate()
        ScreenshotHarness.settle()

        scenario.onActivity { assertNull(dialogFragment(it)) }
    }

    private fun dialogFragment(activity: TagsActivity) =
        activity.supportFragmentManager.findFragmentByTag(TagDialogFragment.DIALOG_TAG) as TagDialogFragment?

    private fun nameField(fragment: TagDialogFragment): EditText =
        fragment.requireDialog().findViewById<TextInputLayout>(R.id.input_tag_name).editText!!

    private companion object {
        const val TAG_KEY = "recipes"
    }
}
