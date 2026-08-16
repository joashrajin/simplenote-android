package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;

import org.junit.Test;

public class TextHighlighterTest {
    private static final int FOREGROUND_ATTRIBUTE = 1;
    private static final int BACKGROUND_ATTRIBUTE = 2;
    private static final int FOREGROUND_COLOR = 0xFF123456;
    private static final int BACKGROUND_COLOR = 0xFF654321;

    @Test
    public void constructorRecyclesAttributesWhenColorResolutionFails() {
        Context context = mock(Context.class);
        TypedArray colors = mock(TypedArray.class);
        UnsupportedOperationException failure = new UnsupportedOperationException("not a color");
        when(context.obtainStyledAttributes(aryEq(attributes()))).thenReturn(colors);
        when(colors.getColor(0, 0xFFFF0000)).thenReturn(FOREGROUND_COLOR);
        when(colors.getColor(1, 0xFF00FFFF)).thenThrow(failure);

        UnsupportedOperationException thrown = assertThrows(
                UnsupportedOperationException.class,
                () -> new TextHighlighter(context, FOREGROUND_ATTRIBUTE, BACKGROUND_ATTRIBUTE)
        );

        assertSame(failure, thrown);
        verify(colors).recycle();
    }

    @Test
    public void constructorPreservesColorFailureWhenRecycleAlsoFails() {
        Context context = mock(Context.class);
        TypedArray colors = mock(TypedArray.class);
        UnsupportedOperationException colorFailure = new UnsupportedOperationException("not a color");
        IllegalStateException recycleFailure = new IllegalStateException("recycle failed");
        when(context.obtainStyledAttributes(aryEq(attributes()))).thenReturn(colors);
        when(colors.getColor(0, 0xFFFF0000)).thenReturn(FOREGROUND_COLOR);
        when(colors.getColor(1, 0xFF00FFFF)).thenThrow(colorFailure);
        doThrow(recycleFailure).when(colors).recycle();

        UnsupportedOperationException thrown = assertThrows(
                UnsupportedOperationException.class,
                () -> new TextHighlighter(context, FOREGROUND_ATTRIBUTE, BACKGROUND_ATTRIBUTE)
        );

        assertSame(colorFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(recycleFailure, thrown.getSuppressed()[0]);
        verify(colors).recycle();
    }

    @Test
    public void constructorPropagatesRecycleFailureAfterSuccessfulReads() {
        Context context = mock(Context.class);
        TypedArray colors = mock(TypedArray.class);
        IllegalStateException recycleFailure = new IllegalStateException("recycle failed");
        when(context.obtainStyledAttributes(aryEq(attributes()))).thenReturn(colors);
        when(colors.getColor(0, 0xFFFF0000)).thenReturn(FOREGROUND_COLOR);
        when(colors.getColor(1, 0xFF00FFFF)).thenReturn(BACKGROUND_COLOR);
        doThrow(recycleFailure).when(colors).recycle();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new TextHighlighter(context, FOREGROUND_ATTRIBUTE, BACKGROUND_ATTRIBUTE)
        );

        assertSame(recycleFailure, thrown);
        verify(colors).recycle();
    }

    @Test
    public void constructorStoresResolvedColorsAndBuildsExpectedSpanTypes() {
        Context context = mock(Context.class);
        TypedArray colors = mock(TypedArray.class);
        when(context.obtainStyledAttributes(aryEq(attributes()))).thenReturn(colors);
        when(colors.getColor(0, 0xFFFF0000)).thenReturn(FOREGROUND_COLOR);
        when(colors.getColor(1, 0xFF00FFFF)).thenReturn(BACKGROUND_COLOR);

        TextHighlighter highlighter = new TextHighlighter(
                context,
                FOREGROUND_ATTRIBUTE,
                BACKGROUND_ATTRIBUTE
        );
        Object[] spans = highlighter.buildSpans();

        assertEquals(FOREGROUND_COLOR, highlighter.mForegroundColor);
        assertEquals(BACKGROUND_COLOR, highlighter.mBackgroundColor);
        assertEquals(2, spans.length);
        assertTrue(spans[0] instanceof ForegroundColorSpan);
        assertTrue(spans[1] instanceof BackgroundColorSpan);
        verify(colors).recycle();
    }

    private static int[] attributes() {
        return new int[]{FOREGROUND_ATTRIBUTE, BACKGROUND_ATTRIBUTE};
    }
}
