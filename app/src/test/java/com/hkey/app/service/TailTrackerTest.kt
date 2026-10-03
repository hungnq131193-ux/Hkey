package com.hkey.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bản sao cục bộ ~40 ký tự cuối do IME tự gõ — giảm IPC getTextBeforeCursor.
 *  null = không biết (con trỏ đổi từ ngoài) -> phải đọc lại 1 lần. */
class TailTrackerTest {

    @Test
    fun append_growsAndCapsAt40() {
        val t = TailTracker(40)
        t.seed("xin chào")
        t.append("bạn")
        assertEquals("xin chàobạn", t.tail)
        t.seed("a".repeat(50))
        assertEquals(40, t.tail!!.length)
        t.append("z".repeat(10))
        assertEquals(40, t.tail!!.length)
        assertEquals("z".repeat(10), t.tail!!.takeLast(10))
    }

    @Test
    fun append_onUnknown_staysUnknown() {
        val t = TailTracker()
        t.append("x") // chưa seed -> không được tự bịa tail
        assertNull(t.tail)
    }

    @Test
    fun drop_partialAndOverflow() {
        val t = TailTracker()
        t.seed("abcdef")
        t.drop(2)
        assertEquals("abcd", t.tail)
        t.drop(10) // xoá nhiều hơn biết -> không còn tin được
        assertNull(t.tail)
    }

    @Test
    fun invalidate_forcesRefetch() {
        val t = TailTracker()
        t.seed("abc")
        t.invalidate()
        assertNull(t.tail)
        t.drop(1) // drop trên null vẫn null
        assertNull(t.tail)
    }
}
