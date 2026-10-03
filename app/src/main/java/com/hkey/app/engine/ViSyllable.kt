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
        "ưa", "ưi", "ưu", "ươ",
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
        "ă", "â", "iê", "oă", "uă", "uâ", "uô", "uyê", "ươ"
    )
    private val codas = setOf("", "c", "ch", "m", "n", "ng", "nh", "p", "t")

    /** Âm cuối thanh chắc: chỉ đi với sắc (1) hoặc nặng (5) (1.3). */
    private val sharpCodas = setOf("c", "ch", "p", "t")

    /** true nếu word là một âm tiết tiếng Việt hợp lệ về mặt hình thái.
     *  strict=false: vần chỉ-đóng (ă â iê uô ươ...) được phép đứng trần —
     *  dùng cho kiểm tra GIỮA lúc gõ ("việ" sẽ thành "việt"), còn commit
     *  cuối cùng cần strict vì dạng trần đó không phải từ ("muô","iế"). */
    fun isValid(word: String, strict: Boolean = true): Boolean {
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
     *  sạch ("tesst"->"test") thì chốt đúng như hiển thị, không lộ phím thô. */
    fun restorable(raw: String, transformed: String): Boolean =
        transformed != raw && !isValid(transformed.lowercase()) &&
            transformed.any { it.code > 127 }
}
