package com.hkey.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update con trỏ khớp một vị trí IME tự tạo (kể cả đến trễ) = self-edit,
 *  không phải người dùng dời con trỏ. */
class ExpectedSelsTest {

    @Test
    fun unchangedUpdate_isSelf() {
        val e = ExpectedSels()
        // không có pending nào, update đứng yên tại vị trí đã biết -> self
        assertTrue(e.isSelf(5, 5, 5, 5))
    }

    @Test
    fun lateUpdate_matchingQueued_isSelf() {
        val e = ExpectedSels()
        e.push(10) // commit xong, dự đoán con trỏ ở 10
        e.push(11) // gõ chữ đầu từ kế, dự đoán 11 (con trỏ theo dõi = 11)
        // update của commit TRƯỚC đến trễ: newSel=10, tracked=11
        assertTrue(e.isSelf(10, 10, 11, 11))
        // mục 10 đã dùng; 11 còn trong hàng
        assertEquals(1, e.size)
    }

    @Test
    fun popThrough_dropsCoalescedEarlier() {
        val e = ExpectedSels()
        e.push(3)
        e.push(7)
        e.push(9)
        // app gộp update, chỉ báo vị trí cuối: 3 và 7 bị dọn theo
        assertTrue(e.isSelf(9, 9, 5, 5))
        assertEquals(0, e.size)
    }

    @Test
    fun realMove_isNotSelf_andDrainsQueue() {
        val e = ExpectedSels()
        e.push(10)
        e.push(11)
        // người dùng chạm về đầu dòng — không khớp mục nào
        assertFalse(e.isSelf(2, 2, 11, 11))
        assertEquals(0, e.size) // hàng đã dọn, update sau không khớp nhầm
        assertFalse(e.isSelf(10, 10, 2, 2))
    }

    @Test
    fun confirmedPos_removed_cannotMatchRealMoveLater() {
        val e = ExpectedSels()
        e.push(5) // edit đầu tới pos 5
        // update "không đổi" xác nhận pos -> mục bị rút khỏi hàng
        assertTrue(e.isSelf(5, 5, 5, 5))
        assertEquals(0, e.size)
        // sau đó người dùng thật sự chạm lại pos 5 từ chỗ khác -> thật
        assertFalse(e.isSelf(5, 5, 9, 9))
    }

    @Test
    fun nonCollapsedUpdate_isNotSelf() {
        val e = ExpectedSels()
        e.push(4)
        // bôi chọn vùng -> luôn là thật (ta chỉ đẩy vị trí thu gọn)
        assertFalse(e.isSelf(2, 4, 4, 4))
    }

    @Test
    fun push_overCap_dropsOldest() {
        val e = ExpectedSels(4)
        repeat(6) { e.push(it) } // hàng = [2,3,4,5]
        assertEquals(4, e.size)
        // mục cũ nhất còn giữ (2) vẫn nhận; mục đã bị đẩy ra (1) không nhận
        assertTrue(e.isSelf(2, 2, 9, 9))
        assertFalse(e.isSelf(1, 1, 9, 9))
    }
}
