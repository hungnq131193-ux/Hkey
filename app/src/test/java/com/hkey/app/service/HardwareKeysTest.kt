package com.hkey.app.service

import android.view.KeyEvent
import com.hkey.app.settings.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.4.0 (H1): phím cứng — classify thuần + e2e qua HKeyIME.onKeyDown.
 *  KeyEvent dựng bằng constructor đầy đủ (action/code/repeat/meta). */
@RunWith(RobolectricTestRunner::class)
class HardwareKeysTest {

    /** deviceId=3: ctor 6 tham số đặt -1 = VIRTUAL_KEYBOARD -> usable()=false. */
    private fun ev(code: Int, meta: Int = 0, repeat: Int = 0) =
        KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, repeat, meta, 3, 0)

    private fun harness() = ImeHarness(prefsSetup = {
        putBoolean(SettingsKeys.HW_KEYBOARD, true)
    })

    /** 'a'..'z' -> KEYCODE_A..Z (keyCodeFromString không map chữ thường). */
    private fun codeOf(c: Char) = KeyEvent.KEYCODE_A + (c.lowercaseChar() - 'a')

    private fun down(h: ImeHarness, code: Int, meta: Int = 0, repeat: Int = 0) =
        h.ime.onKeyDown(code, ev(code, meta, repeat))

    private fun press(h: ImeHarness, code: Int, meta: Int = 0): Boolean {
        val r = down(h, code, meta)
        h.ime.onKeyUp(code, KeyEvent(0, 1, KeyEvent.ACTION_UP, code, 0, meta, 3, 0))
        return r
    }

    // ------------------------------------------------ classify thuần

    @Test
    fun classifyBasics() {
        assertEquals("v", (HardwareKeys.classify(ev(KeyEvent.KEYCODE_V)) as HardwareKeys.Key.Char).c)
        assertTrue(HardwareKeys.classify(ev(KeyEvent.KEYCODE_DEL)) is HardwareKeys.Key.Del)
        assertTrue(HardwareKeys.classify(ev(KeyEvent.KEYCODE_FORWARD_DEL)) is HardwareKeys.Key.ForwardDel)
        assertTrue(HardwareKeys.classify(ev(KeyEvent.KEYCODE_DPAD_LEFT)) is HardwareKeys.Key.Arrow)
        assertTrue(HardwareKeys.classify(ev(KeyEvent.KEYCODE_ESCAPE)) is HardwareKeys.Key.Esc)
        assertEquals("5", (HardwareKeys.classify(ev(KeyEvent.KEYCODE_5)) as HardwareKeys.Key.Digit).c)
    }

    @Test
    fun shiftMakesUpper() {
        val k = HardwareKeys.classify(ev(KeyEvent.KEYCODE_V, KeyEvent.META_SHIFT_ON))
        assertEquals("V", (k as HardwareKeys.Key.Char).c)
    }

    @Test
    fun capsLockXorShift() {
        val both = HardwareKeys.classify(
            ev(KeyEvent.KEYCODE_V, KeyEvent.META_SHIFT_ON or KeyEvent.META_CAPS_LOCK_ON)
        )
        assertEquals("v", (both as HardwareKeys.Key.Char).c)
        val caps = HardwareKeys.classify(ev(KeyEvent.KEYCODE_V, KeyEvent.META_CAPS_LOCK_ON))
        assertEquals("V", (caps as HardwareKeys.Key.Char).c)
    }

    // ------------------------------------------------ e2e qua IME

    @Test
    fun typeVietnamese() {
        val h = harness()
        for (c in "vieetj") assertTrue(press(h, codeOf(c)))
        press(h, KeyEvent.KEYCODE_SPACE)
        assertEquals("ok việt ", h.text())
    }

    @Test
    fun shiftTypesUpperCase() {
        val h = harness()
        press(h, KeyEvent.KEYCODE_V, KeyEvent.META_SHIFT_ON)
        press(h, KeyEvent.KEYCODE_I)
        assertEquals("Vi", h.composing())
    }

    @Test
    fun ctrlCNotConsumed() {
        val h = harness()
        val consumed = down(h, KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON)
        assertFalse(consumed) // trả super -> app vẫn nhận Ctrl+C
    }

    @Test
    fun shiftSpaceTogglesLang() {
        val h = harness()
        val before = langOf(h)
        assertTrue(press(h, KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON))
        assertEquals(!before, langOf(h))
        press(h, KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON)
        assertEquals(before, langOf(h))
    }

    @Test
    fun delDeletesOneDisplayChar() {
        val h = harness()
        for (c in "viet") press(h, codeOf(c))
        val comp = h.composing()
        assertTrue(comp.isNotEmpty())
        press(h, KeyEvent.KEYCODE_DEL)
        assertEquals(comp.length - 1, h.composing().length)
    }

    @Test
    fun delRepeats() {
        val h = harness()
        for (c in "viet") press(h, codeOf(c))
        val comp = h.composing().length
        down(h, KeyEvent.KEYCODE_DEL)                    // nhấn: xoá 1
        down(h, KeyEvent.KEYCODE_DEL, repeat = 1)        // giữ: xoá tiếp
        h.ime.onKeyUp(KeyEvent.KEYCODE_DEL, ev(KeyEvent.KEYCODE_DEL))
        assertEquals(comp - 2, h.composing().length)
    }

    @Test
    fun arrowCommitsAndPasses() {
        val h = harness()
        for (c in "viet") press(h, codeOf(c))
        val consumed = down(h, KeyEvent.KEYCODE_DPAD_LEFT)
        assertFalse(consumed) // app tự xử lý mũi tên
        assertEquals("", h.composing()) // từ đã được chốt trước khi trả app
    }

    @Test
    fun hwDisabledFallsThrough() {
        val h = ImeHarness() // HW_KEYBOARD mặc định off
        assertFalse(down(h, KeyEvent.KEYCODE_V))
    }

    private fun langOf(h: ImeHarness): Boolean {
        val f = HKeyIME::class.java.getDeclaredField("vietMode")
        f.isAccessible = true
        return f.getBoolean(h.ime)
    }
}
