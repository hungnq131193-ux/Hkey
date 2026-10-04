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

    /** 1.4.0 (P3/C4): thời gian transform Telex trung bình trên 10k lần gọi
     *  với từ điển thật — in ra để so sánh trước/sau khi tối ưu. */
    @Test
    fun transformBench() {
        val dict = dictFile().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val keys = dict.mapNotNull { telexKeys(it) }
        val e = TelexEngine()
        repeat(3) { keys.forEach { e.transform(it) } } // warm-up
        // min-of-7: máy shared nhiễu lớn, min là phổ sạch nhất
        val best = LongArray(7) {
            measureNanoTime {
                var i = 0
                while (i < 10_000) {
                    e.transform(keys[i % keys.size]); i++
                }
            }
        }.min()
        println("\ntransform x10k (${keys.size} phím): ${best / 1_000_000.0} ms | trung bình ${best / 10_000.0} ns/từ (min-of-7)")

        // Workload thật trong IME: render + suggest + commit gọi transform
        // 3 lần trên cùng raw mỗi phím — cache 1 mục nên 2 lần sau gần free.
        val e2 = TelexEngine()
        val hit = LongArray(7) {
            measureNanoTime {
                var i = 0
                while (i < 10_000) {
                    val k = keys[i % keys.size]
                    e2.transform(k); e2.transform(k); e2.transform(k)
                    i++
                }
            }
        }.min()
        println("workload 3-call/phím x10k: ${hit / 1_000_000.0} ms | trung bình ${hit / 30_000.0} ns/gọi (min-of-7)")
    }

    private fun decomp(c: Char): Pair<Char, Int> {
        val groups = mapOf(
            'a' to "aáàảãạ", 'ă' to "ăắằẳẵặ", 'â' to "âấầẩẫậ",
            'e' to "eéèẻẽẹ", 'ê' to "êếềểễệ",
            'i' to "iíìỉĩị",
            'o' to "oóòỏõọ", 'ô' to "ôốồổỗộ", 'ơ' to "ơớờởỡợ",
            'u' to "uúùủũụ", 'ư' to "ưứừửữự",
            'y' to "yýỳỷỹỵ"
        )
        for ((b, v) in groups) {
            val i = v.indexOf(c)
            if (i >= 0) return b to i
        }
        return c to 0
    }

    /** Mã hoá Telex giống LiveRestoreTest: "ươ"->"uow", đ->dd, â->aa...;
     *  dấu thanh chèn sau cụm nguyên âm. */
    private fun telexKeys(word: String): String? {
        var tone = 0
        var vowelEnd = 0
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val (base, t) = decomp(word[i])
            if (t > 0) {
                if (tone > 0) return null
                tone = t
            }
            when {
                base == 'đ' -> sb.append("dd")
                base == 'ư' && i + 1 < word.length && decomp(word[i + 1]).first == 'ơ' -> {
                    sb.append("uow"); i++; vowelEnd = sb.length
                }
                base in "aăâeêioôơuưy" -> {
                    sb.append(
                        when (base) {
                            'ă' -> "aw"; 'â' -> "aa"; 'ê' -> "ee"
                            'ô' -> "oo"; 'ơ' -> "ow"; 'ư' -> "w"
                            else -> base.toString()
                        }
                    )
                    vowelEnd = sb.length
                }
                else -> sb.append(base)
            }
            i++
        }
        if (tone > 0) sb.insert(vowelEnd, "sfrxj"[tone - 1])
        return sb.toString()
    }
}
