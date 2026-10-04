package com.hkey.app.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.4.0 (R1): đoạn mẫu Phụ lục A gõ qua IME thật — regression cho
 *  transform + commit + space async fix. Phím đã sửa theo chú thích plan
 *  ("tự sửa chính tả chuỗi phím nếu lệch"): boj->booj, baarn->barn,
 *  queee->quee, toi->tooi, hoc->hocj, baawng->bawfng, ddaapj->dapj,
 *  tren->treen, roi->roif, gui->guir, sep->seesp. */
@RunWith(RobolectricTestRunner::class)
class SampleParagraphTest {

    @Test
    fun phuLucA() {
        val h = ImeHarness(initialText = "")
        h.type(
            "Tooi ddang gox thuwr booj gox HKey phieen barn mowis. " +
                "Nguwowif Vieejt Nam raats yeeu quee huwowng. " +
                "Thuowr nhor, tooi hay ddi hocj bawfng xe ddapj. " +
                "Hoaf bifnh, khoer manhj, thuyr thur. " +
                "Download file treen Google Drive rooif guwir email cho seesp nhes!"
        )
        h.idle()
        assertEquals(
            "Tôi đang gõ thử bộ gõ HKey phiên bản mới. " +
                "Người Việt Nam rất yêu quê hương. " +
                "Thuở nhỏ, tôi hay đi học bằng xe đạp. " +
                "Hoà bình, khoẻ mạnh, thuỷ thủ. " +
                "Download file trên Google Drive rồi gửi email cho sếp nhé!",
            h.text()
        )
    }
}
