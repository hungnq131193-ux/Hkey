package com.hkey.app.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * 1.5.3 — test kho từ: gõ TỪNG PHÍM qua TelexEngine/VniEngine trên toàn
 * từ điển res/raw/vi_dict.txt với mọi cách đặt phím dấu người dùng hay
 * bấm: phím dấu cuối từ ("tieengs"), giữa từ trước âm cuối ("tieesng",
 * "dduowjc"), lọt giữa cụm nguyên âm ("tiseeng"), bỏ qua phím dấu phụ
 * ("tiengs", "dduocj"), gõ đúp phím dấu huỷ ("bass" -> "bas"), 'z' huỷ
 * phím dấu ("hoasz" -> "hoas").
 *
 * Engine được cấu hình như production: commonWord = từ điển — cổng phím
 * lệ thường 1.4.5 và cổng giữ-phím-thô từ-đã-biết 1.5.1 đều hoạt động.
 *
 * Mỗi từ sinh ra nhiều cách gõ; kết quả mong đợi luôn là chính từ đó.
 * Kết quả lệch ghi theo category ra build/corpus-telex.txt để triage;
 * assert tổng mismatch = 0 ở cuối.
 */
class CorpusTelexTest {

    private val telex = TelexEngine(
        EngineOptions(commonWord = { isCommon(it) }, commonRank = ::rankOf)
    )
    private val vni = VniEngine(
        EngineOptions(ImeMethod.VNI, commonWord = { isCommon(it) }, commonRank = ::rankOf)
    )
    /** Bản sao không cổng phím lệ thường (commonWord=null -> phím dấu lọt
     *  cụm nguyên âm in chữ) — dựng kỳ vọng cho nhánh literal. */
    private val plainTelex = TelexEngine(EngineOptions())

    /** Vần "oo" nguyên văn không mã hoá được Telex (trùng TelexRoundTripTest). */
    private val unencodable = setOf(
        "boong", "boóng", "choòng", "gioong", "goong", "goòng", "moong",
        "moóc", "noong", "oóc", "phoóc", "soong", "soóc", "sóoc", "toóc",
        "voọc", "xoong", "bông", "giông", "gông", "mông", "nông", "sông",
        "xông", "bống", "chồng", "gồng", "mốc", "ốc", "phốc", "sốc",
        "tốc", "vộc"
    )

    // --- giống TelexRoundTripTest: bản đồ tách dấu ---
    private val decompose = ViGlyphs.decompose
    private fun decomp(c: Char) = decompose[c] ?: (c to 0)

    private fun isVietnameseSyllable(word: String) = ViSyllable.isValid(word)

    private data class Case(val keys: String, val category: String)

    /** Phím Telex chuẩn (dấu thanh cuối). Null = không mã hoá được. */
    private fun telexEncode(word: String): String? {
        val toneKeys = charArrayOf(' ', 's', 'f', 'r', 'x', 'j')
        var tone = 0
        val sb = StringBuilder()
        for (c in word) {
            val (base, t) = decomp(c)
            if (t > 0) {
                if (tone > 0) return null
                tone = t
            }
            sb.append(
                when (base) {
                    'đ' -> "dd"; 'ă' -> "aw"; 'â' -> "aa"; 'ê' -> "ee"
                    'ô' -> "oo"; 'ơ' -> "ow"; 'ư' -> "w"
                    else -> base.toString()
                }
            )
        }
        if (tone > 0) sb.append(toneKeys[tone])
        return sb.toString()
    }

    /** Bản đã encode KHÔNG có phím thanh (bỏ ký tự cuối nếu là phím dấu). */
    private fun telexNoTone(word: String): Pair<String, Char>? {
        val keys = telexEncode(word) ?: return null
        val last = keys.last()
        return if (last in "sfrxj") keys.dropLast(1) to last
        else keys to ' '
    }

    /** Cách gõ VNI chuẩn (số dấu thanh cuối): đ=d9 ă=a8 â=a6 ê=e6
     *  ô=o6 ơ=o7 ư=u7, thanh 1-5. */
    private fun vniEncode(word: String): String? {
        var tone = 0
        val sb = StringBuilder()
        for (c in word) {
            val (base, t) = decomp(c)
            if (t > 0) {
                if (tone > 0) return null
                tone = t
            }
            when (base) {
                'đ' -> sb.append("d9")
                'ă' -> sb.append("a8")
                'â' -> sb.append("a6")
                'ê' -> sb.append("e6")
                'ô' -> sb.append("o6")
                'ơ' -> sb.append("o7")
                'ư' -> sb.append("u7")
                else -> sb.append(base)
            }
        }
        if (tone > 0) sb.append('0' + tone)
        return sb.toString()
    }

    /** Ký tự thuộc phần nguyên âm trong chuỗi phím đã encode ('w' là phím
     *  móc/ư nhưng vẫn nằm trong cụm nguyên âm người dùng đang gõ). */
    private fun isVowelKey(c: Char) = c in "aeiouyw"

    /** Mọi vị trí hợp lý để đặt phím dấu trong chuỗi phím đã encode:
     *  cuối cụm nguyên âm (trước phụ âm cuối) và lọt giữa các nguyên âm
     *  (user bấm dấu trước khi gõ xong cụm). */
    private fun tonePositions(keys: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        for (i in 1..keys.length) {
            if (!isVowelKey(keys[i - 1])) continue
            when {
                i == keys.length -> out += i to "toneEnd"
                !isVowelKey(keys[i]) -> out += i to "toneBeforeCoda"
                else -> {
                    // lọt giữa hai nguyên âm — chỉ khi cụm nguyên âm không
                    // bắt đầu ngay đầu từ (vẫn còn onset/âm đầu trước đó)
                    var j = i - 1
                    while (j > 0 && isVowelKey(keys[j - 1])) j--
                    if (j >= 1) out += i to "toneInsideCluster"
                }
            }
        }
        return out
    }

    /** Phím thô bỏ dấu phụ nguyên âm (giữ dd cho đ) + thanh cuối — cách
     *  gõ "tắt" bỏ qua aa/ee/oo/aw/ow/uw ("tiengs" cho "tiếng"). */
    private fun unmarkedKeys(word: String): String? {
        val toneKeys = charArrayOf(' ', 's', 'f', 'r', 'x', 'j')
        var tone = 0
        val sb = StringBuilder()
        for (c in word) {
            val (base, t) = decomp(c)
            if (t > 0) {
                if (tone > 0) return null
                tone = t
            }
            sb.append(
                when (base) {
                    'đ' -> "dd"
                    'ă', 'â' -> 'a'; 'ê' -> 'e'
                    'ô', 'ơ' -> 'o'; 'ư' -> 'u'
                    else -> base
                }
            )
        }
        if (tone > 0) sb.append(toneKeys[tone])
        return sb.toString()
    }

    private fun telexCases(word: String): List<Case> {
        val out = mutableListOf<Case>()
        val (nt, tone) = telexNoTone(word) ?: return out
        if (tone == ' ') {
            out += Case(nt, "canonical")
            return out
        }
        for ((pos, cat) in tonePositions(nt)) {
            out += Case(nt.substring(0, pos) + tone + nt.substring(pos), cat)
        }
        // gõ đúp phím dấu -> huỷ dấu, in chữ ("hoass" -> "hoas")
        out += Case(nt + tone + tone, "undoDoubleTone")
        // 'z' huỷ phím dấu liền trước ("hoasz" -> "hoas")
        out += Case(nt + tone + "z", "zUndo")
        // bỏ qua phím dấu phụ ("tiengs" cho "tiếng", "dduocj" cho "được") —
        // chỉ khi nucleus của từ thuộc dạng bắt buộc dấu phụ; vần trần hợp
        // lệ ("bán"/"bấn") mơ hồ bẩm sinh, không kỳ vọng tự bẻ.
        if (repairableNucleus(word)) {
            unmarkedKeys(word)?.let { bare ->
                val k = if (bare.last() in "sfrxj") bare.dropLast(1) else bare
                if (k != nt) {
                    for ((pos, cat) in tonePositions(k)) {
                        out += Case(
                            k.substring(0, pos) + tone + k.substring(pos),
                            "unmarked.$cat"
                        )
                    }
                }
            }
        }
        return out.distinctBy { it.keys }
    }

    /** Nucleus của word (dạng trần) thuộc bảng bắt buộc dấu phụ không —
     *  tách theo cùng luật ViSyllable. */
    private fun repairableNucleus(word: String): Boolean {
        val plain = word.map { c ->
            if (ViGlyphs.decomposed(c).first == 'đ') 'd'
            else ViGlyphs.decomposed(c).first
        }.joinToString("")
        val (_, nuc, coda) = ViSyllable.split(plain) ?: return false
        val bare = nuc.map(ViGlyphs::bareChar).joinToString("")
        return ViSyllable.forcedMarks
            .containsKey("$bare|${if (coda.isEmpty()) "" else "C"}")
    }

    private fun vniCases(word: String): List<Case> {
        val out = mutableListOf<Case>()
        val full = vniEncode(word) ?: return out
        val last = full.last()
        if (last !in '1'..'5') {
            out += Case(full, "vni.canonical")
            return out
        }
        val nt = full.dropLast(1)
        // đơn giản hoá: thử đặt số thanh sau mỗi nguyên âm liền trước một
        // phụ âm (user gõ "hoa1n" thay "hoan1")
        for (i in 1 until nt.length) {
            if (nt[i - 1] in "aeiouy" && nt[i] !in "aeiouy" &&
                nt[i] !in '1'..'9'
            ) {
                out += Case(
                    nt.substring(0, i) + last + nt.substring(i),
                    "vni.toneMid"
                )
            }
        }
        out += Case(full, "vni.toneEnd")
        // gõ đúp số dấu -> in số ("hoa11" -> "hoa1")
        out += Case(nt + last + last, "vni.undoDoubleTone")
        return out.distinctBy { it.keys }
    }

    @Test
    fun corpusAllSpellings() {
        val words = dictFile().readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && isVietnameseSyllable(it) }
        val byCat = sortedMapOf<String, MutableList<String>>()
        var cases = 0
        for (w in words) {
            if (w in unencodable) continue
            for (c in telexCases(w)) {
                // phím gõ trùng một từ đã biết ("visa" cho "vía", "boom"
                // cho "bôm") — mơ hồ bẩm sinh, engine phải giữ từ thật
                if (isCommon(c.keys) && c.keys != w) continue
                cases++
                val got = telex.transform(c.keys)
                if (got !in expectedFor(w, c)) {
                    byCat.getOrPut(c.category) { mutableListOf() }
                        .add("${c.keys} -> $got (muốn ${expectedFor(w, c)}: $w)")
                }
            }
            for (c in vniCases(w)) {
                cases++
                val got = vni.transform(c.keys)
                if (got !in expectedFor(w, c)) {
                    byCat.getOrPut(c.category) { mutableListOf() }
                        .add("${c.keys} -> $got (muốn ${expectedFor(w, c)}: $w)")
                }
            }
        }
        val report = buildString {
            appendLine("words=${words.size} cases=$cases mismatch=${byCat.values.sumOf { it.size }}")
            for ((cat, list) in byCat) {
                appendLine("=== $cat: ${list.size} ===")
                list.forEach { appendLine(it) }
            }
        }
        File("build/corpus-telex.txt").also { it.parentFile?.mkdirs() }
            .writeText(report)
        println(report.lineSequence().take(60).joinToString("\n"))
        assertTrue(
            "${byCat.values.sumOf { it.size }} cách gõ lệch — xem build/corpus-telex.txt",
            byCat.isEmpty()
        )
    }

    /** Kỳ vọng (có thể nhiều đáp án đúng): undo = phần đã gõ KHÔNG thanh
     *  biến đổi như khi theo sau bởi phụ âm ('q' giả làm mốc — "uow" giữa
     *  từ ra "ươ" chứ không "uơ") + 1 phím dấu in chữ; chấp nhận thêm dạng
     *  gộp trần nguyên văn. Còn lại = từ gốc. */
    private fun expectedFor(word: String, c: Case): Set<String> = when {
        c.category == "undoDoubleTone" || c.category == "zUndo" -> {
            val nt = c.keys.dropLast(2)
            val t = c.keys[c.keys.length - 2]
            setOf(
                telex.transform(nt + "q").dropLast(1) + t,
                nt + t
            )
        }
        c.category == "vni.undoDoubleTone" -> {
            val nt = c.keys.dropLast(2)
            val t = c.keys[c.keys.length - 2]
            setOf(vni.transform(nt + "q").dropLast(1) + t, nt + t)
        }
        // vần trần mơ hồ hai dạng ("uo|C" -> uô/ươ, "uoi|" -> uôi/ươi):
        // engine chọn nhánh có tần suất cao hơn — "nuocs"->nước chứ không
        // nuốc. Kỳ vọng = argmax rank trên các ứng viên, không phải từ gốc.
        c.category.startsWith("unmarked.") -> ambiguousCandidates(word)
            ?.let { cands ->
                val best = cands.maxBy(::rankOf)
                // phím dấu lọt cụm nguyên âm chỉ ăn khi kết quả là từ đã
                // biết (cổng 1.4.5); không ai biết -> phím dấu in chữ,
                // các phím dấu phụ khác vẫn ăn ("ddusong" -> "đusong")
                if (c.category == "unmarked.toneInsideCluster" &&
                    !isCommon(best)
                ) setOf(c.keys, plainTelex.transform(c.keys))
                else setOf(best)
            } ?: setOf(word)
        else -> setOf(word)
    }

    /** Từ gốc w có vần trần mơ hồ -> dựng mọi ứng viên (giữ thanh của w)
     *  để chọn theo rank; null = vần không mơ hồ/không repair được. */
    private fun ambiguousCandidates(word: String): List<String>? {
        val plain = word.map { c ->
            if (ViGlyphs.decomposed(c).first == 'đ') 'd'
            else ViGlyphs.decomposed(c).first
        }.joinToString("")
        val (onsetLen, nuc, coda) = ViSyllable.split(plain) ?: return null
        val bare = nuc.map(ViGlyphs::bareChar).joinToString("")
        val cands = ViSyllable.forcedMarks["$bare|${if (coda.isEmpty()) "" else "C"}"]
            ?: return null
        if (cands.size < 2) return null
        return cands.map { cand ->
            word.substring(0, onsetLen) +
                ViSyllable.buildNucleus(word, onsetLen, cand) +
                word.substring(onsetLen + nuc.length)
        }
    }

    companion object {
        private fun dictFile(): File {
            for (p in listOf(
                "src/main/res/raw/vi_dict.txt",
                "app/src/main/res/raw/vi_dict.txt"
            )) {
                val f = File(p)
                if (f.exists()) return f
            }
            error("không tìm thấy vi_dict.txt")
        }

        private val dictSet: Set<String> by lazy {
            dictFile().readLines().map { it.trim().lowercase() }.toSet()
        }

        /** Tần suất corpus thật từ vi_model.bin (như loadPacked trong app);
         *  từ chỉ có trong vi_dict được mặc định 100 giống addWords. */
        private val modelFreqs: Map<String, Int> by lazy {
            var f = File("src/main/res/raw/vi_model.bin")
            if (!f.exists()) f = File("app/src/main/res/raw/vi_model.bin")
            val p = ViModelBin.read(ByteBuffer.wrap(f.readBytes()))
                ?: return@lazy emptyMap()
            buildMap { for (i in p.words.indices) put(p.words[i], p.freqs[i]) }
        }

        /** Bản đồ "từ phổ biến" y như ContextPredictor.commonWordFreqs():
         *  chỉ từ có freq >= COMMON_FREQ trong model (dict-only freq=100
         *  không đủ — "taxi" vẫn giữ nguyên). */
        @JvmStatic
        fun rankOf(w: String): Int = modelFreqs[w]?.takeIf { it >= 200 } ?: 0

        /** Cổng commonWord của test: dict word (phím thô là từ thật) hoặc
         *  từ đủ phổ biến theo model — giống commonFreqs trong production. */
        @JvmStatic
        fun isCommon(w: String): Boolean = w in dictSet || rankOf(w) > 0
    }
}
