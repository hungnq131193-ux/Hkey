package com.hkey.app.engine

/**
 * Vietnamese Telex engine.
 * Phím dấu (s f r x j) được "tiêu thụ" khi đứng sau nguyên âm: gõ đè dấu mới
 * thay dấu cũ ("hoanfs" -> "hoán", "dasng" -> "dáng"). Gõ đúp phím dấu in chữ
 * thật ("bass" -> "bas"); 'z' huỷ phím dấu liền trước ("hoasz" -> "hoas").
 * Dấu mũ/móc: aa->â ee->ê oo->ô aw->ă ow->ơ uw->ư dd->đ, w đơn -> ư.
 * Dấu thanh đặt theo kiểu mới (QĐ 1989/BGDĐT-2018): nguyên âm mang dấu phụ
 * (ă â ê ô ơ ư) -> trên nó; cụm 2 nguyên âm kèm phụ âm cuối -> nguyên âm 2
 * ("hoàn"); cụm mở oa/oe/uy -> nguyên âm 2 ("hoà", "khoẻ", "thuỷ"); cụm mở
 * khác -> nguyên âm 1 ("của", "tái"); cụm 3 nguyên âm -> giữa ("xoài").
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

    /** Cụm mở đặt dấu ở nguyên âm 2 theo kiểu mới: hoà, khoẻ, thuỷ. */
    private val secondVowelOpenClusters = setOf("oa", "oe", "uy")

    fun transform(input: String): String {
        if (input.isEmpty()) return ""
        val isFirstUpper = input.first().isUpperCase()
        var text = input.lowercase()

        // 'z' sau phím dấu = phím dấu đó in thành chữ thường ("hoasz" -> "hoas").
        // Viết hoa tạm để vòng quét dấu bên dưới bỏ qua nó, hạ lại ở cuối.
        if (text.last() == 'z' && text.length >= 2 && toneMap.containsKey(text[text.length - 2])) {
            text = text.dropLast(1)
            text = text.substring(0, text.length - 1) + text.last().uppercaseChar()
        }

        // Phím dấu cuối cùng sau nguyên âm là dấu đang dùng; các phím dấu trước
        // đó (đã tiêu thụ) bị gỡ — nên gõ dấu mới đè lên dấu cũ.
        var toneIdx = 0
        if ((1 until text.length).any { isToneCommand(text, it) }) {
            toneIdx = toneMap.getValue(text[(text.length - 1 downTo 1).first { isToneCommand(text, it) }])
            val sb = StringBuilder(text.length)
            for (i in text.indices) {
                if (!isToneCommand(text, i)) sb.append(text[i])
            }
            text = sb.toString()
        }

        text = text.lowercase() // hạ lại ký tự đã escape bằng 'z'

        // Cặp đúp phím dấu còn lại = chữ thật ("bass" -> "bas")
        text = collapseDoubledToneKeys(text)

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

    private fun isVowelChar(c: Char) = c in plainVowels || c in markedVowels || c == 'w'

    private fun vowelBefore(text: String, i: Int) = (0 until i).any { isVowelChar(text[it]) }

    /**
     * Phím dấu "đang hoạt động": đứng sau nguyên âm, không nằm trong cặp đúp,
     * và là ký tự cuối hoặc đứng trước phụ âm ("dasng" -> "dáng" mà "taxi"
     * vẫn là taxi).
     */
    private fun isToneCommand(text: String, i: Int): Boolean {
        val c = text[i]
        if (i == 0 || !toneMap.containsKey(c)) return false
        if (text[i - 1] == c || (i + 1 < text.length && text[i + 1] == c)) return false
        if (!vowelBefore(text, i)) return false
        return i + 1 == text.length || !isVowelChar(text[i + 1])
    }

    private fun collapseDoubledToneKeys(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            sb.append(text[i])
            if (toneMap.containsKey(text[i]) && i + 1 < text.length && text[i + 1] == text[i]) i++
            i++
        }
        return sb.toString()
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
        // Nguyên âm đã có dấu phụ (lấy cái cuối: "ươ" -> ơ, "uô" -> ô)
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

        val len = e - s + 1
        if (len == 1) return s
        if (len >= 3) return s + 1 // cụm 3 âm: dấu ở giữa (xoài)

        // Cụm 2 nguyên âm: có phụ âm cuối -> âm 2 ("hoàn"); mở oa/oe/uy -> âm 2
        // kiểu mới ("hoà", "khoẻ", "thuỷ"); còn lại -> âm 1 ("của", "tái")
        val hasFinalConsonant = e + 1 < text.length
        return if (hasFinalConsonant || text.substring(s, e + 1) in secondVowelOpenClusters) e else s
    }
}
