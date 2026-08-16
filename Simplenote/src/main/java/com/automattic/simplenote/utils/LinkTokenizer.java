package com.automattic.simplenote.utils;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.MultiAutoCompleteTextView;

public class LinkTokenizer implements MultiAutoCompleteTextView.Tokenizer {
    private static final Character CHARACTER_BACKSLASH = '\\';
    private static final Character CHARACTER_BRACKET_CLOSE = ']';
    private static final Character CHARACTER_BRACKET_OPEN = '[';

    @Override
    public int findTokenEnd(CharSequence text, int cursor) {
        int i = cursor;
        int length = text.length();

        while (i < length) {
            if (text.charAt(i) == CHARACTER_BRACKET_CLOSE) {
                return i;
            } else {
                i++;
            }
        }

        return length;
    }

    @Override
    public int findTokenStart(CharSequence text, int cursor) {
        int i = cursor;

        while (i > 0 && text.charAt(i - 1) != CHARACTER_BRACKET_OPEN) {
            i--;
        }

        if (i < 1 || text.charAt(i - 1) != CHARACTER_BRACKET_OPEN) {
            return cursor;
        }

        return i;
    }

    @Override
    public CharSequence terminateToken(CharSequence text) {
        String sourceText = String.valueOf(text);
        SpannableStringBuilder escapedText = new SpannableStringBuilder(sourceText);
        if (text instanceof Spanned) {
            TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, escapedText, 0);
        }
        for (int i = sourceText.length() - 1; i >= 0; i--) {
            char character = sourceText.charAt(i);
            if (character == CHARACTER_BACKSLASH || character == CHARACTER_BRACKET_OPEN ||
                    character == CHARACTER_BRACKET_CLOSE) {
                escapedText.insert(i, CHARACTER_BACKSLASH.toString());
            }
        }
        String terminatedText = escapedText.toString() + CHARACTER_BRACKET_CLOSE;

        if (text instanceof Spanned) {
            SpannableString spannableString = new SpannableString(terminatedText);
            TextUtils.copySpansFrom(escapedText, 0, escapedText.length(), Object.class, spannableString, 0);
            return spannableString;
        } else {
            return terminatedText;
        }
    }
}
