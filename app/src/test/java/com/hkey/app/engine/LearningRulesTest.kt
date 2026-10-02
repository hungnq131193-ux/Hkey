package com.hkey.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.3 — tự học không làm bẩn dữ liệu. */
class LearningRulesTest {

    @Test
    fun junkTokensAreNotLearned() {
        val p = ContextPredictor()
        // Chuỗi có số/ký hiệu/phải quá dài không vào vocab -> không gợi ý, không bị tự sửa
        repeat(5) { p.recordSequence("tôi", "abc123") }
        repeat(5) { p.recordSequence("tôi", "a".repeat(30)) }
        assertFalse(p.completions("abc").contains("abc123"))
        assertFalse(p.completions("aaa").any { it.length > 24 })
    }

    @Test
    fun newWordNeedsTwoSightings() {
        val p = ContextPredictor()
        p.recordSequence("tôi", "kiemtraz")
        // Lần 1: vào vocab (để không bị sửa lại) nhưng chưa lên gợi ý/ứng viên sửa
        assertFalse(p.completions("kiem").contains("kiemtraz"))
        assertNull(p.correction("kiemtrax", null))
        // Lần 2: đủ tin cậy -> xuất hiện trong gợi ý
        p.recordSequence("tôi", "kiemtraz")
        assertTrue(p.completions("kiem").contains("kiemtraz"))
    }

    @Test
    fun learnedWordStillBlocksCorrection() {
        // Cơ chế "không sửa lại" (revert autocorrect) dựa vào việc từ đã gõ
        // có trong vocab -> correction() trả null cho chính nó.
        val p = ContextPredictor()
        p.recordSequence("tôi", "kiemtraz")
        assertNull(p.correction("kiemtraz", null))
    }
}
