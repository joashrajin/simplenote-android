package com.automattic.simplenote.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimplenoteLinkifyTest {
    @Test
    public void noteLinkEscapesMarkdownLabelBrackets() {
        assertEquals(
            "[safe\\](https://evil.example)\\[spoof](simplenote://note/note-id)",
            SimplenoteLinkify.getNoteLinkWithTitle("safe](https://evil.example)[spoof", "note-id")
        );
    }

    @Test
    public void noteLinkEscapesBackslashes() {
        assertEquals(
            "[Back\\\\slash](simplenote://note/note-id)",
            SimplenoteLinkify.getNoteLinkWithTitle("Back\\slash", "note-id")
        );
    }

    @Test
    public void noteLinkPreservesPlainTitle() {
        assertEquals(
            "[Roadmap](simplenote://note/abc_123)",
            SimplenoteLinkify.getNoteLinkWithTitle("Roadmap", "abc_123")
        );
    }
}
