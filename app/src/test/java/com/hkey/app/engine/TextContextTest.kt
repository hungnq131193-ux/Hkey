package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** B3 — ngữ cảnh đúng sau dấu kết câu / xuống dòng / ô trống. */
class TextContextTest {

    @Test
    fun wordDirectlyBeforeCursor() {
        assertEquals("chào", TextContext.wordBefore("xin chào", ""))
        assertEquals("chào", TextContext.wordBefore("xin chào ", ""))
        assertEquals("Next", TextContext.wordBefore("word. Next", ""))
        // Dấu phẩy giữ hành vi cũ: fallback từ đã commit (quyết ở Phase 3)
        assertEquals("hôm", TextContext.wordBefore("xin chào, ", "hôm"))
    }

    @Test
    fun sentenceStartIsBos() {
        assertEquals("", TextContext.wordBefore("", "hôm"))
        assertEquals("", TextContext.wordBefore("   ", "hôm"))
        assertEquals("", TextContext.wordBefore("xin chào.", "hôm"))
        assertEquals("", TextContext.wordBefore("xin chào. ", "hôm"))
        assertEquals("", TextContext.wordBefore("xin chào!\n", "hôm"))
        assertEquals("", TextContext.wordBefore("xin chào\n", "hôm"))
        assertEquals("", TextContext.wordBefore("xin chào\n   ", "hôm"))
    }

    @Test
    fun nullFallsBackToLastCommitted() {
        // App không trả text trước con trỏ -> dùng từ vừa commit trong session
        assertEquals("hôm", TextContext.wordBefore(null, "hôm"))
    }

    @Test
    fun lastTwoForTrigram() {
        assertEquals("đi" to "anh", TextContext.lastTwo("anh đi ", "x"))
        assertEquals("đi" to "anh", TextContext.lastTwo("sáng. anh đi ", "x"))
        assertEquals("" to "", TextContext.lastTwo("đi. ", "x"))
        assertEquals("" to "", TextContext.lastTwo("đi\n", "x"))
        assertEquals("đi" to "", TextContext.lastTwo("đi", "x"))
        assertEquals("x" to "", TextContext.lastTwo(null, "x"))
    }
}
