package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.x — kiểu gõ VNI / Simple / Quick Telex, kiểu dấu cũ-mới, spell-check. */
class MethodPhase2Test {

    private fun telex(o: EngineOptions = EngineOptions()) = TelexEngine(o)
    private fun vni(o: EngineOptions = EngineOptions(ImeMethod.VNI)) = VniEngine(o)

    @Test
    fun simpleTelex() {
        val e = telex(EngineOptions(ImeMethod.TELEX_SIMPLE))
        assertEquals("aa", e.transform("aa"))   // không còn aa->â
        assertEquals("ee", e.transform("ee"))
        assertEquals("oo", e.transform("oo"))
        assertEquals("aaa", e.transform("aaa")) // không có luật đúp -> không hủy
        assertEquals("ăn", e.transform("awn"))
        assertEquals("ơn", e.transform("own"))
        assertEquals("ư", e.transform("w"))
        assertEquals("đi", e.transform("ddi"))
        assertEquals("làm", e.transform("lamf")) // dấu vẫn hoạt động
        assertEquals("dd", e.transform("ddd"))
    }

    @Test
    fun quickTelex() {
        val e = telex(EngineOptions(ImeMethod.TELEX_QUICK))
        assertEquals("thanh", e.transform("ttanh"))
        assertEquals("nghe", e.transform("nnhe"))
        assertEquals("ngê", e.transform("nnee"))
        assertEquals("chim", e.transform("ccim"))
        assertEquals("gió", e.transform("gios"))
        assertEquals("khi", e.transform("kki"))
        assertEquals("quen", e.transform("qqen"))
        assertEquals("phố", e.transform("ppoos"))
        // Telex thường vẫn chạy
        assertEquals("đi", e.transform("ddi"))
        assertEquals("tâm", e.transform("taam"))
    }

    @Test
    fun toneStyleOldNew() {
        val mới = telex(EngineOptions(newToneStyle = true))
        val cũ = telex(EngineOptions(newToneStyle = false))
        assertEquals("hoà", mới.transform("hoaf"))
        assertEquals("hòa", cũ.transform("hoaf"))
        assertEquals("khoẻ", mới.transform("khoer"))
        assertEquals("khỏe", cũ.transform("khoer"))
        assertEquals("thuỷ", mới.transform("thuyr"))
        assertEquals("thủy", cũ.transform("thuyr"))
        // cụm có phụ âm cuối giống nhau cả hai kiểu
        assertEquals("hoàn", mới.transform("hoanf"))
        assertEquals("hoàn", cũ.transform("hoanf"))
    }

    @Test
    fun spellCheckToneGoesLiteral() {
        val e = telex()
        // coda sắc nhọn + huyền/hỏi/ngã -> phím dấu in thô
        assertEquals("sachf", e.transform("sachf"))
        assertEquals("tetr", e.transform("tetr"))
        assertEquals("duocx", e.transform("duocx"))
        // dấu hợp lệ vẫn áp bình thường
        assertEquals("sách", e.transform("sachs"))
        assertEquals("duọc", e.transform("duocj"))
        assertEquals("dáng", e.transform("dasng")) // phím dấu giữa không bị ảnh hưởng
        // tắt tính năng -> hành vi cũ
        assertEquals("sàch", telex(EngineOptions(spellCheckTone = false)).transform("sachf"))
    }

    @Test
    fun vniBasics() {
        val e = vni()
        assertEquals("á", e.transform("a1"))
        assertEquals("à", e.transform("a2"))
        assertEquals("ả", e.transform("a3"))
        assertEquals("ã", e.transform("a4"))
        assertEquals("ạ", e.transform("a5"))
        assertEquals("â", e.transform("a6"))
        assertEquals("ă", e.transform("a8"))
        assertEquals("ơ", e.transform("o7"))
        assertEquals("ư", e.transform("u7"))
        assertEquals("đ", e.transform("d9"))
        assertEquals("đi", e.transform("d9i"))
        assertEquals("việt", e.transform("vie6t5"))
        assertEquals("ương", e.transform("u7o7ng"))
        assertEquals("được", e.transform("d9u7o7c5"))
    }

    @Test
    fun vniLiteralAndUndo() {
        val e = vni()
        assertEquals("a1", e.transform("a11"))    // đúp số = in số thô
        assertEquals("t7", e.transform("t7"))     // không có o/u -> literal
        assertEquals("k6", e.transform("k6"))     // không có a/e/o -> literal
        assertEquals("t9", e.transform("t9"))     // không có d -> literal
        assertEquals("f", e.transform("f"))       // phím thường là chữ
        assertEquals("xói", e.transform("xoi1"))  // 1 = sắc, không phải mũ
    }

    @Test
    fun vniCaseAndSpellCheck() {
        val e = vni()
        assertEquals("VIỆT", e.transform("VIE6T5"))
        assertEquals("Được", e.transform("D9u7o7c5"))
        // spell-check: coda t + ngã sai -> số in thô
        assertEquals("tet4", e.transform("tet4"))
        assertEquals("tét", e.transform("tet1"))
    }
}
