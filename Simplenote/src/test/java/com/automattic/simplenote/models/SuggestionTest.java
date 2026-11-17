package com.automattic.simplenote.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SuggestionTest {
    @Test
    public void typeConstantsAreSwitchableCompileTimeConstants() {
        assertEquals("history", label(Suggestion.Type.HISTORY));
        assertEquals("query", label(Suggestion.Type.QUERY));
        assertEquals("tag", label(Suggestion.Type.TAG));
    }

    @Test
    public void typeConstantValues() {
        assertEquals(0, Suggestion.Type.HISTORY);
        assertEquals(1, Suggestion.Type.QUERY);
        assertEquals(2, Suggestion.Type.TAG);
    }

    @Test
    public void constructorSetsNameAndType() {
        Suggestion suggestion = new Suggestion("groceries", Suggestion.Type.TAG);

        assertEquals("groceries", suggestion.getName());
        assertEquals(Suggestion.Type.TAG, suggestion.getType());
    }

    @Test
    public void settersReplaceNameAndType() {
        Suggestion suggestion = new Suggestion("gro", Suggestion.Type.QUERY);

        suggestion.setName("grocery");
        suggestion.setType(Suggestion.Type.HISTORY);

        assertEquals("grocery", suggestion.getName());
        assertEquals(Suggestion.Type.HISTORY, suggestion.getType());
    }

    @Test
    public void nameAcceptsNull() {
        Suggestion suggestion = new Suggestion(null, Suggestion.Type.QUERY);
        assertNull(suggestion.getName());

        suggestion.setName("query");
        suggestion.setName(null);
        assertNull(suggestion.getName());
    }

    private static String label(@Suggestion.Type int type) {
        switch (type) {
            case Suggestion.Type.HISTORY:
                return "history";
            case Suggestion.Type.QUERY:
                return "query";
            case Suggestion.Type.TAG:
                return "tag";
            default:
                return "unknown";
        }
    }
}
