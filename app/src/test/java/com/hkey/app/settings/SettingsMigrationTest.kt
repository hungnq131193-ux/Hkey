package com.hkey.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {

    @Test
    fun freshInstall_setsVersionOnly() {
        val p = FakePrefs()
        SettingsMigration.run(p)
        assertEquals(1, p.getInt(SettingsKeys.SETTINGS_VERSION, 0))
        assertFalse(p.contains(SettingsKeys.KB_THEME))
    }

    @Test
    fun legacyDarkFalse_migratesToLight() {
        val p = FakePrefs()
        p.edit().putBoolean(SettingsKeys.DARK_THEME_LEGACY, false).apply()
        SettingsMigration.run(p)
        assertEquals("light", p.getString(SettingsKeys.KB_THEME, null))
        assertEquals(1, p.getInt(SettingsKeys.SETTINGS_VERSION, 0))
        assertTrue(p.contains(SettingsKeys.DARK_THEME_LEGACY)) // không xoá cờ cũ
    }

    @Test
    fun legacyDarkTrue_migratesToDark() {
        val p = FakePrefs()
        p.edit().putBoolean(SettingsKeys.DARK_THEME_LEGACY, true).apply()
        SettingsMigration.run(p)
        assertEquals("dark", p.getString(SettingsKeys.KB_THEME, null))
    }

    @Test
    fun existingTheme_kept() {
        val p = FakePrefs()
        p.edit()
            .putBoolean(SettingsKeys.DARK_THEME_LEGACY, true)
            .putString(SettingsKeys.KB_THEME, "ocean")
            .apply()
        SettingsMigration.run(p)
        assertEquals("ocean", p.getString(SettingsKeys.KB_THEME, null))
        assertEquals(1, p.getInt(SettingsKeys.SETTINGS_VERSION, 0))
    }

    @Test
    fun idempotent_secondRunNoChange() {
        val p = FakePrefs()
        p.edit().putBoolean(SettingsKeys.DARK_THEME_LEGACY, false).apply()
        SettingsMigration.run(p)
        val snapshot = HashMap(p.map)
        SettingsMigration.run(p)
        assertEquals(snapshot, p.map)
    }

    @Test
    fun newerVersion_untouched() {
        val p = FakePrefs()
        p.edit().putInt(SettingsKeys.SETTINGS_VERSION, 5)
            .putBoolean(SettingsKeys.DARK_THEME_LEGACY, false).apply()
        SettingsMigration.run(p)
        assertEquals(5, p.getInt(SettingsKeys.SETTINGS_VERSION, 0))
        assertFalse(p.contains(SettingsKeys.KB_THEME))
    }
}
