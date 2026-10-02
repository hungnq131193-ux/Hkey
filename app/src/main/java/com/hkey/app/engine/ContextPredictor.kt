package com.hkey.app.engine

import kotlin.math.min

/**
 * Next-word prediction (bigram), prefix completion, and conservative
 * correction. Correction NEVER rewrites a word on its own — it only produces
 * a candidate for the suggestion bar; what the user typed is committed as-is.
 */
class ContextPredictor {

    private val vocabulary = mutableMapOf(
        "tôi" to 500, "bạn" to 450, "anh" to 400, "em" to 420, "chị" to 380,
        "của" to 480, "và" to 520, "là" to 530, "có" to 470, "không" to 460,
        "được" to 440, "này" to 410, "cho" to 390, "với" to 370, "người" to 300,
        "hôm" to 300, "nay" to 350, "qua" to 200, "mai" to 220, "kia" to 150,
        "đi" to 600, "làm" to 550, "học" to 380, "chơi" to 320, "ăn" to 360,
        "cơm" to 250, "uống" to 280, "cà" to 190, "phê" to 190, "nước" to 300,
        "chuyển" to 210, "khoản" to 210, "tiền" to 310, "công" to 260,
        "việc" to 340, "gặp" to 230, "nhé" to 330, "ạ" to 290, "vâng" to 350,
        "cảm" to 340, "ơn" to 380, "gì" to 400, "ở" to 430, "đâu" to 310,
        "rồi" to 440, "thì" to 350, "mà" to 430, "vẫn" to 300, "đang" to 380
    )

    private val bigramModel = mutableMapOf<String, MutableMap<String, Int>>(
        "hôm" to mutableMapOf("nay" to 100, "qua" to 60, "kia" to 20),
        "đi" to mutableMapOf("làm" to 120, "học" to 80, "chơi" to 70, "cà" to 50, "đâu" to 60),
        "cà" to mutableMapOf("phê" to 150),
        "chuyển" to mutableMapOf("khoản" to 130, "tiền" to 90),
        "làm" to mutableMapOf("việc" to 110, "gì" to 80, "ăn" to 50),
        "cảm" to mutableMapOf("ơn" to 140),
        "ăn" to mutableMapOf("cơm" to 110, "gì" to 60),
        "không" to mutableMapOf("có" to 90, "được" to 80, "biết" to 50),
        "có" to mutableMapOf("không" to 100, "gì" to 60),
        "của" to mutableMapOf("tôi" to 90, "anh" to 60, "em" to 60)
    )

    /** Gợi ý từ tiếp theo theo từ liền trước; fallback = từ phổ biến nhất. */
    fun predictNext(previousWord: String): List<String> {
        val prev = previousWord.lowercase().trim()
        val nextWords = bigramModel[prev]
        return if (!nextWords.isNullOrEmpty()) {
            nextWords.entries.sortedByDescending { it.value }.map { it.key }.take(3)
        } else {
            vocabulary.entries.sortedByDescending { it.value }.map { it.key }.take(3)
        }
    }

    /** Gợi ý hoàn thành từ theo prefix đang gõ. */
    fun completions(prefix: String): List<String> {
        val p = prefix.lowercase().trim()
        if (p.isEmpty()) return emptyList()
        return vocabulary.entries
            .filter { it.key.startsWith(p) && it.key != p }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(3)
    }

    /**
     * Trả về phương án sửa tốt nhất cho từ đã gõ, hoặc null nếu từ hợp lệ
     * hoặc không có phương án đủ gần (chỉ sửa khi lệch đúng 1 ký tự).
     */
    fun correction(typedWord: String, previousWord: String?): String? {
        val word = typedWord.lowercase().trim()
        if (word.isEmpty() || vocabulary.containsKey(word)) return null

        previousWord?.lowercase()?.trim()?.let { prev ->
            bigramModel[prev]?.entries
                ?.minByOrNull { levenshteinDistance(word, it.key) }
                ?.takeIf { levenshteinDistance(word, it.key) == 1 }
                ?.let { return it.key }
        }

        return vocabulary.entries
            .minByOrNull { levenshteinDistance(word, it.key) }
            ?.takeIf { levenshteinDistance(word, it.key) == 1 }
            ?.key
    }

    /** Tự học: ghi nhận chuỗi từ người dùng gõ. */
    fun recordSequence(prev: String, current: String) {
        val p = prev.lowercase().trim()
        val c = current.lowercase().trim()
        if (p.isEmpty() || c.isEmpty()) return

        val transitions = bigramModel.getOrPut(p) { mutableMapOf() }
        transitions[c] = (transitions[c] ?: 0) + 1
        vocabulary[c] = (vocabulary[c] ?: 0) + 1
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
