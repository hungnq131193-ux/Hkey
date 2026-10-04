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

    /** 1.4.1: correction thử xoá 1 phím thừa quanh dấu — "tiénge" (đặt sắc
     *  sớm + thừa 'e') về "tiéng" rồi sửa thành "tiếng". Tiếng Anh không
     *  repair được vẫn trả null. */
    @Test
    fun extraCharAroundToneRepaired() {
        val p = ContextPredictor()
        p.addWords(listOf("tiếng"))
        assertEquals("tiếng", p.correction("tiénge", null))
        assertNull(p.correction("expect", null))
        assertNull(p.correction("ẽpect", null))
    }

    /** 1.5.0: repair-xoá chỉ chạy khi từ có glyph Việt (dấu đã in ra —
     *  bằng chứng "phím thừa quanh dấu"). Từ thuần ASCII tuyệt đối không
     *  bị xoá chữ: "plan"→"lan", "code"→"coe", "max"→"ma" là bug 1.4.x. */
    @Test
    fun asciiWordsNeverRepairedByDeletion() {
        val p = ContextPredictor()
        p.addWords(listOf("lan", "ma", "con", "coe", "code"))
        assertNull(p.correction("plan", null))  // repair sẽ cho "lan" — cấm
        assertNull(p.correction("max", null))   // repair sẽ cho "ma" — cấm
        assertNull(p.correction("code", null))  // "code" trong từ điển -> null sớm
        // Glyph Việt vẫn repair được như cũ
        p.addWords(listOf("tiếng"))
        assertEquals("tiếng", p.correction("tiénge", null))
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
