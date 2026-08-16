package com.automattic.simplenote.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class AccountTest {
    private static final String EMAIL = "user@example.com";

    @Test
    public void nonStringSentToIsNotSent() throws JSONException {
        assertFalse(accountWith("sent_to", true).hasSentEmail(EMAIL));
    }

    @Test
    public void nonStringTokenIsNotVerified() throws JSONException {
        assertFalse(accountWith("token", 7).hasVerifiedEmail(EMAIL));
    }

    @Test
    public void nonStringTokenUsernameIsNotVerified() throws JSONException {
        String token = new JSONObject().put("username", 7).toString();

        assertFalse(accountWith("token", token).hasVerifiedEmail(EMAIL));
    }

    @Test
    public void validStringFieldsMatchEmailIgnoringCase() throws JSONException {
        JSONObject properties = new JSONObject()
                .put("sent_to", "USER@example.com")
                .put("token", new JSONObject().put("username", "USER@example.com").toString());
        Account account = new Account.Schema().build(Account.KEY_EMAIL_VERIFICATION, properties);

        assertTrue(account.hasSentEmail(EMAIL));
        assertTrue(account.hasVerifiedEmail(EMAIL));
    }

    private Account accountWith(String field, Object value) throws JSONException {
        return new Account.Schema().build(Account.KEY_EMAIL_VERIFICATION, new JSONObject().put(field, value));
    }
}
