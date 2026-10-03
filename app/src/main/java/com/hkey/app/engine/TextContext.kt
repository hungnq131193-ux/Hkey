package com.hkey.app.engine

/**
 * Từ ngữ cảnh ngay trước con trỏ (B3). Chỉ lấy từ khi hai bên chỉ cách nhau
 * khoảng trắng; sau dấu kết câu . ! ? … + khoảng trắng, xuống dòng \n hoặc
 * ô trống trả về "" (đầu câu — BOS). Dấu . ! ? dính liền chữ ("hu.io.vn",
 * "1.5") là giữa token, không phải kết câu (1.1). Dấu , ; : hiện giữ hành vi
 * cũ (fallback), quyết ở Phase 3 khi có số liệu mô hình.
 */
object TextContext {

    private val sentenceEnd = setOf('.', '!', '?', '…')
    private val closers = "\"')]}»›"

    /** Ký tự nối token kỹ thuật (url/email/ip/giờ): từ gõ ngay sau chúng là
     *  một phần của token — không gợi ý, không tự sửa, không học (1.1). */
    private val tokenGlue = setOf('.', '@', '/', ':')

    fun wordBefore(before: CharSequence?, fallback: String): String =
        lastTwo(before, fallback).first

    /** (từ liền trước con trỏ, từ trước nữa). Đầu câu -> ("","") (G4/B3). */
    fun lastTwo(before: CharSequence?, fallback: String): Pair<String, String> {
        if (before == null) return fallback to ""
        var i = before.length
        var sawNewline = false
        while (i > 0 && before[i - 1].isWhitespace()) {
            if (before[i - 1] == '\n') sawNewline = true
            i--
        }
        if (i == 0 || sawNewline || endsSentence(before, i, i < before.length)) {
            return "" to ""
        }
        var j = i
        while (j > 0 && before[j - 1].isLetter()) j--
        if (j == i) return fallback to ""
        val w1 = before.substring(j, i).toString()
        var k = j
        var crossedNl = false
        while (k > 0 && before[k - 1].isWhitespace()) {
            if (before[k - 1] == '\n') crossedNl = true
            k--
        }
        if (k == 0 || crossedNl || endsSentence(before, k, k < j)) return w1 to ""
        var m = k
        while (m > 0 && before[m - 1].isLetter()) m--
        return w1 to if (m < k) before.substring(m, k).toString() else ""
    }

    /** Dấu kết câu (cho phép ngoặc/nháy đóng đứng giữa) chỉ tính khi có
     *  khoảng trắng theo sau: "chào. " là kết câu, "hu.io" không phải. */
    private fun endsSentence(before: CharSequence, pos: Int, hadSpace: Boolean): Boolean {
        if (!hadSpace) return false
        var q = pos
        while (q > 0 && before[q - 1] in closers) q--
        return q > 0 && before[q - 1] in sentenceEnd
    }

    /** Con trỏ đứng ở đầu câu? true khi: ô trống / app không trả text / sau
     *  xuống dòng / sau ".!?…" + khoảng trắng (qua ngoặc, nháy đóng). */
    fun sentenceBoundary(before: CharSequence?): Boolean {
        if (before == null) return true
        var i = before.length
        var sawWs = false
        while (i > 0 && before[i - 1].isWhitespace()) {
            if (before[i - 1] == '\n') return true
            sawWs = true
            i--
        }
        if (i == 0) return true
        while (i > 0 && before[i - 1] in closers) i--
        if (i == 0) return true
        return sawWs && before[i - 1] in sentenceEnd
    }

    /** Text (đã bỏ vùng composing) kết thúc bằng ký tự nối token không —
     *  tức từ đang gõ nằm trong url/email/ip/giờ (1.1). */
    fun gluedToken(before: CharSequence?): Boolean =
        before != null && before.isNotEmpty() && before.last() in tokenGlue
}
