package com.hkey.app.engine

import kotlin.math.min

/**
 * Context-aware Auto-correction and Bigram/Trigram next-word prediction engine.
 */
class ContextPredictor {

    // Từ điển phổ biến kèm trọng số n-gram cơ bản
    private val vocabulary = mutableMapOf(
        "tôi" to 500, "bạn" to 450, "anh" to 400, "em" to 420,
        "hôm" to 300, "nay" to 350, "qua" to 200, "mai" to 220,
        "đi" to 600, "làm" to 550, "học" to 380, "chơi" to 320,
        "cơm" to 250, "uống" to 280, "cà" to 190, "phê" to 190,
        "chuyển" to 210, "khoản" to 210, "tiền" to 310, "công" to 260,
        "việc" to 340, "gặp" to 230, "nhé" to 330, "ạ" to 290
    )

    // Bigram transitions: Từ trước -> Danh sách từ tiếp theo kèm tần suất
    private val bigramModel = mutableMapOf<String, MutableMap<String, Int>>(
        "hôm" to mutableMapOf("nay" to 100, "qua" to 60, "kia" to 20),
        "đi" to mutableMapOf("làm" to 120, "học" to 80, "chơi" to 70, "cà" to 50),
        "cà" to mutableMapOf("phê" to 150),
        "chuyển" to mutableMapOf("khoản" to 130, "tiền" to 90),
        "làm" to mutableMapOf("việc" to 110, "gì" to 80)
    )

    /**
     * Gợi ý từ tiếp theo dựa vào từ liền trước (Context Next-Word Prediction)
     */
    fun predictNext(previousWord: String): List<String> {
        val prev = previousWord.lowercase().trim()
        val nextWords = bigramModel[prev]
        return if (!nextWords.isNullOrEmpty()) {
            nextWords.entries.sortedByDescending { it.value }.map { it.key }.take(3)
        } else {
            // Mặc định từ phổ biến nhất
            listOf("và", "là", "đi")
        }
    }

    /**
     * Tự học khi người dùng gõ (Dynamic Learning)
     */
    fun recordSequence(prev: String, current: String) {
        val p = prev.lowercase().trim()
        val c = current.lowercase().trim()
        if (p.isEmpty() || c.isEmpty()) return

        val transitions = bigramModel.getOrPut(p) { mutableMapOf() }
        transitions[c] = (transitions[c] ?: 0) + 1
        vocabulary[c] = (vocabulary[c] ?: 0) + 1
    }

    /**
     * Tự động sửa từ sai ngữ cảnh dựa trên Levenshtein + N-gram
     */
    fun autoCorrect(typedWord: String, previousWord: String?): String {
        val word = typedWord.lowercase().trim()
        if (vocabulary.containsKey(word)) return typedWord

        // Nếu có từ trước, ưu tiên tìm trong bigram của từ đó trước
        if (!previousWord.isNullOrEmpty()) {
            val p = previousWord.lowercase().trim()
            val candidates = bigramModel[p]
            if (candidates != null) {
                for (cand in candidates.keys) {
                    if (levenshteinDistance(word, cand) <= 2) {
                        return cand
                    }
                }
            }
        }

        // Tìm từ gần nhất trong toàn bộ từ điển
        var bestMatch = typedWord
        var minDistance = 3 // Chỉ sửa nếu sai không quá 2 ký tự

        for (dictWord in vocabulary.keys) {
            val dist = levenshteinDistance(word, dictWord)
            if (dist < minDistance) {
                minDistance = dist
                bestMatch = dictWord
            }
        }

        return bestMatch
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
