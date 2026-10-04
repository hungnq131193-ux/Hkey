package com.hkey.app.settings

import android.content.SharedPreferences

/** 1.4.0 (U2): ảnh chụp bất biến toàn bộ cài đặt — Compose render theo
 *  state này; [from] đọc prefs một lượt. */
data class SettingsState(
    val method: String = "telex",
    val toneNew: Boolean = true,
    val spellCheck: Boolean = true,
    val numberRow: Boolean = false,
    val doubleSpace: Boolean = true,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
    val kbHeight: Int = 100,
    val kbSide: Int = 0,
    val kbTheme: String? = null,
    val keyShape: String = "medium",
    val macros: String = "",
    val liveRestore: Boolean = true,
    val autoCorrect: Boolean = true,
    val suggestions: Boolean = true,
    val autoCap: Boolean = true,
    val spaceSwipe: Boolean = true,
    val vibrateMs: Int = 20,
    val soundVol: Int = 50,
    val longpressMs: Int = 360,
    val hwKeyboard: Boolean = true
) {
    companion object {
        fun from(p: SharedPreferences): SettingsState = SettingsState(
            method = p.getString(SettingsKeys.METHOD, "telex") ?: "telex",
            toneNew = p.getBoolean(SettingsKeys.TONE_NEW, true),
            spellCheck = p.getBoolean(SettingsKeys.SPELL_CHECK, true),
            numberRow = p.getBoolean(SettingsKeys.NUMBER_ROW, false),
            doubleSpace = p.getBoolean(SettingsKeys.DOUBLE_SPACE, true),
            vibrate = p.getBoolean(SettingsKeys.VIBRATE, true),
            sound = p.getBoolean(SettingsKeys.SOUND, true),
            kbHeight = p.getInt(SettingsKeys.KB_HEIGHT, 100),
            kbSide = p.getInt(SettingsKeys.KB_SIDE, 0),
            kbTheme = p.getString(SettingsKeys.KB_THEME, null),
            keyShape = p.getString(SettingsKeys.KEY_SHAPE, "medium") ?: "medium",
            macros = p.getString(SettingsKeys.MACROS, "") ?: "",
            liveRestore = p.getBoolean(SettingsKeys.LIVE_RESTORE, true),
            autoCorrect = p.getBoolean(SettingsKeys.AUTO_CORRECT, true),
            suggestions = p.getBoolean(SettingsKeys.SUGGESTIONS, true),
            autoCap = p.getBoolean(SettingsKeys.AUTO_CAP, true),
            spaceSwipe = p.getBoolean(SettingsKeys.SPACE_SWIPE, true),
            vibrateMs = p.getInt(SettingsKeys.VIBRATE_STRENGTH, 20),
            soundVol = p.getInt(SettingsKeys.SOUND_VOLUME, 50),
            longpressMs = p.getInt(SettingsKeys.LONGPRESS_MS, 360),
            hwKeyboard = p.getBoolean(SettingsKeys.HW_KEYBOARD, true)
        )
    }
}
