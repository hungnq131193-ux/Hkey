package com.hkey.app.ui

/**
 * Bảng màu + kiểu dáng một giao diện bàn phím. Thuần dữ liệu (Int ARGB) —
 * không đụng android.graphics để unit test JVM được.
 *
 * 1.3.2: thêm nền gradient (bgTop -> bgBottom), viền phím (keyStroke, 0 =
 * không viền), bóng phím tuỳ theme (shadowDp = 0 -> phím phẳng, dùng cho
 * phím trong suốt trên nền gradient) và màu chữ phím chức năng riêng.
 */
class KbPalette(
    val key: Int, val keyPressed: Int, val func: Int, val funcPressed: Int,
    val text: Int, val dim: Int, val accent: Int, val popupBg: Int,
    val bg: Int, val bar: Int, val divider: Int,
    val shadow: Int, val enter: Int, val enterPressed: Int, val onAccent: Int,
    val bgTop: Int = bg,
    val bgBottom: Int = bg,
    val keyStroke: Int = 0,
    val shadowDp: Float = 1.2f,
    val funcText: Int = text,
    /** true = theme nền sáng (thanh điều hướng/ripple dùng icon tối). */
    val light: Boolean = false
) {
    val gradient: Boolean get() = bgTop != bgBottom

    companion object {
        val DARK = KbPalette(
            key = 0xFF24383F.toInt(), keyPressed = 0xFF35515C.toInt(),
            func = 0xFF172A31.toInt(), funcPressed = 0xFF27424C.toInt(),
            text = 0xFFF0F7F8.toInt(), dim = 0xFFA6BFC5.toInt(),
            accent = 0xFF70DBCE.toInt(), popupBg = 0xFF2C454E.toInt(),
            bg = 0xFF0C191E.toInt(), bar = 0xFF0C191E.toInt(), divider = 0xFF1D3037.toInt(),
            shadow = 0xFF060F13.toInt(), enter = 0xFF007F79.toInt(),
            enterPressed = 0xFF00665F.toInt(), onAccent = 0xFFFFFFFF.toInt()
        )
        val LIGHT = KbPalette(
            key = 0xFFFFFFFF.toInt(), keyPressed = 0xFFDCE9E8.toInt(),
            func = 0xFFDDE8E8.toInt(), funcPressed = 0xFFC3D5D4.toInt(),
            text = 0xFF14262C.toInt(), dim = 0xFF5A7680.toInt(),
            accent = 0xFF007F79.toInt(), popupBg = 0xFFFFFFFF.toInt(),
            bg = 0xFFF4F8F8.toInt(), bar = 0xFFF4F8F8.toInt(), divider = 0xFFD5E2E2.toInt(),
            shadow = 0xFFB9C9C9.toInt(), enter = 0xFF007F79.toInt(),
            enterPressed = 0xFF00665F.toInt(), onAccent = 0xFFFFFFFF.toInt(),
            light = true
        )
    }
}

/** Một giao diện chọn được trong cài đặt. */
class KbTheme(val id: String, val name: String, val palette: KbPalette)

object KbThemes {
    const val SYSTEM = "system"
    const val DEFAULT = "dark"

    private fun c(v: Long) = v.toInt()

    val AMOLED = KbPalette(
        key = c(0xFF1C1C1E), keyPressed = c(0xFF3A3A3C),
        func = c(0xFF111113), funcPressed = c(0xFF2C2C2E),
        text = c(0xFFFFFFFF), dim = c(0xFF8E8E93),
        accent = c(0xFF0A84FF), popupBg = c(0xFF2C2C2E),
        bg = c(0xFF000000), bar = c(0xFF000000), divider = c(0xFF2C2C2E),
        shadow = c(0xFF000000), enter = c(0xFF0A84FF),
        enterPressed = c(0xFF0066CC), onAccent = c(0xFFFFFFFF),
        shadowDp = 0f
    )

    val OCEAN = KbPalette(
        key = c(0xFF1F4E66), keyPressed = c(0xFF2C6A88),
        func = c(0xFF163B4F), funcPressed = c(0xFF235670),
        text = c(0xFFEAF6FB), dim = c(0xFF8FB8CC),
        accent = c(0xFF5EEAD4), popupBg = c(0xFF24607D),
        bg = c(0xFF0E2A3B), bar = c(0x00000000), divider = c(0xFF24526A),
        shadow = c(0xFF081B26), enter = c(0xFF14B8A6),
        enterPressed = c(0xFF0F9184), onAccent = c(0xFFFFFFFF),
        bgTop = c(0xFF0B2233), bgBottom = c(0xFF123B52)
    )

    val SAKURA = KbPalette(
        key = c(0xFFFFFFFF), keyPressed = c(0xFFFCE4EC),
        func = c(0xFFF8C8D8), funcPressed = c(0xFFF0A9C1),
        text = c(0xFF4A2333), dim = c(0xFF9C6B7E),
        accent = c(0xFFD81B60), popupBg = c(0xFFFFFFFF),
        bg = c(0xFFFFE4EC), bar = c(0x00000000), divider = c(0xFFF3B9CC),
        shadow = c(0xFFE7A9BE), enter = c(0xFFEC407A),
        enterPressed = c(0xFFC2185B), onAccent = c(0xFFFFFFFF),
        bgTop = c(0xFFFFEEF3), bgBottom = c(0xFFFFD6E3),
        light = true
    )

    val FOREST = KbPalette(
        key = c(0xFF2B4135), keyPressed = c(0xFF3D5A49),
        func = c(0xFF21342A), funcPressed = c(0xFF324C3E),
        text = c(0xFFEFF5EC), dim = c(0xFF9DB5A3),
        accent = c(0xFFA5D66F), popupBg = c(0xFF395242),
        bg = c(0xFF18261E), bar = c(0xFF18261E), divider = c(0xFF2B4135),
        shadow = c(0xFF0D1611), enter = c(0xFF43A047),
        enterPressed = c(0xFF2E7D32), onAccent = c(0xFFFFFFFF)
    )

    val SUNSET = KbPalette(
        key = c(0x30FFFFFF), keyPressed = c(0x60FFFFFF),
        func = c(0x1AFFFFFF), funcPressed = c(0x48FFFFFF),
        text = c(0xFFFFFFFF), dim = c(0xFFFFD9C7),
        accent = c(0xFFFFC46B), popupBg = c(0xFF7A3150),
        bg = c(0xFF5A2A4C), bar = c(0x00000000), divider = c(0x40FFFFFF),
        shadow = c(0x00000000), enter = c(0xFFFF7A45),
        enterPressed = c(0xFFE65A26), onAccent = c(0xFFFFFFFF),
        bgTop = c(0xFF3A1C4A), bgBottom = c(0xFFB0485A),
        keyStroke = c(0x26FFFFFF), shadowDp = 0f
    )

    val GALAXY = KbPalette(
        key = c(0x2EFFFFFF), keyPressed = c(0x5CFFFFFF),
        func = c(0x17FFFFFF), funcPressed = c(0x45FFFFFF),
        text = c(0xFFFFFFFF), dim = c(0xFFC9B8F0),
        accent = c(0xFFC4A5FF), popupBg = c(0xFF3E2775),
        bg = c(0xFF22134A), bar = c(0x00000000), divider = c(0x40FFFFFF),
        shadow = c(0x00000000), enter = c(0xFF7C4DFF),
        enterPressed = c(0xFF6236E0), onAccent = c(0xFFFFFFFF),
        bgTop = c(0xFF140B30), bgBottom = c(0xFF3C1F73),
        keyStroke = c(0x24FFFFFF), shadowDp = 0f
    )

    val MINT = KbPalette(
        key = c(0xFFFFFFFF), keyPressed = c(0xFFD7EFE9),
        func = c(0xFFC5E6DE), funcPressed = c(0xFFA8D7CB),
        text = c(0xFF12352E), dim = c(0xFF5B8278),
        accent = c(0xFF00897B), popupBg = c(0xFFFFFFFF),
        bg = c(0xFFE4F3EF), bar = c(0xFFE4F3EF), divider = c(0xFFC5E6DE),
        shadow = c(0xFFA9CFC5), enter = c(0xFF00A389),
        enterPressed = c(0xFF00806B), onAccent = c(0xFFFFFFFF),
        light = true
    )

    val COFFEE = KbPalette(
        key = c(0xFF43342C), keyPressed = c(0xFF5A473C),
        func = c(0xFF35291F), funcPressed = c(0xFF4B3A2F),
        text = c(0xFFF5EBDD), dim = c(0xFFBCA58E),
        accent = c(0xFFE0B07A), popupBg = c(0xFF57453A),
        bg = c(0xFF2A201B), bar = c(0xFF2A201B), divider = c(0xFF43342C),
        shadow = c(0xFF17110E), enter = c(0xFFB9855A),
        enterPressed = c(0xFF9A6B44), onAccent = c(0xFFFFFFFF)
    )

    val NORD = KbPalette(
        key = c(0xFF434C5E), keyPressed = c(0xFF4C566A),
        func = c(0xFF3B4252), funcPressed = c(0xFF4C566A),
        text = c(0xFFECEFF4), dim = c(0xFFA3ADBF),
        accent = c(0xFF88C0D0), popupBg = c(0xFF4C566A),
        bg = c(0xFF2E3440), bar = c(0xFF2E3440), divider = c(0xFF434C5E),
        shadow = c(0xFF242933), enter = c(0xFF5E81AC),
        enterPressed = c(0xFF4C6A91), onAccent = c(0xFFFFFFFF)
    )

    /** Thứ tự hiển thị trong cài đặt. "system" = Sáng/Tối theo máy. */
    val ALL: List<KbTheme> = listOf(
        KbTheme(SYSTEM, "Theo hệ thống", KbPalette.DARK),
        KbTheme("dark", "Tối", KbPalette.DARK),
        KbTheme("light", "Sáng", KbPalette.LIGHT),
        KbTheme("amoled", "Đen AMOLED", AMOLED),
        KbTheme("ocean", "Đại dương", OCEAN),
        KbTheme("galaxy", "Thiên hà", GALAXY),
        KbTheme("sunset", "Hoàng hôn", SUNSET),
        KbTheme("sakura", "Anh đào", SAKURA),
        KbTheme("mint", "Bạc hà", MINT),
        KbTheme("forest", "Rừng xanh", FOREST),
        KbTheme("coffee", "Cà phê", COFFEE),
        KbTheme("nord", "Bắc Âu", NORD)
    )

    fun byId(id: String?): KbTheme = ALL.firstOrNull { it.id == id } ?: ALL.first { it.id == DEFAULT }

    /** id theme hiệu lực: "system" -> "dark"/"light" theo chế độ đêm. */
    fun resolveId(id: String?, nightMode: Boolean): String {
        val t = byId(id)
        return if (t.id == SYSTEM) (if (nightMode) "dark" else "light") else t.id
    }

    fun palette(id: String?, nightMode: Boolean): KbPalette = byId(resolveId(id, nightMode)).palette

    /** Đọc pref theme; bản ≤1.3.1 chỉ có cờ dark_theme -> chuyển đổi. */
    fun prefId(themePref: String?, legacyDark: Boolean): String =
        themePref ?: if (legacyDark) "dark" else "light"

    /** Kiểu bo góc phím (dp) theo pref key_shape. */
    fun cornerDp(shape: String?): Float = when (shape) {
        "square" -> 3f
        "round" -> 12f
        else -> 7f
    }
}
