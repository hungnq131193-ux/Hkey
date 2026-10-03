package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Phase 3 — mô hình n-gram, BOS, lưu/xoá/giới hạn dữ liệu học. */
class ModelPhase3Test {

    @Test
    fun viModelParsesTsv() {
        val m = ViModel.parse(sequenceOf(
            "# comment",
            "u\tlà\t50000",
            "s\ttôi\t9000",
            "b\ttôi\tđi\t300",
            "t\ttôi\tsẽ\tvề\t20"
        ))
        assertEquals(50000, m.unigrams["là"])
        assertEquals(300, m.bigrams["tôi"]?.get("đi"))
        assertEquals(20, m.trigrams["tôi|sẽ"]?.get("về"))
        assertEquals(listOf("tôi"), m.bos)
    }

    @Test
    fun trigramBeatsBigram() {
        val p = ContextPredictor()
        p.loadModel(
            unigrams = mapOf("đi" to 1000, "về" to 900, "nhà" to 800),
            bigrams = mapOf("sẽ" to mapOf("đi" to 500)),
            trigrams = mapOf("tôi|sẽ" to mapOf("về" to 10)),
            bos = emptyList()
        )
        // Có trigram (tôi sẽ -> về) thắng bigram (sẽ -> đi)
        assertEquals(listOf("về"), p.predictNext("sẽ", "tôi").take(1))
        // Không có w-2 -> fallback bigram
        assertEquals(listOf("đi"), p.predictNext("sẽ", "anh").take(1))
    }

    @Test
    fun bosSuggestWhenContextEmpty() {
        val p = ContextPredictor()
        p.loadModel(
            unigrams = mapOf("tôi" to 999, "mình" to 800, "anh" to 700),
            bigrams = emptyMap(), trigrams = emptyMap(),
            bos = listOf("tôi", "mình", "anh")
        )
        assertEquals(listOf("tôi", "mình", "anh"), p.predictNext(""))
        assertEquals(listOf("tôi", "mình", "anh"), p.predictNext("", ""))
    }

    @Test
    fun learnedDataRoundTrips() {
        val p = ContextPredictor()
        repeat(3) { p.recordSequence("uống", "càphê") }
        p.recordSequence("tôi", "đã")
        val (words, bis) = p.exportLearned()
        assertTrue(words.any { it.first == "càphê" && it.second == 3 })
        assertTrue(bis.contains(Triple("uống", "càphê", 3)))

        val q = ContextPredictor()
        q.importLearned(words, bis)
        // Từ học khôi phục: gợi ý được + bigram cá nhân còn
        assertTrue(q.completions("caph").contains("càphê"))
        assertTrue(q.predictNext("uống").contains("càphê"))
    }

    @Test
    fun clearLearnedResetsPersonal() {
        val p = ContextPredictor()
        repeat(3) { p.recordSequence("uống", "càphê") }
        p.recordSequence("", "là") // tăng personal cho từ điển
        p.clearLearned()
        assertFalse(p.completions("caph").contains("càphê"))
        assertTrue(p.exportLearned().first.isEmpty())
        assertTrue(p.exportLearned().second.isEmpty())
    }

    @Test
    fun boundLearnedEvictsLeastUsed() {
        val p = ContextPredictor()
        repeat(10) { p.recordSequence("x", "giữlại", now = it.toLong()) }
        repeat(2) { p.recordSequence("x", "bỏđi") }
        p.recordSequence("x", "bỏđii")
        p.boundLearned(1)
        assertTrue(p.completions("giul").contains("giữlại"))
        assertFalse(p.completions("bod").contains("bỏđi"))
        assertFalse(p.completions("bod").contains("bỏđii"))
    }

    @Test
    fun learningStoreRoundTripAndCorrupt() {
        val dir = createTempDir()
        val f = File(dir, "learned_data.tsv")
        val store = LearningStore(f)
        val data = LearningStore.Data(
            listOf(Triple("càphê", 3, 100L)),
            listOf(Triple("uống", "càphê", 3))
        )
        store.save(data)
        val back = store.load()!!
        assertEquals(Triple("càphê", 3, 100L), back.words[0])
        assertEquals(Triple("uống", "càphê", 3), back.bigrams[0])
        assertFalse(File(dir, "learned_data.tsv.tmp").exists()) // rename đã xong

        f.writeText("rác không phải header\nw\tbad") // file hỏng -> null
        assertNull(store.load())
        f.delete()
        assertNull(store.load()) // không có file -> null
    }

    @Test
    fun correctionGuardsPhase3() {
        val p = ContextPredictor()
        // <3 ký tự: không sửa
        assertNull(p.correction("di", null))
        // có số/ký hiệu: không sửa
        assertNull(p.correction("abc1", null))
        // không phải dạng âm tiết VN (tiếng Anh/mã/URL): không bao giờ sửa
        assertNull(p.correction("wiki", null))   // có 'w' — không phải chữ VN
        assertNull(p.correction("url", null))    // coda "rl" vô lý
        assertNull(p.correction("pour", null))
        assertNull(p.correction("nobel", null))
        // typo gõ thừa phím vẫn sửa được (gom lặp -> âm tiết VN)
        assertEquals("nay", p.correction("nayy", null))
    }

    @Test
    fun prefixGateForCorrection() {
        val p = ContextPredictor()
        p.addWords(listOf("hương"))
        // "hươ" là tiền tố của "hương" -> đang gõ dở, không bày bản sửa
        assertTrue(p.isPrefixOfKnownWord("hươ"))
        assertFalse(p.isPrefixOfKnownWord("hương"))
        assertFalse(p.isPrefixOfKnownWord("zzz"))
    }
}
