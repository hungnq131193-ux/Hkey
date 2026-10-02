package com.hkey.app.engine

import android.text.InputType

/**
 * Phân loại ô nhập (B1, B2): RAW = mật khẩu/email/URL, ô bật cờ
 * NO_SUGGESTIONS, và mọi ô không phải TYPE_CLASS_TEXT — gõ thẳng, không
 * Telex, không gợi ý, không tự học. Thuần phép toán trên int nên test được
 * trên JVM (hằng InputType inline lúc compile).
 */
object FieldMode {

    fun isRaw(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return true
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return true
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
}
