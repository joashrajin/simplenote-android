package com.automattic.simplenote

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.FileNotFoundException

class PreferencesFragmentExportTest {
    private val uri = mock<Uri>()
    private val descriptor = mock<ParcelFileDescriptor>()

    @Test
    fun opensWithTruncateWhenTheProviderSupportsIt() {
        val resolver = mock<ContentResolver> {
            on { openFileDescriptor(uri, "wt") } doReturn descriptor
        }

        assertSame(descriptor, PreferencesFragment.openForOverwrite(resolver, uri))
        verify(resolver, never()).openFileDescriptor(uri, "w")
    }

    @Test
    fun fallsBackToWriteWhenTheProviderRejectsTruncate() {
        listOf(
            FileNotFoundException("wt"),
            IllegalArgumentException("wt"),
            UnsupportedOperationException("wt"),
        ).forEach { rejection ->
            val resolver = mock<ContentResolver> {
                on { openFileDescriptor(uri, "wt") } doThrow rejection
                on { openFileDescriptor(uri, "w") } doReturn descriptor
            }

            assertSame(descriptor, PreferencesFragment.openForOverwrite(resolver, uri))
        }
    }
}
