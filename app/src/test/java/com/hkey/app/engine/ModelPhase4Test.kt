package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelPhase4Test {

    /** Ghi một blob vi_model.bin tối thiểu theo đúng format tsv_to_bin. */
    private fun makeBin(
        words: List<Pair<String, Int>>,
        bos: List<Int>,
        bi: List<Triple<Int, Int, Int>>,
        tri: List<List<Int>>
    ): ByteBuffer {
        val buf = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(ViModelBin.MAGIC.toInt())
        buf.putShort(1); buf.putShort(0)
        buf.putInt(words.size); buf.putInt(bos.size)
        buf.putInt(bi.size); buf.putInt(tri.size)
        for ((w, f) in words) {
            val b = w.toByteArray(Charsets.UTF_8)
            buf.putShort(b.size.toShort()); buf.put(b); buf.putInt(f)
        }
        for (i in bos) buf.putInt(i)
        for ((p, n, c) in bi) { buf.putInt(p); buf.putInt(n); buf.putInt(c) }
        for (t in tri) { for (v in t) buf.putInt(v) }
        buf.flip()
        return buf
    }

    @Test
    fun binRead() {
        val words = listOf("tôi" to 900, "đi" to 800, "học" to 700, "ăn" to 600)
        val bin = makeBin(words, listOf(0), listOf(Triple(0, 1, 50)),
            listOf(listOf(0, 1, 2, 30)))
        val p = ViModelBin.read(bin)!!
        assertEquals(4, p.words.size)
        assertEquals("tôi", p.words[0])
        assertEquals(900, p.freqs[0])
        assertEquals(1, p.bosIdx.size)
        assertEquals(50, p.biC[0])
        assertEquals(30, p.triC[0])
    }

    @Test
    fun binRejectsGarbage() {
        assertNull(ViModelBin.read(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))))
        val tooShort = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(ViModelBin.MAGIC.toInt()).putShort(1).putShort(0)
        tooShort.flip()
        assertNull(ViModelBin.read(tooShort))
    }

    @Test
    fun loadPackedWorksLikeLoadModel() {
        val p = ContextPredictor()
        val words = listOf("tôi" to 900, "đi" to 800, "học" to 700, "ăn" to 600)
        val bin = makeBin(words, listOf(0), listOf(Triple(0, 1, 50)), emptyList())
        p.loadPacked(ViModelBin.read(bin)!!)
        val next = p.predictNext("tôi")
        assertTrue("đi" in next)
    }

    @Test
    fun phrasesBoostNextWord() {
        val p = ContextPredictor()
        p.addPhrases(listOf("phở" to "gà"))
        assertTrue(p.predictNext("phở").contains("gà"))
    }

    @Test
    fun learnsAndReturnsCase() {
        val p = ContextPredictor()
        p.recordSequence("", "IPhone")
        p.recordSequence("", "IPhone")
        // gợi ý theo kiểu hoa đã học, không flatten về lowercase
        assertTrue(p.completions("iph").contains("IPhone"))
        // export mang casing, import phục hồi casing (4.x)
        val (words, _) = p.exportLearned()
        assertEquals("IPhone", words.first().first)
        val q = ContextPredictor()
        q.importLearned(words, emptyList())
        assertEquals("IPhone", q.displayOf("iphone"))
    }

    @Test
    fun typoNotLearned() {
        val p = ContextPredictor()
        // "xin" có sẵn trong vocab; "xjn" cách 1 ký tự, không phải âm tiết VN
        assertTrue(p.looksLikeTypo("xjn"))
        // từ biết/vn hợp lệ không bị coi là typo
        assertFalse(p.looksLikeTypo("xin"))
        assertFalse(p.looksLikeTypo("việt"))
        // từ lạ hoàn toàn (không gần mục nào) = từ mới hợp lệ, được học
        assertFalse(p.looksLikeTypo("zzzkkkqqq"))
    }
}
