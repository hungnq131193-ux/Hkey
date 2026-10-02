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
        assertEquals("hóa", telex.transform("hoas"))
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
    fun testPredictionAndCompletion() {
        val predictor = ContextPredictor()
        val suggestions = predictor.predictNext("hôm")
        assertTrue(suggestions.contains("nay"))

        val comp = predictor.completions("hô")
        assertTrue(comp.contains("hôm"))
    }

    @Test
    fun testCorrectionIsConservative() {
        val predictor = ContextPredictor()
        // Từ hợp lệ -> không sửa
        assertNull(predictor.correction("nay", "hôm"))
        assertNull(predictor.correction("đi", null))
        // Sai 1 ký tự -> gợi ý sửa
        assertEquals("nay", predictor.correction("nayy", "hôm"))
        assertEquals("nay", predictor.correction("nayy", null))
        // Sai xa hoặc từ lạ -> không đoán bừa
        assertNull(predictor.correction("xyzqq", null))
    }
}
