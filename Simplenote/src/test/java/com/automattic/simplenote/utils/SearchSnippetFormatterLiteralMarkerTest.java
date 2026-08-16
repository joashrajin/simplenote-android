package com.automattic.simplenote.utils;

import android.app.Application;
import android.text.Spannable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SearchSnippetFormatterLiteralMarkerTest {
    @Test
    public void trailingLiteralOpeningMarkerIsPreservedAfterGeneratedMatch() {
        Object highlight = new Object();
        String snippet = "<match>needle</match> prefix <match> suffix";

        Spannable formatted = SearchSnippetFormatter.formatString(
            null,
            snippet,
            content -> new Object[]{highlight},
            0
        );

        assertEquals("needle prefix <match> suffix", formatted.toString());
        assertEquals(0, formatted.getSpanStart(highlight));
        assertEquals(6, formatted.getSpanEnd(highlight));
    }

    @Test
    public void balancedMatchMarkersStillHighlightContent() {
        Object highlight = new Object();

        Spannable formatted = SearchSnippetFormatter.formatString(
            null,
            "prefix <match>needle</match> suffix",
            content -> new Object[]{highlight},
            0
        );

        assertEquals("prefix needle suffix", formatted.toString());
        assertEquals(7, formatted.getSpanStart(highlight));
        assertEquals(13, formatted.getSpanEnd(highlight));
    }
}
