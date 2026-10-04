package com.hkey.app.engine

/** Kiểu gõ (2.x). pref = giá trị lưu trong SharedPreferences "ime_method". */
enum class ImeMethod(val pref: String) {
    TELEX("telex"),
    TELEX_SIMPLE("simple"),  // chỉ w/dd + dấu; không aa/ee/oo
    TELEX_QUICK("quick"),    // Telex + cc=ch gg=gi kk=kh nn=ng qq=qu pp=ph tt=th
    VNI("vni");

    companion object {
        fun fromPref(s: String?): ImeMethod = entries.firstOrNull { it.pref == s } ?: TELEX
    }
}

/** Tùy chọn engine: kiểu gõ, bỏ dấu kiểu mới/cũ, kiểm chính tả trước dấu. */
data class EngineOptions(
    val method: ImeMethod = ImeMethod.TELEX,
    val newToneStyle: Boolean = true,   // true: "hoà"; false: "hòa"
    val spellCheckTone: Boolean = true, // dấu vi phạm luật coda -> in phím thô
    /** 1.4.5: tra cứu "từ phổ biến" cho phím ở vị trí lệ thường (dấu thanh
     *  giữa cụm nguyên âm "phari"->'r', đúp nguyên âm đơn sau phím dấu
     *  "motoj"). Chỉ tiêu thụ phím lệ thường khi kết quả là từ phổ biến —
     *  "phari"->"phải" nhưng "taxi" giữ nguyên ("tãi" không phổ biến).
     *  null = không tiêu thụ phím lệ thường (mặc định, giữ hành vi cũ). */
    val commonWord: ((String) -> Boolean)? = null,
    /** 1.5.3: tần suất tương đối của từ đã biết — chọn nhánh khi vần trần
     *  mơ hồ hai dạng ("nuocs" -> "nước" chứ không "nuốc"). Lớn hơn = phổ
     *  biến hơn; 0/âm = không có trong từ điển. null = chọn nhánh đầu tiên
     *  khớp commonWord (hành vi cũ). */
    val commonRank: ((String) -> Int)? = null
)

/** Giao diện chung của bộ gõ — HKeyIME giữ engine qua interface này (2.x). */
interface ImeEngine {
    fun transform(raw: String): String
    fun stripTones(s: String): String
    /** Phím mark kiểu Telex ('w'); VNI không có -> null -> resume đường buffer. */
    fun applyW(word: String): String? = null

    /** ⌫ xóa 1 ký tự HIỂN THỊ cuối (1.7): thử bỏ từng phím thô để kết quả
     *  đúng bằng phần hiển thị còn lại; không khớp thì cắt dần đuôi thô,
     *  chấp nhận kết quả không dài hơn, đúng tiền tố hoặc chỉ kém dấu. */
    fun dropLastDisplayChar(raw: String): String {
        if (raw.isEmpty()) return raw
        val target = transform(raw).dropLast(1)
        for (i in raw.length - 1 downTo 0) {
            val cand = raw.removeRange(i, i + 1)
            if (transform(cand) == target) return cand
        }
        // Fallback: cắt dần đuôi thô, chọn candidate tốt nhất — khớp đúng
        // target > cùng độ dài chỉ lệch dấu > là tiền tố của target (ít nhất).
        var best = ""
        var bestScore = 0
        var r = raw
        while (r.isNotEmpty()) {
            r = r.dropLast(1)
            val t = transform(r)
            if (t.length > target.length) continue
            val score = when {
                t == target -> return r
                t.length == target.length &&
                    t.indices.all { ViGlyphs.bareChar(t[it]) == ViGlyphs.bareChar(target[it]) } &&
                    t.count { it.code > 127 } <= target.count { it.code > 127 } -> 2
                target.startsWith(t) -> 1
                else -> 0
            }
            if (score > bestScore) { bestScore = score; best = r }
        }
        return best
    }
}
