package com.automattic.simplenote

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.utils.TagUtils
import com.automattic.simplenote.utils.TestBucket
import com.automattic.simplenote.utils.getRandomStringOfLen
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import dagger.hilt.android.testing.HiltAndroidRule
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.wordpress.passcodelock.AppLockManager
import org.wordpress.passcodelock.DefaultAppLock

/**
 * Base for the Hilt UI tests. Subclasses must be annotated with @HiltAndroidTest so
 * [TestDataModule] supplies the in-memory buckets this class clears between tests.
 */
open class BaseUITest {
    @JvmField
    @Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var injectedTagsBucket: Bucket<Tag>

    @Inject
    lateinit var injectedNotesBucket: Bucket<Note>

    protected lateinit var tagsBucket: TestBucket<Tag>
    protected lateinit var notesBucket: TestBucket<Note>

    @Before
    fun setup() {
        hiltRule.inject()

        // Some activities under test call DisplayUtils.disableScreenshotsIfLocked from onResume,
        // which requires an installed app lock. The production Simplenote.onCreate installs one,
        // but these tests run on HiltTestApplication.
        val application = ApplicationProvider.getApplicationContext<Application>()
        AppLockManager.getInstance().setCurrentAppLock(DefaultAppLock(application))

        tagsBucket = (injectedTagsBucket as TestBucket<Tag>).apply {
            clear()
            newObjectShouldFail = false
        }

        notesBucket = (injectedNotesBucket as TestBucket<Note>).apply {
            clear()
            newObjectShouldFail = false
        }
    }

    protected fun getResourceString(id: Int): String {
        val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return targetContext.resources.getString(id)
    }

    protected fun getResourceStringWithArgs(id: Int, vararg args: Any): String {
        val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return targetContext.resources.getString(id, *args)
    }

    protected fun getRandomString(len: Int): String {
        return getRandomStringOfLen(len)
    }

    protected fun createTag(tagName: String): Tag {
        TagUtils.createTagIfMissing(tagsBucket, tagName)
        return tagsBucket.getObject(TagUtils.hashTag(tagName))
    }

    protected fun createNote(content: String, tags: List<String>): Note {
        val note = notesBucket.newObject(content.hashCode().toString())
        note.content = content
        note.tags = tags

        return note
    }

    protected fun getTag(tagName: String): Tag? {
        val hashName = TagUtils.hashTag(tagName)
        return try {
            tagsBucket.getObject(hashName)
        } catch (b: BucketObjectMissingException) {
            null
        }
    }
}
