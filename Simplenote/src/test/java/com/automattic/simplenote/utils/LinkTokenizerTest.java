package com.automattic.simplenote.utils;

import android.app.Application;
import android.text.NoCopySpan;
import android.text.SpannableString;
import android.text.Spanned;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class LinkTokenizerTest {
    private final LinkTokenizer tokenizer = new LinkTokenizer();

    @Test
    public void terminateTokenEscapesMarkdownLabelBrackets() {
        assertEquals(
            "safe\\](https://evil.example)\\[spoof]",
            tokenizer.terminateToken("safe](https://evil.example)[spoof").toString()
        );
    }

    @Test
    public void terminateTokenEscapesBackslashes() {
        assertEquals("Back\\\\slash]", tokenizer.terminateToken("Back\\slash").toString());
    }

    @Test
    public void terminateTokenPreservesPlainTitle() {
        assertEquals("Roadmap]", tokenizer.terminateToken("Roadmap").toString());
    }

    @Test
    public void terminateTokenPreservesNullAndEmptyTitles() {
        assertEquals("null]", tokenizer.terminateToken(null));
        assertEquals("]", tokenizer.terminateToken(""));
    }

    @Test
    public void terminateTokenPreservesSpansBeforeClosingBracket() {
        Object span = new NoCopySpan.Concrete();
        SpannableString title = new SpannableString("A]B");
        title.setSpan(span, 0, title.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        SpannableString token = (SpannableString) tokenizer.terminateToken(title);

        assertEquals("A\\]B]", token.toString());
        assertEquals(0, token.getSpanStart(span));
        assertEquals(token.length() - 1, token.getSpanEnd(span));
        assertEquals(Spanned.SPAN_INCLUSIVE_INCLUSIVE, token.getSpanFlags(span));
    }
}
