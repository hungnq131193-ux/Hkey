package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUnitTest {

    @Test
    fun testTelexTransform() {
        val telex = TelexEngine()
        assertEquals("hôm", telex.transform("hoom"))
        assertEquals("nay", telex.transform("nay"))
        assertEquals("đi", telex.transform("ddi"))
        assertEquals("làm", telex.transform("lamf"))
        assertEquals("phê", telex.transform("phee"))
    }

    @Test
    fun testTelexToneKeyOnlyAtEnd() {
        val telex = TelexEngine()
        // Phụ âm đầu s/f/x không bị hiểu nhầm là dấu thanh
        assertEquals("sa", telex.transform("sa"))
        assertEquals("song", telex.transform("song"))
        assertEquals("xa", telex.transform("xa"))
        assertEquals("fun", telex.transform("fun"))
        // Dấu thanh khi gõ cuối từ
        assertEquals("sáng", telex.transform("sangs"))
        assertEquals("của", telex.transform("cuar"))
        assertEquals("quý", telex.transform("quys"))
        assertEquals("tiền", telex.transform("tieenf"))
        assertEquals("mượn", telex.transform("muwownj"))
        // Gõ đúp phím dấu = in ký tự thật
        assertEquals("bas", telex.transform("bass"))
        // 'z' huỷ dấu: phím dấu thành chữ thường
        assertEquals("hoas", telex.transform("hoasz"))
        assertEquals("tus", telex.transform("tusz"))
        // Viết hoa chữ đầu
        assertEquals("Đi", telex.transform("Ddi"))
    }

    @Test
    fun testUppercaseMask() {
        val telex = TelexEngine()
        // 1.2: giữ kiểu hoa theo từng ký tự — không còn về dạng "Usa"
        assertEquals("USA", telex.transform("USA"))
        assertEquals("VN", telex.transform("VN"))
        assertEquals("iPhone", telex.transform("iPhone"))
        assertEquals("ĐƯỢC", telex.transform("ĐƯỢC"))
        assertEquals("VIỆT", telex.transform("VIEETJ"))
        assertEquals("HOÀ", telex.transform("HOAF"))
        assertEquals("Hoà", telex.transform("Hoaf"))
        assertEquals("Ân", telex.transform("Aan"))
        // Phím dấu hoa (SHIFT+S) vẫn ăn thành dấu sắc
        assertEquals("Hoá", telex.transform("HoaS"))
        // Gõ đúp phím dấu hoa vẫn in chữ thật, giữ hoa
        assertEquals("BAS", telex.transform("BASS"))
        // 'z' huỷ dấu trên từ hoa
        assertEquals("HOAS", telex.transform("HOASZ"))
    }

    @Test
    fun testRetroMarkKeepsCase() {
        val telex = TelexEngine()
        // 1.2: applyW/stripTones trên từ đã commit giữ hoa nguyên âm bị đổi
        assertEquals("HƠN", telex.applyW("HON"))
        assertEquals("Hơn", telex.applyW("Hon"))
        assertEquals("HƠn", telex.applyW("HOn"))
        assertEquals("CƯƠI", telex.applyW("CUOI"))
        assertEquals("HOAN", telex.stripTones("HOÁN"))
        assertEquals("Hoan", telex.stripTones("Hoán"))
    }

    @Test
    fun testToneCodaRule() {
        // 1.3: âm tiết kết thúc c/ch/p/t chỉ nhận sắc hoặc nặng
        assertTrue(ViSyllable.isValid("tét"))
        assertTrue(ViSyllable.isValid("tẹt"))
        assertTrue(ViSyllable.isValid("đặt"))
        assertTrue(ViSyllable.isValid("sách"))
        assertTrue(ViSyllable.isValid("sạch"))
        assertFalse(ViSyllable.isValid("tẽt"))
        assertFalse(ViSyllable.isValid("tèt"))
        assertFalse(ViSyllable.isValid("xẻp"))
        assertFalse(ViSyllable.isValid("cầp"))
        assertTrue(ViSyllable.isValid("xẹp"))
    }

    @Test
    fun testUndoByRetype() {
        val telex = TelexEngine()
        // 1.3: gõ lặp phím dấu phụ hủy mark về chữ thật
        assertEquals("aa", telex.transform("aaa"))
        assertEquals("dd", telex.transform("ddd"))
        assertEquals("w", telex.transform("ww"))
        assertEquals("ww", telex.transform("www"))
        assertEquals("aw", telex.transform("aww"))
        // Mark 2 phím vẫn hoạt động bình thường
        assertEquals("â", telex.transform("aa"))
        assertEquals("đ", telex.transform("dd"))
        assertEquals("ư", telex.transform("w"))
        // Chữ hoa giữ nguyên khi hủy
        assertEquals("AA", telex.transform("AAA"))
    }

    @Test
    fun testDeleteDisplayChar() {
        val telex = TelexEngine()
        // 1.7: ⌫ xóa ký tự hiển thị — xóa 't' của "việt" phải giữ tone -> "việ"
        assertEquals("vieej", telex.dropLastDisplayChar("vieetj"))
        assertEquals("hoaf", telex.dropLastDisplayChar("hoanf")) // hoàn -> hoà
        assertEquals("", telex.dropLastDisplayChar("dd")) // đ -> xóa hết
        assertEquals("a", telex.dropLastDisplayChar("aaa")) // aa -> a
        assertEquals("a", telex.dropLastDisplayChar("ass")) // as -> a
        assertEquals("a", telex.dropLastDisplayChar("add")) // ađ -> a
    }

    @Test
    fun testAutoRestoreRaw() {
        // 1.3: kết quả không phải âm tiết VN -> chốt bằng phím thô
        assertTrue(ViSyllable.restorable("text", "tẽt"))
        assertTrue(ViSyllable.restorable("google", "gôgle"))
        assertTrue(ViSyllable.restorable("www", "ưưư"))
        assertTrue(ViSyllable.restorable("expect", "ẽpect"))
        assertTrue(ViSyllable.restorable("TEXT", "TẼT"))
        assertFalse(ViSyllable.restorable("duoc", "duọc"))
        assertFalse(ViSyllable.restorable("nay", "nay"))
        assertFalse(ViSyllable.restorable("DUOCJ", "DUỌC"))
    }

    @Test
    fun testTonePlacementModern() {
        val telex = TelexEngine()
        // 2 nguyên âm + phụ âm cuối -> dấu ở nguyên âm 2
        assertEquals("hoàn", telex.transform("hoanf"))
        assertEquals("toán", telex.transform("toans"))
        assertEquals("xuán", telex.transform("xuans"))
        assertEquals("xuân", telex.transform("xuaan"))
        assertEquals("xuấn", telex.transform("xuaans"))
        // Cụm mở oa/oe/uy -> dấu ở nguyên âm 2 (kiểu mới)
        assertEquals("hoá", telex.transform("hoas"))
        assertEquals("khoẻ", telex.transform("khoer"))
        assertEquals("khoè", telex.transform("khoef"))
        assertEquals("thuỷ", telex.transform("thuyr"))
        // Cụm mở khác -> dấu ở nguyên âm 1
        assertEquals("cùa", telex.transform("cuaf"))
        assertEquals("tái", telex.transform("tais"))
        assertEquals("ngòi", telex.transform("ngoif"))
        assertEquals("đào", telex.transform("ddaof"))
        // 3 nguyên âm -> dấu giữa
        assertEquals("xoài", telex.transform("xoaif"))
        // gi- và qu- là phụ âm
        assertEquals("già", telex.transform("giaf"))
        assertEquals("quà", telex.transform("quaf"))
        // Nguyên âm có dấu phụ mang dấu thanh
        assertEquals("tiền", telex.transform("tieenf"))
        assertEquals("nhuộm", telex.transform("nhuoomj"))
        assertEquals("trắng", telex.transform("trawngs"))
    }

    @Test
    fun testToneOverrideAndMidWord() {
        val telex = TelexEngine()
        // Gõ dấu mới đè dấu cũ
        assertEquals("hoán", telex.transform("hoanfs"))
        assertEquals("sàng", telex.transform("sangsf"))
        // Dấu gõ giữa từ (trước phụ âm)
        assertEquals("dáng", telex.transform("dasng"))
        // Chữ thật giữa nguyên âm không bị ăn
        assertEquals("taxi", telex.transform("taxi"))
    }

    @Test
    fun testRetroMarkAndTrailingW() {
        val telex = TelexEngine()
        // 'w' gõ sau phụ âm cuối vẫn bẻ dấu nguyên âm
        assertEquals("hơn", telex.transform("honw"))
        assertEquals("thươ", telex.transform("thuow"))
        assertEquals("quơ", telex.transform("quow"))
        // applyW trên từ đã commit (bỏ dấu từ xa), giữ tone cũ
        assertEquals("hơn", telex.applyW("hon"))
        assertEquals("cươi", telex.applyW("cuoi"))
        assertEquals("hớn", telex.applyW("hón"))
        assertNull(telex.applyW("xem")) // 'e' không nhận w -> null
        // stripTones: phím 'z' xoá dấu từ đã gõ
        assertEquals("hoan", telex.stripTones("hoán"))
        assertEquals("tiên", telex.stripTones("tiến"))
        assertEquals("đươc", telex.stripTones("được")) // tone gỡ, dấu phụ giữ
    }

    @Test
    fun testPredictionAndCompletion() {
        val predictor = ContextPredictor()
        val suggestions = predictor.predictNext("hôm")
        assertTrue(suggestions.contains("nay"))

        val comp = predictor.completions("hô")
        assertTrue(comp.contains("hôm"))
        // Prefix không dấu cũng gợi ý được
        assertTrue(predictor.completions("ho").contains("hôm"))
        assertTrue(predictor.completions("dien").contains("điện"))
    }

    @Test
    fun testCorrectionIsConservative() {
        val predictor = ContextPredictor()
        // Từ hợp lệ -> không sửa
        assertNull(predictor.correction("nay", "hôm"))
        assertNull(predictor.correction("đi", null))
        // Sai 1 ký tự, duy nhất 1 phương án -> gợi ý sửa
        assertEquals("nay", predictor.correction("nayy", "hôm"))
        assertEquals("nay", predictor.correction("nayy", null))
        // Nhiều phương án cùng lệch 1 ký tự -> không đoán bừa
        assertNull(predictor.correction("con", null))
        // Sai xa hoặc từ lạ -> không đoán bừa
        assertNull(predictor.correction("xyzqq", null))
        // Đảo 2 ký tự kề (lỗi gõ nhanh)
        assertEquals("gian", predictor.correction("gain", null))
        // Đặt nhầm vị trí dấu, chỉ có 1 phương án cùng thân từ
        assertEquals("người", predictor.correction("ngưòi", null))
        // Thiếu dấu, duy nhất một từ cùng thân trong từ điển
        assertEquals("hôm", predictor.correction("hom", null))
        // Nhiều phương án cùng gần ("tia" ~ kia/tin/bia/...) -> không đoán bừa
        assertNull(predictor.correction("tia", null))
    }

    @Test
    fun testBundledDictionary() {
        val predictor = ContextPredictor()
        // Mô phỏng từ điển nạp từ res/raw/vi_dict.txt
        predictor.addWords(listOf("nghiệm", "trường", "xuyến"))
        // Từ có trong từ điển -> hợp lệ, không bị sửa oan
        assertNull(predictor.correction("nghiệm", null))
        assertNull(predictor.correction("trường", null))
        // Prefix không dấu vẫn gợi ý được từ từ điển ngoài
        assertTrue(predictor.completions("nghie").contains("nghiệm"))
        // Gợi ý từ tiếp theo vẫn hoạt động sau khi index dựng lại
        assertTrue(predictor.predictNext("hôm").contains("nay"))
    }
}
