package com.automattic.simplenote.utils

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SearchTokenizerTest {

    @Test
    fun testPerformsLiteralQuery() {
        val tokenizer = SearchTokenizer("/hello world")

        assertThat(tokenizer.toString(), `is`("hello world"))
    }

    @Test
    fun testFixesMalformedLiteralQuery() {
        val tokenizer = SearchTokenizer("/\"hello\" \"world")

        assertThat(tokenizer.toString(), `is`("\"hello\" \"world\""))
    }

    @Test
    fun testDetectsTokensAndAddsGlobs() {
        val tokenizer = SearchTokenizer("hello world")

        assertThat(tokenizer.toString(), `is`("hello* world*"))
    }

    @Test
    fun testDetectsStrictSearchStrings() {
        val tokenizer = SearchTokenizer("\"hello world\" lorem")

        assertThat(tokenizer.toString(), `is`("\"hello world\" lorem*"))
    }

    @Test
    fun testDetectsFieldScopes() {
        val tokenizer = SearchTokenizer("tag: something")

        assertThat(tokenizer.toString(), `is`("tag: something*"))
    }

    @Test
    fun testDetectsEscapedQuotes() {
        var tokenizer = SearchTokenizer("this is \\\" an escaped quote")
        assertThat(tokenizer.toString(), `is`("this* is* \\\"* an* escaped* quote*"))

        tokenizer = SearchTokenizer("this is \\' an escaped quote")
        assertThat(tokenizer.toString(), `is`("this* is* \\'* an* escaped* quote*"))
    }

    @Test
    fun testMixedQuotes() {
        var tokenizer = SearchTokenizer("quote in 'a \" quote")
        assertThat(tokenizer.toString(), `is`("quote* in* 'a \" quote'"))

        tokenizer = SearchTokenizer("quote in \"a ' quote\"")
        assertThat(tokenizer.toString(), `is`("quote* in* \"a ' quote\""))
    }
}
