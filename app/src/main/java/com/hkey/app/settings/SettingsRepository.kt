package com.hkey.app.settings

import android.content.SharedPreferences
import androidx.annotation.Keep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 1.4.0 (U2): đọc/ghi SharedPreferences, expose StateFlow<SettingsState>.
 *  Không DataStore — IME đọc prefs đồng bộ trên cùng file. Ghi bằng
 *  apply(); listener cập nhật state khi key đổi (kể cả ghi từ chỗ khác). */
class SettingsRepository(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(SettingsState.from(prefs))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    @field:Keep
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = SettingsState.from(prefs)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun set(key: String, value: Boolean) =
        prefs.edit().putBoolean(key, value).apply()

    fun set(key: String, value: Int) =
        prefs.edit().putInt(key, value).apply()

    fun set(key: String, value: String?) =
        prefs.edit().putString(key, value).apply()

    /** Khôi phục mặc định: xoá mọi key cài đặt trừ settings_version +
     *  recent_emoji (dữ liệu, không phải tuỳ chọn). */
    fun resetDefaults() {
        val keep = setOf(SettingsKeys.SETTINGS_VERSION, SettingsKeys.RECENT_EMOJI)
        val e = prefs.edit()
        for (k in prefs.all.keys) if (k !in keep) e.remove(k)
        e.apply()
    }
}
