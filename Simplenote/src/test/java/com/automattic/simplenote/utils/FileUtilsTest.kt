package com.automattic.simplenote.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class FileUtilsTest {
    private lateinit var contentResolver: ContentResolver
    private lateinit var context: Context
    private lateinit var uri: Uri

    @Before
    fun setUp() {
        contentResolver = mock()
        context = mock()
        uri = mock()
        whenever(context.contentResolver).thenReturn(contentResolver)
    }

    @Test
    fun readFilePreservesLinesAndClosesTheStream() {
        val inputStream = TrackingInputStream("first\nsecond".toByteArray())
        whenever(contentResolver.openInputStream(uri)).thenReturn(inputStream)

        assertEquals("first\nsecond\n", FileUtils.readFile(context, uri))
        assertEquals(1, inputStream.closeCount)
    }

    @Test
    fun readFileClosesTheStreamWhenReadingFails() {
        val readFailure = IOException("read failed")
        val inputStream = TrackingInputStream(readFailure = readFailure)
        whenever(contentResolver.openInputStream(uri)).thenReturn(inputStream)

        val thrown = assertThrows(IOException::class.java) {
            FileUtils.readFile(context, uri)
        }

        assertSame(readFailure, thrown)
        assertEquals(1, inputStream.closeCount)
    }

    @Test
    fun readFilePreservesReadFailureWhenCloseAlsoFails() {
        val readFailure = IOException("read failed")
        val closeFailure = IOException("close failed")
        val inputStream = TrackingInputStream(readFailure = readFailure, closeFailure = closeFailure)
        whenever(contentResolver.openInputStream(uri)).thenReturn(inputStream)

        val thrown = assertThrows(IOException::class.java) {
            FileUtils.readFile(context, uri)
        }

        assertSame(readFailure, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertSame(closeFailure, thrown.suppressed.single())
        assertEquals(1, inputStream.closeCount)
    }

    @Test
    fun readFileReportsANullProviderStreamAsFileNotFound() {
        whenever(contentResolver.openInputStream(uri)).thenReturn(null)

        assertThrows(FileNotFoundException::class.java) {
            FileUtils.readFile(context, uri)
        }
    }

    private class TrackingInputStream(
        private val bytes: ByteArray = byteArrayOf(),
        private val readFailure: IOException? = null,
        private val closeFailure: IOException? = null,
    ) : InputStream() {
        var closeCount = 0
            private set
        private var index = 0

        override fun read(): Int {
            readFailure?.let { throw it }
            return if (index < bytes.size) bytes[index++].toInt() and 0xff else -1
        }

        override fun close() {
            closeCount++
            closeFailure?.let { throw it }
        }
    }
}
