package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

// Robolectric supplies the real TextUtils; the stubbed android.jar would return false for isEmpty(null).
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class StrUtilsTest {
    @Test
    public void spaceStringIsASingleSpace() {
        assertEquals(" ", StrUtils.SPACE_STRING);
    }

    @Test
    public void isEmailAcceptsWellFormedAddresses() {
        assertTrue(StrUtils.isEmail("user@example.com"));
        assertTrue(StrUtils.isEmail("first.last+tag@sub.example.co"));
    }

    @Test
    public void isEmailRejectsNullAndEmpty() {
        assertFalse(StrUtils.isEmail(null));
        assertFalse(StrUtils.isEmail(""));
    }

    @Test
    public void isEmailRejectsMalformedAddresses() {
        assertFalse(StrUtils.isEmail("not-an-email"));
        assertFalse(StrUtils.isEmail("user@"));
        assertFalse(StrUtils.isEmail("@example.com"));
        assertFalse(StrUtils.isEmail("user@example"));
        assertFalse(StrUtils.isEmail("user @example.com"));
        assertFalse(StrUtils.isEmail(" user@example.com"));
    }

    @Test
    public void notNullStrMapsNullToEmpty() {
        assertEquals("", StrUtils.notNullStr(null));
    }

    @Test
    public void notNullStrReturnsTheSameInstanceOtherwise() {
        String value = "abc";
        assertSame(value, StrUtils.notNullStr(value));
        assertEquals("", StrUtils.notNullStr(""));
    }

    @Test
    public void strToIntParsesSignedIntegers() {
        assertEquals(42, StrUtils.strToInt("42", 0));
        assertEquals(-7, StrUtils.strToInt("-7", 0));
        assertEquals(5, StrUtils.strToInt("+5", 0));
        assertEquals(Integer.MAX_VALUE, StrUtils.strToInt("2147483647", 0));
        assertEquals(Integer.MIN_VALUE, StrUtils.strToInt("-2147483648", 0));
    }

    @Test
    public void strToIntRejectsWhatIntegerParsingRejects() {
        assertEquals(3, StrUtils.strToInt("", 3));
        assertEquals(3, StrUtils.strToInt("1.5", 3));
        assertEquals(3, StrUtils.strToInt(" 1", 3));
        assertEquals(3, StrUtils.strToInt("2147483648", 3));
        assertEquals(3, StrUtils.strToInt("-2147483649", 3));
    }

    @Test
    public void strToIntWithDefaultReturnsTheDefaultOnFailure() {
        assertEquals(9, StrUtils.strToInt(null, 9));
        assertEquals(-1, StrUtils.strToInt("x", -1));
        assertEquals(3, StrUtils.strToInt("99999999999", 3));
        assertEquals(12, StrUtils.strToInt("12", 9));
        assertEquals(0, StrUtils.strToInt("0", 9));
    }

    @Test
    public void isSameStrHandlesNulls() {
        assertTrue(StrUtils.isSameStr(null, null));
        assertFalse(StrUtils.isSameStr(null, "a"));
        assertFalse(StrUtils.isSameStr("a", null));
    }

    @Test
    public void isSameStrComparesByValueAndCase() {
        assertTrue(StrUtils.isSameStr("a", new String("a")));
        assertTrue(StrUtils.isSameStr("", ""));
        assertFalse(StrUtils.isSameStr("a", "A"));
        assertFalse(StrUtils.isSameStr("a", "b"));
    }
}
