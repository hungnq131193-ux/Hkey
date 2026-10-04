package com.hkey.app.settings

import android.content.SharedPreferences
import org.json.JSONObject

/** 1.4.0 (U2): sao lưu cài đặt ra JSON — chỉ key trong SettingsKeys (trừ
 *  learning_cleared, recent_emoji: dữ liệu/flag nội bộ, không phải tuỳ
 *  chọn). Khôi phục: key lạ bỏ, giá trị sai kiểu/ngoài khoảng bỏ. */
object BackupCodec {

    private enum class T { BOOL, INT, STR }

    private data class Field(
        val key: String, val t: T, val min: Int = 0, val max: Int = 0
    )

    private val FIELDS = listOf(
        Field(SettingsKeys.METHOD, T.STR),
        Field(SettingsKeys.TONE_NEW, T.BOOL),
        Field(SettingsKeys.SPELL_CHECK, T.BOOL),
        Field(SettingsKeys.NUMBER_ROW, T.BOOL),
        Field(SettingsKeys.DOUBLE_SPACE, T.BOOL),
        Field(SettingsKeys.VIBRATE, T.BOOL),
        Field(SettingsKeys.SOUND, T.BOOL),
        Field(SettingsKeys.KB_HEIGHT, T.INT, 70, 130),
        Field(SettingsKeys.KB_SIDE, T.INT, 0, 24),
        Field(SettingsKeys.KB_THEME, T.STR),
        Field(SettingsKeys.KEY_SHAPE, T.STR),
        Field(SettingsKeys.MACROS, T.STR),
        Field(SettingsKeys.LIVE_RESTORE, T.BOOL),
        Field(SettingsKeys.AUTO_CORRECT, T.BOOL),
        Field(SettingsKeys.SUGGESTIONS, T.BOOL),
        Field(SettingsKeys.AUTO_CAP, T.BOOL),
        Field(SettingsKeys.SPACE_SWIPE, T.BOOL),
        Field(SettingsKeys.VIBRATE_STRENGTH, T.INT, 5, 60),
        Field(SettingsKeys.SOUND_VOLUME, T.INT, 0, 100),
        Field(SettingsKeys.LONGPRESS_MS, T.INT, 200, 700),
        Field(SettingsKeys.HW_KEYBOARD, T.BOOL)
    )

    fun backup(prefs: SharedPreferences): String {
        val v = JSONObject()
        for (f in FIELDS) {
            when (f.t) {
                T.BOOL -> if (prefs.contains(f.key))
                    v.put(f.key, prefs.getBoolean(f.key, false))
                T.INT -> if (prefs.contains(f.key))
                    v.put(f.key, prefs.getInt(f.key, 0))
                T.STR -> if (prefs.contains(f.key))
                    v.put(f.key, prefs.getString(f.key, ""))
            }
        }
        return JSONObject().put("format", 1).put("values", v).toString()
    }

    /** Áp key hợp lệ vào prefs; trả số key đã áp. Ném Exception nếu JSON
     *  hỏng/format khác — gọi phía UI bọc dialog báo lỗi. */
    fun restore(prefs: SharedPreferences, json: String): Int {
        val root = JSONObject(json)
        if (root.optInt("format", -1) != 1) throw IllegalArgumentException("format")
        val v = root.optJSONObject("values") ?: JSONObject()
        var n = 0
        val e = prefs.edit()
        for (f in FIELDS) {
            if (!v.has(f.key)) continue
            when (f.t) {
                T.BOOL -> {
                    val x = v.opt(f.key)
                    if (x is Boolean) { e.putBoolean(f.key, x); n++ }
                }
                T.INT -> {
                    val x = v.opt(f.key)
                    if (x is Int && x in f.min..f.max) { e.putInt(f.key, x); n++ }
                }
                T.STR -> {
                    val x = v.opt(f.key)
                    if (x is String) { e.putString(f.key, x); n++ }
                }
            }
        }
        e.apply()
        return n
    }
}
