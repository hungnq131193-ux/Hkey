package com.hkey.app.service

/** Bản sao cục bộ ~[max] ký tự cuối ĐÃ COMMIT trước con trỏ — chỉ những gì
 *  chính IME ghi (commitText/deleteSurroundingText). Không chứa vùng
 *  composing. null = không biết -> caller đọc getTextBeforeCursor 1 lần rồi
 *  seed lại. Thuần Kotlin để test JVM được. */
class TailTracker(private val max: Int = 40) {

    var tail: String? = null
        private set

    fun seed(s: CharSequence) {
        tail = s.takeLast(max).toString()
    }

    fun invalidate() {
        tail = null
    }

    /** Ghi thêm text vừa commit; tail chưa biết thì KHÔNG tự bịa. */
    fun append(s: String) {
        val t = tail ?: return
        tail = (t + s).takeLast(max)
    }

    /** Xoá n ký tự trước con trỏ; nhiều hơn số đã biết -> về null (đọc lại). */
    fun drop(n: Int) {
        val t = tail ?: return
        tail = if (n < t.length) t.dropLast(n) else null
    }
}
