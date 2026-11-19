package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReviewAccountIconAccessibilityTest {
    @Test
    fun statusIconStaysDecorativeWhenTheFragmentDescribesIt() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Default)
        val layout = LayoutInflater.from(context).inflate(R.layout.fragment_review_account_verify_email, null)
        val image = layout.findViewById<ImageView>(R.id.image)

        // ReviewAccountVerifyEmailFragment still sets a description at runtime.
        image.contentDescription = context.getString(R.string.description_mail)

        assertFalse(image.isImportantForAccessibility)
    }
}
