package com.hkey.app.service

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputBinding
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.ui.KbKey
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController

/** 1.4.0 (T0): khởi động HKeyIME thật dưới Robolectric với FakeInputConnection.
 *  Quyết định: dùng Robolectric (đủ khả năng khởi tạo InputMethodService),
 *  không cần tách TypingController.
 *
 *  Đường vào: InputMethodImpl.bindInput -> mInputConnection, rồi gọi
 *  onStartInput/onStartInputView trực tiếp (public callback). Mọi thay đổi
 *  selection từ phía "app" phải báo lại qua [notifySel] — y như framework. */
class ImeHarness(
    private val inputType: Int = InputType.TYPE_CLASS_TEXT,
    imeOptions: Int = 0,
    /** Text có sẵn trong ô (mặc định "ok " = giữa câu, không auto-cap). */
    initialText: String = "ok ",
    private val prefsSetup: (SharedPreferences.Editor.() -> Unit)? = null
) {
    private val controller: ServiceController<HKeyIME> =
        Robolectric.buildService(HKeyIME::class.java)
    val ime: HKeyIME = controller.create().get()
    val conn = FakeInputConnection()
    private val info = EditorInfo()

    init {
        if (prefsSetup != null) {
            ime.getSharedPreferences(SettingsKeys.PREFS, 0).edit()
                .apply(prefsSetup).apply()
        }
        conn.text.append(initialText)
        conn.setSelection(initialText.length, initialText.length)
        info.inputType = inputType
        info.imeOptions = imeOptions
        info.initialSelStart = initialText.length
        info.initialSelEnd = initialText.length
        info.packageName = "com.hkey.test"
        info.fieldId = 1
        val impl = ime.onCreateInputMethodInterface()
        impl.bindInput(InputBinding(conn, android.os.Binder(), 0, 35))
        ime.onStartInput(info, false)
        ime.onStartInputView(info, false)
    }

    /** Pump main looper (postOnAnimation, worker posts về main). */
    fun idle() = Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    /** Báo selection mới từ phía app (gọi sau mỗi edit ngoài IME). */
    fun notifySel(oldS: Int, oldE: Int, newS: Int, newE: Int) {
        ime.onUpdateSelection(
            oldS, oldE, newS, newE, conn.composingStart, conn.composingEnd
        )
    }

    /** Báo selection hiện tại như framework sau commit (cursor = selEnd). */
    fun notifySelCurrent() {
        val s = conn.selEnd
        ime.onUpdateSelection(s, s, s, s, conn.composingStart, conn.composingEnd)
    }

    /** Gõ chuỗi phím: chữ -> ch:, số/dấu -> p:, ' ' -> space, '\n' -> enter,
     *  '⌫' -> del. */
    fun type(keys: String) {
        for (c in keys) when (c) {
            ' ' -> ime.dispatchKey(KbKey("fn:space"))
            '\n' -> ime.dispatchKey(KbKey("fn:enter"))
            '⌫' -> ime.dispatchKey(KbKey("fn:del"))
            in 'a'..'z', in 'A'..'Z' -> ime.dispatchKey(KbKey("ch:$c"))
            else -> ime.dispatchKey(KbKey("p:$c"))
        }
    }

    /** App dời con trỏ tới pos rồi báo lại IME. */
    fun moveCursor(pos: Int) {
        val os = conn.selStart; val oe = conn.selEnd
        conn.setSelection(pos, pos)
        notifySel(os, oe, pos, pos)
    }

    fun text(): String = conn.text.toString()

    /** Chữ đang ở vùng composing trong ô (rỗng nếu không còn gõ dở). */
    fun composing(): String =
        if (conn.composingStart >= 0)
            conn.text.substring(conn.composingStart, conn.composingEnd)
        else ""
}
