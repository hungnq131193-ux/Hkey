package com.hkey.app.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.4.0 (E1/A1): rời từ giữa chừng (chạm chỗ khác/đổi ô/ẩn phím) chốt từ
 *  đúng: áp restore phím thô + macro, KHÔNG auto-correct. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinalizeWordTest {

    @Test
    fun leaveWord_englishRestoresRaw() {
        val h = ImeHarness()
        h.type("new")            // hiển thị "neư"
        h.moveCursor(0)          // user chạm lên đầu ô
        h.idle()
        assertEquals("ok new", h.text())
    }

    @Test
    fun leaveWord_vietnameseCommitsTransformed() {
        val h = ImeHarness()
        h.type("vieetj")
        h.moveCursor(0)
        h.idle()
        assertEquals("ok việt", h.text())
    }

    @Test
    fun leaveWord_noAutoCorrect() {
        val h = ImeHarness()
        h.type("khoogn")         // "khôgn" hiển thị — restorable -> raw
        h.moveCursor(0)
        h.idle()
        // KHÔNG sửa thành "không"; restore phím thô theo luật chung
        assertEquals("ok khoogn", h.text())
    }

    @Test
    fun leaveWord_macroExpands() {
        val h = ImeHarness {
            putString(com.hkey.app.settings.SettingsKeys.MACROS, "om=ông mày")
        }
        h.type("om")
        h.moveCursor(0)
        h.idle()
        assertEquals("ok ông mày", h.text())
    }

    @Test
    fun finishInput_commitsComposing() {
        val h = ImeHarness()
        h.type("vieetj")
        h.ime.onFinishInput()
        h.idle()
        assertEquals("ok việt", h.text())
    }

    @Test
    fun finishInputView_commitsComposing() {
        val h = ImeHarness()
        h.type("vieetj")
        h.ime.onFinishInputView(false)
        h.idle()
        assertEquals("ok việt", h.text())
    }

    @Test
    fun leaveWord_displayedWordUnchanged_onlyFinishes() {
        // từ đúng hiển thị: finalize chỉ đóng vùng composing, text không đổi
        val h = ImeHarness()
        h.type("hoas")
        h.moveCursor(0)
        h.idle()
        assertEquals("ok hoá", h.text())
    }
}
