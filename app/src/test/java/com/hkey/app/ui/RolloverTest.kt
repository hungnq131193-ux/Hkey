package com.hkey.app.ui

import android.graphics.RectF
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RolloverTest {

    private val a = KbKey("ch:a")
    private val b = KbKey("ch:b")
    private val c = KbKey("ch:c")
    private val del = KbKey("fn:del", "⌫", 1.5f, func = true, repeat = true)
    private val space = KbKey("fn:space", "HKey", 4f, swipe = true)
    private val emoji = KbKey("tx:😀", "😀")

    @Test
    fun flush_firesPendingCharsInInsertionOrder() {
        val s = KeyTouchState()
        s.down(0, a, 0f, 0L)
        s.down(1, b, 0f, 0L)
        val flushed = s.flushPendingCharacters(excludePid = 2)
        assertEquals(listOf(a, b), flushed)
        assertNull("ngón 0 đã consumed -> nhả không bắn", s.up(0).fire)
        assertNull("ngón 1 cũng đã flush -> nhả không bắn", s.up(1).fire)
    }

    @Test
    fun flush_skipsExcludePidAndConsumed() {
        val s = KeyTouchState()
        s.down(0, a, 0f, 0L)
        s.down(1, b, 0f, 0L)
        s.consumed += 1
        val flushed = s.flushPendingCharacters(excludePid = 1)
        assertEquals(listOf(a), flushed)
    }

    @Test
    fun flush_skipsFuncRepeatSwipeEmoji() {
        val s = KeyTouchState()
        s.down(0, del, 0f, 0L)
        s.down(1, space, 0f, 0L)
        s.down(2, emoji, 0f, 0L)
        assertTrue(s.flushPendingCharacters(excludePid = 3).isEmpty())
        assertSame(space, s.up(1).fire)
        assertSame(emoji, s.up(2).fire)
    }

    @Test
    fun flush_usesCurrentKeyAfterSlide() {
        val s = KeyTouchState()
        s.down(0, a, 0f, 0L)
        s.moveTo(0, c)
        assertEquals(listOf(c), s.flushPendingCharacters(excludePid = 1))
    }

    @Test
    fun solePointer_upStillFires() {
        val s = KeyTouchState()
        s.down(0, a, 0f, 0L)
        assertSame(a, s.up(0).fire)
    }

    private fun newView(widthPx: Int = 1080): KeyboardView {
        val v = KeyboardView(RuntimeEnvironment.getApplication())
        v.soundEnabled = false
        v.vibrateEnabled = false
        v.configure(100, 0, KbPalette.DARK, numRow = false)
        v.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(
                widthPx, android.view.View.MeasureSpec.EXACTLY
            ),
            android.view.View.MeasureSpec.makeMeasureSpec(
                600, android.view.View.MeasureSpec.AT_MOST
            )
        )
        v.layout(0, 0, widthPx, v.measuredHeight)
        return v
    }

    private fun center(v: KeyboardView, tag: String): Pair<Float, Float> {
        val r: RectF = v.testHitArea(tag)
            ?: throw AssertionError("không thấy phím $tag")
        return r.centerX() to r.centerY()
    }

    private fun downTime() = android.os.SystemClock.uptimeMillis()

    private fun evDown(x: Float, y: Float, t: Long) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun evPtrDown(t: Long, vararg pts: Pair<Float, Float>): MotionEvent {
        val n = pts.size
        val pp = Array(n) { i ->
            MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val pc = Array(n) { i ->
            MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second }
        }
        val action = MotionEvent.ACTION_POINTER_DOWN or
            ((n - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return MotionEvent.obtain(t, t, action, n, pp, pc, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun evPtrUp(t: Long, upIndex: Int, vararg pts: Pair<Float, Float>): MotionEvent {
        val n = pts.size
        val pp = Array(n) { i ->
            MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val pc = Array(n) { i ->
            MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second }
        }
        val action = MotionEvent.ACTION_POINTER_UP or
            (upIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return MotionEvent.obtain(t, t, action, n, pp, pc, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun evUp(x: Float, y: Float, t: Long) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0)

    @Test
    fun rollover_twoFingers_preserveOrder() {
        val v = newView()
        val fired = mutableListOf<String>()
        v.onKey = { fired += it.tag }
        val (ax, ay) = center(v, "ch:a")
        val (bx, by) = center(v, "ch:b")
        val t = downTime()
        v.dispatchTouchEvent(evDown(ax, ay, t))
        v.dispatchTouchEvent(evPtrDown(t + 5, ax to ay, bx to by))
        assertEquals(listOf("ch:a"), fired)
        v.dispatchTouchEvent(evPtrUp(t + 10, 1, ax to ay, bx to by))
        assertEquals(listOf("ch:a", "ch:b"), fired)
        v.dispatchTouchEvent(evUp(ax, ay, t + 15))
        assertEquals(listOf("ch:a", "ch:b"), fired)
    }

    @Test
    fun rollover_threeFingers_preserveOrder() {
        val v = newView()
        val fired = mutableListOf<String>()
        v.onKey = { fired += it.tag }
        val (ax, ay) = center(v, "ch:a")
        val (sx, sy) = center(v, "ch:s")
        val (dx, dy) = center(v, "ch:d")
        val t = downTime()
        v.dispatchTouchEvent(evDown(ax, ay, t))
        v.dispatchTouchEvent(evPtrDown(t + 5, ax to ay, sx to sy))
        v.dispatchTouchEvent(evPtrDown(t + 10, ax to ay, sx to sy, dx to dy))
        assertEquals(listOf("ch:a", "ch:s"), fired)
        v.dispatchTouchEvent(evPtrUp(t + 15, 2, ax to ay, sx to sy, dx to dy))
        v.dispatchTouchEvent(evPtrUp(t + 20, 1, ax to ay, sx to sy))
        v.dispatchTouchEvent(evUp(ax, ay, t + 25))
        assertEquals(listOf("ch:a", "ch:s", "ch:d"), fired)
    }

    @Test
    fun rollover_functionKeyNotFlushed() {
        val v = newView()
        val fired = mutableListOf<String>()
        v.onKey = { fired += it.tag }
        val (dx, dy) = center(v, "fn:del")
        val (ax, ay) = center(v, "ch:a")
        val t = downTime()
        v.dispatchTouchEvent(evDown(dx, dy, t))
        v.dispatchTouchEvent(evPtrDown(t + 5, dx to dy, ax to ay))
        assertEquals("del chỉ bắn 1 lần lúc DOWN", listOf("fn:del"), fired)
        v.dispatchTouchEvent(evPtrUp(t + 10, 1, dx to dy, ax to ay))
        v.dispatchTouchEvent(evUp(dx, dy, t + 15))
        assertEquals(listOf("fn:del", "ch:a"), fired)
    }

    @Test
    fun singleFingerTap_unchanged() {
        val v = newView()
        val fired = mutableListOf<String>()
        v.onKey = { fired += it.tag }
        val (ax, ay) = center(v, "ch:a")
        val t = downTime()
        v.dispatchTouchEvent(evDown(ax, ay, t))
        assertTrue(fired.isEmpty())
        v.dispatchTouchEvent(evUp(ax, ay, t + 30))
        assertEquals(listOf("ch:a"), fired)
    }
}
