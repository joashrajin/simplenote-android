package com.automattic.simplenote.utils;

/*
 *  misc. string utilities
 *  added 01-Apr-2013 by Nick Bradbury
 */

import static androidx.core.util.PatternsCompat.EMAIL_ADDRESS;

import android.text.TextUtils;

@SuppressWarnings("unused")
public class StrUtils {
    public static final String SPACE_STRING = " ";

    // suppress default constructor for non-instantiability
    private StrUtils() {
        throw new AssertionError();
    }

    public static boolean isEmail(String str) {
        return !TextUtils.isEmpty(str) && EMAIL_ADDRESS.matcher(str).matches();
    }

    // returns "" if the passed string is null, otherwise returns the passed string
    public static String notNullStr(final String value) {
        if (value == null)
            return "";
        return value;
    }

    // exception-less conversion of string to int
    public static int strToInt(final String value) {
        return strToInt(value, 0);
    }

    public static int strToInt(String value, int defaultInt) {
        if (value == null)
            return defaultInt;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultInt;
        }
    }

    // returns true if passed strings are the same, handles null so caller can skip null check before comparison
    public static boolean isSameStr(String s1, String s2) {
        if (s1 == null || s2 == null)
            return (s1 == null && s2 == null);
        return s1.equals(s2);
    }
}
