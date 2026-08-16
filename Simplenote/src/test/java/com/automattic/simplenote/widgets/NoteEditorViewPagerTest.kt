package com.automattic.simplenote.widgets

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Parcelable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class NoteEditorViewPagerTest {
    private lateinit var activity: Activity
    private lateinit var savedState: Parcelable

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        savedState = createPager().apply {
            setPagingEnabled(false)
            layoutPager(this)
        }.savePagerState()
    }

    @Test
    fun restoredPagerRejectsPagingByDefault() {
        val pager = createPager().apply {
            setCurrentItem(1, false)
            restorePagerState(savedState)
        }
        layoutPager(pager)

        assertEquals(0, pager.currentItem)
        assertFalse(interceptsHorizontalSwipe(pager))
    }

    @Test
    fun restoredPagerAcceptsPagingWhenEnabled() {
        val pager = createPager().apply {
            setCurrentItem(1, false)
            setPagingEnabled(true)
            restorePagerState(savedState)
        }
        layoutPager(pager)

        assertEquals(0, pager.currentItem)
        assertTrue(interceptsHorizontalSwipe(pager))
    }

    private fun createPager(): TestNoteEditorViewPager {
        return TestNoteEditorViewPager(activity).apply {
            adapter = TwoPageAdapter()
            activity.setContentView(this)
        }
    }

    private fun layoutPager(pager: NoteEditorViewPager) {
        pager.measure(exactly(1_000), exactly(600))
        pager.layout(0, 0, pager.measuredWidth, pager.measuredHeight)
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }

    private fun interceptsHorizontalSwipe(pager: NoteEditorViewPager): Boolean {
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 750f, 300f, 0)
        val move = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_MOVE, 250f, 300f, 0)
        val cancel = MotionEvent.obtain(0L, 32L, MotionEvent.ACTION_CANCEL, 250f, 300f, 0)

        return try {
            assertFalse(pager.onInterceptTouchEvent(down))
            pager.onInterceptTouchEvent(move)
        } finally {
            pager.onInterceptTouchEvent(cancel)
            down.recycle()
            move.recycle()
            cancel.recycle()
        }
    }

    private class TestNoteEditorViewPager(context: Context) : NoteEditorViewPager(context, null) {
        fun savePagerState(): Parcelable {
            return requireNotNull(super.onSaveInstanceState())
        }

        fun restorePagerState(state: Parcelable) {
            super.onRestoreInstanceState(state)
        }
    }

    private class TwoPageAdapter : PagerAdapter() {
        override fun getCount(): Int = 2

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            return View(container.context).also(container::addView)
        }

        override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
            container.removeView(item as View)
        }

        override fun isViewFromObject(view: View, item: Any): Boolean = view === item
    }
}
