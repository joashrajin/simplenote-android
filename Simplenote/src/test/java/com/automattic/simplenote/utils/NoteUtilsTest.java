package com.automattic.simplenote.utils;

import org.junit.Test;

import java.text.NumberFormat;

import static org.junit.Assert.assertEquals;

public class NoteUtilsTest {
    @Test
    public void wordCountDoesNotCountLeadingSeparatorAsAWord() {
        assertEquals(NumberFormat.getInstance().format(2), NoteUtils.getWordCount("# Heading\nbody"));
    }

    @Test
    public void wordCountPreservesExistingTokenRules() {
        assertEquals(NumberFormat.getInstance().format(4), NoteUtils.getWordCount("one, two-two 123"));
        assertEquals(NumberFormat.getInstance().format(0), NoteUtils.getWordCount("###"));
        assertEquals(NumberFormat.getInstance().format(0), NoteUtils.getWordCount(" \n\t"));
    }

    @Test
    public void contentWithoutTitlePreservesMatchingBodyText() {
        String title = "Plan [v1]";
        String content = title + "\nKeep " + title + " here\n" + title;

        assertEquals(
            "Keep Plan [v1] here\nPlan [v1]",
            NoteUtils.getContentWithoutTitle(content, title)
        );
    }

    @Test
    public void contentWithoutTitleReturnsBody() {
        assertEquals("Body", NoteUtils.getContentWithoutTitle("Title\nBody", "Title"));
    }

    @Test
    public void contentWithoutTitleReturnsEmptyForSingleLineNote() {
        assertEquals("", NoteUtils.getContentWithoutTitle("Title", "Title"));
    }

    @Test
    public void contentWithoutTitlePreservesWhitespaceAndCrLfHandling() {
        assertEquals("Body", NoteUtils.getContentWithoutTitle("  Title  \r\nBody", "Title"));
    }

    @Test
    public void contentWithoutTitleMatchesLiterallyForRegexMetacharacterTitles() {
        String title = "Budget (2026";
        String content = title + "\nSpent " + title + " already\n" + title;

        assertEquals(
            "Spent Budget (2026 already\nBudget (2026",
            NoteUtils.getContentWithoutTitle(content, title)
        );
    }

    @Test
    public void contentWithoutTitleFallsBackToFirstLineStripForAbsentTitle() {
        assertEquals("Body", NoteUtils.getContentWithoutTitle("Header\nBody", "Missing"));
    }
}
