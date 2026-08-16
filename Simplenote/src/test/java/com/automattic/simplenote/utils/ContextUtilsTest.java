package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ContextUtilsTest {
    private static final String CSS_FILE = "style.css";

    @Test
    public void readCssFilePreservesContentAndClosesTheAssetOnce() throws IOException {
        TrackingInputStream stream = new TrackingInputStream("first\nsecond", null, null);

        String css = ContextUtils.readCssFile(contextWith(stream), CSS_FILE);

        assertEquals("first\nsecond\n", css);
        assertEquals(1, stream.closeCount);
    }

    @Test
    public void readCssFileClosesTheAssetOnceWhenReadingFails() throws IOException {
        IOException readFailure = new IOException("read failed");
        TrackingInputStream stream = new TrackingInputStream("", readFailure, null);

        String css = ContextUtils.readCssFile(contextWith(stream), CSS_FILE);

        assertNull(css);
        assertEquals(1, stream.closeCount);
    }

    @Test
    public void readCssFileKeepsSuccessfulContentWhenTheSingleCloseFails() throws IOException {
        IOException closeFailure = new IOException("close failed");
        TrackingInputStream stream = new TrackingInputStream("body", null, closeFailure);

        String css = ContextUtils.readCssFile(contextWith(stream), CSS_FILE);

        assertEquals("body\n", css);
        assertEquals(1, stream.closeCount);
    }

    private Context contextWith(InputStream stream) throws IOException {
        Context context = mock(Context.class);
        Resources resources = mock(Resources.class);
        AssetManager assets = mock(AssetManager.class);
        when(context.getResources()).thenReturn(resources);
        when(resources.getAssets()).thenReturn(assets);
        when(assets.open(CSS_FILE)).thenReturn(stream);
        return context;
    }

    private static class TrackingInputStream extends InputStream {
        private final byte[] content;
        private final IOException readFailure;
        private final IOException closeFailure;
        private int position;
        private int closeCount;

        TrackingInputStream(String content, IOException readFailure, IOException closeFailure) {
            this.content = content.getBytes(StandardCharsets.US_ASCII);
            this.readFailure = readFailure;
            this.closeFailure = closeFailure;
        }

        @Override
        public int read() throws IOException {
            if (readFailure != null) {
                throw readFailure;
            }
            if (position >= content.length) {
                return -1;
            }
            return content[position++] & 0xff;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
