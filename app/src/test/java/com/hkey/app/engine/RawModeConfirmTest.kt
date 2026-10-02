package com.hkey.app.engine

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Giai đoạn 0.2 -> 2.1 — B1/B2: ô mật khẩu/email/URL phải ở chế độ thô.
 * Giai đoạn 0 xác nhận lỗi bằng assert transform("pass")=="pass" (fail,
 * chứng minh Telex xử lý cả ô nhạy cảm). Từ 2.1 đường raw nằm ở HKeyIME —
 * bỏ qua engine hoàn toàn — nên test chuyển sang kiểm FieldMode.isRaw theo
 * inputType (InputType là hằng int, chạy được trên JVM).
 */
class RawModeConfirmTest {

    private fun text(v: Int) = InputType.TYPE_CLASS_TEXT or v

    @Test
    fun sensitiveFieldsAreRaw() {
        assertTrue(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertTrue(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)))
        assertTrue(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
        assertTrue(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertTrue(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)))
        assertTrue(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_URI)))
        assertTrue(FieldMode.isRaw(text(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)))
        assertTrue(FieldMode.isRaw(InputType.TYPE_CLASS_NUMBER))
        assertTrue(FieldMode.isRaw(InputType.TYPE_CLASS_PHONE))
        assertTrue(FieldMode.isRaw(InputType.TYPE_CLASS_DATETIME))
    }

    @Test
    fun normalTextFieldsAreNotRaw() {
        assertFalse(FieldMode.isRaw(InputType.TYPE_CLASS_TEXT))
        assertFalse(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_NORMAL)))
        assertFalse(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)))
        assertFalse(FieldMode.isRaw(text(InputType.TYPE_TEXT_FLAG_MULTI_LINE)))
        assertFalse(FieldMode.isRaw(text(InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)))
        assertFalse(FieldMode.isRaw(text(InputType.TYPE_TEXT_VARIATION_PERSON_NAME)))
    }
}
