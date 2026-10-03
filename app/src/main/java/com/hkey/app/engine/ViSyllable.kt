package com.hkey.app.engine

/**
 * Luật hình thái âm tiết tiếng Việt: onset + vần (nucleus + coda).
 * Dùng để: (1) tự sửa chỉ đụng từ có dạng âm tiết VN — từ tiếng Anh/mã/code
 * không bao giờ bị "sửa" (3.5); (2) test lọc từ điển/model.
 */
object ViSyllable {

    private val rows = listOf(
        "aáàảãạ", "ăắằẳẵặ", "âấầẩẫậ",
        "eéèẻẽẹ", "êếềểễệ", "iíìỉĩị",
        "oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ",
        "uúùủũụ", "ưứừửữự", "yýỳỷỹỵ"
    )

    private val decompose: Map<Char, Pair<Char, Int>> = buildMap {
        for (row in rows) row.forEachIndexed { i, c -> put(c, row[0] to i) }
        put('đ', 'đ' to 0)
    }

    private fun decomp(c: Char) = decompose[c] ?: (c to 0)

    private fun deaccent(c: Char): Char = when (decomp(c).first) {
        'đ' -> 'd'; 'ă', 'â' -> 'a'; 'ê' -> 'e'
        'ô', 'ơ' -> 'o'; 'ư' -> 'u'
        else -> decomp(c).first
    }

    private val onsets = listOf(
        "ngh", "qu", "gi", "gh", "ng", "nh", "ch", "kh", "ph", "th", "tr",
        "b", "c", "d", "g", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "x", ""
    )
    private val nuclei = setOf(
        "a", "e", "i", "o", "u", "y",
        "ai", "ao", "au", "ay", "eo", "eu", "ia", "ie", "iu",
        "oa", "oe", "oi", "oo", "ua", "ue", "ui", "uo", "uy", "uu", "ya", "ye",
        "ieu", "yeu", "uya", "uye", "uyu", "uoi", "uou", "oai", "oao", "oay",
        "oeo", "uay"
    )
    private val codas = setOf("", "c", "ch", "m", "n", "ng", "nh", "p", "t")

    /** Âm cuối thanh chắc: chỉ đi với sắc (1) hoặc nặng (5) (1.3). */
    private val sharpCodas = setOf("c", "ch", "p", "t")

    /** true nếu word là một âm tiết tiếng Việt hợp lệ về mặt hình thái. */
    fun isValid(word: String): Boolean {
        if (word.isEmpty()) return false
        var toneCount = 0
        var toneVal = 0
        for ((i, c) in word.withIndex()) {
            val (base, tone) = decomp(c)
            if (c !in 'a'..'z' && c !in decompose) return false
            if (tone > 0) {
                toneCount++
                toneVal = tone
            }
            if (base == 'đ' && i != 0) return false
        }
        if (toneCount > 1) return false
        val plain = word.map { deaccent(it) }.joinToString("")
        for (onset in onsets) {
            if (!plain.startsWith(onset)) continue
            val rhyme = plain.removePrefix(onset)
            for (len in rhyme.length downTo 1) {
                if (rhyme.substring(0, len) in nuclei && rhyme.substring(len) in codas) {
                    // "tẽt" không hợp lệ — thử cách tách khác trước khi bỏ
                    if (rhyme.substring(len) in sharpCodas &&
                        toneVal != 0 && toneVal != 1 && toneVal != 5
                    ) continue
                    return true
                }
            }
        }
        return false
    }

    /** Kết quả transform không phải âm tiết VN hợp lệ -> chốt bằng phím thô
     *  ("text/expect/window" về nguyên gõ — 1.3). */
    fun restorable(raw: String, transformed: String): Boolean =
        transformed != raw && !isValid(transformed.lowercase())
}
