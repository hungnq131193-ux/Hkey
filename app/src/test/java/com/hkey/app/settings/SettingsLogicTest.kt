package com.hkey.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.4.0 (U4): MacroCodec + BackupCodec + SettingsState + ImeStatus. */
@RunWith(RobolectricTestRunner::class)
class SettingsLogicTest {

    // ------------------------------------------------ MacroCodec

    @Test
    fun macroRoundTrip() {
        val m = mapOf("ck" to "chính khách", "hn" to "Hà Nội")
        val s = MacroCodec.serialize(m)
        assertEquals(m, MacroCodec.parse(s))
    }

    @Test
    fun macroBadLinesDropped() {
        val p = MacroCodec.parse("a=1\n\n=noval\nnoeq\n  \nb= 2  ")
        assertEquals(mapOf("a" to "1", "b" to "2"), p)
    }

    @Test
    fun macroDupKeepsLast() {
        val p = MacroCodec.parse("k=one\nk=two\nK=three")
        assertEquals(mapOf("k" to "three"), p) // khoá lowercase + toMap
    }

    // ------------------------------------------------ BackupCodec

    private fun prefs() = org.robolectric.RuntimeEnvironment.getApplication()
        .getSharedPreferences("bk_test", 0)
        .also { it.edit().clear().apply() }

    @Test
    fun backupRoundTrip() {
        val p = prefs()
        p.edit()
            .putString(SettingsKeys.METHOD, "vni")
            .putInt(SettingsKeys.KB_HEIGHT, 90)
            .putBoolean(SettingsKeys.AUTO_CAP, false)
            .putBoolean(SettingsKeys.LEARNING_CLEARED, true)
            .putString(SettingsKeys.RECENT_EMOJI, "😀")
            .apply()
        val json = BackupCodec.backup(p)
        assertTrue(json.contains("\"format\":1"))
        assertFalse(json.contains("learning_cleared"))
        assertFalse(json.contains("recent_emoji"))

        val p2 = org.robolectric.RuntimeEnvironment.getApplication()
            .getSharedPreferences("bk_test2", 0)
        p2.edit().clear().apply()
        val n = BackupCodec.restore(p2, json)
        assertEquals("vni", p2.getString(SettingsKeys.METHOD, null))
        assertEquals(90, p2.getInt(SettingsKeys.KB_HEIGHT, 0))
        assertFalse(p2.getBoolean(SettingsKeys.AUTO_CAP, true))
        assertTrue(n >= 3)
    }

    @Test
    fun restoreSkipsInvalid() {
        val p = prefs()
        val json = """{"format":1,"values":{
            "kb_height":500,"kb_side":-3,"vibrate_ms":20,
            "ime_method":"vni","unknown_key":1,"tone_new":"yes"
        }}"""
        BackupCodec.restore(p, json)
        assertEquals(100, p.getInt(SettingsKeys.KB_HEIGHT, 100)) // ngoài khoảng -> bỏ
        assertEquals(0, p.getInt(SettingsKeys.KB_SIDE, 0))
        assertEquals(20, p.getInt(SettingsKeys.VIBRATE_STRENGTH, 0))
        assertEquals("vni", p.getString(SettingsKeys.METHOD, null))
        assertFalse(p.contains("unknown_key"))
        assertTrue(p.getBoolean(SettingsKeys.TONE_NEW, true)) // sai kiểu -> bỏ
    }

    @Test
    fun restoreBadFormatThrows() {
        try {
            BackupCodec.restore(prefs(), """{"format":2,"values":{}}""")
            org.junit.Assert.fail()
        } catch (e: IllegalArgumentException) { }
    }

    // ------------------------------------------------ SettingsState

    @Test
    fun stateDefaults() {
        val p = org.robolectric.RuntimeEnvironment.getApplication()
            .getSharedPreferences("st_test", 0)
        p.edit().clear().apply()
        val st = SettingsState.from(p)
        assertEquals("telex", st.method)
        assertTrue(st.toneNew)
        assertTrue(st.autoCorrect)
        assertTrue(st.suggestions)
        assertEquals(20, st.vibrateMs)
        assertEquals(50, st.soundVol)
        assertEquals(360, st.longpressMs)
        assertTrue(st.hwKeyboard)
        assertFalse(st.numberRow)
    }

    // ------------------------------------------------ ImeStatus

    @Test
    fun isOurImeMatch() {
        assertTrue(ImeStatus.isOurIme("com.hkey.app/.service.HKeyIME", "com.hkey.app"))
        assertFalse(ImeStatus.isOurIme("com.gboard/.LatinIME", "com.hkey.app"))
        assertFalse(ImeStatus.isOurIme(null, "com.hkey.app"))
        assertFalse(ImeStatus.isOurIme("com.hkey.app.other/.X", "com.hkey.app")) // prefix sai
    }
}
