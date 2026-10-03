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
 * Kiểu hoa giữ theo từng ký tự: "USA" -> "USA", "iPhone" -> "iPhone" (1.2).
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

    /** Sentinel cho luật "gõ lặp hủy" (1.3): BREAK chặn gộp cặp literal,
     *  W_LITERAL đứng thay 'w' thật để khỏi bị rule ư/aw/ow/uw ăn. */
    private val MARK_BREAK = ''
    private val W_LITERAL = ''

    /** Tách ký tự có dấu -> (nguyên âm gốc giữ dấu phụ, tone 0..5). */
    private val decompose: Map<Char, Pair<Char, Int>> = buildMap {
        for ((base, variants) in vowelBase) {
            variants.forEachIndexed { i, s -> put(s[0], base to i) }
        }
        put('đ', 'đ' to 0)
    }

    private fun decomposed(c: Char): Pair<Char, Int> =
        decompose[c] ?: c.lowercaseChar().let { decompose[it] ?: (c to 0) }

    /** Bỏ dấu thanh, giữ dấu phụ và kiểu hoa: "HoÁn" -> "HoAn". */
    fun stripTones(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val base = decomposed(c).first
            sb.append(if (c.isUpperCase()) base.uppercaseChar() else base)
        }
        return sb.toString()
    }

    /**
     * Bẻ dấu 'w' lên từ đã có dấu/chữ (bỏ dấu từ xa, hoặc 'w' gõ sau phụ âm
     * cuối): "uo" không sau q -> "ươ"; nguyên âm cuối a/o/u -> ă/ơ/ư, giữ tone
     * và kiểu hoa của ký tự bị đổi (1.2).
     */
    fun applyW(word: String): String? {
        for (i in word.length - 2 downTo 0) {
            if (decomposed(word[i]).first == 'u' && decomposed(word[i + 1]).first == 'o' &&
                !(i > 0 && word[i - 1].lowercaseChar() == 'q')
            ) {
                val u = vowelBase.getValue('ư')[decomposed(word[i]).second]
                val o = vowelBase.getValue('ơ')[decomposed(word[i + 1]).second]
                return word.substring(0, i) +
                    (if (word[i].isUpperCase()) u.uppercase() else u) +
                    (if (word[i + 1].isUpperCase()) o.uppercase() else o) +
                    word.substring(i + 2)
            }
        }
        for (i in word.length - 1 downTo 0) {
            val target = when (decomposed(word[i]).first) {
                'a' -> 'ă'; 'o' -> 'ơ'; 'u' -> 'ư'; else -> null
            } ?: continue
            val v = vowelBase.getValue(target)[decomposed(word[i]).second]
            return word.substring(0, i) +
                (if (word[i].isUpperCase()) v.uppercase() else v) +
                word.substring(i + 1)
        }
        return null
    }

    /** "uow" -> "ươ" phải chạy trước rule "ow"/"uw"; 'u' sau 'q' là phụ âm.
     *  Mask: ư lấy hoa của 'u', ơ lấy hoa của 'o'/'w' (1.2). */
    private fun replaceUow(text: String, up: BooleanArray): Pair<String, BooleanArray> {
        var t = text
        var u = up
        var i = t.indexOf("uow")
        while (i >= 0) {
            if (i > 0 && t[i - 1] == 'q') {
                i = t.indexOf("uow", i + 1)
            } else {
                t = t.substring(0, i) + "ươ" + t.substring(i + 3)
                u = BooleanArray(t.length) { j ->
                    when {
                        j < i -> u[j]
                        j == i -> u[i]
                        j == i + 1 -> u[i + 1] || u[i + 2]
                        else -> u[j + 1]
                    }
                }
                i = t.indexOf("uow", i + 2)
            }
        }
        return t to u
    }

    /** Thay mọi `from` thành `to`; ký tự gộp lấy hoa nếu một ký tự nguồn hoa
     *  ("Aa" -> "Â", "Dd" -> "Đ"). Chỉ dùng với `to` một ký tự (1.2). */
    private fun replaceMasked(
        text: String, up: BooleanArray, from: String, to: String
    ): Pair<String, BooleanArray> {
        var t = text
        var u = up
        var i = t.indexOf(from)
        while (i >= 0) {
            val m = (i until i + from.length).any { u[it] }
            t = t.substring(0, i) + to + t.substring(i + from.length)
            u = BooleanArray(t.length) { j ->
                when {
                    j < i -> u[j]
                    j == i -> m
                    else -> u[j - 1 + from.length]
                }
            }
            i = t.indexOf(from, i + 1)
        }
        return t to u
    }

    fun transform(input: String): String {
        if (input.isEmpty()) return ""
        var text = input.lowercase()
        var up = BooleanArray(input.length) { input[it].isUpperCase() }

        // 'z' sau phím dấu = phím dấu đó in thành chữ thường ("hoasz" -> "hoas").
        // Viết hoa tạm để vòng quét dấu bên dưới bỏ qua nó, hạ lại ở cuối.
        if (text.last() == 'z' && text.length >= 2 && toneMap.containsKey(text[text.length - 2])) {
            text = text.dropLast(1)
            up = up.copyOf(text.length)
            text = text.substring(0, text.length - 1) + text.last().uppercaseChar()
        }

        // Phím dấu cuối cùng sau nguyên âm là dấu đang dùng; các phím dấu trước
        // đó (đã tiêu thụ) bị gỡ — nên gõ dấu mới đè lên dấu cũ.
        var toneIdx = 0
        if ((1 until text.length).any { isToneCommand(text, it) }) {
            toneIdx = toneMap.getValue(text[(text.length - 1 downTo 1).first { isToneCommand(text, it) }])
            val sb = StringBuilder(text.length)
            val nup = BooleanArray(text.length)
            var n = 0
            for (i in text.indices) {
                if (!isToneCommand(text, i)) {
                    sb.append(text[i])
                    nup[n++] = up[i]
                }
            }
            text = sb.toString()
            up = nup.copyOf(n)
        }

        text = text.lowercase() // hạ lại ký tự đã escape bằng 'z'

        // Cặp đúp phím dấu còn lại = chữ thật ("bass" -> "bas")
        run {
            val sb = StringBuilder(text.length)
            val nup = BooleanArray(text.length)
            var n = 0
            var i = 0
            while (i < text.length) {
                sb.append(text[i])
                nup[n] = up[i]
                if (toneMap.containsKey(text[i]) && i + 1 < text.length &&
                    text[i + 1] == text[i]
                ) {
                    nup[n] = nup[n] || up[i + 1]
                    i++
                }
                n++; i++
            }
            text = sb.toString()
            up = nup.copyOf(n)
        }

        // Gõ lặp phím dấu phụ hủy về chữ thật: "aaa"->aa, "ddd"->dd,
        // "ww"->w (1.3). BREAK ngăn cặp literal bị gộp lại; WLITERAL đứng
        // thay 'w' để khỏi bị các rule w (ư/aw/ow/uw) ăn mất.
        run {
            val sb = StringBuilder(text.length + 4)
            val nup = BooleanArray(text.length + 4)
            var n = 0
            var i = 0
            while (i < text.length) {
                val c = text[i]
                var j = i + 1
                while (j < text.length && text[j] == c) j++
                val run = j - i
                if (c == 'w' && run >= 2) {
                    repeat(run - 1) { k -> sb.append(W_LITERAL); nup[n] = up[i + k]; n++ }
                } else if (c in "aeod" && run >= 3) {
                    var left = run
                    var k = i
                    while (left >= 3) {
                        nup[n] = up[k]; sb.append(c); n++
                        sb.append(MARK_BREAK); nup[n] = false; n++ // giữ chỗ trong mask
                        nup[n] = up[k + 1]; sb.append(c); n++
                        k += 3; left -= 3
                    }
                    repeat(left) { t -> nup[n] = up[k + t]; sb.append(c); n++ }
                } else {
                    repeat(run) { t -> nup[n] = up[i + t]; sb.append(c); n++ }
                }
                i = j
            }
            text = sb.toString()
            up = nup.copyOf(n)
        }

        replaceMasked(text, up, "dd", "đ").let { text = it.first; up = it.second }
        replaceMasked(text, up, "aa", "â").let { text = it.first; up = it.second }
        replaceMasked(text, up, "ee", "ê").let { text = it.first; up = it.second }
        replaceMasked(text, up, "oo", "ô").let { text = it.first; up = it.second }
        replaceUow(text, up).let { text = it.first; up = it.second }
        replaceMasked(text, up, "aw", "ă").let { text = it.first; up = it.second }
        replaceMasked(text, up, "ow", "ơ").let { text = it.first; up = it.second }
        replaceMasked(text, up, "uw", "ư").let { text = it.first; up = it.second }
        // 'w' cuối sau phụ âm -> bẻ dấu nguyên âm trước nó ("honw" -> "hơn")
        if (text.endsWith("w")) {
            applyW(text.dropLast(1))?.let {
                text = it
                up = up.copyOf(text.length)
            }
        }
        replaceMasked(text, up, "w", "ư").let { text = it.first; up = it.second }

        if (toneIdx > 0) {
            val i = toneTargetIndex(text)
            if (i >= 0) {
                vowelBase[text[i]]?.get(toneIdx)?.let { accented ->
                    text = text.substring(0, i) + accented + text.substring(i + 1)
                }
            }
        }

        // Dọn sentinel sau khi mọi phép thay đã xong (1.3)
        if (text.indexOf(MARK_BREAK) >= 0 || text.indexOf(W_LITERAL) >= 0) {
            val sb = StringBuilder(text.length)
            val nup = BooleanArray(text.length)
            var m = 0
            for (i in text.indices) {
                when (text[i]) {
                    MARK_BREAK -> Unit
                    W_LITERAL -> { sb.append('w'); nup[m] = up[i]; m++ }
                    else -> { sb.append(text[i]); nup[m] = up[i]; m++ }
                }
            }
            text = sb.toString()
            up = nup.copyOf(m)
        }

        if (!up.any { it }) return text
        val sb = StringBuilder(text)
        for (i in text.indices) {
            if (i < up.size && up[i]) sb.setCharAt(i, text[i].uppercaseChar())
        }
        return sb.toString()
    }

    /** Ký tự gốc bỏ mọi dấu: 'ấ'->'a', 'đ'->'d' (so khớp khi xóa — 1.7). */
    private fun bareChar(c: Char): Char = when (val b = decomposed(c).first) {
        'ă', 'â' -> 'a'; 'ê' -> 'e'; 'ô', 'ơ' -> 'o'; 'ư' -> 'u'; 'đ' -> 'd'
        else -> b
    }

    /** ⌫ xóa 1 ký tự HIỂN THỊ cuối (1.7): thử bỏ từng phím thô (thường nằm
     *  giữa — "vieetj"->việt bỏ 't' -> "vieej"->việ, giữ tone); không khớp
     *  thì cắt dần đuôi thô, chấp nhận kết quả không dài hơn phần còn lại,
     *  đúng tiền tố hoặc chỉ khác ở dấu và không nhiều dấu hơn ("ass"->as:
     *  ⌫ bỏ 's' -> "as"->á không chấp nhận, cắt tiếp -> "a"). */
    fun dropLastDisplayChar(raw: String): String {
        if (raw.isEmpty()) return raw
        val target = transform(raw).dropLast(1)
        for (i in raw.length - 1 downTo 0) {
            val cand = raw.removeRange(i, i + 1)
            if (transform(cand) == target) return cand
        }
        var r = raw
        while (r.isNotEmpty()) {
            r = r.dropLast(1)
            val t = transform(r)
            val acceptable = target.startsWith(t) ||
                (t.length == target.length &&
                    t.indices.all { bareChar(t[it]) == bareChar(target[it]) } &&
                    t.count { it.code > 127 } <= target.count { it.code > 127 })
            if (t.length <= target.length && acceptable) return r
        }
        return ""
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
