package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.TypedArray;

import com.automattic.simplenote.R;

import org.junit.Test;

public class ThemeUtilsTest {
    private static final int COLOR_ATTRIBUTE = 1;
    private static final int COLOR_RESOURCE = 2;

    @Test
    public void themeTextColorKeepsTheNullContextFallback() {
        assertEquals(0, ThemeUtils.getThemeTextColorId(null));
    }

    @Test
    public void themeTextColorRecyclesAttributesWhenLookupFails() {
        Context context = mock(Context.class);
        TypedArray attributes = mock(TypedArray.class);
        UnsupportedOperationException failure = new UnsupportedOperationException("not a resource");
        when(context.obtainStyledAttributes(aryEq(new int[]{R.attr.noteEditorTextColor})))
                .thenReturn(attributes);
        when(attributes.getResourceId(0, android.R.color.black)).thenThrow(failure);

        UnsupportedOperationException thrown = assertThrows(
                UnsupportedOperationException.class,
                () -> ThemeUtils.getThemeTextColorId(context)
        );

        assertSame(failure, thrown);
        verify(attributes).recycle();
    }

    @Test
    public void colorResourceRecyclesAttributesWhenLookupFails() {
        Context context = mock(Context.class);
        TypedArray attributes = mock(TypedArray.class);
        UnsupportedOperationException failure = new UnsupportedOperationException("not a resource");
        when(context.obtainStyledAttributes(aryEq(new int[]{COLOR_ATTRIBUTE}))).thenReturn(attributes);
        when(attributes.getResourceId(0, android.R.color.black)).thenThrow(failure);

        UnsupportedOperationException thrown = assertThrows(
                UnsupportedOperationException.class,
                () -> ThemeUtils.getColorResourceFromAttribute(context, COLOR_ATTRIBUTE)
        );

        assertSame(failure, thrown);
        verify(attributes).recycle();
    }

    @Test
    public void colorResourcePreservesLookupFailureWhenRecycleAlsoFails() {
        Context context = mock(Context.class);
        TypedArray attributes = mock(TypedArray.class);
        UnsupportedOperationException lookupFailure = new UnsupportedOperationException("not a resource");
        IllegalStateException recycleFailure = new IllegalStateException("recycle failed");
        when(context.obtainStyledAttributes(aryEq(new int[]{COLOR_ATTRIBUTE}))).thenReturn(attributes);
        when(attributes.getResourceId(0, android.R.color.black)).thenThrow(lookupFailure);
        doThrow(recycleFailure).when(attributes).recycle();

        UnsupportedOperationException thrown = assertThrows(
                UnsupportedOperationException.class,
                () -> ThemeUtils.getColorResourceFromAttribute(context, COLOR_ATTRIBUTE)
        );

        assertSame(lookupFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(recycleFailure, thrown.getSuppressed()[0]);
        verify(attributes).recycle();
    }

    @Test
    public void colorResourcePropagatesRecycleFailureAfterSuccessfulLookup() {
        Context context = mock(Context.class);
        TypedArray attributes = mock(TypedArray.class);
        IllegalStateException recycleFailure = new IllegalStateException("recycle failed");
        when(context.obtainStyledAttributes(aryEq(new int[]{COLOR_ATTRIBUTE}))).thenReturn(attributes);
        when(attributes.getResourceId(0, android.R.color.black)).thenReturn(COLOR_RESOURCE);
        doThrow(recycleFailure).when(attributes).recycle();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> ThemeUtils.getColorResourceFromAttribute(context, COLOR_ATTRIBUTE)
        );

        assertSame(recycleFailure, thrown);
        verify(attributes).recycle();
    }

    @Test
    public void colorResourceReturnsResolvedResourceAndRecyclesAttributes() {
        Context context = mock(Context.class);
        TypedArray attributes = mock(TypedArray.class);
        when(context.obtainStyledAttributes(aryEq(new int[]{COLOR_ATTRIBUTE}))).thenReturn(attributes);
        when(attributes.getResourceId(0, android.R.color.black)).thenReturn(COLOR_RESOURCE);

        int colorResource = ThemeUtils.getColorResourceFromAttribute(context, COLOR_ATTRIBUTE);

        assertEquals(COLOR_RESOURCE, colorResource);
        verify(attributes).recycle();
    }
}
