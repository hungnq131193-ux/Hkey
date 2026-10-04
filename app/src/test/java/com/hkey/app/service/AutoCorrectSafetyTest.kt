package com.hkey.app.service

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkey.app.settings.SettingsKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.5.0: từ gõ ĐÚNG không bị tự sửa — chặn nhánh xoá-1-ký-tự trên từ
 *  thuần ASCII ("plan"→"lan", "code"→"coe", "max"→"ma"), bảo vệ từ user
 *  vừa hoàn tác, và huỷ bản sửa nền khi đổi ô nhập. */
@RunWith(RobolectricTestRunner::class)
class AutoCorrectSafetyTest {

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

    private fun harness(
        p: QueuePoster,
        prefsSetup: (android.content.SharedPreferences.Editor.() -> Unit)? = null
    ): ImeHarness {
        HKeyIME.workerPosterOverride = p.post
        val h = ImeHarness(prefsSetup = prefsSetup)
        p.pump(); h.idle() // nạp dict đồng bộ
        return h
    }

    /** "plan"→"lan", "code"→"coe", "max"→"ma" không được xảy ra: từ
     *  thuần ASCII không có glyph Việt thì không có bằng chứng "phím thừa
     *  quanh dấu" -> repair-xoá không được chạy. */
    @Test
    fun englishLikeWordsNotShrunkOnSpace() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("plan ")
        qp.pump(); h.idle()
        h.type("code ")
        qp.pump(); h.idle()
        h.type("maxx ") // 'x' gõ đúp in chữ thật -> buffer hiển thị "max"
        qp.pump(); h.idle()
        assertEquals("ok plan code max ", h.text())
    }

    /** VNI: phím dấu là số nên "max"/"plan" đi thẳng ra buffer nguyên
     *  trạng — vẫn không được xoá chữ ở bản sửa nền. */
    @Test
    fun vniAsciiWordsNotShrunk() {
        val qp = QueuePoster()
        val h = harness(qp) { putString(SettingsKeys.METHOD, "vni") }
        h.type("max plan ")
        qp.pump(); h.idle()
        assertEquals("ok max plan ", h.text())
    }

    /** Hoàn tác auto-fix phải HỌC từ user gõ — lần sau cùng phím thô
     *  không bị sửa lại. Trước đây learn() bỏ qua từ "trông như typo"
     *  nên từ vừa hoàn tác cứ bị sửa oan mãi. */
    @Test
    fun revertedWordIsLearnedAndStays() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("khoogn ")
        qp.pump(); h.idle()
        assertEquals("ok không ", h.text())
        h.type("⌫") // hoàn tác -> "không" đổi về buffer thô "khoogn" composing
        h.type(" ") // chốt lại y nguyên (live restore -> commit phím thô)
        qp.pump(); h.idle()
        assertEquals("ok khoogn ", h.text()) // không được sửa lại thành "không"
        h.type("khoogn ")
        qp.pump(); h.idle()
        assertEquals("ok khoogn khoogn ", h.text())
    }

    /** Bản sửa nền chưa kịp áp mà user đã rời ô -> phải bỏ, không sửa
     *  lùi vào ô mới. */
    @Test
    fun pendingFixDroppedOnFieldSwitch() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("khoogn ") // commit xong, fix đang nằm trong hàng đợi
        h.ime.onStartInput(
            EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT
                packageName = "com.hkey.test2"
            },
            false
        )
        qp.pump(); h.idle()
        assertEquals("ok khoogn ", h.text())
    }
}
