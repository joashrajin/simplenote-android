package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.text.ParseException;
import java.time.Instant;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeUtilsTest {
    private static final String EXPORTED_TIMESTAMP = "2020-01-02T03:04:05.678Z";
    private static final long EXPECTED_MILLIS = Instant.parse(EXPORTED_TIMESTAMP).toEpochMilli();

    @Test
    public void exportedTimestampKeepsItsInstantAndReturnedDeviceZone() throws ParseException {
        TimeZone deviceTimeZone = TimeZone.getTimeZone("GMT+09:00");
        Calendar parsed = parseWithDefaults(Locale.US, deviceTimeZone);

        assertEquals(EXPECTED_MILLIS, parsed.getTimeInMillis());
        assertEquals(deviceTimeZone, parsed.getTimeZone());
    }

    @Test
    public void exportedTimestampKeepsItsGregorianYearOutsideUsLocale() throws ParseException {
        Calendar parsed = parseWithDefaults(Locale.forLanguageTag("th-TH"), TimeZone.getTimeZone("UTC"));

        assertEquals(EXPECTED_MILLIS, parsed.getTimeInMillis());
    }

    @Test
    public void exportedTimestampKeepsTheExistingUtcUsResult() throws ParseException {
        Calendar parsed = parseWithDefaults(Locale.US, TimeZone.getTimeZone("UTC"));

        assertEquals(EXPECTED_MILLIS, parsed.getTimeInMillis());
    }

    private Calendar parseWithDefaults(Locale locale, TimeZone timeZone) throws ParseException {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(locale);
            TimeZone.setDefault(timeZone);
            return DateTimeUtils.getDateCalendar(EXPORTED_TIMESTAMP);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }
}
