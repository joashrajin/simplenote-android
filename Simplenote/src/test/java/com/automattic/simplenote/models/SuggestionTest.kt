package com.automattic.simplenote.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionTest {
    @Test
    fun typeConstantsAreSwitchableCompileTimeConstants() {
        assertEquals("history", label(Suggestion.Type.HISTORY))
        assertEquals("query", label(Suggestion.Type.QUERY))
        assertEquals("tag", label(Suggestion.Type.TAG))
    }

    @Test
    fun typeConstantValues() {
        assertEquals(0, Suggestion.Type.HISTORY)
        assertEquals(1, Suggestion.Type.QUERY)
        assertEquals(2, Suggestion.Type.TAG)
    }

    @Test
    fun constructorSetsNameAndType() {
        val suggestion = Suggestion("groceries", Suggestion.Type.TAG)

        assertEquals("groceries", suggestion.name)
        assertEquals(Suggestion.Type.TAG, suggestion.type)
    }

    @Test
    fun nameAcceptsNull() {
        val suggestion = Suggestion(null, Suggestion.Type.QUERY)
        assertNull(suggestion.name)
    }

    private fun label(@Suggestion.Type type: Int): String {
        return when (type) {
            Suggestion.Type.HISTORY -> "history"
            Suggestion.Type.QUERY -> "query"
            Suggestion.Type.TAG -> "tag"
            else -> "unknown"
        }
    }
}
