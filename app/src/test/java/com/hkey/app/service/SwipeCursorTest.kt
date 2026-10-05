package com.hkey.app.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeCursorTest {

    private fun swipe(ime: HKeyIME, dir: Int) {
        HKeyIME::class.java.getDeclaredMethod("swipeCursor", Int::class.java)
            .apply { isAccessible = true }.invoke(ime, dir)
    }

    private fun setup(h: ImeHarness, text: String, cursor: Int) {
        h.conn.text.clear()
        h.conn.text.append(text)
        val os = h.conn.selStart; val oe = h.conn.selEnd
        h.conn.setSelection(cursor, cursor)
        h.notifySel(os, oe, cursor, cursor)
        h.idle()
    }

    private val family = "👨‍👩‍👧‍👦"
    private val toned = "👍🏽"
    private val flag = "🇻🇳"
    private val combining = "á"

    @Test
    fun swipeRight_skipsWholeFamilyZWJ() {
        val h = ImeHarness(initialText = "")
        val t = "x${family}y"
        setup(h, t, 1)
        swipe(h.ime, 1)
        assertEquals("phải qua hết cụm ZWJ", 1 + family.length, h.conn.selStart)
    }

    @Test
    fun swipeLeft_skipsWholeFamilyZWJ() {
        val h = ImeHarness(initialText = "")
        val t = "x${family}y"
        setup(h, t, 1 + family.length)
        swipe(h.ime, -1)
        assertEquals(1, h.conn.selStart)
    }

    @Test
    fun swipe_skipsTonedAndFlagAndCombining() {
        val h = ImeHarness(initialText = "")
        val t = "$toned$flag$combining"
        var pos = t.length
        setup(h, t, pos)
        swipe(h.ime, -1)
        pos -= combining.length
        assertEquals(pos, h.conn.selStart)
        swipe(h.ime, -1)
        pos -= flag.length
        assertEquals(pos, h.conn.selStart)
        swipe(h.ime, -1)
        pos -= toned.length
        assertEquals(pos, h.conn.selStart)
        swipe(h.ime, 1)
        assertEquals(toned.length, h.conn.selStart)
        swipe(h.ime, 1)
        assertEquals(toned.length + flag.length, h.conn.selStart)
        swipe(h.ime, 1)
        assertEquals(t.length, h.conn.selStart)
    }

    @Test
    fun swipe_mixedAsciiAndEmoji() {
        val h = ImeHarness(initialText = "")
        val t = "ab${toned}cd"
        setup(h, t, 2)
        swipe(h.ime, 1)
        assertEquals(2 + toned.length, h.conn.selStart)
        swipe(h.ime, 1)
        assertEquals(2 + toned.length + 1, h.conn.selStart)
    }

    @Test
    fun swipe_atBounds_noChange() {
        val h = ImeHarness(initialText = "")
        setup(h, "ab", 0)
        swipe(h.ime, -1)
        assertEquals(0, h.conn.selStart)
        setup(h, "ab", 2)
        swipe(h.ime, 1)
        assertEquals(2, h.conn.selStart)
    }

    @Test
    fun swipe_collapsesSelectionWithoutMoving() {
        val h = ImeHarness(initialText = "")
        h.conn.text.clear()
        h.conn.text.append("abcd")
        val os = h.conn.selStart; val oe = h.conn.selEnd
        h.conn.setSelection(1, 3)
        h.notifySel(os, oe, 1, 3)
        h.idle()
        swipe(h.ime, -1)
        assertEquals(1, h.conn.selStart)
        h.conn.setSelection(1, 3)
        swipe(h.ime, 1)
        assertEquals(3, h.conn.selStart)
    }

    @Test
    fun deleteAfterSwipe_removesWholeCluster() {
        val h = ImeHarness(initialText = "")
        val t = "x${family}"
        setup(h, t, t.length)
        h.type("⌫")
        assertEquals("⌫ phải xoá trọn cụm ZWJ", "x", h.text())
    }
}
