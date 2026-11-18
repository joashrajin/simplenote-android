package com.automattic.simplenote.utils

import android.text.Spannable
import androidx.test.InstrumentationRegistry
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.utils.SearchSnippetFormatter.SpanFactory
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Matchers.arrayWithSize
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class SearchSnippetFormatterTest {

    @Test
    fun testFormatSnippet() {
        val snippet = "… This is just <match>an example</match> of a\n<match>snippet</match> …"
        val formatted: Spannable = SearchSnippetFormatter.formatString(
            InstrumentationRegistry.getTargetContext(),
            snippet,
            SpanFactory { arrayOf(Any()) },
            0
        )

        assertThat(formatted.toString(), `is`("… This is just an example of a snippet …"))

        val spans: Array<Any> = formatted.getSpans(0, formatted.length, Any::class.java)
        assertThat(spans, arrayWithSize(2))

        assertThat(formatted.getSpanStart(spans[0]), `is`(15))
        assertThat(formatted.getSpanEnd(spans[0]), `is`(25))

        assertThat(formatted.getSpanStart(spans[1]), `is`(31))
        assertThat(formatted.getSpanEnd(spans[1]), `is`(38))
    }

}
