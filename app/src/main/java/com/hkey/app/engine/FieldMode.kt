package com.hkey.app.engine

import android.text.InputType

/**
 * Phân loại ô nhập (B1, B2): RAW = mật khẩu/email/URL và mọi ô không phải
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
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> true
            else -> false
        }
    }

    /** App xin đừng gợi ý (code/ô tìm kiếm kỹ thuật…): vẫn Telex nhưng
     *  không candidate, không auto-correct, không học (1.4). */
    fun noSuggestions(inputType: Int): Boolean =
        inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
}
