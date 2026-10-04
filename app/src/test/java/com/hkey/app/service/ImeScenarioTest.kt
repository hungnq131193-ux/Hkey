package com.hkey.app.service

import android.text.InputType
import android.view.KeyEvent
import com.hkey.app.ui.KbKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.5.3: bổ sung kịch bản FakeInputConnection còn thiếu —
 *  select-all+⌫, ⌫ giữa từ đã commit, dấu câu chốt từ, emoji + ⌫ trọn
 *  grapheme, ô TYPE_NULL (app không báo text). */
@RunWith(RobolectricTestRunner::class)
class ImeScenarioTest {

    /** Bôi đen cả ô rồi ⌫ -> xoá trọn vùng chọn (không xoá 1 ký tự lề). */
    @Test
    fun selectAllThenDeleteClearsField() {
        val h = ImeHarness(initialText = "xoa het")
        val os = h.conn.selStart; val oe = h.conn.selEnd
        h.conn.setSelection(0, 7)
        h.notifySel(os, oe, 0, 7)
        h.type("⌫")
        assertEquals("", h.text())
    }

    /** ⌫ khi con trỏ đứng GIỮA từ đã commit -> xoá đúng 1 grapheme trước
     *  con trỏ, phần sau giữ nguyên. */
    @Test
    fun backspaceMidCommittedWord() {
        val h = ImeHarness(initialText = "tien viet")
        h.moveCursor(3) // "tie|n viet"
        h.type("⌫")
        assertEquals("tin viet", h.text())
    }

    /** ⌫ đang composing xoá 1 ký tự HIỂN THỊ ("đốc"⌫ -> "đố"), buffer
     *  thô suy ngược lại "ddoos" — không xoá tràn 1 phím thô. */
    @Test
    fun backspaceDropsDisplayCharInComposing() {
        val h = ImeHarness()
        h.type("ddoocs")
        assertEquals("đốc", h.composing())
        h.type("⌫")
        assertEquals("đố", h.composing())
    }

    /** Dấu câu chốt từ đang gõ rồi commit chính nó: "vasng," -> "váng,". */
    @Test
    fun punctCommitsComposingThenItself() {
        val h = ImeHarness()
        h.type("vasng,")
        assertEquals("ok váng,", h.text())
        assertEquals("", h.composing())
    }

    /** Emoji (tx:) commit nguyên văn; ⌫ xoá TRỌN grapheme (surrogate pair)
     *  không để lại "�". */
    @Test
    fun emojiCommitAndGraphemeBackspace() {
        val h = ImeHarness()
        h.ime.dispatchKey(KbKey("tx:😀"))
        assertEquals("ok 😀", h.text())
        h.type("⌫")
        assertEquals("ok ", h.text())
    }

    /** Ô TYPE_NULL (terminal/app không báo text): chữ vẫn đi qua commitText,
     *  ⌫/Enter gửi phím cứng thật thay vì deleteSurroundingText. */
    @Test
    fun typeNullFieldUsesHardwareKeys() {
        val h = ImeHarness(inputType = InputType.TYPE_NULL, initialText = "")
        h.type("ab")
        assertEquals("ab", h.text())
        h.type("⌫")
        assertTrue(h.conn.keyEvents.any { it.keyCode == KeyEvent.KEYCODE_DEL })
        assertEquals("ab", h.text()) // app tự xoá, IME không đụng text
        h.type("\n")
        assertTrue(h.conn.keyEvents.any { it.keyCode == KeyEvent.KEYCODE_ENTER })
    }
}
