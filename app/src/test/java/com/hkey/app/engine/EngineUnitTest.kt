package com.hkey.app.engine

import org.junit.Assert.assertEquals
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
}
