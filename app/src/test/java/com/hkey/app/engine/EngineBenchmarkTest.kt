package com.hkey.app.engine

import org.junit.Test
import java.io.File
import kotlin.system.measureNanoTime

/**
 * Giai đoạn 0.4 — benchmark JVM trên từ điển thật (~7.4k từ).
 * Không cần thiết bị; kết quả ghi ra build/baseline-benchmark.txt.
 * S1 được chứng minh bằng đếm số lần indexCache bị huỷ qua reflection.
 */
class EngineBenchmarkTest {

    private fun dictFile(): File {
        for (p in listOf("src/main/res/raw/vi_dict.txt", "app/src/main/res/raw/vi_dict.txt")) {
            val f = File(p)
            if (f.exists()) return f
        }
        error("không tìm thấy vi_dict.txt")
    }

    private fun indexCacheIsNull(p: ContextPredictor): Boolean {
        val f = ContextPredictor::class.java.getDeclaredField("indexCache")
        f.isAccessible = true
        return f.get(p) == null
    }

    /** Giai đoạn 1.1 — S1: recordSequence không được huỷ/dựng lại chỉ mục.
     *  Fail trên code cũ (indexCache=null mỗi từ), pass sau khi cập nhật tăng dần. */
    @org.junit.Test
    fun recordSequenceDoesNotRebuildIndex() {
        val p = ContextPredictor()
        p.addWords(dictFile().readLines())
        p.completions("ho") // build lần đầu
        val f = ContextPredictor::class.java.getDeclaredField("indexCache")
        f.isAccessible = true
        val idx = f.get(p)
        org.junit.Assert.assertNotNull(idx)
        repeat(1000) {
            p.recordSequence("tôi", "kiểmtra$it")
            org.junit.Assert.assertSame("chỉ mục bị dựng lại ở từ $it", idx, f.get(p))
        }
    }

    private fun median(ns: LongArray): Double {
        val s = ns.sorted()
        return if (s.size % 2 == 1) s[s.size / 2].toDouble()
        else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    @Test
    fun baseline() {
        val dict = dictFile().readLines().map { it.trim() }.filter { it.isNotEmpty() }

        // Nạp từ điển + dựng chỉ mục lần đầu (cold)
        val p = ContextPredictor()
        val tAdd = measureNanoTime { p.addWords(dict) }
        val tColdBuild = measureNanoTime { p.completions("ho") } // gồm buildIndex

        // Truy vấn ấm: completions/correction/predictNext
        val prefixes = dict.filter { it.length >= 3 }.take(2000).map { it.substring(0, 3) }
        val tComp = LongArray(5) { measureNanoTime { prefixes.forEach { p.completions(it, "tôi") } } }
        val tCorr = LongArray(5) { measureNanoTime { repeat(2000) { p.correction("khom", "nay") } } }
        val tNext = LongArray(5) { measureNanoTime { repeat(2000) { p.predictNext("hôm") } } }

        // Vòng lặp "gõ từ": recordSequence + completions — mỗi từ huỷ chỉ mục (S1)
        val typeWords = dict.take(500)
        var invalidations = 0
        val tTyping = LongArray(3) {
            measureNanoTime {
                for (w in typeWords) {
                    p.recordSequence("tôi", w)
                    if (indexCacheIsNull(p)) invalidations++
                    p.completions("ho", "tôi")
                }
            }
        }

        val report = buildString {
            appendLine("dict=${dict.size} từ | jvm=${System.getProperty("java.version")}")
            appendLine("addWords(7.4k)          : ${tAdd / 1_000_000.0} ms")
            appendLine("buildIndex (cold query) : ${tColdBuild / 1_000_000.0} ms")
            appendLine("completions x2000 (median): ${median(tComp) / 1_000_000.0} ms")
            appendLine("correction  x2000 (median): ${median(tCorr) / 1_000_000.0} ms")
            appendLine("predictNext x2000 (median): ${median(tNext) / 1_000_000.0} ms")
            appendLine("recordSeq+completions x500 (median): ${median(tTyping) / 1_000_000.0} ms")
            appendLine("indexCache bị huỷ sau recordSequence: $invalidations lần / ${typeWords.size} từ")
        }
        File("build/baseline-benchmark.txt").writeText(report)
        println("\n$report")
    }
}
