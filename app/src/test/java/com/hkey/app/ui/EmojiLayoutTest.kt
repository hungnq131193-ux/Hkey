package com.hkey.app.ui

import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmojiLayoutTest {

    private val density
        get() = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    @Test
    fun emojiHasSeparateCategoryAndActionRows() {
        val rows = KbLayouts.emoji(1, emptyList())
        val tabs = rows[rows.size - 2].keys
        val actions = rows.last().keys
        assertEquals("hàng tab phải đủ 7 nhóm", KbLayouts.EMOJI_TABS.size, tabs.size)
        assertTrue(tabs.all { it.tag.startsWith("fn:ecat:") })
        for (t in listOf("fn:abc", "fn:paste", "fn:space", "fn:enter", "fn:del")) {
            assertTrue("thiếu $t ở hàng tác vụ", actions.any { it.tag == t })
        }
        assertFalse(actions.any { it.tag.startsWith("fn:ecat:") })
    }

    @Test
    fun emptyRecentStillTwoFixedRows() {
        val empty = KbLayouts.emoji(0, emptyList())
        assertEquals(2, empty.size)
        assertTrue(empty[0].keys.all { it.tag.startsWith("fn:ecat:") })
        assertTrue(empty[1].keys.any { it.tag == "fn:del" })
    }

    private fun emojiView(widthDp: Int = 360, numRow: Boolean = false): KeyboardView {
        val widthPx = (widthDp * density).toInt()
        val v = KeyboardView(RuntimeEnvironment.getApplication())
        v.soundEnabled = false
        v.vibrateEnabled = false
        v.recentEmoji = listOf("😀", "❤️")
        v.configure(100, 0, KbPalette.DARK, numRow = numRow)
        v.showPage(KeyboardView.Page.EMOJI)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )
        v.layout(0, 0, widthPx, v.measuredHeight)
        return v
    }

    private fun lettersView(numRow: Boolean): KeyboardView {
        val widthPx = (360 * density).toInt()
        val v = KeyboardView(RuntimeEnvironment.getApplication())
        v.soundEnabled = false
        v.vibrateEnabled = false
        v.configure(100, 0, KbPalette.DARK, numRow = numRow)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )
        return v
    }

    private fun evDown(x: Float, y: Float, t: Long) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun evMove(x: Float, y: Float, t: Long) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_MOVE, x, y, 0)

    private fun evUp(x: Float, y: Float, t: Long) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0)

    private fun assertNoOverlap(row: List<RectF>, label: String) {
        val sorted = row.sortedBy { it.left }
        for (i in 1 until sorted.size) {
            assertTrue(
                "$label: target $i chồng lên target ${i - 1}",
                sorted[i].left >= sorted[i - 1].right
            )
        }
    }

    @Test
    fun narrowWidth320dp_categoryAndActionTargetsUsable() {
        for (widthDp in listOf(320, 360)) {
            val v = emojiView(widthDp)
            val catRow = (0 until KbLayouts.EMOJI_TABS.size).map {
                assertNotNull("thiếu tab $it @${widthDp}dp", v.testHitArea("fn:ecat:$it"))
                v.testHitArea("fn:ecat:$it")!!
            }
            val actRow = listOf("fn:abc", "fn:paste", "fn:space", "fn:enter", "fn:del").map {
                assertNotNull("thiếu $it @${widthDp}dp", v.testHitArea(it))
                v.testHitArea(it)!!
            }
            if (widthDp == 320) {
                for ((i, r) in catRow.withIndex()) {
                    assertTrue("tab $i hẹp hơn 40dp", r.width() >= 40f * density)
                }
            }
            assertNoOverlap(catRow, "tab@${widthDp}dp")
            assertNoOverlap(actRow, "tác vụ@${widthDp}dp")
            assertTrue(
                "hàng tab và hàng tác vụ chồng nhau @${widthDp}dp",
                catRow.maxOf { it.bottom } <= actRow.minOf { it.top } + 1
            )
        }
    }

    @Test
    fun emojiPageHeight_matchesLettersPage() {
        for (numRow in listOf(false, true)) {
            val letters = lettersView(numRow)
            val emoji = emojiView(360, numRow)
            assertEquals(
                "numRow=$numRow: trang emoji phải cao bằng trang chữ",
                letters.measuredHeight, emoji.measuredHeight
            )
        }
    }

    @Test
    fun emojiPageHeight_matchesLettersPage_landscape() {
        RuntimeEnvironment.setQualifiers("land")
        try {
            val letters = lettersView(false)
            val emoji = emojiView(360, false)
            assertEquals(letters.measuredHeight, emoji.measuredHeight)
        } finally {
            RuntimeEnvironment.setQualifiers("")
        }
    }

    @Test
    fun gridPaddingTap_selectsNothing() {
        val v = emojiView()
        v.showPage(KeyboardView.Page.EMOJI)
        val fired = mutableListOf<String>()
        v.onKey = { fired += it.tag }
        val emojiCell = v.testHitArea("tx:😀")!!
        val padX = v.width - 8f
        val padY = emojiCell.centerY()
        assertNull("điểm đệm trong lưới không được gán emoji",
            v.testKeyAt(padX, padY))
        val t = android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(evDown(padX, padY, t))
        v.dispatchTouchEvent(evUp(padX, padY, t + 40))
        assertTrue("chạm đệm không được chọn emoji", fired.isEmpty())
    }

    @Test
    fun scrollDoesNotSelectEmoji() {
        val v = emojiView()
        val fired = mutableListOf<String>()
        v.onKey = { fired += it.tag }
        val cell = v.testHitArea("tx:😀") ?: v.testHitArea("fn:ecat:1")!!
        val x = cell.centerX(); val y = cell.centerY()
        val t = android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(evDown(x, y, t))
        v.dispatchTouchEvent(evMove(x, y - 80f, t + 60))
        v.dispatchTouchEvent(evUp(x, y - 80f, t + 100))
        assertTrue("cuộn không được chọn emoji", fired.none { it.startsWith("tx:") })
    }

    @Test
    fun a11y_excludesOffscreenGridNodes() {
        val v = emojiView()
        v.testSetEmojiCategory(1)
        val idsTop = v.testVisibleNodeIds()
        val bounds = idsTop.map { it to v.testNodeBounds(it) }
        val gridClip = v.testGridClip()
        for ((id, r) in bounds) {
            assertTrue("node $id tràn khỏi vùng nhìn: $r",
                r.bottom <= v.height && r.top >= -1)
        }
        v.testScrollGridTo(10_000f)
        val idsBottom = v.testVisibleNodeIds()
        assertTrue("cuộn đáy vẫn phải giới hạn node nhìn thấy",
            idsBottom.size < idsTop.size || idsBottom != idsTop)
        val bottomBounds = idsBottom.map { v.testNodeBounds(it) }
        assertTrue(bottomBounds.any { it.top >= gridClip.bottom.toInt() - 1 })
    }

    @Test
    fun emojiInsertBackspaceAbc_selectionIntegrity() {
        val h = com.hkey.app.service.ImeHarness()
        h.idle()
        h.ime.dispatchKey(KbKey("tx:😀", "😀"))
        h.idle()
        assertEquals("ok 😀", h.text())
        h.type("⌫")
        h.idle()
        assertEquals("ok ", h.text())
        h.ime.dispatchKey(KbKey("fn:abc", "ABC", func = true))
        h.type("v")
        h.idle()
        assertEquals("v", h.composing())
        assertEquals(4, h.conn.selEnd)
    }

    @Test
    fun a11y_controlButtonsClickable() {
        val v = emojiView()
        val fired = mutableListOf<String>()
        v.onKey = { fired += it.tag }
        val ids = v.testVisibleNodeIds()
        var clickedAbc = false
        for (id in ids) {
            if (v.testNodeTag(id) == "fn:abc") {
                clickedAbc = v.testNodeClick(id)
            }
        }
        assertTrue("phải click được nút ABC qua accessibility", clickedAbc)
        assertTrue("fn:abc" in fired)
    }
}
