package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Giai đoạn 0.3 — vòng khứ hồi Telex trên toàn từ điển res/raw/vi_dict.txt.
 * Với mỗi âm tiết tiếng Việt hợp lệ: sinh phím Telex chuẩn (encode) rồi kiểm
 * transform() trả lại đúng từ gốc. Từ không phải tiếng Việt (account, btw,
 * cj, f…) bị lọc bởi luật âm tiết. Từ điển đã chuẩn hoá sang kiểu đặt dấu
 * mới (quyết định người dùng). Các từ Telex vốn không mã hoá được — vần `oo`
 * nguyên văn (boong) và `u+ơ` đánh vào luật uow (huơ) — nằm trong UNENCODABLE
 * và được kiểm ngược: nếu engine sau này gõ được chúng, test báo đỏ để xoá
 * khỏi danh sách.
 */
class TelexRoundTripTest {

    /** Từ không gõ được bằng Telex chuẩn; giữ trong từ điển để còn gợi ý. */
    private val unencodable = setOf(
        "boong", "boóng", "choòng", "gioong", "goong", "goòng", "moong",
        "moóc", "noong", "oóc", "phoóc", "soong", "soóc", "sóoc", "toóc",
        "voọc", "xoong", "huơ", "khuơ", "thuở", "uở"
    )

    private val telex = TelexEngine()

    // --- Bản đồ ký tự có dấu (giống vowelBase trong TelexEngine) ---
    private val vowelBase = mapOf(
        'a' to "aáàảãạ", 'ă' to "ăắằẳẵặ", 'â' to "âấầẩẫậ",
        'e' to "eéèẻẽẹ", 'ê' to "êếềểễệ", 'i' to "iíìỉĩị",
        'o' to "oóòỏõọ", 'ô' to "ôốồổỗộ", 'ơ' to "ơớờởỡợ",
        'u' to "uúùủũụ", 'ư' to "ưứừửữự", 'y' to "yýỳỷỹỵ"
    )
    private val decompose: Map<Char, Pair<Char, Int>> = buildMap {
        for ((base, variants) in vowelBase)
            variants.forEachIndexed { i, s -> put(s, base to i) }
        put('đ', 'đ' to 0)
    }
    private fun decomp(c: Char) = decompose[c] ?: (c to 0)

    /** Bỏ cả dấu thanh lẫn dấu phụ: đ->d, ăâ->a, ê->e, ôơ->o, ư->u. */
    private fun deaccent(c: Char): Char = when (decomp(c).first) {
        'đ' -> 'd'; 'ă', 'â' -> 'a'; 'ê' -> 'e'; 'ô', 'ơ' -> 'o'; 'ư' -> 'u'
        else -> decomp(c).first
    }

    /** Luật âm tiết VN — impl chung nằm ở main (ViSyllable) để correction
     *  dùng cùng một luật lọc. */
    private fun isVietnameseSyllable(word: String) = ViSyllable.isValid(word)

    /** Sinh phím Telex chuẩn cho một từ có dấu (phím dấu thanh đặt cuối). */
    private fun telexEncode(word: String): String? {
        val toneKeys = charArrayOf(' ', 's', 'f', 'r', 'x', 'j')
        var tone = 0
        val sb = StringBuilder()
        for (c in word) {
            val (base, t) = decomp(c)
            if (t > 0) {
                if (tone > 0) return null // >1 dấu thanh: không mã hoá được
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

    private fun dictFile(): File {
        for (p in listOf("src/main/res/raw/vi_dict.txt", "app/src/main/res/raw/vi_dict.txt")) {
            val f = File(p)
            if (f.exists()) return f
        }
        error("không tìm thấy vi_dict.txt")
    }

    @Test
    fun dictionaryWordsRoundTrip() {
        val words = dictFile().readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val skipped = mutableListOf<String>()
        val mismatches = mutableListOf<String>()
        val fixed = mutableListOf<String>()
        var checked = 0
        for (w in words) {
            if (!isVietnameseSyllable(w)) {
                skipped.add(w)
                continue
            }
            val keys = telexEncode(w)
            if (keys == null) {
                skipped.add(w)
                continue
            }
            val out = telex.transform(keys)
            if (w in unencodable) {
                if (out == w) fixed.add(w)
            } else if (out != w) {
                mismatches.add("$w <- $keys => $out")
            }
            checked++
        }
        val report = buildString {
            appendLine("checked=$checked skipped=${skipped.size} mismatches=${mismatches.size} unencodable=${unencodable.size}")
            appendLine("--- skipped (không phải âm tiết VN / không mã hoá được) ---")
            skipped.forEach { appendLine(it) }
            appendLine("--- mismatches (từ <- phím => kết quả) ---")
            mismatches.forEach { appendLine(it) }
        }
        File("build/telex-roundtrip.txt").also { it.parentFile?.mkdirs() }.writeText(report)
        println(report.lineSequence().take(40).joinToString("\n"))
        assertTrue(
            "Giờ gõ được rồi, xoá khỏi UNENCODABLE: $fixed",
            fixed.isEmpty()
        )
        assertTrue(
            "${mismatches.size} từ lệch vòng khứ hồi, xem build/telex-roundtrip.txt. Mẫu: " +
                mismatches.take(20).joinToString("; "),
            mismatches.isEmpty()
        )
    }

    /** 1.3.4 — isValid giữ mũ/móc: mọi từ VN trong từ điển phải còn hợp lệ;
     *  tập từ không hợp lệ cố định = tiếng Anh giữ lại + 4 mục rác
     *  (nêông, nôen, â, ă). Thêm từ mới bị tụt -> sửa ViSyllable, không
     *  được sửa danh sách này. */
    @Test
    fun dictionaryWordsStayValid() {
        val knownInvalid = setOf(
            "admin", "ads", "aids", "android", "app", "apps", "bluetooth",
            "btc", "bug", "camera", "code", "covid", "cpu", "cv", "deal",
            "dev", "eth", "fan", "flu", "freeship", "game", "gpu", "hdd",
            "hiv", "hp", "hr", "inbox", "internet", "ipad", "iphone",
            "laptop", "like", "link", "links", "livestream", "login",
            "logout", "macbook", "messenger", "modem", "nokia", "nêông",
            "nôen", "offline", "online", "oppo", "otp", "pr", "sale",
            "samsung", "selfie", "share", "shop", "ssd", "story", "team",
            "telegram", "tiktok", "usb", "usd", "video", "virus", "visa",
            "vivo", "voucher", "web", "website", "wifi", "xiaomi",
            "youtube", "zalo", "â", "ă"
        )
        val invalid = dictFile().readLines()
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .filter { !isVietnameseSyllable(it) }
        assertEquals(
            "từ từ điển bị isValid tụt ngoài danh sách cho phép",
            knownInvalid, invalid.toSet()
        )
    }

    /** 2.4 — phát hiện khoá trùng trong bigramModel (G6). */
    @Test
    fun noDuplicateBigramKeys() {
        val src = File("src/main/java/com/hkey/app/engine/ContextPredictor.kt").readText()
        val outer = Regex(""""([^"]+)"\s+to\s+mutableMapOf""")
            .findAll(src).map { it.groupValues[1] }.toList()
        assertEquals("khoá bigram ngoài bị trùng", outer.toSet().size, outer.size)
        for (line in src.lineSequence().filter { "mutableMapOf(" in it }) {
            val inner = Regex(""""([^"]+)"\s+to\s+\d+""")
                .findAll(line).map { it.groupValues[1] }.toList()
            assertEquals("khoá bigram trong bị trùng: $line", inner.toSet().size, inner.size)
        }
    }
}
