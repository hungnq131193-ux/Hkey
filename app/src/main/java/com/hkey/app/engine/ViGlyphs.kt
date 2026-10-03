package com.hkey.app.engine

/**
 * Bảng ký tự tiếng Việt dùng chung cho TelexEngine và VniEngine (2.x):
 * biến thể nguyên âm theo tone 0..5, tách ký tự có dấu, bỏ mọi dấu.
 */
internal object ViGlyphs {

    val vowelBase = mapOf(
        'a' to listOf("a", "á", "à", "ả", "ã", "ạ"),
        'ă' to listOf("ă", "ắ", "ằ", "ẳ", "ẵ", "ặ"),
        'â' to listOf("â", "ấ", "ầ", "ẩ", "ẫ", "ậ"),
        'e' to listOf("e", "é", "è", "ẻ", "ẽ", "ẹ"),
        'ê' to listOf("ê", "ế", "ề", "ể", "ễ", "ệ"),
        'i' to listOf("i", "í", "ì", "ỉ", "ĩ", "ị"),
        'o' to listOf("o", "ó", "ò", "ỏ", "õ", "ọ"),
        'ô' to listOf("ô", "ố", "ồ", "ổ", "ỗ", "ộ"),
        'ơ' to listOf("ơ", "ớ", "ờ", "ở", "ỡ", "ợ"),
        'u' to listOf("u", "ú", "ù", "ủ", "ũ", "ụ"),
        'ư' to listOf("ư", "ứ", "ừ", "ử", "ữ", "ự"),
        'y' to listOf("y", "ý", "ỳ", "ỷ", "ỹ", "ỵ")
    )

    val markedVowels = setOf('ă', 'â', 'ê', 'ô', 'ơ', 'ư')
    val plainVowels = setOf('a', 'e', 'i', 'o', 'u', 'y')

    /** Tách ký tự có dấu -> (nguyên âm gốc giữ dấu phụ, tone 0..5). */
    val decompose: Map<Char, Pair<Char, Int>> = buildMap {
        for ((base, variants) in vowelBase) {
            variants.forEachIndexed { i, s -> put(s[0], base to i) }
        }
        put('đ', 'đ' to 0)
    }

    fun decomposed(c: Char): Pair<Char, Int> =
        decompose[c] ?: c.lowercaseChar().let { decompose[it] ?: (c to 0) }

    /** Ký tự gốc bỏ mọi dấu: 'ấ'->'a', 'đ'->'d'. */
    fun bareChar(c: Char): Char = when (val b = decomposed(c).first) {
        'ă', 'â' -> 'a'; 'ê' -> 'e'; 'ô', 'ơ' -> 'o'; 'ư' -> 'u'; 'đ' -> 'd'
        else -> b
    }
}

/** Luật đặt dấu thanh dùng chung Telex/VNI (2.x). */
internal object ViTone {

    /** Cụm mở đặt dấu ở nguyên âm 2 theo kiểu mới: hoà, khoẻ, thuỷ. */
    private val secondVowelOpenClusters = setOf("oa", "oe", "uy")

    /** Nguyên âm thật — 'w' KHÔNG tính: 'w' là phím mark, đứng sau phím
     *  dấu không chặn phím dấu đó ("osw" -> "ớ", "asw" -> "ắ"). */
    fun isVowelChar(c: Char) =
        c in ViGlyphs.plainVowels || c in ViGlyphs.markedVowels

    /** 'w' ở trước vẫn tính là đã có nguyên âm (nó sẽ thành ư/ă/ơ) để
     *  phím dấu đứng sau tiêu thụ được: "ws" -> "ứ". */
    fun vowelBefore(text: String, i: Int) =
        (0 until i).any { isVowelChar(text[it]) || text[it] == 'w' }

    /** 'u' sau 'q' và 'i' sau 'g' trước nguyên âm là phụ âm (qu-, gi-). */
    fun isVowel(text: String, i: Int): Boolean {
        val c = text[i]
        if (c !in ViGlyphs.plainVowels && c !in ViGlyphs.markedVowels) return false
        if (c == 'u' && i > 0 && text[i - 1] == 'q') return false
        if (c == 'i' && i > 0 && text[i - 1] == 'g' &&
            i + 1 < text.length &&
            (text[i + 1] in ViGlyphs.plainVowels || text[i + 1] in ViGlyphs.markedVowels)
        ) return false
        return true
    }

    /** Vị trí nguyên âm nhận dấu; newStyle=false -> kiểu cũ "hòa" (âm 1). */
    fun toneTargetIndex(text: String, newStyle: Boolean = true): Int {
        // Nguyên âm đã có dấu phụ (lấy cái cuối: "ươ" -> ơ, "uô" -> ô)
        var lastMarked = -1
        for (i in text.indices) if (text[i] in ViGlyphs.markedVowels) lastMarked = i
        if (lastMarked >= 0) return lastMarked

        // Cụm nguyên âm đầu tiên (đã loại qu-, gi-)
        var s = -1
        var e = -1
        for (i in text.indices) {
            if (isVowel(text, i)) {
                if (s == -1) s = i
                e = i
            } else if (s != -1) break
        }
        if (s == -1) return -1

        val len = e - s + 1
        if (len == 1) return s
        if (len >= 3) return s + 1 // cụm 3 âm: dấu ở giữa (xoài)

        // Cụm 2 nguyên âm: có phụ âm cuối -> âm 2 ("hoàn"); mở oa/oe/uy ->
        // âm 2 kiểu mới ("hoà", "khoẻ", "thuỷ"), âm 1 kiểu cũ ("hòa","khòe")
        val hasFinalConsonant = e + 1 < text.length
        return if (hasFinalConsonant ||
            (newStyle && text.substring(s, e + 1) in secondVowelOpenClusters)
        ) e else s
    }
}
