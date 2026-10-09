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

    /** 1.4.0 (C3): cache 1 mục — render/commit/suggest gọi transform lặp
     *  lại cùng raw, chỉ biến đổi lại khi buffer thật sự đổi. @Volatile:
     *  có thể gọi từ worker thread (SuggestWorker); cặp giá trị là immutable
     *  nên đọc/ghi tham chiếu là atomic, race chỉ làm mất cache (an toàn). */
    @Volatile
    private var lastTransform: Pair<String, String>? = null

    private val toneMap = mapOf(
        's' to 1, // Sắc
        'f' to 2, // Huyền
        'r' to 3, // Hỏi
        'x' to 4, // Ngã
        'j' to 5  // Nặng
    )

    internal fun toneIndexOf(c: Char) = toneMap[c.lowercaseChar()] ?: 0

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
     * cuối): "uo" không sau q -> "ươ" (1.4.0: "uo" cuối từ -> "uơ" giữ 'u');
     * nguyên âm cuối a/o/u -> ă/ơ/ư, giữ tone và kiểu hoa của ký tự bị đổi (1.2).
     */
    override fun applyW(word: String): String? {
        for (i in word.length - 2 downTo 0) {
            if (decomposed(word[i]).first == 'u' && decomposed(word[i + 1]).first == 'o' &&
                !(i > 0 && word[i - 1].lowercaseChar() == 'q')
            ) {
                // 1.4.0 (A4): không còn ký tự sau "uo" -> "uơ" giữ 'u'
                if (i + 2 == word.length) {
                    val o = vowelBase.getValue('ơ')[decomposed(word[i + 1]).second]
                    return word.substring(0, i + 1) +
                        (if (word[i + 1].isUpperCase()) o.uppercase() else o)
                }
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

    /** Phím `mark` ('a'/'e'/'o') gõ sau cùng (đã cắt khỏi word): quét ngược
     *  tìm nguyên âm cùng loại đứng kề một nguyên âm khác rồi đúp lên
     *  â/ê/ô, giữ tone và kiểu hoa — như applyW nhưng chặt hơn: nguyên âm
     *  đơn lẻ giữa phụ âm không đụng ("data", "delete").
     *  1.4.5: `relaxed` cho phép đúp cả nguyên âm đơn lẻ ("mot"+"o" -> "môt")
     *  — chỉ bật khi có bằng chứng gõ TV (phím thanh đã tiêu thụ) và kết quả
     *  còn qua cổng từ phổ biến ở caller. Trả Pair(kết quả, đụng-đơn-lẻ). */
    private fun applyRetroDouble(
        word: String, mark: Char, relaxed: Boolean
    ): Pair<String, Boolean>? {
        val target = when (mark) {
            'a' -> 'â'; 'e' -> 'ê'; 'o' -> 'ô'; else -> return null
        }
        var loneHit: String? = null
        for (i in word.length - 1 downTo 0) {
            if (decomposed(word[i]).first != mark) continue
            val nearVowel = (i > 0 && ViTone.isVowelChar(word[i - 1])) ||
                (i + 1 < word.length && ViTone.isVowelChar(word[i + 1]))
            if (!nearVowel) {
                if (relaxed && loneHit == null) {
                    val v = vowelBase.getValue(target)[decomposed(word[i]).second]
                    loneHit = word.substring(0, i) +
                        (if (word[i].isUpperCase()) v.uppercase() else v) +
                        word.substring(i + 1)
                }
                continue
            }
            val v = vowelBase.getValue(target)[decomposed(word[i]).second]
            return (word.substring(0, i) +
                (if (word[i].isUpperCase()) v.uppercase() else v) +
                word.substring(i + 1)) to false
        }
        return loneHit?.let { it to true }
    }

    /** "uow" -> "ươ" phải chạy trước rule "ow"/"uw"; 'u' sau 'q' là phụ âm.
     *  Mask: ư lấy hoa của 'u', ơ lấy hoa của 'o'/'w' (1.2).
     *  1.4.0 (A4): "uow" không còn gì đi sau -> chỉ bẻ "ow"->ơ giữ 'u'
     *  ("thuow"->"thuơ"); có coda/nguyên âm sau -> "ươ" ("dduowc"->"được"). */
    private fun replaceUow(text: String, up: BooleanArray): Pair<String, BooleanArray> {
        var t = text
        var u = up
        var i = t.indexOf("uow")
        while (i >= 0) {
            if (i > 0 && t[i - 1] == 'q') {
                i = t.indexOf("uow", i + 1)
            } else if (i + 3 == t.length) {
                t = t.substring(0, i + 1) + "ơ"
                u = BooleanArray(t.length) { j ->
                    when {
                        j <= i -> u[j]
                        else -> u[i + 1] || u[i + 2]
                    }
                }
                i = t.indexOf("uow", i + 2)
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
        if (!text.contains(from)) return text to up
        // 1.4.0 (C4): quét 1 lượt + 1 StringBuilder/BooleanArray thay vì
        // dựng lại text+mask cho mỗi lần khớp. `to` luôn là ký tự VN nên
        // không thể tạo match mới tràn lên match trước — quét tiếp sau đuôi
        // match tương đương indexOf trên text đã thay.
        val sb = StringBuilder(text.length + 4)
        val nup = BooleanArray(text.length + 4)
        var n = 0
        var i = 0
        var m = text.indexOf(from)
        while (m >= 0) {
            while (i < m) { nup[n] = up[i]; sb.append(text[i]); n++; i++ }
            nup[n] = (m until m + from.length).any { up[it] }
            sb.append(to); n++
            i = m + from.length
            m = text.indexOf(from, i)
        }
        while (i < text.length) { nup[n] = up[i]; sb.append(text[i]); n++; i++ }
        return sb.toString() to nup.copyOf(n)
    }

    /** Thay `from` thành `to` CÙNG ĐỘ DÀI (Quick Telex "tt"->"th") — mask hoa
     *  giữ nguyên vị trí, không cần co giãn (2.x). */
    private fun replaceMaskedSameLen(
        text: String, up: BooleanArray, from: String, to: String
    ): Pair<String, BooleanArray> {
        var m = text.indexOf(from)
        if (m < 0) return text to up
        val sb = StringBuilder(text.length)
        var i = 0
        while (m >= 0) {
            sb.append(text, i, m).append(to)
            i = m + from.length
            m = text.indexOf(from, i)
        }
        sb.append(text, i, text.length)
        return sb.toString() to up
    }

    override fun transform(input: String): String {
        if (input.isEmpty()) return ""
        // 1.5.1: phím thô là từ đã biết/đã học ("max" sau khi user chạm ô
        // phím thô -> học) -> giữ nguyên, không bẻ thành "mã" nữa. Phải chạy
        // TRƯỚC lastTransform — cache "max"->"mã" cũ sẽ nuốt cổng này; cũng
        // phủ phím dấu thường ('x' cuối từ), không chỉ phím lệ thường.
        // 1.5.10: "tesst"/"usser" lọt vào dữ liệu học từ trước KHÔNG phải từ
        // thật mà là phím Telex gõ lặp (= chữ s thường) — bỏ cổng cho chúng.
        val lower = input.lowercase()
        if (!input.equals("dd", ignoreCase = true) &&
            !isTelexKeySequence(input) &&
            opts.commonWord?.invoke(lower) == true
        ) {
            lastTransform = input to input
            return input
        }
        return transformBody(input)
    }

    /** Phần transform không qua cổng 1.5.1 — dùng cho cả đường chính và
     *  kiểm tra isTelexKeySequence. */
    private fun transformBody(input: String): String {
        lastTransform?.let { if (it.first == input) return it.second }
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
            return transformInternal(input, true).also { lastTransform = input to it }
        }
        lastTransform = input to out
        return out
    }

    /** 1.5.10: chuỗi có phím dấu đúp (ss/ff/rr/xx/jj) mà transform gộp ra
     *  ASCII sạch thì là phím Telex, không phải từ — "tesst"->"test",
     *  "usser"->"user". "address"->"ađres" có chữ Việt nên vẫn là từ thật.
     *  Không dùng cache để tránh đọc nhầm kết quả cổng cũ. */
    private fun isTelexKeySequence(input: String): Boolean {
        val lower = input.lowercase()
        if ((1 until lower.length).none {
                lower[it] == lower[it - 1] && lower[it] in "sfrxj"
            }
        ) return false
        val out = transformInternal(input, false)
        val full = if (opts.spellCheckTone && input.length >= 2 &&
            isToneCommand(lower, input.length - 1) &&
            !ViSyllable.isValid(out.lowercase(), strict = false)
        ) transformInternal(input, true) else out
        return full.all { it.code < 128 }
    }

    /** toneLiteral=true: phím dấu CUỐI được giữ làm chữ thường (đường spell-
     *  check của transform()); các phím dấu trước vẫn tiêu thụ bình thường.
     *  allowAmbiguous=false: lượt chạy lại sau khi cổng từ phổ biến từ chối —
     *  phím ở vị trí lệ thường giữ làm chữ (1.4.5). */
    private fun transformInternal(
        input: String, toneLiteral: Boolean, allowAmbiguous: Boolean = true
    ): String {
        var text = input.lowercase()
        var up = BooleanArray(input.length) { input[it].isUpperCase() }
        val amb = allowAmbiguous && opts.commonWord != null

        // 'z' sau phím dấu = phím dấu đó in thành chữ thường ("hoasz" -> "hoas").
        // Viết hoa tạm để vòng quét dấu bên dưới bỏ qua nó, hạ lại ở cuối.
        var zEscaped = false
        if (text.last() == 'z' && text.length >= 2 && toneMap.containsKey(text[text.length - 2])) {
            text = text.dropLast(1)
            up = up.copyOf(text.length)
            text = text.substring(0, text.length - 1) + text.last().uppercaseChar()
            zEscaped = true
        }

        // Phím dấu cuối cùng sau nguyên âm là dấu đang dùng; các phím dấu trước
        // đó (đã tiêu thụ) bị gỡ — nên gõ dấu mới đè lên dấu cũ. toneLiteral:
        // phím dấu cuối giữ làm chữ (đường spell-check 2.x).
        // 1.4.0 (C4): quét 1 lượt, không tạo List chỉ mục trung gian.
        var toneIdx = 0
        var hasCmd = false
        var lastCmd = -1
        if (toneLiteral) {
            for (i in 1 until text.length) if (isToneCommand(text, i)) lastCmd = i
        }
        val litIdx = if (toneLiteral) lastCmd else -1
        var ambiguousUsed = false
        for (i in 1 until text.length) {
            val ambKey = amb && isAmbiguousTone(text, i)
            if ((isToneCommand(text, i) || ambKey) && i != litIdx) {
                if (ambKey) ambiguousUsed = true
                hasCmd = true
                toneIdx = toneMap.getValue(text[i])
            }
        }
        if (hasCmd || litIdx >= 0) {
            val sb = StringBuilder(text.length)
            val nup = BooleanArray(text.length)
            var n = 0
            for (i in text.indices) {
                val cmd = isToneCommand(text, i) || (amb && isAmbiguousTone(text, i))
                if (!cmd || i == litIdx) {
                    sb.append(text[i])
                    nup[n++] = up[i]
                }
            }
            text = sb.toString()
            up = nup.copyOf(n)
        }

        if (zEscaped) text = text.lowercase() // hạ lại ký tự đã escape bằng 'z'

        // 1.4.0 (C4): pre-scan 1 lượt — hai vòng dưới chỉ cần chạy khi có
        // cặp ký tự lặp (phím dấu đúp, run >=3 của aeod, hay "ww"), đa số
        // từ không có nên bỏ qua luôn cấp phát StringBuilder/BooleanArray.
        var hasRun = false
        run {
            var i = 1
            var rpt = 1
            while (i < text.length) {
                if (text[i] == text[i - 1]) {
                    rpt++
                    if ((toneMap.containsKey(text[i]) && rpt >= 2) ||
                        (text[i] == 'w' && rpt >= 2) ||
                        (text[i] in "aeod" && rpt >= 3)
                    ) { hasRun = true; break }
                } else rpt = 1
                i++
            }
        }

        // Cặp đúp phím dấu còn lại = chữ thật ("bass" -> "bas")
        if (hasRun) run {
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
        if (hasRun) run {
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
        // 'a'/'e'/'o' cuối sau phụ âm -> đúp nguyên âm cùng loại đứng kề một
        // nguyên âm khác ("tienge" -> "tiêng", "khuyana" -> "khuyân") —
        // nguyên âm đơn lẻ giữa phụ âm là chữ thật ("data", "delete" giữ
        // nguyên). Phím thanh đứng ngay trước phím đúp cũng là dấu
        // ("tiengse" = "tieng" + 's' + 'e'). Simple Telex không có
        // aa/ee/oo -> không bẻ.
        // 1.4.5: đã tiêu thụ phím thanh -> đúp được cả nguyên âm đơn lẻ
        // ("motoj" = "mot"+"o"+"j" -> "một"), vẫn qua cổng từ phổ biến
        // ("photos" -> "phốt" không phổ biến -> giữ "photos").
        if (opts.method != ImeMethod.TELEX_SIMPLE && text.length > 1 &&
            text.last() in "aeo"
        ) {
            var word = text.dropLast(1)
            var t2 = 0
            if (toneMap.containsKey(word.last()) &&
                ViTone.vowelBefore(word, word.length - 1)
            ) {
                t2 = toneMap.getValue(word.last())
                word = word.dropLast(1)
            }
            if (word.isNotEmpty() && !ViTone.isVowelChar(word.last())) {
                applyRetroDouble(
                    word, text.last(), amb && (hasCmd || t2 > 0)
                )?.let { (res, lone) ->
                    if (lone) ambiguousUsed = true
                    text = res
                    up = up.copyOf(text.length)
                    if (t2 > 0) toneIdx = t2
                }
            } else if (word.isNotEmpty()) {
                applyRetroDouble(word, text.last(), false)?.first?.let { res ->
                    val marked = word.any {
                        it.code > 127 && it != MARK_BREAK && it != W_LITERAL
                    }
                    val effTone = if (t2 > 0) t2 else toneIdx
                    val i = if (effTone > 0)
                        ViTone.toneTargetIndex(res, opts.newToneStyle) else -1
                    val cand = if (i >= 0)
                        res.substring(0, i) +
                            vowelBase.getValue(res[i])[effTone] +
                            res.substring(i + 1)
                    else res
                    val known = word.last() != 'y' &&
                        opts.commonWord?.invoke(cand) == true
                    if ((marked || hasCmd || t2 > 0 || known) &&
                        ViSyllable.isNativeSyllable(cand)
                    ) {
                        text = res
                        up = up.copyOf(text.length)
                        if (t2 > 0) toneIdx = t2
                    }
                }
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

        // 1.5.3: hoàn thiện dấu phụ bắt buộc của vần trần không tồn tại
        // trong chính tả ("tiengs"->"tiếng", "dduocj"->"được" nhờ cổng từ
        // phổ biến) — thay ký tự cùng độ dài, mask hoa không đổi.
        text = ViSyllable.repairBareNucleus(text, opts.commonWord, opts.commonRank)

        // 1.4.5: đã dùng phím ở vị trí lệ thường -> chỉ giữ kết quả khi nó
        // là từ phổ biến còn phím thô thì không ("phari"->"phải"; "taxi"
        // ->"tãi" không phổ biến -> chạy lại giữ 'x' làm chữ).
        if (ambiguousUsed && !ambiguousOk(input, text)) {
            return transformInternal(input, toneLiteral, allowAmbiguous = false)
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

    /** 1.4.5: phím dấu kẹt GIỮA hai nguyên âm ("phari" -> 'r') — lệ thường,
     *  chỉ tiêu thụ khi kết quả qua cổng từ phổ biến (xem [ambiguousOk]).
     *  1.5.3: 'w' cũng là phím nguyên âm (ư/aw/ow/uw) nên phím dấu kẹt giữa
     *  'w' và nguyên âm cũng ăn ("bwsowc" -> "bước"). */
    private fun isAmbiguousTone(text: String, i: Int): Boolean =
        i > 0 && i + 1 < text.length && toneMap.containsKey(text[i]) &&
            (ViTone.isVowelChar(text[i - 1]) || text[i - 1] == 'w') &&
            (ViTone.isVowelChar(text[i + 1]) || text[i + 1] == 'w')

    /** Cổng 1.4.5: giữ kết quả từ phím lệ thường chỉ khi nó là từ phổ biến
     *  còn phím thô thì không — "phải" phổ biến + "phari" không -> ăn;
     *  "tãi" không phổ biến -> "taxi" giữ nguyên. */
    private fun ambiguousOk(input: String, result: String): Boolean {
        val known = opts.commonWord ?: return false
        return known(result) && !known(input.lowercase())
    }

    companion object {
        /** Quick Telex (2.x): cặp phụ âm đôi -> âm đầu ghép. */
        private val QUICK_PAIRS = listOf(
            "cc" to "ch", "gg" to "gi", "kk" to "kh", "nn" to "ng",
            "qq" to "qu", "pp" to "ph", "tt" to "th"
        )
    }
}
