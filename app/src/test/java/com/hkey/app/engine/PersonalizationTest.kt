package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.3.0 — cá nhân hoá: user trigram + recency boost. "Dùng càng nhiều,
 *  đề xuất càng tốt": cụm 3 từ hay gõ được nhớ, từ dùng gần đây được ưu
 *  tiên hơn từ dùng nhiều nhưng đã lâu. */
class PersonalizationTest {

    private val DAY = 86_400_000L

    @Test
    fun userTrigramBeatsUserBigramWithContext() {
        val p = ContextPredictor()
        // "nay" -> "ở" học 5 lần (bigram mạnh) ...
        repeat(5) { p.recordSequence("nay", "ở") }
        // ... nhưng "tối nay" -> "đi" chỉ học 1 lần ở lớp trigram
        p.recordSequence("nay", "đi", prev2 = "tối")
        // Không có ngữ cảnh 2 từ: bigram thắng như thường
        assertEquals("ở", p.predictNext("nay").first())
        // Có đủ "tối nay": trigram cá nhân thắng
        assertEquals("đi", p.predictNext("nay", "tối").first())
    }

    @Test
    fun recentUseOutranksStaleUse() {
        val p = ContextPredictor()
        val now = System.currentTimeMillis()
        val stale = now - 30 * DAY
        repeat(3) { p.recordSequence("anh", "xua", now = stale) }
        repeat(3) { p.recordSequence("anh", "moi", now = now) }
        // Cùng tần suất + cùng bigram cá nhân -> từ dùng gần đây thắng
        assertEquals("moi", p.predictNext("anh").first())
    }

    @Test
    fun trigramNotLearnedFromBadContext() {
        val p = ContextPredictor()
        repeat(5) { p.recordSequence("nay", "ở") }
        // prev2 có số không hợp lệ -> chỉ học bigram "nay"->"đi" (đúng),
        // KHÔNG học cạnh trigram "t0i|nay"->"đi". Nếu trigram rò, đi (+300M)
        // sẽ thắng ở (bigram 5 lần ~10M) ngay cả khi chỉ gõ 1 lần.
        p.recordSequence("nay", "đi", prev2 = "t0i")
        assertEquals("ở", p.predictNext("nay", "t0i").first())
    }

    @Test
    fun trigramSurvivesExportImport() {
        val p = ContextPredictor()
        repeat(2) { p.recordSequence("cà", "phê", prev2 = "uống") }
        val (w, b, t) = p.exportLearned()
        val q = ContextPredictor()
        q.importLearned(w, b, t)
        assertEquals("phê", q.predictNext("cà", "uống").first())
    }

    @Test
    fun storeV2RoundTripAndReadsV1() {
        val f = java.io.File.createTempFile("hkey-learn", ".tsv")
        val s = LearningStore(f)
        // file v1 (không dòng trigram) vẫn đọc được
        f.writeText("hkey-learned-v1\nw\tchào\t3\t123\nb\ttôi\tmuốn\t4\n")
        val old = s.load()!!
        assertEquals(3, old.words[0].second)
        assertTrue(old.trigrams.isEmpty())
        // ghi v2 -> đọc lại đủ trigram
        s.save(
            LearningStore.Data(
                words = listOf(Triple("chào", 3, 123L)),
                bigrams = listOf(Triple("tôi", "muốn", 4)),
                trigrams = listOf(UserTri("uống", "cà", "phê", 2))
            )
        )
        assertEquals("hkey-learned-v2", f.readLines().first())
        val d = s.load()!!
        assertEquals(1, d.trigrams.size)
        assertEquals(UserTri("uống", "cà", "phê", 2), d.trigrams[0])
        f.delete()
    }
}
