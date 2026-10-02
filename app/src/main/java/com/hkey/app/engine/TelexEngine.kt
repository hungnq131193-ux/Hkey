package com.hkey.app.engine

/**
 * Vietnamese Telex engine.
 * Tone keys (s f r x j) only apply when they are the LAST typed character and
 * the word already contains a vowel. Doubling a tone key (ss, ff...) prints the
 * literal letter. Marks: aa->â ee->ê oo->ô aw->ă ow->ơ uw->ư dd->đ, lone w->ư.
 */
class TelexEngine {

    private val toneMap = mapOf(
        's' to 1, // Sắc
        'f' to 2, // Huyền
        'r' to 3, // Hỏi
        'x' to 4, // Ngã
        'j' to 5  // Nặng
    )

    private val vowelBase = mapOf(
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

    private val markedVowels = setOf('ă', 'â', 'ê', 'ô', 'ơ', 'ư')
    private val plainVowels = setOf('a', 'e', 'i', 'o', 'u', 'y')

    fun transform(input: String): String {
        if (input.isEmpty()) return ""
        val isFirstUpper = input.first().isUpperCase()
        var text = input.lowercase()

        var toneIdx = 0
        var last = text.last()
        // 'z' sau phím dấu = huỷ dấu, in ký tự dấu thành chữ thường ("hoasz" -> "hoas")
        if (last == 'z' && text.length >= 2 && toneMap.containsKey(text[text.length - 2])) {
            text = text.dropLast(1)
            last = text.last()
        } else if (toneMap.containsKey(last)) {
            if (text.length >= 2 && text[text.length - 2] == last) {
                text = text.dropLast(1) // gõ đúp = in ký tự thật
            } else if (text.dropLast(1).any { it in plainVowels || it in markedVowels }) {
                toneIdx = toneMap.getValue(last)
                text = text.dropLast(1)
            }
        }

        text = text.replace("dd", "đ")
        text = text.replace("aa", "â").replace("ee", "ê").replace("oo", "ô")
        text = text.replace("aw", "ă").replace("ow", "ơ").replace("uw", "ư")
        text = text.replace("w", "ư")

        if (toneIdx > 0) {
            val i = toneTargetIndex(text)
            if (i >= 0) {
                vowelBase[text[i]]?.get(toneIdx)?.let { accented ->
                    text = text.substring(0, i) + accented + text.substring(i + 1)
                }
            }
        }

        return if (isFirstUpper && text.isNotEmpty()) {
            text.replaceFirstChar { it.uppercase() }
        } else {
            text
        }
    }

    /** 'u' sau 'q' và 'i' sau 'g' trước nguyên âm là phụ âm (qu-, gi-), không phải nguyên âm. */
    private fun isVowel(text: String, i: Int): Boolean {
        val c = text[i]
        if (c !in plainVowels && c !in markedVowels) return false
        if (c == 'u' && i > 0 && text[i - 1] == 'q') return false
        if (c == 'i' && i > 0 && text[i - 1] == 'g' &&
            i + 1 < text.length && (text[i + 1] in plainVowels || text[i + 1] in markedVowels)
        ) return false
        return true
    }

    private fun toneTargetIndex(text: String): Int {
        // Ưu tiên nguyên âm đã có dấu mũ/móc (lấy cái cuối: "ươ" -> ơ, "uô" -> ô)
        var lastMarked = -1
        for (i in text.indices) if (text[i] in markedVowels) lastMarked = i
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

        // Kiểu mới: dấu đặt ở nguyên âm đầu (hóa, thủy, của); cụm 3+ âm -> giữa (xoài, xoáy)
        return if (e - s + 1 >= 3) s + (e - s + 1) / 2 else s
    }
}
