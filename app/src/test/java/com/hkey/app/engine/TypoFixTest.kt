package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.3.2 — sửa typo trên chuỗi không-phải-âm-tiết (correction bỏ qua) +
 *  lọc gợi ý theo dấu đã gõ. */
class TypoFixTest {

    @Test
    fun transposedLettersFixed() {
        val p = ContextPredictor()
        assertEquals("không", p.typoFix("khôgn", null))
    }

    @Test
    fun missingMiddleCharFixed() {
        val p = ContextPredictor()
        assertEquals("trong", p.typoFix("trog", null))
    }

    @Test
    fun asciiTypoMatchesAccentedWord() {
        val p = ContextPredictor()
        assertEquals("không", p.typoFix("kohng", null))
    }

    @Test
    fun validOrForeignWordsUntouched() {
        val p = ContextPredictor()
        assertNull(p.typoFix("không", null))   // âm tiết hợp lệ
        assertNull(p.typoFix("ab", null))      // quá ngắn
        assertNull(p.typoFix("xyzqq", null))   // không có phương án
    }

    @Test
    fun marksCompatibility() {
        val p = ContextPredictor()
        // Dấu phụ đã gõ phải khớp đúng vị trí
        assertTrue(p.marksCompatible("đươ", "được"))
        assertFalse(p.marksCompatible("đươ", "đuổi"))
        // Chưa gõ dấu -> từ có dấu vẫn được
        assertTrue(p.marksCompatible("duo", "được"))
        // Thanh đã gõ phải trùng (vị trí thanh tự do)
        assertTrue(p.marksCompatible("hoà", "hoàn"))
        assertFalse(p.marksCompatible("hoà", "hoán"))
    }

    @Test
    fun completionsRespectTypedMarks() {
        val p = ContextPredictor()
        val c = p.completions("đươ")
        assertTrue(c.contains("được"))
        assertFalse(c.contains("đuổi"))
        // "đươ" vẫn là tiền tố hợp lệ ("được") -> không hiện bản sửa
        assertTrue(p.isPrefixOfKnownWord("đươ"))
    }
}
