package com.automattic.simplenote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMarkdownFragmentTest {
    @Test
    fun markdownContentDoesNotInjectRemoteStylesheets() {
        val css = "<style>body { color: #123456; }</style>"
        val html = NoteMarkdownFragment.getMarkdownFormattedContent(css, "# Local heading")
        val remoteStylesheet = Regex("""<link\b[^>]*href=["']https?://""", RegexOption.IGNORE_CASE)

        assertFalse(
            "Generated preview HTML must not inject remote stylesheet links",
            remoteStylesheet.containsMatchIn(html)
        )
        assertTrue(html.contains("<meta name=\"viewport\""))
        assertTrue(html.contains(css))
        assertTrue(html.contains("<h1>Local heading</h1>"))
    }
}
