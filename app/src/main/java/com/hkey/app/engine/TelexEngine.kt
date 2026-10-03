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
class TelexEngine(
    private val opts: EngineOptions = EngineOptions()
) : ImeEngine {

    private val vowelBase = ViGlyphs.vowelBase
    private val decomposed = ViGlyphs::decomposed

    private val toneMap = mapOf(
        's' to 1, // Sắc
        'f' to 2, // Huyền
        'r' to 3, // Hỏi
        'x' to 4, // Ngã
        'j' to 5  // Nặng
    )

    /** Sentinel cho luật "gõ lặp hủy" (1.3): BREAK chặn gộp cặp literal,
     *  W_LITERAL đứng thay 'w' thật để khỏi bị rule ư/aw/ow/uw ăn. */
    private val MARK_BREAK = ''
    private val W_LITERAL = ''

    /** Bỏ dấu thanh, giữ dấu phụ và kiểu hoa: "HoÁn" -> "HoAn". */
    override fun stripTones(s: String): String {
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
    override fun applyW(word: String): String? {
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

    /** Thay `from` thành `to` CÙNG ĐỘ DÀI (Quick Telex "tt"->"th") — mask hoa
     *  giữ nguyên vị trí, không cần co giãn (2.x). */
    private fun replaceMaskedSameLen(
        text: String, up: BooleanArray, from: String, to: String
    ): Pair<String, BooleanArray> {
        var t = text
        var i = t.indexOf(from)
        while (i >= 0) {
            t = t.substring(0, i) + to + t.substring(i + from.length)
            i = t.indexOf(from, i + to.length)
        }
        return t to up
    }

    override fun transform(input: String): String {
        if (input.isEmpty()) return ""
        val out = transformInternal(input, false)
        // 2.x spell-check: phím dấu là ký tự cuối và kết quả không phải âm
        // tiết VN ("sachf"->"sàch" sai coda) -> in phím dấu thô ("sachf").
        // Chỉ xét phím CUỐI để không phá ký tự dấu giữa buffer ("dasng").
        if (opts.spellCheckTone && input.length >= 2 &&
            isToneCommand(input.lowercase(), input.length - 1) &&
            // 1.3.4: non-strict — vần chỉ-đóng đứng trần ("việ") là trạng
            // thái gõ dở hợp lệ, không in phím dấu thô
            !ViSyllable.isValid(out.lowercase(), strict = false)
        ) {
            return transformInternal(input, true)
        }
        return out
    }

    /** toneLiteral=true: phím dấu CUỐI được giữ làm chữ thường (đường spell-
     *  check của transform()); các phím dấu trước vẫn tiêu thụ bình thường. */
    private fun transformInternal(input: String, toneLiteral: Boolean): String {
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
        // đó (đã tiêu thụ) bị gỡ — nên gõ dấu mới đè lên dấu cũ. toneLiteral:
        // phím dấu cuối giữ làm chữ (đường spell-check 2.x).
        var toneIdx = 0
        val cmds = (1 until text.length).filter { isToneCommand(text, it) }
        if (cmds.isNotEmpty()) {
            val litIdx = if (toneLiteral) cmds.last() else -1
            val eff = if (toneLiteral) cmds.dropLast(1) else cmds
            if (eff.isNotEmpty()) toneIdx = toneMap.getValue(text[eff.last()])
            val sb = StringBuilder(text.length)
            val nup = BooleanArray(text.length)
            var n = 0
            for (i in text.indices) {
                if (!isToneCommand(text, i) || i == litIdx) {
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
                } else if (c in "aeod" && run >= 3 &&
                    // Simple Telex không có cặp aa/ee/oo -> không cần hủy lặp (2.x)
                    (opts.method != ImeMethod.TELEX_SIMPLE || c == 'd')
                ) {
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

        // Quick Telex: cặp phụ âm đôi (2.x). Cùng độ dài nên mask không đổi.
        if (opts.method == ImeMethod.TELEX_QUICK) {
            for ((a, b) in QUICK_PAIRS) {
                replaceMaskedSameLen(text, up, a, b).let { text = it.first }
            }
        }
        replaceMasked(text, up, "dd", "đ").let { text = it.first; up = it.second }
        if (opts.method != ImeMethod.TELEX_SIMPLE) { // Simple: không aa/ee/oo (2.x)
            replaceMasked(text, up, "aa", "â").let { text = it.first; up = it.second }
            replaceMasked(text, up, "ee", "ê").let { text = it.first; up = it.second }
            replaceMasked(text, up, "oo", "ô").let { text = it.first; up = it.second }
        }
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
            val i = ViTone.toneTargetIndex(text, opts.newToneStyle)
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

    /**
     * Phím dấu "đang hoạt động": đứng sau nguyên âm, không nằm trong cặp đúp,
     * và là ký tự cuối hoặc đứng trước phụ âm ("dasng" -> "dáng" mà "taxi"
     * vẫn là taxi).
     */
    private fun isToneCommand(text: String, i: Int): Boolean {
        val c = text[i]
        if (i == 0 || !toneMap.containsKey(c)) return false
        if (text[i - 1] == c || (i + 1 < text.length && text[i + 1] == c)) return false
        if (!ViTone.vowelBefore(text, i)) return false
        return i + 1 == text.length || !ViTone.isVowelChar(text[i + 1])
    }

    companion object {
        /** Quick Telex (2.x): cặp phụ âm đôi -> âm đầu ghép. */
        private val QUICK_PAIRS = listOf(
            "cc" to "ch", "gg" to "gi", "kk" to "kh", "nn" to "ng",
            "qq" to "qu", "pp" to "ph", "tt" to "th"
        )
    }
}
