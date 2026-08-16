package com.automattic.simplenote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

public class ImporterTest {
    private Importer mImporter;

    @Before
    public void setUp() {
        mImporter = new Importer(mock(Simplenote.class));
    }

    @Test
    public void missingRequiredNoteSectionsReturnParseError() {
        assertParseError("{}");
        assertParseError("{\"trashedNotes\":[]}");
        assertParseError("{\"activeNotes\":[]}");
    }

    @Test
    public void nonArrayRequiredNoteSectionsReturnParseError() {
        assertParseError("{\"activeNotes\":{},\"trashedNotes\":[]}");
        assertParseError("{\"activeNotes\":[],\"trashedNotes\":{}}");
    }

    @Test
    public void emptyRequiredNoteArraysRemainValid() throws Importer.ImportException {
        mImporter.importJsonFile("{\"activeNotes\":[],\"trashedNotes\":[]}");
    }

    private void assertParseError(String content) {
        Importer.ImportException exception = assertThrows(
                Importer.ImportException.class,
                () -> mImporter.importJsonFile(content)
        );

        assertEquals(Importer.FailureReason.ParseError, exception.getReason());
    }
}
