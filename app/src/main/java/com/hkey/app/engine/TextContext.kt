package com.hkey.app.engine

/**
 * Từ ngữ cảnh ngay trước con trỏ (B3). Chỉ lấy từ khi hai bên chỉ cách nhau
 * khoảng trắng; sau dấu kết câu . ! ? , xuống dòng \n hoặc ô trống trả về ""
 * (đầu câu — BOS). Dấu , ; : hiện giữ hành vi cũ (fallback), quyết ở Phase 3
 * khi có số liệu mô hình.
 */
object TextContext {

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
        if (i == 0 || sawNewline || before[i - 1] in ".!?") return "" to ""
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
        if (k == 0 || crossedNl || before[k - 1] in ".!?") return w1 to ""
        var m = k
        while (m > 0 && before[m - 1].isLetter()) m--
        return w1 to if (m < k) before.substring(m, k).toString() else ""
    }
}
