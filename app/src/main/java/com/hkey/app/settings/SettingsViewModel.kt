package com.hkey.app.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 1.4.0 (U2): sự kiện UI -> repository; trạng thái kích hoạt IME cập nhật
 *  từ ON_RESUME và onWindowFocusChanged (picker là dialog, không gây
 *  onResume). */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs get() =
        getApplication<Application>()
            .getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)

    val repo = SettingsRepository(
        getApplication<Application>()
            .getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
    )
    val state: StateFlow<SettingsState> = repo.state

    private val _imeEnabled = MutableStateFlow(false)
    private val _imeSelected = MutableStateFlow(false)
    val imeEnabled: StateFlow<Boolean> = _imeEnabled.asStateFlow()
    val imeSelected: StateFlow<Boolean> = _imeSelected.asStateFlow()

    private val _hwDetected = MutableStateFlow(false)
    val hwDetected: StateFlow<Boolean> = _hwDetected.asStateFlow()

    fun refreshImeStatus() {
        val c = getApplication<Application>()
        _imeEnabled.value = ImeStatus.isEnabled(c)
        _imeSelected.value = ImeStatus.isSelected(c)
        _hwDetected.value =
            c.resources.configuration.keyboard !=
                android.content.res.Configuration.KEYBOARD_NOKEYS
    }

    fun openImeSettings() {
        val c = getApplication<Application>()
        c.startActivity(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun showImePicker() {
        getApplication<Application>()
            .getSystemService(InputMethodManager::class.java)
            ?.showInputMethodPicker()
    }

    fun set(key: String, v: Boolean) = repo.set(key, v)
    fun set(key: String, v: Int) = repo.set(key, v)
    fun set(key: String, v: String?) = repo.set(key, v)

    /** Số từ đã học — đọc nhanh (đếm dòng file), UI gọi trên IO dispatcher. */
    fun learnedCount(): Int {
        val f = java.io.File(getApplication<Application>().filesDir, "learned_data.tsv")
        return try { if (f.exists()) f.readLines().size else 0 } catch (e: Exception) { 0 }
    }

    /** Xoá file học + cờ để IME đang chạy dọn bộ nhớ (giữ hành vi 1.3.x). */
    fun clearLearning() {
        val d = getApplication<Application>().filesDir
        java.io.File(d, "learned_data.tsv").delete()
        java.io.File(d, "learned_data.tsv.tmp").delete()
        prefs.edit().putBoolean(SettingsKeys.LEARNING_CLEARED, true).apply()
    }

    fun backupJson(): String = BackupCodec.backup(prefs)

    fun restoreJson(json: String): Int = BackupCodec.restore(prefs, json)

    fun resetDefaults() = repo.resetDefaults()
}
