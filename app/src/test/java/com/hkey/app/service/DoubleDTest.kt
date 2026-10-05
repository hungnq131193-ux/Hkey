package com.hkey.app.service

import com.hkey.app.engine.EngineOptions
import com.hkey.app.engine.TelexEngine
import com.hkey.app.settings.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.5.6: gõ "dd" ra "đ" trong mọi nhịp callback selection — callback bình
 *  thường, không callback, callback đến trễ, và callback lặp "không đổi"
 *  sau khi app bỏ vùng composing. Cộng thêm cổng engine: Telex cơ bản
 *  (dd->đ) phải thắng từ đã học/đã biết "dd". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DoubleDTest {

    @Test
    fun dd_normalCallbacks_producesDStroke() {
        val h = ImeHarness(initialText = "") {
            putBoolean(SettingsKeys.AUTO_CAP, false)
        }
        h.type("d")
        h.notifySelCurrent()         // framework báo cursor sau 'd'
        h.type("d")
        h.idle()
        assertEquals("đ", h.composing())
    }

    @Test
    fun dd_noCallbacks_producesDStroke() {
        val h = ImeHarness(initialText = "") {
            putBoolean(SettingsKeys.AUTO_CAP, false)
        }
        h.type("dd")                 // FakeInputConnection không tự callback
        h.idle()
        assertEquals("đ", h.composing())
    }

    @Test
    fun dd_delayedFirstDCallback_keepsDStroke() {
        val h = ImeHarness(initialText = "") {
            putBoolean(SettingsKeys.AUTO_CAP, false)
        }
        h.type("d")                  // conn cursor = 1, chưa báo IME
        h.type("d")                  // -> "đ", conn cursor vẫn 1
        // callback của 'd' ĐẦU đến trễ sau khi 'đ' đã hiển thị
        h.notifySel(0, 0, 1, 1)
        h.idle()
        assertEquals("đ", h.composing())
    }

    @Test
    fun dd_appDropsComposing_noChangeCallbackKeepsText() {
        val h = ImeHarness(initialText = "") {
            putBoolean(SettingsKeys.AUTO_CAP, false)
        }
        h.type("dd")
        h.conn.finishComposingText() // app bỏ vùng composing (giữ text)
        h.notifySelCurrent()         // update "không đổi" lặp lại
        h.idle()
        assertEquals("đ", h.text())
        assertEquals(-1, h.conn.composingStart)
    }

    @Test
    fun dd_uppercase_producesUppercaseDStroke() {
        val h1 = ImeHarness(initialText = "") {
            putBoolean(SettingsKeys.AUTO_CAP, false)
        }
        h1.type("Dd")
        h1.idle()
        assertEquals("Đ", h1.composing())

        val h2 = ImeHarness(initialText = "") {
            putBoolean(SettingsKeys.AUTO_CAP, false)
        }
        h2.type("DD")
        h2.idle()
        assertEquals("Đ", h2.composing())
    }

    @Test
    fun dd_spaceFinishes() {
        val h = ImeHarness(initialText = "") {
            putBoolean(SettingsKeys.AUTO_CAP, false)
        }
        h.type("dd ")
        h.idle()
        assertEquals("đ ", h.text())
    }

    // --- Engine level ---

    @Test
    fun engine_ddLearnedStillConverts() {
        // "dd" nằm trong từ đã biết/đã học (user từng chọn phím thô) — luật
        // Telex cơ bản dd->đ vẫn phải chạy, không trả phím thô.
        val e = TelexEngine(EngineOptions(commonWord = { it == "dd" }))
        assertEquals("đ", e.transform("dd"))
    }

    @Test
    fun engine_learnedRawStillWins() {
        // Baseline 1.5.1: từ đã học giữ phím thô ("max" không bẻ thành "mã").
        val e = TelexEngine(EngineOptions(commonWord = { it == "max" }))
        assertEquals("max", e.transform("max"))
    }

    @Test
    fun engine_ddExemptAllMethods() {
        for (m in listOf(
            com.hkey.app.engine.ImeMethod.TELEX,
            com.hkey.app.engine.ImeMethod.TELEX_SIMPLE,
            com.hkey.app.engine.ImeMethod.TELEX_QUICK
        )) {
            val e = TelexEngine(EngineOptions(method = m, commonWord = { it == "dd" }))
            assertEquals("đ ($m)", "đ", e.transform("dd"))
        }
    }

    @Test
    fun engine_ddKnownMixedCase() {
        val e = TelexEngine(EngineOptions(commonWord = { it == "dd" }))
        assertEquals("Đ", e.transform("Dd"))
        assertEquals("Đ", e.transform("DD"))
    }

    @Test
    fun engine_dddEscapePreserved() {
        val e = TelexEngine(EngineOptions(commonWord = { it == "dd" }))
        assertEquals("dd", e.transform("ddd"))
    }

    @Test
    fun engine_knownEnglishContainingDdKept() {
        val e = TelexEngine(EngineOptions(commonWord = { it in setOf("add", "address", "max") }))
        assertEquals("add", e.transform("add"))
        assertEquals("address", e.transform("address"))
        assertEquals("max", e.transform("max"))
    }

    private class QueuePoster {
        private val q = ArrayDeque<Runnable>()
        val post: (Runnable) -> Unit = { q.addLast(it) }
        fun pump() { while (q.isNotEmpty()) q.removeFirst().run() }
    }

    @Test
    fun ime_learnedDdStillProducesDStroke() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val lf = java.io.File(ctx.filesDir, "learned_data.tsv")
        lf.writeText("hkey-learned-v2\nw\tdd\t5\t1\n")
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        try {
            val h = ImeHarness(initialText = "") {
                putBoolean(SettingsKeys.AUTO_CAP, false)
            }
            qp.pump(); h.idle()
            h.type("dd")
            h.idle()
            assertEquals("đ", h.composing())
        } finally {
            HKeyIME.workerPosterOverride = null
            lf.delete()
        }
    }
}
