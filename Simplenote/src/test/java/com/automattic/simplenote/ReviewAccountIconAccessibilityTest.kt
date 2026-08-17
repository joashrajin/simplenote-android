package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class ReviewAccountIconAccessibilityTest {
    @Test
    fun emailStateExcludesDecorativeIconWhileTextRemainsAvailable() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Style_Default)
        controller.setup()

        try {
            val root = LayoutInflater.from(activity)
                .inflate(R.layout.fragment_review_account_verify_email, null) as ViewGroup
            val image = root.findViewById<ImageView>(R.id.image)
            val title = root.findViewById<TextView>(R.id.text_title)
            val subtitle = root.findViewById<TextView>(R.id.text_subtitle)

            image.contentDescription = activity.getString(R.string.description_warning)
            image.setImageResource(R.drawable.ic_mail_24dp)
            title.setText(R.string.fullscreen_verify_email_title)
            subtitle.text = String.format(
                activity.getString(R.string.fullscreen_verify_email_subtitle),
                "person@example.com"
            )
            activity.setContentView(root)

            val accessibleChildren = arrayListOf<View>()
            (image.parent as ViewGroup).addChildrenForAccessibility(accessibleChildren)

            assertEquals(activity.getString(R.string.description_warning), image.contentDescription)
            assertFalse(accessibleChildren.contains(image))
            assertTrue(accessibleChildren.contains(title))
            assertTrue(accessibleChildren.contains(subtitle))
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, image.importantForAccessibility)
            assertFalse(image.isImportantForAccessibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
