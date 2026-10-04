package com.hkey.app.service

import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.4.0 (P1/C1): SuggestWorker điều phối predictor; kết quả mang số hiệu
 *  gen — request cũ (đã có request mới hơn) bị bỏ khi về main. Dùng poster
 *  hàng đợi tay để sắp xếp thứ tự chạy xác định. */
@RunWith(RobolectricTestRunner::class)
class SuggestWorkerTest {

    private class QueuePoster {
        private val q = ArrayDeque<Runnable>()
        val post: (Runnable) -> Unit = { q.addLast(it) }
        fun pump(n: Int = Int.MAX_VALUE) {
            var k = n
            while (k-- > 0 && q.isNotEmpty()) q.removeFirst().run()
        }
        fun pending() = q.size
    }

    @After fun resetWorker() {
        HKeyIME.workerPosterOverride = null
    }

    private fun updateSug(ime: HKeyIME) {
        HKeyIME::class.java.getDeclaredMethod("updateSuggestions")
            .apply { isAccessible = true }.invoke(ime)
    }

    private fun cand(ime: HKeyIME, name: String): String =
        (HKeyIME::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(ime) as? TextView)
            ?.text?.toString() ?: ""

    @Test
    fun staleGenerationDiscarded() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness()
        qp.pump() // nạp dict + dựng index đồng bộ trên worker giả
        h.idle()

        h.type("v")
        updateSug(h.ime) // request gen1 (current "v") -> queue
        qp.pump(1)       // gen1 tính xong -> apply đăng lên main
        h.type("i")
        updateSug(h.ime) // request gen2 (current "vi") — gen1 giờ đã cũ
        h.idle()         // apply gen1 chạy: gen lệch -> phải bị bỏ
        // Nếu không có kiểm gen, ô giữa đang là "v" của request cũ
        assertTrue(cand(h.ime, "candidate2") != "v")

        qp.pump()        // gen2 tính -> apply
        h.idle()
        assertEquals("vi", cand(h.ime, "candidate2"))
    }

    /** 1.4.0 (P5/C7): trước khi dict sẵn sàng — không post request tới
     *  predictor, ô giữa hiện chữ đang gõ, hai bên trống, không crash. */
    @Test
    fun noSuggestBeforeDictReady() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness()
        // KHÔNG pump: task nạp dict còn trong hàng -> predictorReady=false
        h.type("viet")
        updateSug(h.ime)
        assertEquals("viet", cand(h.ime, "candidate2"))
        assertEquals("", cand(h.ime, "candidate1"))
        assertEquals("", cand(h.ime, "candidate3"))
        assertEquals(1, qp.pending()) // chỉ còn task nạp dict, không có request gợi ý

        qp.pump() // nạp xong -> ready
        h.idle()
        h.type("t")
        updateSug(h.ime)
        qp.pump()
        h.idle()
        assertTrue(cand(h.ime, "candidate2").isNotEmpty())
    }

    @Test
    fun computeCandidatesDeterministic() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness()
        qp.pump()
        val req = HKeyIME.SuggestRequest(1, "viet", null, "ok", "", false)
        val a = h.ime.computeCandidates(req)
        val b = h.ime.computeCandidates(req)
        assertEquals(a, b)
        // Ô giữa luôn hiện chữ đang gõ khi không có bản sửa
        assertEquals(req.current, a.c2.takeIf { it.isNotEmpty() } ?: req.current)
    }
}
