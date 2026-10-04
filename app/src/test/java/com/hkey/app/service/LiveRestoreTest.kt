package com.hkey.app.service

import com.hkey.app.engine.EngineOptions
import com.hkey.app.engine.ImeMethod
import com.hkey.app.engine.TelexEngine
import com.hkey.app.engine.ViGlyphs
import com.hkey.app.engine.ViSyllable
import com.hkey.app.engine.VniEngine
import com.hkey.app.settings.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 1.4.0 (E2) — Live restore tiếng Anh.
 *
 * Cổng #1: với mọi âm tiết VN trong vi_dict, sinh chuỗi phím chuẩn (Telex
 * kiểu "dấu cuối" lẫn "dấu sau nguyên âm", Telex Simple, VNI) rồi kiểm
 * mọi TIỀN TỐ không bao giờ liveRestorable — gõ từ VN không bị giật về
 * phím thô giữa chừng. Fail ở từ nào nghĩa là isValid(non-strict) thiếu
 * onset/nucleus -> sửa ViSyllable, không nới test.
 *
 * Cổng #2: ~300 từ tiếng Anh phổ biến sau khi gõ hết phím phải hiển thị
 * == phím thô (liveRestorable) hoặc transform không chứa glyph VN.
 * Từ trùng âm tiết VN hợp lệ ("now"->"nơ") là giới hạn thiết kế — ghi
 * trong knownCollisions để test vẫn bắt từ mới rơi vào nhóm này.
 */
@RunWith(RobolectricTestRunner::class)
class LiveRestoreTest {

    private val decomp = ViGlyphs::decomposed
    private val telex = TelexEngine()
    private val telexOld = TelexEngine(EngineOptions(newToneStyle = false))
    private val telexSimple = TelexEngine(EngineOptions(method = ImeMethod.TELEX_SIMPLE))
    private val vni = VniEngine()

    /** keys = phím thô không dấu thanh; vowelEnd = vị trí chèn dấu thanh
     *  kiểu "sau cụm nguyên âm"; tone = 1..5 (0 = trần). */
    private data class Enc(val keys: String, val vowelEnd: Int, val tone: Int)

    /** Mã hoá Telex chuẩn: "ươ" -> "uow" (không "wow" — prefix "đưo" vô
     *  nghĩa), "ư" đơn -> "w", "ơ" -> "ow". Trả null nếu 2 dấu thanh. */
    private fun telexKeys(word: String): Enc? {
        var tone = 0
        val sb = StringBuilder()
        var vowelEnd = 0
        var i = 0
        while (i < word.length) {
            val (base, t) = decomp(word[i])
            if (t > 0) {
                if (tone > 0) return null
                tone = t
            }
            when {
                base == 'đ' -> sb.append("dd")
                base == 'ư' && i + 1 < word.length &&
                    decomp(word[i + 1]).first == 'ơ' -> {
                    val t2 = decomp(word[i + 1]).second
                    if (t2 > 0) {
                        if (tone > 0) return null
                        tone = t2
                    }
                    sb.append("uow"); i++; vowelEnd = sb.length
                }
                base in "aăâeêioôơuưy" -> {
                    sb.append(
                        when (base) {
                            'ă' -> "aw"; 'â' -> "aa"; 'ê' -> "ee"
                            'ô' -> "oo"; 'ơ' -> "ow"; 'ư' -> "w"
                            else -> base.toString()
                        }
                    )
                    vowelEnd = sb.length
                }
                else -> sb.append(base)
            }
            i++
        }
        return Enc(sb.toString(), vowelEnd, tone)
    }

    /** Mã hoá VNI chuẩn: digit mark ngay sau nguyên âm của nó. */
    private fun vniKeys(word: String): Enc? {
        var tone = 0
        val sb = StringBuilder()
        var vowelEnd = 0
        for (c in word) {
            val (base, t) = decomp(c)
            if (t > 0) {
                if (tone > 0) return null
                tone = t
            }
            when {
                base == 'đ' -> sb.append("d9")
                base == 'ă' -> { sb.append("a8"); vowelEnd = sb.length }
                base == 'â' -> { sb.append("a6"); vowelEnd = sb.length }
                base == 'ê' -> { sb.append("e6"); vowelEnd = sb.length }
                base == 'ô' -> { sb.append("o6"); vowelEnd = sb.length }
                base == 'ơ' -> { sb.append("o7"); vowelEnd = sb.length }
                base == 'ư' -> { sb.append("u7"); vowelEnd = sb.length }
                base in "aeiouy" -> { sb.append(base); vowelEnd = sb.length }
                else -> sb.append(base)
            }
        }
        return Enc(sb.toString(), vowelEnd, tone)
    }

    /** Hai kiểu đặt dấu thanh: cuối từ, và ngay sau cụm nguyên âm. */
    private fun variants(e: Enc, toneKeys: CharArray): List<String> {
        if (e.tone == 0) return listOf(e.keys)
        val k = toneKeys[e.tone]
        val end = e.keys + k
        if (e.vowelEnd >= e.keys.length) return listOf(end)
        val mid = e.keys.substring(0, e.vowelEnd) + k + e.keys.substring(e.vowelEnd)
        return listOf(end, mid)
    }

    private fun dictFile(): File {
        for (p in listOf("src/main/res/raw/vi_dict.txt", "app/src/main/res/raw/vi_dict.txt")) {
            val f = File(p)
            if (f.exists()) return f
        }
        error("không tìm thấy vi_dict.txt")
    }

    private val toneTelex = charArrayOf(' ', 's', 'f', 'r', 'x', 'j')
    private val toneVni = charArrayOf(' ', '1', '2', '3', '4', '5')

    /** Quét prefixes của một chuỗi phím: không prefix nào được liveRestorable,
     *  và chuỗi đầy đủ phải transform ra đúng từ. */
    private fun checkWord(
        engine: com.hkey.app.engine.ImeEngine, word: String, keys: String,
        bad: MutableList<String>
    ) {
        for (i in 1 until keys.length) {
            val p = keys.substring(0, i)
            val t = engine.transform(p)
            if (ViSyllable.liveRestorable(p, t)) {
                bad.add("$word <- $keys : prefix $p => $t")
                return
            }
        }
        val out = engine.transform(keys)
        if (out != word) bad.add("$word <- $keys => $out")
    }

    @Test
    fun dictNeverLiveRestoresTelex() {
        val bad = mutableListOf<String>()
        val unreachable = mutableListOf<String>()
        var checked = 0
        for (w in dictWords()) {
            val enc = telexKeys(w) ?: continue
            for (v in variants(enc, toneTelex)) {
                // chọn engine đặt dấu khớp chính tả của từ (mới: "hoà", cũ: "hòa")
                val engine = when {
                    telex.transform(v) == w -> telex
                    telexOld.transform(v) == w -> telexOld
                    else -> {
                        unreachable.add("$w <- $v (new=${telex.transform(v)} old=${telexOld.transform(v)})")
                        continue
                    }
                }
                checkWord(engine, w, v, bad)
                checked++
            }
        }
        File("build/lr-telex.txt").also { it.parentFile?.mkdirs() }
            .writeText("bad:\n" + bad.joinToString("\n") +
                "\nunreachable:\n" + unreachable.joinToString("\n"))
        println("telex checked=$checked bad=${bad.size} unreachable=${unreachable.size}")
        assertTrue(
            "${bad.size} từ VN bị live restore giữa chừng. Mẫu: " +
                bad.take(15).joinToString("; "),
            bad.isEmpty()
        )
    }

    @Test
    fun dictNeverLiveRestoresTelexSimple() {
        val bad = mutableListOf<String>()
        var checked = 0
        var skippedMarks = 0
        for (w in dictWords()) {
            val enc = telexKeys(w) ?: continue
            // Simple không có aa/ee/oo — từ cần â/ê/ô không gõ được -> bỏ
            if (enc.keys.contains("aa") || enc.keys.contains("ee") ||
                enc.keys.contains("oo")
            ) { skippedMarks++; continue }
            for (v in variants(enc, toneTelex)) {
                if (telexSimple.transform(v) != w) {
                    bad.add("$w <- $v : không round-trip => ${telexSimple.transform(v)}")
                    continue
                }
                checkWord(telexSimple, w, v, bad)
                checked++
            }
        }
        File("build/lr-simple.txt").also { it.parentFile?.mkdirs() }
            .writeText(bad.joinToString("\n"))
        println("simple checked=$checked skippedMarks=$skippedMarks bad=${bad.size}")
        assertTrue(
            "${bad.size} từ (Simple) bị live restore. Mẫu: " +
                bad.take(15).joinToString("; "),
            bad.isEmpty()
        )
    }

    @Test
    fun dictNeverLiveRestoresVni() {
        val bad = mutableListOf<String>()
        val unreachable = mutableListOf<String>()
        var checked = 0
        for (w in dictWords()) {
            val enc = vniKeys(w) ?: continue
            for (v in variants(enc, toneVni)) {
                val out = vni.transform(v)
                if (out != w) {
                    // từ 'oo' không mã hoá được — không phải đường gõ thật
                    unreachable.add("$w <- $v => $out")
                    continue
                }
                checkWord(vni, w, v, bad)
                checked++
            }
        }
        File("build/lr-vni.txt").also { it.parentFile?.mkdirs() }
            .writeText("bad:\n" + bad.joinToString("\n") +
                "\nunreachable:\n" + unreachable.joinToString("\n"))
        println("vni checked=$checked bad=${bad.size} unreachable=${unreachable.size}")
        assertTrue(
            "${bad.size} từ (VNI) bị live restore / lệch. Mẫu: " +
                bad.take(15).joinToString("; "),
            bad.isEmpty()
        )
    }

    private fun dictWords(): List<String> = dictFile().readLines()
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && ViSyllable.isValid(it) }

    // ---- Cổng #2: tiếng Anh ----

    /** Từ Anh transform ra đúng âm tiết VN hợp lệ -> hiển thị bản transform
     *  (giới hạn thiết kế, không restore được). Mỗi mục phải chứng minh được:
     *  liveRestorable false VÀ isValid(non-strict) true. */
    private val knownCollisions = setOf(
        "is", "us", "as", "os", "if", "of", "or", "box", "fox", "six", "fix",
        "mix", "tax", "car", "far", "bar", "war", "for", "her", "per", "now",
        "cow", "how", "low", "row", "bow", "sow", "tow", "mow", "law", "raw",
        "saw", "paw", "win", "best", "test", "must", "list", "cost", "post",
        "lost", "host", "most", "fast", "last", "past", "cast", "soon",
        "room", "moon", "boot", "foot", "root", "loop", "hoop", "troop",
        "poop", "coop", "noon", "boom", "door", "poor", "too", "loot",
        "moot", "toot", "fan", "this", "his", "has", "was", "wow",
        "see", "keep", "deep", "meet", "tree", "been", "seen", "seem",
        // 1.4.0: "two"->tưo chỉ va chạm hiển thị (strict vẫn invalid -> commit
        // vẫn ra "two"); its/down/town/own là va chạm thật kể từ 1.3.4
        "two", "its", "down", "town", "own"
    )

    private val english = ("""
        new view power show window windows answer two twelve twenty sweet
        sweep weekend welcome well went were west wet what when where which
        while white who why wide wife will wind winter wire wish with without
        woman women wonder word work world worry worth would write writer
        wrong wow wowza add address odd middle sudden buddy daddy ladder
        aaron between school good food look book took cool pool tool blood
        choose loose goose proof shoot smooth tooth booth zoom bloom gloom
        broom groom spoon cartoon balloon typhoon monsoon afternoon bedroom
        bathroom classroom mushroom football doorway bookstore bookmark
        roommate schoolwork noodle rooster floor doorbell feel need see free
        degree keep deep sleep meet feet street three tree green screen been
        seen seem teeth cheese indeed agree career engineer offer off staff
        stuff differ coffee pass class glass mass boss loss miss kiss dress
        press progress success access across grass message self half golf
        shelf wolf itself star dear hear year near clear ever never over
        under after water later letter better number member remember computer
        internet flower summer winter enter center order border corner partner
        matter driver text next expect exact example experience express extra
        exercise explain except exchange excuse expert export expose extend
        external extreme index just job join joke jump june july january
        project object subject reject enjoy major journal journey judge junior
        the be to and in that have it for not on with he you do at but by
        from they we say she an my one all would there their what so up out
        about get go me make can like time no him know take people into your
        good some could them other than then only come its think also back
        use how our way even want because any these give day most down town
        brown own draw flaw awful yellow follow tomorrow allow below elbow
        shadow narrow knowledge keyboard hardware software network framework
        workflow downstream downtown password crossroad roadmap household
        warehouse worldwide worthwhile overwhelmed flashlight playwright
        daylight lightning midnight nightmare highlight sunlight daylight
        download upload reload preload overload overflow airflow rainbow
        meadow shadow window widow yellow hollow mellow pillow willow
        borrow sorrow arrow narrow sparrow tomorrow barrow furrow burrow
        minnow winnow wheel wheat whale wheat while white whale whirl
        whiskey whisper whistle wherever somewhat anywhere everywhere
        nowhere otherwise meanwhile homework
    """).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()

    @Test
    fun englishRestoresOrStaysPlain() {
        assertTrue("cần >= 300 từ Anh, hiện ${english.size}", english.size >= 300)
        val bad = mutableListOf<String>()
        val collisions = mutableListOf<String>()
        for (w in english) {
            val t = telex.transform(w)
            when {
                !t.any { it.code > 127 } -> Unit // không glyph — hiển thị bản ASCII
                ViSyllable.liveRestorable(w, t) -> Unit // live restore về raw
                else -> if (w in knownCollisions) {
                    assertTrue(
                        "collision $w phải ra âm tiết VN hợp lệ, được: $t",
                        ViSyllable.isValid(t.lowercase(), strict = false)
                    )
                    collisions.add(w)
                } else bad.add("$w => $t")
            }
        }
        println("english=${english.size} collisions=${collisions.size} bad=${bad.size}")
        assertTrue(
            "${bad.size} từ Anh bị nhuốm thành VN hợp lệ. Mẫu: " +
                bad.take(20).joinToString("; "),
            bad.isEmpty()
        )
        // từ Anh mới rơi vào nhóm collision phải được thêm vào knownCollisions
        // có chủ đích — không cho tự động
    }

    // ---- E2E qua IME thật ----

    @Test
    fun liveRestoreThroughIme() {
        val h = ImeHarness()
        for (w in listOf("window", "new", "view", "power", "show")) {
            h.type(w)
            h.idle()
            assertEquals("composing của $w phải là phím thô", w, h.composing())
            h.type(" ")
            h.idle()
            assertTrue("sau space phải chốt $w", h.text().endsWith("$w "))
        }
    }

    @Test
    fun vietnameseStillTransforms() {
        val h = ImeHarness()
        h.type("vieetj")
        h.idle()
        assertEquals("việt", h.composing())
        h.type(" ")
        assertTrue(h.text().endsWith("việt "))
        h.type("dduowcs")
        h.idle()
        assertEquals("đước", h.composing())
    }

    @Test
    fun liveRestoreOffShowsTransform() {
        val h = ImeHarness(
            prefsSetup = { putBoolean(SettingsKeys.LIVE_RESTORE, false) }
        )
        h.type("new")
        h.idle()
        // pref tắt -> 1.3.4: hiển thị transform "neư"
        assertEquals("neư", h.composing())
    }

    @Test
    fun backspaceDuringLiveRestore() {
        val h = ImeHarness()
        h.type("new")
        h.idle()
        assertEquals("new", h.composing())
        h.type("⌫")
        h.idle()
        assertEquals("ne", h.composing())
    }
}
