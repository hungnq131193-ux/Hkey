package com.hkey.app.settings

import android.content.SharedPreferences
import com.hkey.app.ui.KbThemes

/** 1.4.0: migration cài đặt theo settings_version, idempotent — gọi ở
 *  HKeyIME.onCreate và MainActivity.onCreate.
 *  v1: bản cũ chỉ có cờ dark_theme -> ghi kb_theme tương ứng (không xoá
 *  dark_theme để downgrade an toàn). */
object SettingsMigration {

    fun run(prefs: SharedPreferences) {
        if (prefs.getInt(SettingsKeys.SETTINGS_VERSION, 0) < 1) {
            val e = prefs.edit()
            if (!prefs.contains(SettingsKeys.KB_THEME) &&
                prefs.contains(SettingsKeys.DARK_THEME_LEGACY)
            ) {
                e.putString(
                    SettingsKeys.KB_THEME,
                    KbThemes.prefId(
                        null,
                        prefs.getBoolean(SettingsKeys.DARK_THEME_LEGACY, true)
                    )
                )
            }
            e.putInt(SettingsKeys.SETTINGS_VERSION, 1).apply()
        }
    }
}
