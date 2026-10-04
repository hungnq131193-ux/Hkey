package com.hkey.app.settings

/** 1.4.0: nguồn duy nhất cho tên key SharedPreferences + giá trị mặc định.
 *  KHÔNG đổi tên key hiện có — dữ liệu người dùng nâng cấp phải còn nguyên. */
object SettingsKeys {
    const val PREFS = "hkey_settings"

    // hiện có — giữ nguyên tên + default
    const val METHOD = "ime_method"          // "telex"
    const val TONE_NEW = "tone_new"          // true
    const val SPELL_CHECK = "spell_check"    // true
    const val NUMBER_ROW = "number_row"      // false
    const val DOUBLE_SPACE = "double_space"  // true
    const val VIBRATE = "vibrate"            // true
    const val SOUND = "key_sound"            // true
    const val KB_HEIGHT = "kb_height"        // 100 (70..130)
    const val KB_SIDE = "kb_side"            // 0 (0..24)
    const val KB_THEME = "kb_theme"          // null -> migrate
    const val DARK_THEME_LEGACY = "dark_theme"
    const val KEY_SHAPE = "key_shape"        // "medium"
    const val MACROS = "macros"              // "k=v\n..."
    const val LEARNING_CLEARED = "learning_cleared"
    const val RECENT_EMOJI = "recent_emoji"

    // mới 1.4.0
    const val LIVE_RESTORE = "live_restore"      // true
    const val AUTO_CORRECT = "auto_correct"      // true
    const val SUGGESTIONS = "suggestions"        // true
    const val AUTO_CAP = "auto_cap"              // true
    const val SPACE_SWIPE = "space_swipe"        // true
    const val VIBRATE_STRENGTH = "vibrate_ms"    // 20 (5..60)
    const val SOUND_VOLUME = "sound_vol"         // 50 (0..100)
    const val LONGPRESS_MS = "longpress_ms"      // 360 (200..700)
    const val HW_KEYBOARD = "hw_keyboard"        // true
    const val SETTINGS_VERSION = "settings_version" // 0 -> 1
}
