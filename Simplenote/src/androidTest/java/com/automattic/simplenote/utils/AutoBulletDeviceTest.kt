package com.automattic.simplenote.utils

import android.text.SpannableStringBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ART's regex is ICU, where `\s` includes U+00A0; the JVM's does not. AutoBulletChecklistTest pins the JVM side of
 * the same input, so this pins what users actually get.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class AutoBulletDeviceTest {
    @Test
    fun noBreakSpaceAfterBulletIsAnEmptyBulletOnArt() {
        val editable = SpannableStringBuilder("- \u00a0\n")

        AutoBullet.apply(editable, 3, 4)

        assertEquals("", editable.toString())
    }
}
