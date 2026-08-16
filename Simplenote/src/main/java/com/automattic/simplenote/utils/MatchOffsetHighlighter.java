package com.automattic.simplenote.utils;

import android.os.Handler;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;

import com.automattic.simplenote.widgets.SimplenoteEditText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchOffsetHighlighter implements Runnable {
    public  static final int MATCH_INDEX_COUNT = 4;
    public  static final int MATCH_INDEX_START = 2;
    private static final Pattern CHECKLIST_PATTERN = Pattern.compile(
            ChecklistUtils.CHECKLIST_REGEX_LINES,
            Pattern.MULTILINE
    );
    private static final int CHECKLIST_REPLACEMENT_BYTE_LENGTH = String
            .valueOf(ChecklistUtils.CHAR_NO_BREAK_SPACE)
            .getBytes(StandardCharsets.UTF_8)
            .length;

    protected static OnMatchListener sListener = new DefaultMatcher();
    private static List<Object> mMatchedSpans = Collections.synchronizedList(new ArrayList<>());
    private SpanFactory mFactory;
    private Thread mThread;
    private SimplenoteEditText mTextView;
    private String mMatches;
    private int mIndex;
    private Spannable mSpannable;
    private String mPlainText;
    private boolean mStopped = false;
    private OnMatchListener mListener = new OnMatchListener() {

        @Override
        public void onMatch(final SpanFactory factory, final Spannable text, final int start, final int end) {

            if (mTextView == null) return;

            Handler handler = mTextView.getHandler();
            if (handler == null) return;

            handler.post(new Runnable() {

                @Override
                public void run() {
                    if (mStopped) return;
                    sListener.onMatch(factory, text, start, end);
                }

            });
        }

    };

    public MatchOffsetHighlighter(SpanFactory factory, SimplenoteEditText textView) {
        mFactory = factory;
        mTextView = textView;
    }

    public static void highlightMatches(Spannable content, String matches, String plainTextContent,
                                        int columnIndex, SpanFactory factory) {

        highlightMatches(content, matches, plainTextContent, columnIndex, factory, new DefaultMatcher());
    }

    public static void highlightMatches(Spannable content, String matches, String plainTextContent,
                                        int columnIndex, SpanFactory factory, OnMatchListener listener) {

        if (TextUtils.isEmpty(matches)) return;

        Scanner scanner = new Scanner(matches);
        byte[] plainTextBytes = plainTextContent.getBytes(StandardCharsets.UTF_8);

        // TODO: keep track of offsets and last index so we don't have to recalculate the entire byte length for every match which is pretty memory intensive
        while (scanner.hasNext()) {

            if (Thread.interrupted()) return;

            int column = scanner.nextInt();
            scanner.nextInt(); // token
            int start = scanner.nextInt();
            int length = scanner.nextInt();
            int end = start + length;

            if (column != columnIndex) {
                continue;
            }

            if (start < 0 || plainTextBytes.length < start) {
                continue;
            }

            int checklistOffset = getChecklistByteOffset(plainTextContent, plainTextBytes, start);
            start -= checklistOffset;
            end -= checklistOffset;

            int span_start = start + getByteOffset(content, 0, start);
            int span_end = span_start + length + getByteOffset(content, start, end);

            if (Thread.interrupted()) return;

            listener.onMatch(factory, content, span_start, span_end);

        }
    }

    // Returns the character location of the first match (3rd index, the 'start' value)
    // The data format for a match is 4 space-separated integers that represent the location
    // of the match: "column token start length" ex: "1 0 42 7"
    public static int getFirstMatchLocation(Spannable content, String matches) {
        int location = getFirstMatchByteLocation(matches);
        return location + getByteOffset(content, 0, location);
    }

    public static int getFirstMatchLocation(Spannable content, String matches, String indexedTextContent) {
        return getCharacterLocation(content, indexedTextContent, getFirstMatchByteLocation(matches));
    }

    private static int getFirstMatchByteLocation(String matches) {
        if (TextUtils.isEmpty(matches)) {
            return 0;
        }

        String[] values = matches.split("\\s+", 4);
        if (values.length > MATCH_INDEX_START) {
            try {
                return Integer.valueOf(values[MATCH_INDEX_START]);
            } catch (NumberFormatException exception) {
                return 0;
            }
        }

        return 0;
    }

    public static int getCharacterLocation(Spannable content, String indexedTextContent, int byteLocation) {
        if (indexedTextContent == null) {
            return 0;
        }

        byte[] indexedTextBytes = indexedTextContent.getBytes(StandardCharsets.UTF_8);
        if (byteLocation < 0 || byteLocation > indexedTextBytes.length) {
            return 0;
        }

        int displayByteLocation = byteLocation
                - getChecklistByteOffset(indexedTextContent, indexedTextBytes, byteLocation);
        if (displayByteLocation < 0) {
            return 0;
        }

        int characterLocation = displayByteLocation + getByteOffset(content, 0, displayByteLocation);
        return characterLocation <= content.length() ? characterLocation : 0;
    }

    private static int getChecklistByteOffset(String plainTextContent, byte[] plainTextBytes, int byteLocation) {
        int characterLocation = new String(
                plainTextBytes,
                0,
                byteLocation,
                StandardCharsets.UTF_8
        ).length();
        String textUpToMatch = plainTextContent.substring(0, characterLocation);
        Matcher matcher = CHECKLIST_PATTERN.matcher(textUpToMatch);
        int checklistOffset = 0;
        while (matcher.find()) {
            String checklist = matcher.group(2);
            if (checklist != null) {
                checklistOffset += checklist.getBytes(StandardCharsets.UTF_8).length
                        - CHECKLIST_REPLACEMENT_BYTE_LENGTH;
            }
        }
        return checklistOffset;
    }

    // Returns the byte offset of the source string up to the matching search result.
    // Note: We need to convert the source string to a byte[] because SQLite provides
    // indices and lengths in bytes. See: https://www.sqlite.org/fts3.html#offsets
    protected static int getByteOffset(Spannable text, int start, int end) {
        String source = text.toString();
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);

        int length = sourceBytes.length;

        // starting index cannot be negative
        if (start < 0) {
            start = 0;
        }

        if (start >= length) {
            // if start is past the end of string
            return 0;
        }

        end = Math.min(end, length);
        if (end < start) {
            return 0;
        }

        String substring = new String(sourceBytes, start, end - start, StandardCharsets.UTF_8);
        return substring.length() - (end - start);
    }

    @Override
    public void run() {
        highlightMatches(mSpannable, mMatches, mPlainText, mIndex, mFactory, mListener);
    }

    public void start() {
        // if there are no matches, we don't have to do anything
        if (TextUtils.isEmpty(mMatches)) return;

        mThread = new Thread(this);
        mStopped = false;
        mThread.start();
    }

    public void stop() {
        mStopped = true;
        if (mThread != null) mThread.interrupt();
    }

    public void highlightMatches(String matches, int columnIndex) {
        highlightMatches(matches, columnIndex, mTextView.getPlainTextContent());
    }

    public void highlightMatches(String matches, int columnIndex, String indexedTextContent) {
        synchronized (this) {
            stop();
            mSpannable = mTextView.getEditableText();
            mMatches = matches;
            mIndex = columnIndex;
            mPlainText = indexedTextContent;
            start();
        }
    }

    public synchronized void removeMatches() {
        stop();
        if (mSpannable != null && mMatchedSpans != null) {
            for (Object span : mMatchedSpans) {
                mSpannable.removeSpan(span);
            }
            mMatchedSpans.clear();
        }
    }

    public interface SpanFactory {
        Object[] buildSpans();
    }

    public interface OnMatchListener {
        void onMatch(SpanFactory factory, Spannable text, int start, int end);
    }

    private static class DefaultMatcher implements OnMatchListener {

        @Override
        public void onMatch(SpanFactory factory, Spannable content, int start, int end) {

            Object[] spans = factory.buildSpans();

            for (Object span : spans) {
                if (start >= 0 && end >= start && end <= content.length()) {
                    content.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    mMatchedSpans.add(span);
                }
            }
        }

    }

}
