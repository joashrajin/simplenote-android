package com.automattic.simplenote.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.NumberFormat

class NoteUtilsTest {
    @Test
    fun wordCountDoesNotCountLeadingSeparatorAsAWord() {
        assertEquals(NumberFormat.getInstance().format(2), NoteUtils.getWordCount("# Heading\nbody"))
    }

    @Test
    fun wordCountPreservesExistingTokenRules() {
        assertEquals(NumberFormat.getInstance().format(4), NoteUtils.getWordCount("one, two-two 123"))
        assertEquals(NumberFormat.getInstance().format(0), NoteUtils.getWordCount("###"))
        assertEquals(NumberFormat.getInstance().format(0), NoteUtils.getWordCount(" \n\t"))
    }

    @Test
    fun contentWithoutTitlePreservesMatchingBodyText() {
        val title = "Plan [v1]"
        val content = "$title\nKeep $title here\n$title"

        assertEquals(
            "Keep Plan [v1] here\nPlan [v1]",
            NoteUtils.getContentWithoutTitle(content, title)
        )
    }

    @Test
    fun contentWithoutTitleReturnsBody() {
        assertEquals("Body", NoteUtils.getContentWithoutTitle("Title\nBody", "Title"))
    }

    @Test
    fun contentWithoutTitleReturnsEmptyForSingleLineNote() {
        assertEquals("", NoteUtils.getContentWithoutTitle("Title", "Title"))
    }

    @Test
    fun contentWithoutTitlePreservesWhitespaceAndCrLfHandling() {
        assertEquals("Body", NoteUtils.getContentWithoutTitle("  Title  \r\nBody", "Title"))
    }

    @Test
    fun contentWithoutTitleMatchesLiterallyForRegexMetacharacterTitles() {
        val title = "Budget (2026"
        val content = "$title\nSpent $title already\n$title"

        assertEquals(
            "Spent Budget (2026 already\nBudget (2026",
            NoteUtils.getContentWithoutTitle(content, title)
        )
    }

    @Test
    fun contentWithoutTitleFallsBackToFirstLineStripForAbsentTitle() {
        assertEquals("Body", NoteUtils.getContentWithoutTitle("Header\nBody", "Missing"))
    }
}
