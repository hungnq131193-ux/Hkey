package com.hkey.app.settings

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("repo_test", 0)
        .also { it.edit().clear().commit() }
    private val repo = SettingsRepository(prefs)

    private fun drain() {
        shadowOf(Looper.getMainLooper()).idle()
        System.gc()
        System.runFinalization()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun setWritesUpdateStateAfterIdleAndGc() {
        drain()
        repo.set(SettingsKeys.NUMBER_ROW, true)
        repo.set(SettingsKeys.KB_HEIGHT, 120)
        repo.set(SettingsKeys.METHOD, "vni")
        repo.set(SettingsKeys.KB_THEME, "dark")
        drain()
        val st = repo.state.value
        assertTrue(st.numberRow)
        assertEquals(120, st.kbHeight)
        assertEquals("vni", st.method)
        assertEquals("dark", st.kbTheme)
        assertTrue(prefs.getBoolean(SettingsKeys.NUMBER_ROW, false))
        assertEquals(120, prefs.getInt(SettingsKeys.KB_HEIGHT, 0))
        assertEquals("vni", prefs.getString(SettingsKeys.METHOD, null))
        assertEquals("dark", prefs.getString(SettingsKeys.KB_THEME, null))
    }

    @Test
    fun repeatedWritesKeepLatest() {
        drain()
        repo.set(SettingsKeys.NUMBER_ROW, true)
        drain()
        assertTrue(repo.state.value.numberRow)
        repo.set(SettingsKeys.NUMBER_ROW, false)
        drain()
        assertFalse(repo.state.value.numberRow)
        repo.set(SettingsKeys.KB_HEIGHT, 80)
        drain()
        assertEquals(80, repo.state.value.kbHeight)
        repo.set(SettingsKeys.KB_HEIGHT, 110)
        drain()
        assertEquals(110, repo.state.value.kbHeight)
        repo.set(SettingsKeys.METHOD, "vni")
        drain()
        assertEquals("vni", repo.state.value.method)
        repo.set(SettingsKeys.METHOD, "telex")
        drain()
        assertEquals("telex", repo.state.value.method)
        repo.set(SettingsKeys.KB_THEME, "dark")
        drain()
        assertEquals("dark", repo.state.value.kbTheme)
        repo.set(SettingsKeys.KB_THEME, null)
        drain()
        assertNull(repo.state.value.kbTheme)
        assertFalse(prefs.getBoolean(SettingsKeys.NUMBER_ROW, true))
        assertEquals(110, prefs.getInt(SettingsKeys.KB_HEIGHT, 0))
        assertEquals("telex", prefs.getString(SettingsKeys.METHOD, null))
        assertNull(prefs.getString(SettingsKeys.KB_THEME, null))
    }

    @Test
    fun externalPrefsWritesRefreshState() {
        drain()
        prefs.edit()
            .putBoolean(SettingsKeys.NUMBER_ROW, true)
            .putInt(SettingsKeys.KB_HEIGHT, 90)
            .putString(SettingsKeys.METHOD, "vni")
            .putString(SettingsKeys.KB_THEME, "oled")
            .apply()
        drain()
        val st = repo.state.value
        assertTrue(st.numberRow)
        assertEquals(90, st.kbHeight)
        assertEquals("vni", st.method)
        assertEquals("oled", st.kbTheme)
    }

    @Test
    fun backupRestoreRefreshesSameRepo() {
        drain()
        val json = """{"format":1,"values":{
            "ime_method":"vni","number_row":true,"kb_height":95,
            "kb_theme":"amoled"}}"""
        val n = BackupCodec.restore(prefs, json)
        assertEquals(4, n)
        drain()
        val st = repo.state.value
        assertEquals("vni", st.method)
        assertTrue(st.numberRow)
        assertEquals(95, st.kbHeight)
        assertEquals("amoled", st.kbTheme)
        assertEquals("vni", prefs.getString(SettingsKeys.METHOD, null))
        assertEquals(95, prefs.getInt(SettingsKeys.KB_HEIGHT, 0))
    }

    @Test
    fun resetDefaultsRefreshesSameRepo() {
        drain()
        repo.set(SettingsKeys.NUMBER_ROW, true)
        repo.set(SettingsKeys.METHOD, "vni")
        drain()
        assertTrue(repo.state.value.numberRow)
        repo.resetDefaults()
        drain()
        val st = repo.state.value
        assertFalse(st.numberRow)
        assertEquals("telex", st.method)
        assertFalse(prefs.contains(SettingsKeys.NUMBER_ROW))
        assertFalse(prefs.contains(SettingsKeys.METHOD))
    }
}
