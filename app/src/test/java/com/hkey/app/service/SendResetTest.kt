package com.hkey.app.service

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkey.app.settings.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.5.6: app tự xoá toàn bộ text (ô chat gửi tin) trong lúc IME còn từ
 *  gõ dở -> IME KHÔNG được ghi lại từ đó; phím kế tiếp chỉ in đúng chữ nó.
 *  Ô rỗng bật auto-cap -> tắt AUTO_CAP để gõ chữ thường.
 *
 *  Đường vào bug: onUpdateSelection (con trỏ nhảy về 0, app đã bỏ vùng
 *  composing) -> finalizeWord -> commitWord gọi setComposingText vì
 *  word != currentDisplay -> hồi sinh text app vừa xoá. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SendResetTest {

    private fun harness(prefsSetup: (android.content.SharedPreferences.Editor.() -> Unit)? = null) =
        ImeHarness(initialText = "") {
            putBoolean(SettingsKeys.AUTO_CAP, false)
            prefsSetup?.let { it() }
        }

    /** App xoá trắng ô rồi báo lại selection — y như EditText.setText("") +
     *  framework giao onUpdateSelection cho IME. */
    private fun appClearsAll(h: ImeHarness, oldPos: Int) {
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        h.notifySel(oldPos, oldPos, 0, 0)
    }

    @Test
    fun externalClear_macroWord_staysEmpty() {
        val h = harness {
            putString(SettingsKeys.MACROS, "om=ông mày")
        }
        h.type("om")                 // composing "om", con trỏ 2
        appClearsAll(h, 2)
        h.idle()
        assertEquals("", h.text())
        h.type("a")
        h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun externalClear_vietnameseWord_staysEmpty() {
        val h = harness()
        h.type("vieetj")             // composing "việt"
        appClearsAll(h, 5)
        h.idle()
        assertEquals("", h.text())
        h.type("a")
        h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun externalClear_englishWord_staysEmpty() {
        val h = harness()
        h.type("new")                // live restore hiển thị thô "new"
        appClearsAll(h, 3)
        h.idle()
        assertEquals("", h.text())
        h.type("a")
        h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun externalClear_restartTrue_staleRawDoesNotReturn() {
        val h = harness()
        h.type("vieetj")
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        // Ô focus lại với restarting=true: buffer gõ dở được giữ (framework
        // cho phép tiếp tục gõ), nhưng app đã xoá text rồi.
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            initialSelStart = 0
            initialSelEnd = 0
            packageName = "com.hkey.test"
            fieldId = 1
        }
        h.ime.onStartInput(info, true)
        h.ime.onStartInputView(info, true)
        h.notifySel(0, 0, 0, 0)      // callback selection sau restart
        h.idle()
        h.type("a")
        h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun imeActionSend_appClearsDuringAction_noStaleText() {
        val h = ImeHarness(
            initialText = "",
            imeOptions = EditorInfo.IME_ACTION_SEND
        ) {
            putBoolean(SettingsKeys.AUTO_CAP, false)
            putString(SettingsKeys.MACROS, "om=ông mày")
        }
        h.conn.onEditorAction = {
            h.conn.text.clear()
            h.conn.finishComposingText()
            h.conn.setSelection(0, 0)
        }
        h.type("om")
        h.type("\n")                 // commit "ông mày" rồi performEditorAction
        h.idle()
        assertEquals(EditorInfo.IME_ACTION_SEND, h.conn.editorActions.lastOrNull())
        assertEquals("", h.text())
        h.type("a")
        h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun externalReplace_noComposing_discardsBuffer() {
        val h = harness {
            putString(SettingsKeys.MACROS, "om=ông mày")
        }
        h.type("om")
        h.conn.text.replace(0, h.conn.text.length, "hi")
        h.conn.finishComposingText()
        h.conn.setSelection(2, 2)
        h.notifySel(2, 2, 2, 2)
        h.idle()
        assertEquals("hi", h.text())
        h.type("a")
        h.idle()
        assertEquals("hia", h.text())
    }

    @Test
    fun finishInputView_afterSilentClear_noResurrect() {
        val h = harness {
            putString(SettingsKeys.MACROS, "om=ông mày")
        }
        h.type("om")
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        h.ime.onFinishInputView(false)
        h.idle()
        assertEquals("", h.text())
    }

    @Test
    fun finishInput_afterSilentClear_noResurrect() {
        val h = harness {
            putString(SettingsKeys.MACROS, "om=ông mày")
        }
        h.type("om")
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        h.ime.onFinishInput()
        h.idle()
        assertEquals("", h.text())
    }

    @Test
    fun restart_unchangedComposing_keepsBuffer() {
        val h = harness()
        h.type("vieetj")
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            initialSelStart = 4
            initialSelEnd = 4
            packageName = "com.hkey.test"
            fieldId = 1
        }
        h.ime.onStartInput(info, true)
        h.ime.onStartInputView(info, true)
        h.notifySel(4, 4, 4, 4)
        h.idle()
        h.type("a")
        h.idle()
        assertEquals("vieetja", h.text())
        h.type("⌫")
        h.idle()
        assertEquals("việt", h.text())
    }

    @Test
    fun extractedTextNull_noCrash() {
        val h = harness {
            putString(SettingsKeys.MACROS, "om=ông mày")
        }
        h.type("om")
        h.conn.extractedOverride = { null }
        h.ime.onFinishInputView(false)
        h.ime.onFinishInput()
        h.idle()
        assertEquals("om", h.text())
    }

    private class QueuePoster {
        private val q = ArrayDeque<Runnable>()
        val post: (Runnable) -> Unit = { q.addLast(it) }
        fun pump() { while (q.isNotEmpty()) q.removeFirst().run() }
    }

    @Test
    fun pendingFix_afterClear_doesNotRecreate() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        try {
            val h = harness()
            h.type("khoogn ")
            h.conn.text.clear()
            h.conn.finishComposingText()
            h.conn.setSelection(0, 0)
            h.notifySel(7, 7, 0, 0)
            qp.pump()
            h.idle()
            assertEquals("", h.text())
        } finally {
            HKeyIME.workerPosterOverride = null
        }
    }
}
