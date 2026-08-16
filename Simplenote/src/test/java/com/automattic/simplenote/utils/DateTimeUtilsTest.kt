package com.automattic.simplenote.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DateTimeUtilsTest {
    @Test
    fun exportedTimestampKeepsItsInstantAndReturnedDeviceZone() {
        val deviceTimeZone = TimeZone.getTimeZone("GMT+09:00")
        val parsed = parseWithDefaults(Locale.US, deviceTimeZone)

        assertEquals(EXPECTED_MILLIS, parsed.timeInMillis)
        assertEquals(deviceTimeZone, parsed.timeZone)
    }

    @Test
    fun exportedTimestampKeepsItsGregorianYearOutsideUsLocale() {
        val parsed = parseWithDefaults(Locale.forLanguageTag("th-TH"), TimeZone.getTimeZone("UTC"))

        assertEquals(EXPECTED_MILLIS, parsed.timeInMillis)
    }

    @Test
    fun exportedTimestampKeepsTheExistingUtcUsResult() {
        val parsed = parseWithDefaults(Locale.US, TimeZone.getTimeZone("UTC"))

        assertEquals(EXPECTED_MILLIS, parsed.timeInMillis)
    }

    private fun parseWithDefaults(locale: Locale, timeZone: TimeZone): Calendar {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(locale)
            TimeZone.setDefault(timeZone)
            return DateTimeUtils.getDateCalendar(EXPORTED_TIMESTAMP)
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private companion object {
        const val EXPORTED_TIMESTAMP = "2020-01-02T03:04:05.678Z"
        val EXPECTED_MILLIS = Instant.parse(EXPORTED_TIMESTAMP).toEpochMilli()
    }
}
