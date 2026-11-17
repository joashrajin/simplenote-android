package com.automattic.simplenote.utils

import android.app.Application
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the tag-input tokenizer: only the ASCII space separates tokens (commas, tabs and newlines
 * are part of a token), and terminateToken always appends one space, even to text that already
 * ends in spaces, copying spans when the input is Spanned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SpaceTokenizerTest {
    private val tokenizer = SpaceTokenizer()

    @Test
    fun findTokenStartReturnsZeroForFirstToken() {
        assertEquals(0, tokenizer.findTokenStart("foo bar", 3))
        assertEquals(0, tokenizer.findTokenStart("foo bar", 1))
    }

    @Test
    fun findTokenStartReturnsIndexAfterPrecedingSpace() {
        assertEquals(4, tokenizer.findTokenStart("foo bar", 7))
        assertEquals(4, tokenizer.findTokenStart("foo bar", 5))
    }

    @Test
    fun findTokenStartAtCursorZeroIsZero() {
        assertEquals(0, tokenizer.findTokenStart("foo", 0))
        assertEquals(0, tokenizer.findTokenStart("", 0))
    }

    @Test
    fun findTokenStartDirectlyAfterSpaceIsCursor() {
        assertEquals(4, tokenizer.findTokenStart("foo ", 4))
        assertEquals(5, tokenizer.findTokenStart("foo  bar", 5))
        assertEquals(4, tokenizer.findTokenStart("foo  bar", 4))
        assertEquals(3, tokenizer.findTokenStart("   ", 3))
    }

    @Test
    fun findTokenStartSkipsLeadingSpaces() {
        assertEquals(2, tokenizer.findTokenStart("  foo", 5))
    }

    @Test
    fun findTokenStartTreatsPunctuationAndOtherWhitespaceAsTokenCharacters() {
        assertEquals(0, tokenizer.findTokenStart("a,b", 3))
        assertEquals(0, tokenizer.findTokenStart("a\tb", 3))
        assertEquals(0, tokenizer.findTokenStart("a\nb", 3))
        assertEquals(2, tokenizer.findTokenStart("x #tag,two", 10))
    }

    @Test
    fun findTokenEndStopsAtNextSpace() {
        assertEquals(3, tokenizer.findTokenEnd("foo bar", 0))
        assertEquals(3, tokenizer.findTokenEnd("foo bar", 3))
        assertEquals(7, tokenizer.findTokenEnd("foo bar", 4))
    }

    @Test
    fun findTokenEndReturnsLengthWithoutTrailingSpace() {
        assertEquals(3, tokenizer.findTokenEnd("foo", 0))
        assertEquals(0, tokenizer.findTokenEnd("", 0))
    }

    @Test
    fun findTokenEndReturnsLengthWhenCursorIsPastEnd() {
        assertEquals(3, tokenizer.findTokenEnd("foo", 5))
    }

    @Test
    fun findTokenEndTreatsPunctuationAndOtherWhitespaceAsTokenCharacters() {
        assertEquals(3, tokenizer.findTokenEnd("a,b", 0))
        assertEquals(3, tokenizer.findTokenEnd("a\tb", 0))
        assertEquals(3, tokenizer.findTokenEnd("a\nb", 0))
    }

    @Test
    fun terminateTokenAppendsSpaceToPlainText() {
        val result = tokenizer.terminateToken("foo")

        assertEquals("foo ", result.toString())
        assertTrue(result is String)
    }

    @Test
    fun terminateTokenAppendsSpaceEvenWhenTextEndsInSpaces() {
        assertEquals("foo  ", tokenizer.terminateToken("foo ").toString())
        assertEquals("    ", tokenizer.terminateToken("   ").toString())
    }

    @Test
    fun terminateTokenOnEmptyTextIsSingleSpace() {
        assertEquals(" ", tokenizer.terminateToken("").toString())
    }

    @Test
    fun terminateTokenCopiesSpansFromSpannedText() {
        val span = StyleSpan(Typeface.BOLD)
        val input = SpannableString("foo")
        input.setSpan(span, 0, 3, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)

        val result = tokenizer.terminateToken(input)

        assertTrue(result is SpannableString)
        assertEquals("foo ", result.toString())
        val spanned = result as Spanned
        val spans = spanned.getSpans(0, spanned.length, StyleSpan::class.java)
        assertEquals(1, spans.size)
        assertSame(span, spans[0])
        assertEquals(0, spanned.getSpanStart(span))
        assertEquals(3, spanned.getSpanEnd(span))
        assertEquals(Spanned.SPAN_EXCLUSIVE_INCLUSIVE, spanned.getSpanFlags(span))
    }

    @Test
    fun terminateTokenOnSpannedWithoutSpansReturnsNewSpannable() {
        val input = SpannableString("foo")

        val result = tokenizer.terminateToken(input)

        assertTrue(result is SpannableString)
        assertFalse(result === input)
        assertEquals("foo ", result.toString())
    }
}
