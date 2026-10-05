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

    /** Nguyên âm gốc GIỮ dấu phụ (đ->d để khớp onset): 'ấ'->'â', 'ớ'->'ơ'.
     *  1.3.4: so vần trên dạng này — dấu mũ/móc đã gõ phải khớp đúng, chữ
     *  chưa dấu mới coi là chưa chốt. Trước đây bỏ hết dấu phụ nên "neư"
     *  trôi qua như "neu" (new/view/power bị đổi không khôi phục được). */
    private fun syllableChar(c: Char): Char =
        if (decomp(c).first == 'đ') 'd' else decomp(c).first

    private val onsets = listOf(
        "ngh", "qu", "gi", "gh", "ng", "nh", "ch", "kh", "ph", "th", "tr",
        "b", "c", "d", "g", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "x", ""
    )
    private val nuclei = setOf(
        "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
        "ai", "ao", "au", "ay", "âu", "ây", "eo", "eu", "êu",
        "ia", "iê", "ie", "iu",
        "oa", "oă", "oe", "oi", "ôi", "ơi", "oo",
        "ua", "uă", "uâ", "ue", "uê", "ui", "uo", "uô", "uơ", "uy", "uu",
        "ya", "ye", "yê",
        "ưa", "ưi", "ưu", "ươ", "ưo",
        "ieu", "iêu", "yeu", "yêu",
        "uya", "uye", "uyê", "uyu",
        "uoi", "uôi", "uou",
        "oai", "oao", "oay", "oây", "oeo",
        "uay", "uây",
        "ươi", "ươu"
    )
    /** Vần chỉ tồn tại khi có âm cuối ("ăn","muôn","tuyến"): dạng mở tương
     *  ứng là a/ua/ia... ("ă","muô","iế","hươ" không phải âm tiết). */
    private val closedOnlyNuclei = setOf(
        "ă", "â", "iê", "oă", "uă", "uâ", "uô", "uyê", "ươ",
        // 1.4.0 (E2): "ưo" = trạng thái gõ dở VNI "u7o" -> "ươ" (giống "việ"
        // -> "việt"); không phải âm tiết nên vẫn closed-only
        "ưo"
    )
    private val codas = setOf("", "c", "ch", "m", "n", "ng", "nh", "p", "t")
    private val bareOnsetNuclei = setOf("ye", "yê", "yeu", "yêu")

    /** Âm cuối thanh chắc: chỉ đi với sắc (1) hoặc nặng (5) (1.3). */
    private val sharpCodas = setOf("c", "ch", "p", "t")

    /** true nếu word là một âm tiết tiếng Việt hợp lệ về mặt hình thái.
     *  strict=false: vần chỉ-đóng (ă â iê uô ươ...) được phép đứng trần —
     *  dùng cho kiểm tra GIỮA lúc gõ ("việ" sẽ thành "việt"), còn commit
     *  cuối cùng cần strict vì dạng trần đó không phải từ ("muô","iế"). */
    fun isValid(word: String, strict: Boolean = true): Boolean =
        parseSyllable(word, strict, nativeOnly = false)

    fun isNativeSyllable(word: String): Boolean =
        parseSyllable(word, strict = true, nativeOnly = true)

    private fun parseSyllable(
        word: String, strict: Boolean, nativeOnly: Boolean
    ): Boolean {
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
        val plain = word.map { syllableChar(it) }.joinToString("")
        for (onset in onsets) {
            if (!plain.startsWith(onset)) continue
            val rhyme = plain.removePrefix(onset)
            for (len in rhyme.length downTo 1) {
                val nuc = rhyme.substring(0, len)
                val coda = rhyme.substring(len)
                if (nuc in nuclei && coda in codas) {
                    if (nativeOnly && onset.isNotEmpty() && onset != "qu" &&
                        nuc in bareOnsetNuclei
                    ) continue
                    // "tẽt" không hợp lệ — thử cách tách khác trước khi bỏ
                    if (coda in sharpCodas &&
                        toneVal != 0 && toneVal != 1 && toneVal != 5
                    ) continue
                    // "muô" không phải âm tiết — thử cách tách khác
                    if (strict && coda.isEmpty() && nuc in closedOnlyNuclei) continue
                    return true
                }
            }
        }
        return false
    }

    /** Transform chèn glyph VN vào kết quả không phải âm tiết hợp lệ -> chốt
     *  bằng phím thô ("text/expect/window" về nguyên gõ — 1.3). 1.3.1: chỉ
     *  restore khi xuất hiện ký tự VN — transform chỉ gộp phím lặp ra ASCII
     *  sạch ("tesst"->"test") thì chốt đúng như hiển thị, không lộ phím thô.
     *  1.5.1: kết quả không nguyên âm = viết tắt có chủ đích ("ddc"->"đc",
     *  "dd"->"đ") — giữ transform, không hoàn nguyên. ("www"->"ưưư" có
     *  nguyên âm vẫn hoàn nguyên như cũ.) */
    fun restorable(raw: String, transformed: String): Boolean =
        transformed != raw && !isValid(transformed.lowercase()) &&
            transformed.any { it.code > 127 } && hasVowel(transformed)

    /** 1.4.0 (E2): như restorable nhưng non-strict — giữa lúc gõ, vần
     *  chỉ-đóng đứng trần ("việ") vẫn là trạng thái hợp lệ; chỉ live restore
     *  kết quả hoàn toàn không phải âm tiết ("window" -> "windoư"). Kết quả
     *  1 ký tự ("dd"->"đ", "aa"->"â") luôn là trạng thái gõ dở.
     *  1.4.1: dấu thanh rơi giữa từ, nằm sau một âm tiết hợp lệ và trước
     *  một âm tiết hợp lệ -> đang gõ TV (đặt dấu sớm kiểu "tiesnge" ->
     *  "tié|nge"), không phải tiếng Anh — hiện transform để thấy dấu đã ăn.
     *  "expect"->"ẻpet": dấu ở đầu từ -> vẫn restore; "west"->"ứet",
     *  "doorway"->"dôửay": đầu âm trước dấu không phải âm -> vẫn restore. */
    fun liveRestorable(raw: String, transformed: String): Boolean {
        // 1.5.1: kết quả không nguyên âm -> viết tắt ("ddc"->"đc") hiển
        // thị transform luôn, không treo phím thô như tiếng Anh.
        if (transformed.length < 2 || transformed == raw ||
            isValid(transformed.lowercase(), strict = false) ||
            transformed.none { it.code > 127 } || !hasVowel(transformed)
        ) return false
        for (i in 1 until transformed.length) {
            if (ViGlyphs.decomposed(transformed[i]).second != 0) {
                val pre = transformed.substring(0, i + 1)
                    .map(ViGlyphs::bareChar).joinToString("")
                val tail = transformed.substring(i + 1)
                    .map(ViGlyphs::bareChar).joinToString("")
                if (isValid(pre.lowercase(), strict = false) &&
                    isValid(tail.lowercase(), strict = false)
                ) return false
            }
        }
        return true
    }

    /** Kết quả transform chứa nguyên âm không — 1.5.1: chuỗi toàn phụ âm
     *  ("đc", "đk") là viết tắt chủ đích -> giữ transform; có nguyên âm
     *  ("ưưư", "tẽt") thì vẫn là tiếng Anh bị bẻ -> hoàn nguyên như cũ. */
    private fun hasVowel(s: String): Boolean =
        s.any { ViGlyphs.bareChar(it).lowercaseChar() in VOWELS }

    private const val VOWELS = "aăâeêioôơuưy"

    // -------------------------------------------------- 1.5.3: dấu phụ bắt buộc

    /** Vần TRẦN không tồn tại trong chính tả -> dạng có dấu phụ.
     *  Khóa = nucleus trần + "|" + (coda rỗng ? "" : "C"). "ie"/"ye"/"uye"
     *  chỉ trần được khi mở; "ue"/"uu"/"uou"/"uo" mở chưa bao giờ trần. */
    internal val forcedMarks = mapOf(
        "ie|C" to listOf(listOf('i', 'ê')),
        "ye|C" to listOf(listOf('y', 'ê')),
        "uye|C" to listOf(listOf('u', 'y', 'ê')),
        "ieu|" to listOf(listOf('i', 'ê', 'u')),
        "yeu|" to listOf(listOf('y', 'ê', 'u')),
        "ue|" to listOf(listOf('u', 'ê')),
        "ue|C" to listOf(listOf('u', 'ê')),
        "uu|" to listOf(listOf('ư', 'u')),
        "uou|" to listOf(listOf('ư', 'ơ', 'u')),
        "uo|" to listOf(listOf('u', 'ơ')),
        // "uo"+coda mơ hồ uô/ươ ("thuốc"/"được") — cổng từ điển quyết
        "uo|C" to listOf(listOf('u', 'ô'), listOf('ư', 'ơ')),
        "uoi|" to listOf(listOf('u', 'ô', 'i'), listOf('ư', 'ơ', 'i'))
    )

    /** Tách word thành (độ dài onset, nucleus, coda) theo đúng thứ tự
     *  duyệt của isValid; null = không tách được. */
    internal fun split(plain: String): Triple<Int, String, String>? {
        for (onset in onsets) {
            if (!plain.startsWith(onset)) continue
            val rhyme = plain.removePrefix(onset)
            for (len in rhyme.length downTo 1) {
                val nuc = rhyme.substring(0, len)
                val coda = rhyme.substring(len)
                if (nuc in nuclei && coda in codas) {
                    return Triple(onset.length, nuc, coda)
                }
            }
        }
        return null
    }

    /**
     * 1.5.3 — hoàn thiện dấu phụ bắt buộc của vần trần (gõ tắt bỏ qua
     *  aa/ee/oo/w): "tiengs" -> "tiếng", "bienr" -> "biển", "dduocj" ->
     *  "được" (cổng từ phổ biến chọn uô/ươ), "muoif" -> "muồi".
     *  Chỉ đụng vần hoàn toàn trần mà dạng trần đó không tồn tại trong
     *  chính tả — vần trần hợp lệ ("bán","bón") giữ nguyên. Giữ thanh
     *  trên từng ký tự được bẻ; độ dài không đổi. */
    fun repairBareNucleus(
        word: String,
        commonWord: ((String) -> Boolean)? = null,
        commonRank: ((String) -> Int)? = null
    ): String {
        // Repair cần từ điển để phân giải nhánh (uô/ươ) và bảo vệ từ thật
        // ("kủo"); không có từ điển thì không đoán.
        if (commonWord == null) return word
        // Chỉ sửa khi user đã gõ phím dấu (kết quả có thanh) — "diet"/"viet"
        // không phím dấu nào thì giữ nguyên, không bị bẻ thành "diêt"/"viêt".
        if (word.none { decomp(it).second > 0 }) return word
        // Từ trần đã là từ thật ("kủo") -> giữ nguyên
        if (commonWord.invoke(word)) return word
        val plain = word.map { syllableChar(it) }.joinToString("")
        val (onsetLen, nuc, coda) = split(plain) ?: return word
        if (nuc.any { it in ViGlyphs.markedVowels }) return word // đã có mũ/móc
        val candidates = forcedMarks["$nuc|${if (coda.isEmpty()) "" else "C"}"]
            ?: return word
        val chosen = if (candidates.size == 1) {
            candidates[0]
        } else {
            // cả hai dạng đều viết được -> chọn theo tần suất từ điển
            // ("nuocs" -> "nước" thắng "nuốc"); không có rank thì nhánh đầu
            // tiên khớp commonWord như cũ.
            val words2 = candidates.map { cand ->
                word.substring(0, onsetLen) +
                    buildNucleus(word, onsetLen, cand) +
                    word.substring(onsetLen + nuc.length)
            }
            val hit = if (commonRank != null) {
                val best = words2.indices.maxByOrNull { commonRank(words2[it]) }
                if (best != null && commonRank(words2[best]) > 0) best else -1
            } else {
                words2.indexOfFirst { commonWord?.invoke(it) == true }
            }
            candidates[if (hit >= 0) hit else 0]
        }
        return word.substring(0, onsetLen) +
            buildNucleus(word, onsetLen, chosen) +
            word.substring(onsetLen + nuc.length)
    }

    /** Dựng nucleus đã bẻ dấu phụ. Tone đang ở ký tự sẽ thành nguyên âm
     *  trần thì dời sang nguyên âm có dấu phụ cuối ("huef" -> "huề" chứ
     *  không "hùê", "chuyesn" -> "chuyến" chứ không "chuýên"). */
    internal fun buildNucleus(word: String, onsetLen: Int, target: List<Char>): String {
        var tonePos = -1
        var tone = 0
        for (j in target.indices) {
            val t = decomp(word[onsetLen + j]).second
            if (t > 0) { tonePos = j; tone = t }
        }
        if (tonePos >= 0 && target[tonePos] !in ViGlyphs.markedVowels) {
            val m = target.indexOfLast { it in ViGlyphs.markedVowels }
            if (m >= 0) tonePos = m
        }
        val sb = StringBuilder(target.size)
        for (j in target.indices) {
            sb.append(
                ViGlyphs.vowelBase.getValue(target[j])[if (j == tonePos) tone else 0]
            )
        }
        return sb.toString()
    }
}
