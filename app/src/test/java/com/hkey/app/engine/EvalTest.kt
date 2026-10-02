package com.hkey.app.engine

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 3.7 — đánh giá offline trên tập câu giữ riêng (tools/eval_corpus.txt,
 * câu KHÔNG nằm trong model). Chỉ số:
 *  - next-word top-3 accuracy
 *  - completion top-3 sau 1/2/3 ký tự
 *  - số phím tiết kiệm được (chạm gợi ý thay vì gõ hết)
 *  - tỷ lệ sửa nhầm: correction() muốn đổi một từ ĐÚNG
 * Chạy 2 cấu hình: có model và không model (baseline cũ) để đặt ngưỡng.
 */
class EvalTest {

    private fun resFile(vararg paths: String): File? =
        paths.firstNotNullOfOrNull { File(it).takeIf { f -> f.exists() } }

    private fun buildPredictor(withModel: Boolean): ContextPredictor {
        val p = ContextPredictor()
        resFile("src/main/res/raw/vi_dict.txt", "app/src/main/res/raw/vi_dict.txt")
            ?.let { p.addWords(it.readLines()) }
        if (withModel) {
            resFile(
                "src/main/res/raw/vi_model.gz",
                "app/src/main/res/raw/vi_model.gz"
            )?.let { m ->
                val model = java.util.zip.GZIPInputStream(m.inputStream())
                    .bufferedReader().use { ViModel.parse(it.lineSequence()) }
                p.loadModel(model.unigrams, model.bigrams, model.trigrams, model.bos)
            }
        }
        return p
    }

    @Test
    fun evalMetrics() {
        val corpusFile = resFile("../tools/eval_corpus.txt", "tools/eval_corpus.txt")
        // Không có corpus -> bỏ qua (CI chạy mà không cần tải Wikipedia).
        assumeTrue("thiếu tools/eval_corpus.txt (build_model.py chưa chạy)",
            corpusFile != null)
        val sentences = corpusFile!!.readLines().asSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }
            .map { l -> l.split(" ").filter { w -> w.isNotEmpty() } }
            .filter { it.size >= 2 }
            .take(20000)
            .toList()

        val report = StringBuilder("sents=${sentences.size}\n")
        for (withModel in listOf(false, true)) {
            System.gc(); Thread.sleep(50)
            val heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val p = buildPredictor(withModel)
            System.gc(); Thread.sleep(50)
            val heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            if (withModel) report.appendLine("modelHeap≈${(heapAfter - heapBefore) / 1048576}MB")
            var nextTotal = 0
            var nextHit = 0
            val compHitAt = intArrayOf(0, 0, 0) // hit ngay sau 1/2/3 ký tự
            var compTotal = 0
            var keysIdeal = 0L   // phím nếu gõ hết
            var keysReal = 0L    // phím nếu dùng gợi ý (best-case)
            var fpCorrect = 0
            var fpTotal = 0

            for (s in sentences) {
                for (i in s.indices) {
                    val w = s[i]
                    val prev = if (i > 0) s[i - 1] else ""
                    val prev2 = if (i > 1) s[i - 2] else ""
                    // next-word
                    if (i > 0) {
                        nextTotal++
                        if (p.predictNext(prev, prev2).contains(w)) nextHit++
                    }
                    // correction FP trên từ đúng
                    if (w.length >= 3) {
                        fpTotal++
                        if (p.correction(w, prev, prev2) != null) fpCorrect++
                    }
                    // completion: sớm nhất ở ký tự thứ k
                    if (w.length >= 2) {
                        compTotal++
                        keysIdeal += w.length
                        var taps = w.length // gõ hết = worst case
                        for (k in 1..minOf(3, w.length - 1)) {
                            if (p.completions(w.take(k), prev, prev2).contains(w)) {
                                compHitAt[k - 1]++
                                taps = k + 1 // k phím + 1 chạm gợi ý
                                break
                            }
                        }
                        keysReal += taps
                    }
                }
            }
            report.appendLine(
                "model=$withModel | nextTop3=${"%.3f".format(nextHit.toDouble() / nextTotal)} " +
                    "| compTop3@1/2/3=${compHitAt.joinToString("/")}/$compTotal " +
                    "| keysSaved=${"%.1f".format(100.0 * (keysIdeal - keysReal) / keysIdeal)}% " +
                    "| fpCorrect=$fpCorrect/$fpTotal=" +
                    "%.3f".format(fpCorrect.toDouble() / fpTotal)
            )
        }
        File("build/eval-report.txt").also { it.parentFile?.mkdirs() }
            .writeText(report.toString())
        println(report)
    }
}
