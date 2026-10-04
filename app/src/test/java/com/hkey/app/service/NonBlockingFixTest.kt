package com.hkey.app.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.4.0 (P2/C2): space commit ngay, bản sửa chạy nền — áp khi con trỏ
 *  vẫn sau "typed + ' '" và user chưa gõ thêm; đã gõ -> bỏ. Dùng poster
 *  hàng đợi tay để điều khiển khi nào worker tính xong. */
@RunWith(RobolectricTestRunner::class)
class NonBlockingFixTest {

    private class QueuePoster {
        private val q = ArrayDeque<Runnable>()
        val post: (Runnable) -> Unit = { q.addLast(it) }
        fun pump(n: Int = Int.MAX_VALUE) {
            var k = n
            while (k-- > 0 && q.isNotEmpty()) q.removeFirst().run()
        }
    }

    @After fun resetWorker() {
        HKeyIME.workerPosterOverride = null
    }

    private fun harness(p: QueuePoster): ImeHarness {
        HKeyIME.workerPosterOverride = p.post
        val h = ImeHarness()
        p.pump(); h.idle() // nạp dict đồng bộ
        return h
    }

    @Test
    fun spaceCommitsNowAndFixesAsync() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("khoogn ")
        // Commit ngay, chưa chờ sửa: editor hiện phím thô + space
        assertEquals("ok khoogn ", h.text())
        qp.pump(); h.idle() // worker tính xong -> vẫn còn hiệu lực -> áp
        assertEquals("ok không ", h.text())
    }

    @Test
    fun pendingFixDroppedWhenUserTypesNext() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("khoogn a") // 'a' gõ trước khi worker kịp sửa
        qp.pump(); h.idle()
        assertEquals("ok khoogn a", h.text())
    }

    /** 1.4.2: "tiesnge" giờ transform thẳng thành "tiếng" hợp lệ ('e' cuối
     *  đúp 'e' kề 'i' -> ê) -> chốt luôn, không cần sửa nền. */
    @Test
    fun retroDoubleCommitsDirectly() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("tiesnge ")
        assertEquals("ok tiếng ", h.text())
    }

    /** 1.4.1: phím thừa quanh dấu — "tieesnge" (đúp ê + sắc sớm + thừa 'e'
     *  cuối) -> "tiếnge" không hợp lệ -> commit phím thô, sửa nền về
     *  "tiếng". */
    @Test
    fun extraCharAroundToneFixedAsync() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("tieesnge ")
        assertEquals("ok tieesnge ", h.text())
        qp.pump(); h.idle()
        assertEquals("ok tiếng ", h.text())
    }
}
