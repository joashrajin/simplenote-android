package com.automattic.simplenote

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.NestedScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfoBottomSheetLayoutFontScaleTest {
    @Test
    fun labelsAndValuesRemainCompleteInItalianAtLargeText() {
        val fixture = inflateInfoSheet(Locale.ITALIAN, 2f)

        fixture.rows.forEach { row ->
            assertTrue(
                "${row.label.text} width ${row.label.measuredWidth}px is too small in a ${row.container.width}px row",
                row.label.measuredWidth >= row.container.width / 3,
            )
            assertTrue(
                "${row.value.text} width ${row.value.measuredWidth}px is too small in a ${row.container.width}px row",
                row.value.measuredWidth >= row.container.width / 2,
            )
            assertCompleteAndContained(row.label, row.container)
            assertCompleteAndContained(row.value, row.container)
        }

        assertTrue(fixture.rows.first().label.layout.lineCount > 1)
        assertTrue(fixture.rows.first().value.layout.lineCount > 1)
    }

    @Test
    fun everyLabelCanWrapInSpanishAtLargeText() {
        val fixture = inflateInfoSheet(Locale.forLanguageTag("es"), 2f, Int.MAX_VALUE)

        fixture.rows.forEach { row ->
            assertTrue(row.label.layout.lineCount > 1)
            assertCompleteAndContained(row.label, row.container)
            assertCompleteAndContained(row.value, row.container)
        }
    }

    @Test
    fun labelsAndValuesRemainSingleLineInEnglishAtDefaultTextSize() {
        val fixture = inflateInfoSheet(Locale.ENGLISH, 1f)

        fixture.rows.forEach { row ->
            assertEquals(1, row.label.layout.lineCount)
            assertEquals(1, row.value.layout.lineCount)
            assertCompleteAndContained(row.label, row.container)
            assertCompleteAndContained(row.value, row.container)
        }
    }

    private fun assertCompleteAndContained(text: TextView, row: ViewGroup) {
        val layout = text.layout
        val contentWidth = text.measuredWidth - text.compoundPaddingLeft - text.compoundPaddingRight

        assertTrue(text.measuredWidth > 0)
        assertEquals(text.text.length, layout.getLineEnd(layout.lineCount - 1))
        assertEquals(0, (0 until layout.lineCount).sumOf(layout::getEllipsisCount))
        assertTrue((0 until layout.lineCount).all { layout.getLineWidth(it) <= contentWidth + 1f })
        assertTrue(text.measuredHeight >= layout.height + text.compoundPaddingTop + text.compoundPaddingBottom)
        assertTrue(text.left >= row.paddingLeft)
        assertTrue(text.right <= row.width - row.paddingRight)
        assertTrue(text.top >= row.paddingTop)
        assertTrue(text.bottom <= row.height - row.paddingBottom)
    }

    private fun inflateInfoSheet(
        locale: Locale,
        fontScale: Float,
        countValue: Int = 1_234_567,
    ): Fixture {
        val context = createContext(locale, fontScale)
        val root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_info, null) as NestedScrollView
        root.layoutDirection = context.resources.configuration.layoutDirection
        val content = root.getChildAt(0) as LinearLayout
        val rows = (0 until 5).map { index ->
            val container = content.getChildAt(index) as LinearLayout
            Row(container, container.getChildAt(0) as TextView, container.getChildAt(1) as TextView)
        }
        val date = formattedDate(locale)
        val count = NumberFormat.getIntegerInstance(locale).format(countValue)

        root.findViewById<TextView>(R.id.date_time_synced).text = date
        root.findViewById<TextView>(R.id.date_time_modified).text = date
        root.findViewById<TextView>(R.id.date_time_created).text = date
        root.findViewById<TextView>(R.id.count_words).text = count
        root.findViewById<TextView>(R.id.count_characters).text = count
        root.findViewById<View>(R.id.references_layout).visibility = View.GONE

        root.measure(exactly(320), atMost(640))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(320, root.measuredWidth)
        return Fixture(rows)
    }

    private fun formattedDate(locale: Locale): String {
        val calendar = Calendar.getInstance(locale).apply {
            set(2026, Calendar.SEPTEMBER, 28, 23, 58, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMM dd, yyyy, h:mm")
        return SimpleDateFormat(pattern, locale).format(calendar.time)
    }

    private fun createContext(locale: Locale, fontScale: Float): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            this.fontScale = fontScale
            setLocale(locale)
        }
        val activityContext = ContextThemeWrapper(
            application.createConfigurationContext(configuration),
            R.style.Style_Default,
        )
        return ContextThemeWrapper(activityContext, R.style.Theme_Simplestyle_BottomSheetDialog_Default)
    }

    private fun exactly(size: Int) =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun atMost(size: Int) =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)

    private data class Fixture(val rows: List<Row>)

    private data class Row(
        val container: LinearLayout,
        val label: TextView,
        val value: TextView,
    )
}
