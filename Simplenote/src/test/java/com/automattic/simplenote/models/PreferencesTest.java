package com.automattic.simplenote.models;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class PreferencesTest {
    @Test
    public void nonStringRecentSearchEntriesAreIgnored() throws JSONException {
        JSONArray recents = new JSONArray()
                .put("alpha")
                .put(7)
                .put("7")
                .put(true)
                .put(new JSONObject().put("key", "value"))
                .put(JSONObject.NULL)
                .put("")
                .put("beta");
        Preferences preferences = preferences(recents);

        assertEquals(Arrays.asList("alpha", "7", "beta"), preferences.getRecentSearches());
    }

    @Test
    public void stringRecentSearchEntriesPreserveValuesAndOrder() throws JSONException {
        JSONArray recents = new JSONArray()
                .put(" first ")
                .put("repeat")
                .put("")
                .put("repeat")
                .put("7");
        Preferences preferences = preferences(recents);

        assertEquals(Arrays.asList(" first ", "repeat", "repeat", "7"), preferences.getRecentSearches());
    }

    private Preferences preferences(JSONArray recents) throws JSONException {
        JSONObject properties = new JSONObject().put("recent_searches", recents);
        return new Preferences.Schema().build(Preferences.PREFERENCES_OBJECT_KEY, properties);
    }
}
