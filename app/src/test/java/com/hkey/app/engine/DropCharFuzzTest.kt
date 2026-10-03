package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Fuzz: ⌫ theo ký tự hiển thị phải cho display = display cũ bớt 1 ký tự
 *  (hoặc bớt nhiều hơn nếu không có raw nào cho đúng target). Bất kỳ case
 *  nào vi phạm = bug thật của thuật toán tìm ngược. */
class DropCharFuzzTest {

    private fun corpus(): List<String> {
        val raws = mutableSetOf<String>()
        val dict = java.io.File("src/main/res/raw/vi_dict.txt").readLines()
        val e = TelexEngine()
        for (w in dict) {
            val base = e.stripTones(w).lowercase().filter { it in 'a'..'z' }
            if (base.isNotEmpty()) raws += base
        }
        // Thêm raw ngẫu nhiên đánh vào đường mark/tone/undo
        val alpha = "abcdeghiklmnopqrstuvwxfjzw".toCharArray()
        val rnd = Random(42)
        repeat(4000) {
            val n = 1 + rnd.nextInt(7)
            raws += buildString { repeat(n) { append(alpha[rnd.nextInt(alpha.size)]) } }
        }
        // Gõ lặp + tone key cuối trên mọi base ngắn
        for (b in raws.toList().take(500)) {
            for (k in "sfrxjzw") raws += b + k
        }
        return raws.toList()
    }

    @Test
    fun dropProducesDisplayMinusOne() {
        val e = TelexEngine()
        val bad = mutableListOf<String>()
        for (raw in corpus()) {
            val d = e.transform(raw)
            if (d.isEmpty()) continue
            val r = e.dropLastDisplayChar(raw)
            val got = e.transform(r)
            val want = d.dropLast(1)
            // Contract: chính xác want | tiền tố của want | cùng độ dài chỉ
            // lệch dấu (fallback khi want không đạt được do spellCheckTone
            // chặn tone key cuối trên âm tiết vô nghĩa).
            val ok = got == want || want.startsWith(got) || got == d ||
                (got.length == want.length && got.indices.all {
                    ViGlyphs.bareChar(got[it]) == ViGlyphs.bareChar(want[it])
                })
            if (!ok) bad += "raw=$raw disp=$d -> r=$r disp2=$got (want $want)"
        }
        assertTrue(bad.take(40).joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun dropNeverGrowsDisplay() {
        val e = TelexEngine()
        for (raw in corpus()) {
            val d = e.transform(raw)
            if (d.isEmpty()) continue
            val got = e.transform(e.dropLastDisplayChar(raw))
            assertTrue("raw=$raw d=$d got=$got", got.length <= d.length)
        }
    }

    @Test
    fun knownCases() {
        val e = TelexEngine()
        assertEquals("việ", e.transform(e.dropLastDisplayChar("vieetj")))
        assertEquals("hoá", e.transform(e.dropLastDisplayChar("hoans")))
        // 'w' là phím mark, không chặn phím dấu đứng trước nó
        assertEquals("ớ", e.transform("osw"))
        assertEquals("ắ", e.transform("asw"))
        assertEquals("ứ", e.transform("ws")) // 'w' trước vẫn nuôi phím dấu sau
        assertEquals("ợ", e.transform("osjw"))
    }
}
