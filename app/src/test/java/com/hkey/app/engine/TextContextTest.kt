package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun gluedDotsAreNotSentenceEnd() {
        // 1.1: '.' dính liền = giữa token (url/email/ip), không phải kết câu.
        // Không có chữ cái nào trước con trỏ -> fallback, không phải BOS.
        assertEquals("hôm", TextContext.wordBefore("xin chào.", "hôm"))
        assertEquals("x" to "", TextContext.lastTwo("hu.", "x"))
        assertEquals("x" to "", TextContext.lastTwo("a.b@c.", "x"))
        assertEquals("vn" to "", TextContext.lastTwo("hu.io.vn", "x"))
        // Dấu kết câu + khoảng trắng vẫn là BOS, kể cả qua ngoặc/nháy đóng.
        assertEquals("" to "", TextContext.lastTwo("chào. ", "x"))
        assertEquals("" to "", TextContext.lastTwo("ok?! ", "x"))
        assertEquals("" to "", TextContext.lastTwo("\"Hi.\" ", "x"))
        assertEquals("" to "", TextContext.lastTwo("chào… ", "x"))
        // "a. b" là hết câu -> w2 rỗng; "a.b c" thì '.' không ngăn w1/w2.
        assertEquals("b" to "", TextContext.lastTwo("a. b ", "x"))
        assertEquals("c" to "b", TextContext.lastTwo("xa.b c", "x"))
    }

    @Test
    fun sentenceBoundaryNeedsSpaceAfterPunct() {
        assertTrue(TextContext.sentenceBoundary(null))
        assertTrue(TextContext.sentenceBoundary(""))
        assertTrue(TextContext.sentenceBoundary("   "))
        assertTrue(TextContext.sentenceBoundary("xin chào. "))
        assertTrue(TextContext.sentenceBoundary("ok?! "))
        assertTrue(TextContext.sentenceBoundary("\"Hi.\" "))
        assertTrue(TextContext.sentenceBoundary("chào… "))
        assertTrue(TextContext.sentenceBoundary("a\n"))
        assertTrue(TextContext.sentenceBoundary("a\n  "))

        assertFalse(TextContext.sentenceBoundary("hu."))
        assertFalse(TextContext.sentenceBoundary("hu.io.vn"))
        assertFalse(TextContext.sentenceBoundary("1.5"))
        assertFalse(TextContext.sentenceBoundary("a.b@c.com"))
        assertFalse(TextContext.sentenceBoundary("chào."))
        assertFalse(TextContext.sentenceBoundary("chào, "))
        assertFalse(TextContext.sentenceBoundary("word "))
        assertFalse(TextContext.sentenceBoundary("a\nb"))
    }

    @Test
    fun gluedTokenDetection() {
        // Token kỹ thuật (url/email/ip/giờ): từ gõ ngay sau . @ / : bị "dính".
        assertTrue(TextContext.gluedToken("hu."))
        assertTrue(TextContext.gluedToken("a@b."))
        assertTrue(TextContext.gluedToken("x/"))
        assertTrue(TextContext.gluedToken("t:"))
        assertFalse(TextContext.gluedToken("chào "))
        assertFalse(TextContext.gluedToken("word"))
        assertFalse(TextContext.gluedToken(""))
        assertFalse(TextContext.gluedToken(null))
    }

    @Test
    fun matchCaseAppliesTypedStyle() {
        // 1.2: bản sửa/gợi ý giữ kiểu hoa của từ đã gõ
        assertEquals("Nói", TextContext.matchCase("Noi", "nói"))
        assertEquals("HOÀN", TextContext.matchCase("HOAN", "hoàn"))
        assertEquals("hoàn", TextContext.matchCase("hoan", "hoàn"))
        // Chữ đầu thường -> giữ nguyên bản đề nghị
        assertEquals("nói", TextContext.matchCase("iPhone", "nói"))
        assertEquals("Anh", TextContext.matchCase("A", "anh"))
        assertEquals("", TextContext.matchCase("X", ""))
    }
}
