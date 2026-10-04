package com.hkey.app.engine

/**
 * Kiểu gõ VNI (2.x): số 1-5 = dấu sắc/huyền/hỏi/ngã/nặng, 6 = mũ (â ê ô),
 * 7 = móc (ơ ư), 8 = trăng (ă), 9 = đ. Lệnh số tác động ngược lên nguyên âm
 * gần nhất phía trước; số không có đích in thô ("t7" -> "t7"); gõ lặp hủy:
 * "a11" -> "a1". Dấu thanh theo cùng luật đặt âm với Telex (ViTone).
 * Kiểu hoa giữ theo ký tự như TelexEngine (1.2).
 */
class VniEngine(
    private val opts: EngineOptions = EngineOptions(ImeMethod.VNI)
) : ImeEngine {

    /** Sentinel cho chữ số in thô sau hủy lặp: LIT_BASE + ('1'..'9'). */
    private val LIT_BASE = ''

    private fun markTargets(d: Char) = when (d) {
        '6' -> mapOf('a' to 'â', 'e' to 'ê', 'o' to 'ô')
        '7' -> mapOf('o' to 'ơ', 'u' to 'ư')
        '8' -> mapOf('a' to 'ă')
        else -> emptyMap()
    }

    /** Số d ở vị trí i có đích để tác động không (có nguyên âm/'d' trước nó). */
    private fun digitApplies(text: String, i: Int): Boolean = when (text[i]) {
        in '1'..'5' -> (0 until i).any { ViTone.isVowelChar(text[it]) }
        '6', '7', '8' -> (0 until i).any { ViGlyphs.decomposed(text[it]).first in markTargets(text[i]) }
        '9' -> (0 until i).any { text[it] == 'd' }
        else -> false
    }

    override fun stripTones(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val base = ViGlyphs.decomposed(c).first
            sb.append(if (c.isUpperCase()) base.uppercaseChar() else base)
        }
        return sb.toString()
    }

    override fun transform(input: String): String {
        if (input.isEmpty()) return ""
        // 1.5.1: phím thô là từ đã biết/đã học -> giữ nguyên (như Telex).
        if (opts.commonWord?.invoke(input.lowercase()) == true) return input
        var text = input.lowercase()
        var up = BooleanArray(input.length) { input[it].isUpperCase() }

        // Run chữ số có tác dụng -> (n-1) số literal (hủy lặp, như Telex).
        run {
            val sb = StringBuilder(text.length)
            val nup = BooleanArray(text.length)
            var n = 0
            var i = 0
            while (i < text.length) {
                val c = text[i]
                var j = i + 1
                while (j < text.length && text[j] == c) j++
                val run = j - i
                if (c in '1'..'9' && run >= 2 && digitApplies(text, i)) {
                    repeat(run - 1) { k ->
                        sb.append(LIT_BASE + (c - '0'))
                        nup[n] = up[i + k]
                        n++
                    }
                } else {
                    repeat(run) { t -> sb.append(c); nup[n] = up[i + t]; n++ }
                }
                i = j
            }
            text = sb.toString()
            up = nup.copyOf(n)
        }

        // Xử lý lệnh số trái -> phải; số áp dụng được thì bị tiêu thụ.
        var i = 0
        while (i < text.length) {
            val d = text[i]
            if (d !in '1'..'9') { i++; continue }
            val saved = text
            var consumed = false
            when (d) {
                in '1'..'5' -> {
                    val t = ViTone.toneTargetIndex(text.substring(0, i), opts.newToneStyle)
                    if (t >= 0) {
                        val base = ViGlyphs.decomposed(text[t]).first
                        ViGlyphs.vowelBase[base]?.let { v ->
                            text = text.substring(0, t) + v[d - '0'] + text.substring(t + 1)
                            consumed = true
                        }
                    }
                }
                '6', '7', '8' -> {
                    val targets = markTargets(d)
                    var t = -1
                    for (k in i - 1 downTo 0) {
                        if (ViGlyphs.decomposed(text[k]).first in targets) { t = k; break }
                    }
                    if (t >= 0) {
                        val (base, tone) = ViGlyphs.decomposed(text[t])
                        val v = ViGlyphs.vowelBase.getValue(targets.getValue(base))[tone]
                        text = text.substring(0, t) + v + text.substring(t + 1)
                        consumed = true
                    }
                }
                '9' -> {
                    var t = -1
                    for (k in i - 1 downTo 0) if (text[k] == 'd') { t = k; break }
                    if (t >= 0) {
                        text = text.substring(0, t) + "đ" + text.substring(t + 1)
                        consumed = true
                    }
                }
            }
            if (consumed) {
                // spell-check: số DẤU CUỐI (1-5) làm hỏng âm tiết -> giữ thô.
                // Chỉ số dấu — số dấu phụ (đ/â/ơ) không revert ("d9"->"đ").
                // Kiểm trên text ĐÃ bỏ số ("á1" bản thân số làm isValid sai).
                if (opts.spellCheckTone && d in '1'..'5' && i == text.length - 1 &&
                    // 1.3.4: non-strict như TelexEngine — "việ" gõ dở vẫn ăn dấu
                    !ViSyllable.isValid(
                        text.removeRange(i, i + 1).lowercase(), strict = false
                    )
                ) {
                    text = saved
                    i++
                    continue
                }
                text = text.removeRange(i, i + 1)
                up = BooleanArray(text.length) { if (it < i) up[it] else up[it + 1] }
            } else i++
        }

        // Dọn sentinel số literal (chiếm đúng 1 slot — mask không đổi)
        if (text.any { it in (LIT_BASE + 1)..(LIT_BASE + 9) }) {
            text = text.map { c ->
                if (c in (LIT_BASE + 1)..(LIT_BASE + 9)) '0' + (c - LIT_BASE) else c
            }.joinToString("")
        }

        // 1.5.3: hoàn thiện dấu phụ bắt buộc của vần trần như Telex
        // ("tien1" -> "tiến", "duoc5" -> "được") — cùng độ dài, mask giữ.
        text = ViSyllable.repairBareNucleus(text, opts.commonWord, opts.commonRank)

        if (!up.any { it }) return text
        val sb = StringBuilder(text)
        for (k in text.indices) {
            if (k < up.size && up[k]) sb.setCharAt(k, text[k].uppercaseChar())
        }
        return sb.toString()
    }
}
