package com.hkey.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 1.4.0 (E3) — resumeWord an toàn hơn.
 * Không resume: token dính url/email, từ vừa commit dạng phím thô (tiếng
 * Anh), từ kề không phải âm tiết VN hợp lệ. Resume vẫn được khi shift 1
 * lần; caps lock (shiftLocked) chặn.
 */
@RunWith(RobolectricTestRunner::class)
class ResumeWordTest {

    @Test
    fun rawCommittedWordDoesNotResume() {
        val h = ImeHarness()
        h.type("show ")   // "show" live-restore -> commit phím thô + space
        h.type("⌫")       // xoá space -> con trỏ sát "show"
        h.idle()
        h.type("s")
        h.idle()
        // resume sẽ kéo "show" về buffer -> composing "shows"; không resume
        // thì chỉ có phím mới
        assertEquals("s", h.composing())
        h.type(" ")
        assertTrue(h.text().endsWith("shows "))
    }

    @Test
    fun validSyllableStillResumes() {
        // "Viet" commit thường (không phải raw) -> 'j' resume, đè dấu nặng
        val h = ImeHarness(initialText = "ok Viet")
        h.type("j")
        h.idle()
        assertEquals("Viẹt", h.composing())
        h.type(" ")
        assertTrue(h.text().endsWith("Viẹt "))
    }

    @Test
    fun gluedTokenDoesNotResume() {
        // con trỏ trong url "hu.io" -> 's' là ký tự mới, không nối vào "io"
        val h = ImeHarness(initialText = "hu.io")
        h.type("s")
        h.idle()
        assertEquals("s", h.composing())
        h.type(" ")
        assertTrue(h.text().endsWith("hu.ios "))
    }

    @Test
    fun invalidSyllableDoesNotResume() {
        // "xyz" không phải âm tiết -> 's' không nối (dù từ commit thường)
        val h = ImeHarness(initialText = "ok xyl")
        h.type("s")
        h.idle()
        assertEquals("s", h.composing())
    }

    @Test
    fun wOnValidWordStillApplies() {
        // "hon" commit thường + 'w' -> bẻ dấu tại chỗ "hơn" (giữ hành vi cũ)
        val h = ImeHarness(initialText = "ok hon")
        h.type("w")
        h.idle()
        assertTrue(h.text().endsWith("hơn"))
    }
}
