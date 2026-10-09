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
    fun testBareOnsetNuclei() {
        assertFalse(ViSyllable.isNativeSyllable("kye"))
        assertFalse(ViSyllable.isNativeSyllable("tye"))
        assertFalse(ViSyllable.isNativeSyllable("hye"))
        assertFalse(ViSyllable.isNativeSyllable("bye"))
        assertTrue(ViSyllable.isValid("bye"))
        assertTrue(ViSyllable.isNativeSyllable("yên"))
        assertTrue(ViSyllable.isNativeSyllable("yêu"))
        assertTrue(ViSyllable.isNativeSyllable("yếm"))
        assertTrue(ViSyllable.isNativeSyllable("quyên"))
        assertTrue(ViSyllable.isNativeSyllable("quyền"))
        assertTrue(ViSyllable.isNativeSyllable("quyết"))
        assertTrue(ViSyllable.isNativeSyllable("khuyên"))
        assertTrue(ViSyllable.isNativeSyllable("chuyện"))
        assertTrue(ViSyllable.isNativeSyllable("khuya"))
        assertTrue(ViSyllable.isNativeSyllable("ký"))
        assertTrue(ViSyllable.isNativeSyllable("kỳ"))
        assertTrue(ViSyllable.isNativeSyllable("ye"))
        assertTrue(ViSyllable.isNativeSyllable("yeu"))
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
        // 1.3.1: transform chỉ gộp phím lặp ra ASCII sạch -> chốt đúng như
        // hiển thị, không restore raw chứa phím Telex dư ("tesst" -> "test")
        assertFalse(ViSyllable.restorable("tesst", "test"))
        assertFalse(ViSyllable.restorable("bass", "bas"))
        assertFalse(ViSyllable.restorable("tessst", "tesst"))
        assertEquals("test", TelexEngine().transform("tesst"))
        assertEquals("tét", TelexEngine().transform("test"))
    }

    @Test
    fun testMarkedVowelsMustMatch() {
        // 1.3.4: dấu phụ (ă â ê ô ơ ư) phải khớp vần VN thật — trước đây
        // isValid bỏ qua dấu phụ nên "neư" trôi qua như "neu", tiếng Anh
        // bị đổi mà không khôi phục được (new->neư, view->vieư...)
        assertTrue(ViSyllable.restorable("new", "neư"))
        assertTrue(ViSyllable.restorable("view", "vieư"))
        assertTrue(ViSyllable.restorable("news", "neứ"))
        assertTrue(ViSyllable.restorable("power", "pởe"))
        assertTrue(ViSyllable.restorable("tower", "tởe"))
        assertTrue(ViSyllable.restorable("law", "lă"))
        assertTrue(ViSyllable.restorable("saw", "să"))
        assertTrue(ViSyllable.restorable("show", "shơ"))
        // "hơ" vẫn là âm tiết hợp lệ — how/now/low không phân biệt được
        assertFalse(ViSyllable.restorable("how", "hơ"))
        // Gõ lười vẫn ăn: "duocj" -> "duọc" giữ nguyên
        assertFalse(ViSyllable.restorable("duocj", "duọc"))
        // Vần mang dấu phụ hợp lệ vẫn nhận
        for (w in listOf(
            "được", "muộn", "xuân", "tuyến", "hương", "xoăn", "khuây",
            "huơ", "yên", "boong", "nếu", "cuăng", "trường", "rượu",
            "người", "câu", "mây", "tôi", "chơi", "huế", "mưa", "gửi",
            "hữu", "kiểu", "yêu", "muối", "tươi", "xoây", "ăn", "ân"
        )) {
            assertTrue("$w phải là âm tiết hợp lệ", ViSyllable.isValid(w))
        }
        // ă â iê oă uă uâ uô uyê ươ bắt buộc phụ âm cuối
        assertFalse(ViSyllable.isValid("ă"))
        assertFalse(ViSyllable.isValid("â"))
        assertFalse(ViSyllable.isValid("muô")) // "muôn" mới hợp lệ
        assertFalse(ViSyllable.isValid("hươ")) // dạng mở là "ưa"
        assertFalse(ViSyllable.isValid("tuyê")) // "tuyến"/"tuyệt" mới hợp lệ
        assertFalse(ViSyllable.isValid("iế"))  // dạng mở là "ia"
        assertFalse(ViSyllable.isValid("cuă")) // "cuăng"/"cuăn" mới hợp lệ
        // Vần không tồn tại
        assertFalse(ViSyllable.isValid("nêông"))
        assertFalse(ViSyllable.isValid("nôen"))
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
        // 1.4.0 (A4): "uow" không coda -> "uơ" giữ 'u'
        assertEquals("thuơ", telex.transform("thuow"))
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
        // 1.4.2: 'a'/'e'/'o' cuối sau phụ âm đúp nguyên âm cùng loại đứng
        // kề nguyên âm khác (như 'w' cuối) — "tieng"+"e" -> "tiêng"
        assertEquals("tiêng", telex.transform("tienge"))
        assertEquals("tiếng", telex.transform("tiesnge"))
        assertEquals("tiên", telex.transform("tiene"))
        assertEquals("tuân", telex.transform("tuana"))
        assertEquals("duôn", telex.transform("duono"))
        // Phím thanh đứng ngay trước phím đúp cuối cũng là dấu
        // ("tiengs" + 'e' -> "tiếng")
        assertEquals("tiếng", telex.transform("tiengse"))
        assertEquals("tiềng", telex.transform("tiengfe"))
        assertEquals("tiệng", telex.transform("tiengje"))
        // Nguyên âm đơn lẻ giữa phụ âm không bẻ -> tiếng Anh giữ nguyên
        assertEquals("data", telex.transform("data"))
        assertEquals("delete", telex.transform("delete"))
        assertEquals("banana", telex.transform("banana"))
    }

    // 1.4.0 (A4): "uow" không gì đi sau -> "uơ" (thuow->thuơ, gõ tiếp thuowng->thương);
    // "uow" có coda/nguyên âm sau -> "ươ" như cũ; applyW cùng quy tắc.
    @Test
    fun testUoOpenVsClosed() {
        val telex = TelexEngine()
        val table = listOf(
            "thuowr" to "thuở",
            "huow" to "huơ",
            "khuow" to "khuơ",
            "thuowng" to "thương",
            "dduowcs" to "đước", // đ+ư+ớ+c
            "dduowcj" to "được", // đ+ư+ợ+c ('j' = nặng)
            "nguowif" to "người",
            "quow" to "quơ",
            "truowcs" to "trước",
            "muowif" to "mười"
        )
        for ((keys, expected) in table) {
            assertEquals("keys=$keys", expected, telex.transform(keys))
        }
        // applyW: 'w' bỏ dấu từ xa — "uo" cuối -> "uơ", có coda -> "ươ"
        assertEquals("thuơ", telex.applyW("thuo"))
        assertEquals("thuở", telex.applyW("thuỏ"))
        assertEquals("dươc", telex.applyW("duoc"))
        // Đặt dấu trên ơ của cụm "uơ"
        assertEquals("huở", telex.transform("huowr"))
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

    @Test
    fun learnedDoubledKey_notKeptAsWord() {
        // 1.5.10: "tesst"/"usser" lọt vào dữ liệu học từ trước KHÔNG phải từ
        // thật mà là phím Telex gõ lặp (= chữ s thường). Phím dấu đúp không
        // bao giờ được cổng "từ đã học" giữ nguyên — phải gộp về "test"/"user".
        val e = TelexEngine(EngineOptions(
            commonWord = { it in setOf("tesst", "usser", "max") }
        ))
        assertEquals("test", e.transform("tesst"))
        assertEquals("user", e.transform("usser"))
        assertEquals("Test", e.transform("Tesst"))
        // Từ thật đã học vẫn được giữ nguyên, không bẻ thành "mã".
        assertEquals("max", e.transform("max"))
    }
}

/** 1.4.5: phím ở vị trí lệ thường — dấu thanh kẹt giữa cụm nguyên âm
 *  ("phari") và đúp nguyên âm đơn sau phím dấu ("motoj") — chỉ tiêu thụ
 *  khi kết quả là từ phổ biến còn phím thô thì không ("taxi" giữ nguyên). */
class AmbiguousKeyTest {

    // Tập "từ phổ biến" mô phỏng ContextPredictor.commonWords(): các từ
    // corpus tần suất >= 200. "tãi"/"phốt"/"vía"/"dất" cố ý vắng mặt —
    // chúng hiếm nên không mở đường tắt cho phím thô Latin.
    private val common = setOf(
        "phải", "phái", "phài", "phãi", "phại",
        "khải", "thải", "cải", "hải", "tải", "vải", "dải", "giải",
        "rải", "trải", "sải", "chải", "ngải",
        "cỏi", "hỏi", "mỏi", "tỏi", "sỏi", "khỏi",
        "một", "tốt", "hốt", "mốt", "bốt", "đốt", "nốt", "sốt",
        "chốt", "nhốt", "thốt", "rốt", "cốt", "đột", "tột", "lột",
        "bột", "hột", "mộc", "tiếng", "dáng", "trước", "hôm", "tuân"
    )
    private val e = TelexEngine(
        EngineOptions(commonWord = { it in common })
    )

    @Test
    fun midClusterToneConsumedWhenResultCommon() {
        // "phải" = pha + r + i — 'r' đứng giữa hai nguyên âm
        assertEquals("phải", e.transform("phari"))
        assertEquals("phái", e.transform("phasi"))
        assertEquals("phài", e.transform("phafi"))
        assertEquals("phãi", e.transform("phaxi"))
        assertEquals("phại", e.transform("phaji"))
        assertEquals("khải", e.transform("khari"))
        assertEquals("thải", e.transform("thari"))
        assertEquals("hỏi", e.transform("hori"))
        assertEquals("khỏi", e.transform("khori"))
        assertEquals("cỏi", e.transform("cori"))
        // Kiểu hoa đầu từ vẫn giữ
        assertEquals("Phải", e.transform("Phari"))
    }

    @Test
    fun midClusterToneKeptWhenResultUncommon() {
        // Kết quả âm tiết hợp lệ nhưng KHÔNG phổ biến -> phím thô
        assertEquals("taxi", e.transform("taxi"))   // "tãi" hiếm
        assertEquals("visa", e.transform("visa"))   // "vía" hiếm
        assertEquals("mari", e.transform("mari"))   // "mải" hiếm
        assertEquals("using", e.transform("using")) // "úsng" không phải từ
        assertEquals("basic", e.transform("basic"))
        assertEquals("music", e.transform("music"))
        assertEquals("major", e.transform("major"))
        assertEquals("paris", e.transform("paris"))
        // Phím thô đã là từ phổ biến -> ưu tiên giữ nguyên (veto)
        val e2 = TelexEngine(
            EngineOptions(commonWord = { it == "phải" || it == "phari" })
        )
        assertEquals("phari", e2.transform("phari"))
    }

    @Test
    fun loneVowelDoubleAfterTone() {
        // "một" = mot + o + j — 'o' đúp nguyên âm ĐƠN LẺ sau phụ âm cuối,
        // chỉ được khi đã tiêu thụ phím thanh và kết quả phổ biến
        assertEquals("một", e.transform("motoj"))
        assertEquals("mốt", e.transform("motos"))
        assertEquals("tốt", e.transform("totos"))
        assertEquals("hốt", e.transform("hotos"))
        assertEquals("bột", e.transform("botoj"))
        assertEquals("Một", e.transform("Motoj"))
        // Kết quả không phổ biến -> giữ nguyên
        assertEquals("photos", e.transform("photos")) // "phốt" hiếm
        assertEquals("datas", e.transform("datas"))   // "dất" hiếm
        // Thanh không đặt được trên coda t -> giữ nguyên (luật coda)
        assertEquals("motof", e.transform("motof"))
        assertEquals("motor", e.transform("motor"))
        // KHÔNG có phím thanh -> nguyên âm đơn lẻ vẫn là chữ thật
        assertEquals("moto", e.transform("moto"))
        assertEquals("mono", e.transform("mono"))
        assertEquals("data", e.transform("data"))
        assertEquals("delete", e.transform("delete"))
        assertEquals("banana", e.transform("banana"))
    }

    @Test
    fun noDictKeepsOldBehavior() {
        // commonWord = null (mặc định) -> phím lệ thường luôn là chữ thật
        val e0 = TelexEngine()
        assertEquals("phari", e0.transform("phari"))
        assertEquals("motoj", e0.transform("motoj"))
        assertEquals("taxi", e0.transform("taxi"))
        // Đường đi chuẩn không đổi
        assertEquals("phải", e0.transform("phair"))
        assertEquals("một", e0.transform("mootj"))
        assertEquals("tiếng", e0.transform("tiengse"))
        assertEquals("tiêng", e0.transform("tienge"))
        assertEquals("dáng", e0.transform("dasng"))
        assertEquals("trước", e0.transform("truowcs"))
        // Và vẫn đúng khi có từ điển (không qua cổng lệ thường)
        assertEquals("phải", e.transform("phair"))
        assertEquals("một", e.transform("mootj"))
        assertEquals("tiếng", e.transform("tiengse"))
        assertEquals("trước", e.transform("truowcs"))
    }

    @Test
    fun commonWordsFromPredictor() {
        // Từ chỉ có trong file từ điển (freq=100) không đủ mở đường tắt;
        // từ corpus tần suất cao và từ đã học thì có.
        val p = ContextPredictor()
        p.addWords(listOf("nghiệm", "xuyến")) // freq=100 -> không phổ biến
        var s = p.commonWords()
        assertTrue("phải" in s)   // corpus seed/corpus nhiễu
        assertTrue("một" in s)
        assertFalse("nghiệm" in s)
        assertFalse("xuyến" in s)
        assertFalse("taxi" in s)
        // Từ đã học (personal>0) luôn tính — bảo vệ phím thô đã học
        p.recordSequence("", "taxi", 0L, "")
        s = p.commonWords()
        assertTrue("taxi" in s)
    }
}
