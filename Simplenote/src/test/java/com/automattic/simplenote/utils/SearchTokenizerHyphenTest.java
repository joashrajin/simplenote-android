package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SearchTokenizerHyphenTest {

    @Test
    public void testMultipleHyphensUseSinglePhrase() {
        assertEquals("\"state-of-the-art*\"", new SearchTokenizer("state-of-the-art").toString());
    }

    @Test
    public void testNumericMultipleHyphensUseSinglePhrase() {
        assertEquals("\"2026-08-14*\"", new SearchTokenizer("2026-08-14").toString());
    }

    @Test
    public void testPhraseStateResetsBetweenTerms() {
        assertEquals("\"a-b-c*\" \"d-e-f*\"", new SearchTokenizer("a-b-c d-e-f").toString());
    }

    @Test
    public void testSingleHyphenRetainsPhraseQuery() {
        assertEquals("\"16-3*\"", new SearchTokenizer("16-3").toString());
    }

    @Test
    public void testLeadingHyphenRetainsExclusion() {
        assertEquals("16* -3*", new SearchTokenizer("16 -3").toString());
    }

    @Test
    public void testQuotedMultipleHyphensRemainUnchanged() {
        assertEquals("\"state-of-the-art\"", new SearchTokenizer("\"state-of-the-art\"").toString());
    }
}
