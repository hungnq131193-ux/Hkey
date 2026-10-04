package com.hkey.app.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.4.0 (T0): chứng minh harness Robolectric chạy được IME thật —
 *  gõ Telex qua dispatchKey ra đúng chữ commit vào ô. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImeSmokeTest {

    @Test
    fun telex_basicWord_commitsTransformed() {
        val h = ImeHarness()
        h.type("vieetj ")
        h.idle()
        assertEquals("ok việt ", h.text())
    }

    @Test
    fun telex_composing_showsTransformed() {
        val h = ImeHarness()
        h.type("hoas")
        assertEquals("hoá", h.composing())
    }

    @Test
    fun backspace_editsComposing() {
        val h = ImeHarness()
        h.type("vietj")
        h.type("⌫")
        // "việt"⌫: không có chuỗi raw ra "việ" -> fallback "vie" (hành vi 1.7)
        assertEquals("vie", h.composing())
    }

    @Test
    fun english_rawKeys_keptAsTyped() {
        val h = ImeHarness()
        h.type("window ")
        h.idle()
        // transform("window") -> "windơ" không phải âm tiết -> chốt phím thô
        assertEquals("ok window ", h.text())
    }
}
