package com.hkey.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.3.2 — đăng ký/resolve theme + kiểu bo góc. */
class KbThemesTest {

    @Test
    fun allThemeIdsUniqueAndNamed() {
        val ids = KbThemes.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(KbThemes.ALL.all { it.name.isNotBlank() })
    }

    @Test
    fun systemResolvesByNightMode() {
        assertEquals("dark", KbThemes.resolveId("system", nightMode = true))
        assertEquals("light", KbThemes.resolveId("system", nightMode = false))
        assertEquals("ocean", KbThemes.resolveId("ocean", nightMode = true))
    }

    @Test
    fun unknownIdFallsBackToDefault() {
        assertEquals(KbThemes.DEFAULT, KbThemes.byId("khong-co").id)
        assertEquals(KbThemes.DEFAULT, KbThemes.byId(null).id)
    }

    @Test
    fun legacyDarkFlagMigrates() {
        assertEquals("dark", KbThemes.prefId(null, legacyDark = true))
        assertEquals("light", KbThemes.prefId(null, legacyDark = false))
        assertEquals("sakura", KbThemes.prefId("sakura", legacyDark = true))
    }

    @Test
    fun cornerShapes() {
        assertEquals(3f, KbThemes.cornerDp("square"))
        assertEquals(12f, KbThemes.cornerDp("round"))
        assertEquals(7f, KbThemes.cornerDp("medium"))
        assertEquals(7f, KbThemes.cornerDp(null))
    }

    @Test
    fun gradientThemesDeclareTwoStops() {
        for (t in KbThemes.ALL) {
            val p = t.palette
            assertEquals(p.bgTop != p.bgBottom, p.gradient)
            // Theme phẳng: bar/bg phải đục để che nền app phía sau
            if (!p.gradient) assertTrue((p.bg ushr 24) == 0xFF)
        }
        assertNotEquals(KbThemes.palette("ocean", false).bgTop,
            KbThemes.palette("ocean", false).bgBottom)
    }
}
