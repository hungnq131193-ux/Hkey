package com.hkey.app.engine

/**
 * Lightweight Vietnamese Telex Transformation Engine.
 * Supports standard Telex tones (s, f, r, x, j) and accents (aa->â, aw->ă, ee->ê, oo->ô, ow->ơ, uw->ư, dd->đ).
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

    fun transform(input: String): String {
        if (input.isEmpty()) return ""
        var text = input.lowercase()
        val isFirstUpper = input.first().isUpperCase()

        // Phụ âm 'đ'
        text = text.replace("dd", "đ")

        // Nguyên âm có dấu mũ/móc
        text = text.replace("aa", "â")
        text = text.replace("aw", "ă")
        text = text.replace("ee", "ê")
        text = text.replace("oo", "ô")
        text = text.replace("ow", "ơ")
        text = text.replace("uw", "ư")
        text = text.replace("w", "ư")

        // Tìm tone key ở cuối hoặc trong từ
        for ((char, toneIdx) in toneMap) {
            if (text.contains(char)) {
                // Kiểm tra xem có nguyên âm để gán dấu thanh không
                val targetVowel = findVowelToAccent(text)
                if (targetVowel != null && vowelBase.containsKey(targetVowel)) {
                    val accented = vowelBase[targetVowel]!![toneIdx]
                    text = text.replaceFirst(targetVowel.toString(), accented)
                    // Xóa ký tự gõ dấu (s, f, r, x, j)
                    val lastCharIdx = text.lastIndexOf(char)
                    if (lastCharIdx != -1) {
                        text = text.removeRange(lastCharIdx, lastCharIdx + 1)
                    }
                    break
                }
            }
        }

        return if (isFirstUpper && text.isNotEmpty()) {
            text.replaceFirstChar { it.uppercase() }
        } else {
            text
        }
    }

    private fun findVowelToAccent(word: String): Char? {
        val vowels = listOf('ơ', 'ê', 'â', 'ă', 'ô', 'ư', 'a', 'e', 'o', 'u', 'i', 'y')
        for (v in vowels) {
            if (word.contains(v)) return v
        }
        return null
    }
}
