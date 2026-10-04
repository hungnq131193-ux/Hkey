package com.hkey.app.service

import android.view.KeyCharacterMap
import android.view.KeyEvent

/** 1.4.0 (H1): phân loại phím vật lý — hàm thuần trên KeyEvent, không
 *  đụng InputConnection/UI nên test được bằng Robolectric. */
object HardwareKeys {

    sealed class Key {
        /** Chữ cái — đã áp Shift xor CapsLock. */
        data class Char(val c: String) : Key()
        data class Digit(val c: String) : Key()
        /** Ký tự in được còn lại (unicodeChar). */
        data class Punct(val s: String) : Key()
        object Space : Key()
        object Enter : Key()
        object Del : Key()
        object ForwardDel : Key()
        /** Mũi tên + Home/End/PageUp/Down — app tự xử lý. */
        object Arrow : Key()
        object Tab : Key()
        object Esc : Key()
        object Other : Key()
    }

    /** Meta khiến IME bỏ qua: giữ Ctrl+C/V/A, Alt+Tab, phím hệ thống. */
    const val SKIP_META = KeyEvent.META_CTRL_ON or
        KeyEvent.META_ALT_ON or KeyEvent.META_META_ON

    /** Sự kiện đến từ bàn phím cứng thật, không phải phím hệ thống. */
    fun usable(event: KeyEvent): Boolean =
        !event.isSystem && event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD

    fun classify(event: KeyEvent): Key = when (event.keyCode) {
        KeyEvent.KEYCODE_DEL -> Key.Del
        KeyEvent.KEYCODE_FORWARD_DEL -> Key.ForwardDel
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Key.Enter
        KeyEvent.KEYCODE_SPACE -> Key.Space
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MOVE_HOME,
        KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN -> Key.Arrow
        KeyEvent.KEYCODE_TAB -> Key.Tab
        KeyEvent.KEYCODE_ESCAPE -> Key.Esc
        else -> {
            val u = event.unicodeChar
            val c = u.toChar()
            when {
                u == 0 -> Key.Other
                c.isLetter() -> {
                    // Hoa theo Shift xor CapsLock theo spec — unicodeChar đã
                    // áp meta, chuẩn hoá lại về base rồi áp xor để kết quả
                    // đúng cả khi shadow/framework tính khác nhau.
                    val up = event.isShiftPressed xor event.isCapsLockOn
                    val base = c.lowercaseChar()
                    Key.Char((if (up) base.uppercaseChar() else base).toString())
                }
                c.isDigit() -> Key.Digit(c.toString())
                else -> Key.Punct(c.toString())
            }
        }
    }
}
