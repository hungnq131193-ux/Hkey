package com.hkey.app.service

import android.view.inputmethod.EditorInfo
import com.hkey.app.settings.SettingsKeys
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.4.0 (S2/B4): cài đặt đổi áp ngay — key giao diện inflate lại khi đang
 *  hiện, key engine đánh dấu bẩn dựng lại lần hiện sau, cờ rẻ đọc lại tức
 *  thì. */
@RunWith(RobolectricTestRunner::class)
class SettingsApplyTest {

    private fun field(ime: HKeyIME, name: String): Any? =
        HKeyIME::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(ime)

    @Test
    fun uiPrefChangeWhileShownReinflatesView() {
        val h = ImeHarness()
        val v1 = field(h.ime, "inputView")
        h.ime.getSharedPreferences(SettingsKeys.PREFS, 0).edit()
            .putBoolean(SettingsKeys.NUMBER_ROW, true).commit()
        h.idle()
        val v2 = field(h.ime, "inputView")
        assertNotSame(v1, v2)
    }

    @Test
    fun enginePrefChangeOnlyDirtiesSig() {
        val h = ImeHarness()
        val v1 = field(h.ime, "inputView")
        // tone_new không thuộc nhóm giao diện -> không inflate giữa chừng
        h.ime.getSharedPreferences(SettingsKeys.PREFS, 0).edit()
            .putBoolean(SettingsKeys.TONE_NEW, false).commit()
        h.idle()
        assertSame(v1, field(h.ime, "inputView"))
        // Lần hiện tiếp theo dựng lại engine -> kiểu dấu cũ "hòa"
        val info = EditorInfo().apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            packageName = "com.hkey.test"
        }
        h.ime.onStartInputView(info, false)
        h.type("hoaf ")
        assertTrue(h.text().endsWith("hòa "))
    }

    @Test
    fun cheapFlagAppliesImmediately() {
        val h = ImeHarness()
        h.type("a  ") // a + 2 space nhanh -> ". " khi double_space bật
        assertTrue(h.text().endsWith("a. "))
        // Tắt cờ giữa chừng -> lần gõ sau không còn ". "
        h.ime.getSharedPreferences(SettingsKeys.PREFS, 0).edit()
            .putBoolean(SettingsKeys.DOUBLE_SPACE, false).commit()
        h.idle()
        h.type("b  ")
        assertTrue(h.text().endsWith("B  ")) // auto-cap sau ". " -> hoa
    }
}
