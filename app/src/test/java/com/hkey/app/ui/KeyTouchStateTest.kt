package com.hkey.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logic chạm phím thuần — quyết định bắn phím/lặp, không đụng Android. */
class KeyTouchStateTest {

    private val del = KbKey("fn:del", "⌫", 1.5f, func = true, repeat = true)
    private val a = KbKey("ch:a")
    private val b = KbKey("ch:b")

    @Test
    fun tapDelete_firesExactlyOnce() {
        val s = KeyTouchState()
        assertTrue("repeat key bắn ngay lúc chạm", s.down(0, del, 0f, 0L))
        val up = s.up(0)
        assertNull("nhả không bắn lại", up.fire)
        assertTrue(up.stopRepeat)
        assertNull(s.repeatKey)
    }

    @Test
    fun holdDelete_repeatsAfterDelay() {
        val s = KeyTouchState()
        assertTrue(s.down(0, del, 0f, 0L))
        assertFalse("chưa tới 400ms không lặp", s.repeatDue(399L))
        var n = 0
        var t = 400L
        while (t < 1000L) {
            if (s.repeatDue(t)) n++
            t += 60
        }
        assertEquals("giữ 1s = 10 nhịp lặp trong (400,1000)", 10, n)
        assertTrue("nhịp kế vẫn đến hạn ở biên 1000ms", s.repeatDue(1000L))
    }

    @Test
    fun slideOffDelete_stopsRepeater() {
        val s = KeyTouchState()
        s.down(0, del, 0f, 0L)
        assertTrue("trượt sang phím thường phải dừng", s.moveTo(0, a))
        assertNull(s.repeatKey)
        assertFalse(s.repeatDue(500L))
        val up = s.up(0)
        assertNull("consumed từ lúc repeat -> không bắn phím mới", up.fire)
    }

    @Test
    fun slideBackToDelete_doesNotRestart() {
        val s = KeyTouchState()
        s.down(0, del, 0f, 0L)
        s.moveTo(0, a)
        assertFalse("trượt quay lại ⌫ không bật lại repeat", s.moveTo(0, del))
        assertNull(s.repeatKey)
        assertFalse(s.repeatDue(500L))
    }

    @Test
    fun otherPointerUp_keepsRepeatAlive() {
        val s = KeyTouchState()
        s.down(0, del, 0f, 0L)
        s.down(1, a, 0f, 0L)
        val up1 = s.up(1)
        assertFalse("nhả ngón phụ không dừng repeat", up1.stopRepeat)
        assertSame(a, up1.fire)
        assertTrue("repeat vẫn chạy sau khi ngón phụ nhả", s.repeatDue(400L))
        val up0 = s.up(0)
        assertTrue(up0.stopRepeat)
        assertNull(s.repeatKey)
    }

    @Test
    fun normalKey_firesOnRelease() {
        val s = KeyTouchState()
        assertFalse("phím thường không bắn lúc chạm", s.down(0, a, 0f, 0L))
        val up = s.up(0)
        assertSame(a, up.fire)
        assertFalse(up.stopRepeat)
    }

    @Test
    fun consumedPointer_noFireOnRelease() {
        val s = KeyTouchState()
        s.down(0, a, 0f, 0L)
        s.consumed += 0 // long-press/swipe đã ăn
        val up = s.up(0)
        assertNull(up.fire)
    }

    @Test
    fun cancel_clearsEverything() {
        val s = KeyTouchState()
        s.down(0, del, 0f, 0L)
        s.down(1, a, 0f, 0L)
        s.consumed += 1
        s.cancelAll()
        assertTrue(s.ptrs.isEmpty())
        assertTrue(s.consumed.isEmpty())
        assertNull(s.repeatKey)
        assertFalse(s.repeatDue(500L))
    }
}
