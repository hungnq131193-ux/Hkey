package com.hkey.app.engine

import android.text.InputType

/**
 * Phân loại ô nhập (B1, B2): RAW = mật khẩu/email và mọi ô không phải
 * TYPE_CLASS_TEXT — gõ thẳng, không Telex, không gợi ý, không tự học.
 * Cờ NO_SUGGESTIONS (1.4) chỉ tắt gợi ý/sửa/học — Telex vẫn hoạt động,
 * xem noSuggestions(). Thuần phép toán trên int nên test được trên JVM
 * (hằng InputType inline lúc compile).
 */
object FieldMode {

    fun isRaw(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return true
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> true
            else -> false
        }
    }

    /** App xin đừng gợi ý/sửa (ô URI — thanh địa chỉ trình duyệt chỉ đánh
     *  dấu URI để chặn auto-correct, xem UrlBar.java của Chromium; cờ
     *  NO_SUGGESTIONS; ô code/tìm kiếm kỹ thuật…): vẫn Telex nhưng không
     *  candidate, không auto-correct, không học (1.4, 1.5.17). */
    fun noSuggestions(inputType: Int): Boolean =
        inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 ||
            inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_URI
}
