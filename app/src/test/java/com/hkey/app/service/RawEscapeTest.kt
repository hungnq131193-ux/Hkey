package com.hkey.app.service

import android.text.InputType
import com.hkey.app.engine.EngineOptions
import com.hkey.app.engine.TelexEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.5.1: ba lỗi user báo sau 1.5.0 —
 *  1) ENTER xuống dòng không làm mới thanh gợi ý (ứng viên dòng cũ còn treo);
 *  2) transform ra âm tiết VN hợp lệ ("max"->"mã") không có lối thoát về
 *     phím thô — ô cuối phải luôn là chữ đã gõ, chạm = học -> giữ nguyên
 *     các lần sau;
 *  3) viết tắt Telex ra chuỗi không nguyên âm ("ddc"->"đc") bị hoàn nguyên
 *     phím thô cả khi hiển thị lẫn khi chốt. */
@RunWith(RobolectricTestRunner::class)
class RawEscapeTest {

    private class QueuePoster {
        private val q = ArrayDeque<Runnable>()
        val post: (Runnable) -> Unit = { q.addLast(it) }
        fun pump(n: Int = Int.MAX_VALUE) {
            var k = n
            while (k-- > 0 && q.isNotEmpty()) q.removeFirst().run()
        }
    }

    @After fun resetWorker() { HKeyIME.workerPosterOverride = null }

    private fun intField(ime: HKeyIME, name: String): Int =
        HKeyIME::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.getInt(ime)

    private fun candView(ime: HKeyIME, name: String): android.widget.TextView =
        HKeyIME::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(ime) as android.widget.TextView

    private fun invoke(ime: HKeyIME, name: String, arg: String) {
        HKeyIME::class.java.getDeclaredMethod(name, String::class.java)
            .apply { isAccessible = true }.invoke(ime, arg)
    }

    // ---------- 1) dòng mới phải xoá gợi ý cũ ----------

    @Test
    fun enterClearsStaleCandidates() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        qp.pump(); h.idle()

        // Thanh đang hiện ứng viên của dòng cũ.
        candView(h.ime, "candidate2").text = "toi"
        val genBefore = intField(h.ime, "suggestGen")

        h.type("\n"); h.idle()

        // ENTER phải xoá ứng viên cũ ngay + vô hiệu request đang bay —
        // không để từ cũ treo lại khi bắt đầu gõ dòng mới (lỗi user báo).
        assertEquals("", candView(h.ime, "candidate2").text.toString())
        assertTrue(intField(h.ime, "suggestGen") > genBefore)
    }

    // ---------- 2) lối thoát về phím thô ----------

    @Test
    fun rawCandidateOfferedWhenTransformed() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness()
        qp.pump()
        // "max" hiển thị "mã" — phải có ô chứa phím thô "max" để user chọn.
        val c = h.ime.computeCandidates(
            HKeyIME.SuggestRequest(1, "mã", null, "ok", "", false, raw = "max")
        )
        assertEquals("mã", c.c2)
        assertTrue(c.c1 == "max" || c.c3 == "max")
    }

    @Test
    fun learnedRawStaysLiteralEndToEnd() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness()
        qp.pump(); h.idle()

        h.type("max")
        qp.pump(); h.idle()
        assertEquals("mã", h.composing())

        // User chạm ô phím thô "max" -> chốt nguyên văn + học.
        invoke(h.ime, "acceptSuggestion", "max")
        qp.pump(); h.idle()
        assertEquals("ok max ", h.text())

        // Đã học -> lần sau "max" giữ nguyên, không bị bẻ thành "mã" nữa.
        h.type("max")
        qp.pump(); h.idle()
        assertEquals("max", h.composing())
    }

    // ---------- 3) viết tắt không nguyên âm ----------

    @Test
    fun consonantAbbrevNotRestored() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness()
        qp.pump(); h.idle()

        h.type("ddc")
        assertEquals("đc", h.composing()) // hiển thị luôn, không treo "ddc"
        h.type(" ")
        qp.pump(); h.idle()
        assertEquals("ok đc ", h.text())
    }

    @Test
    fun consonantAbbrevKeepsCase() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness()
        qp.pump(); h.idle()
        h.type("Ddc ")
        qp.pump(); h.idle()
        assertEquals("ok Đc ", h.text())
    }

    /** Hồi quy: phím thô CÓ nguyên âm vẫn được bảo vệ khi transform ra
     *  chuỗi không phải âm tiết ("window"->"windoư" -> giữ "window"). */
    @Test
    fun voweledRawStillRestored() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness()
        qp.pump(); h.idle()
        h.type("window ")
        qp.pump(); h.idle()
        assertEquals("ok window ", h.text())
    }

    // ---------- engine-level: cổng từ đã biết ----------

    @Test
    fun knownInputStaysLiteral() {
        val e = TelexEngine(EngineOptions(commonWord = { it == "max" }))
        assertEquals("max", e.transform("max"))
        assertEquals("Max", e.transform("Max"))
        // Từ chưa biết vẫn transform bình thường
        assertEquals("má", e.transform("mas"))
    }
}
