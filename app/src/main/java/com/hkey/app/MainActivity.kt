package com.hkey.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.settings.SettingsMigration
import com.hkey.app.ui.KbThemes
import com.hkey.app.ui.ThemePreviewView
import com.hkey.app.ui.ThemeSpinnerAdapter

class MainActivity : AppCompatActivity() {

    private val prefs get() =
        getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsMigration.run(prefs) // 1.4.0: kb_theme từ dark_theme cũ
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_enable_ime)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_pick_ime)?.setOnClickListener {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }

        val swVibrate = findViewById<SwitchCompat>(R.id.sw_vibrate)
        val swSound = findViewById<SwitchCompat>(R.id.sw_sound)
        swVibrate.isChecked = prefs.getBoolean("vibrate", true)
        swSound.isChecked = prefs.getBoolean("key_sound", true)
        swVibrate.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("vibrate", on).apply() }
        swSound.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("key_sound", on).apply() }

        // Kiểu gõ / kiểu dấu / spell-check / gõ tắt (2.x)
        val methods = listOf("Telex", "Telex đơn giản", "Telex nhanh", "VNI")
        val methodPrefs = listOf("telex", "simple", "quick", "vni")
        findViewById<Spinner>(R.id.sp_method)?.apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_item, methods
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(methodPrefs.indexOf(prefs.getString("ime_method", "telex")).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putString("ime_method", methodPrefs[pos]).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        findViewById<SwitchCompat>(R.id.sw_tone_new)?.apply {
            isChecked = prefs.getBoolean("tone_new", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("tone_new", on).apply() }
        }
        findViewById<SwitchCompat>(R.id.sw_spell_check)?.apply {
            isChecked = prefs.getBoolean("spell_check", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("spell_check", on).apply() }
        }
        findViewById<SwitchCompat>(R.id.sw_number_row)?.apply {
            isChecked = prefs.getBoolean("number_row", false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("number_row", on).apply() }
        }
        findViewById<SwitchCompat>(R.id.sw_double_space)?.apply {
            isChecked = prefs.getBoolean("double_space", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("double_space", on).apply() }
        }
        // 1.3.2: chọn giao diện (12 theme) + kiểu bo góc phím có ảnh mô tả / xem trước trực quan.
        val themeIds = KbThemes.ALL.map { it.id }
        var selectedTheme = prefs.getString("kb_theme", null)
            ?: if (prefs.contains("dark_theme"))
                KbThemes.prefId(null, prefs.getBoolean("dark_theme", true))
            else KbThemes.SYSTEM
        val shapeIds = listOf("medium", "square", "round")
        var selectedShape = prefs.getString("key_shape", "medium") ?: "medium"

        val themePreview = findViewById<ThemePreviewView>(R.id.theme_preview)
        themePreview?.setTheme(selectedTheme, selectedShape)

        findViewById<Spinner>(R.id.sp_theme)?.apply {
            adapter = ThemeSpinnerAdapter(this@MainActivity, KbThemes.ALL)
            setSelection(themeIds.indexOf(selectedTheme).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val tId = themeIds[pos]
                    selectedTheme = tId
                    prefs.edit().putString("kb_theme", tId).apply()
                    themePreview?.setTheme(selectedTheme, selectedShape)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        findViewById<Spinner>(R.id.sp_shape)?.apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_item,
                listOf("Góc vừa", "Góc vuông", "Góc tròn")
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(shapeIds.indexOf(selectedShape).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val sId = shapeIds[pos]
                    selectedShape = sId
                    prefs.edit().putString("key_shape", sId).apply()
                    themePreview?.setTheme(selectedTheme, selectedShape)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        findViewById<EditText>(R.id.ed_macros)?.apply {
            setText(prefs.getString("macros", ""))
            doAfterTextChanged { prefs.edit().putString("macros", it?.toString() ?: "").apply() }
        }

        val tvHeight = findViewById<TextView>(R.id.tv_height_label)
        val sbHeight = findViewById<SeekBar>(R.id.sb_height)
        val height = prefs.getInt("kb_height", 100)
        sbHeight.progress = height - 70
        tvHeight.text = "Chiều cao phím: $height%"
        sbHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 70 + p
                tvHeight.text = "Chiều cao phím: $v%"
                prefs.edit().putInt("kb_height", v).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Xoá file dữ liệu học + đặt cờ để IME (nếu đang chạy) dọn bộ nhớ.
        findViewById<Button>(R.id.btn_clear_learning)?.setOnClickListener {
            java.io.File(filesDir, "learned_data.tsv").delete()
            java.io.File(filesDir, "learned_data.tsv.tmp").delete()
            prefs.edit().putBoolean("learning_cleared", true).apply()
            findViewById<TextView>(R.id.tv_learning_cleared)?.apply {
                text = "Đã xóa dữ liệu học."
                visibility = android.view.View.VISIBLE
            }
        }

        val tvSide = findViewById<TextView>(R.id.tv_side_label)
        val sbSide = findViewById<SeekBar>(R.id.sb_side)
        val side = prefs.getInt("kb_side", 0)
        sbSide.progress = side
        tvSide.text = "Thu hẹp 2 bên: ${side}dp"
        sbSide.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvSide.text = "Thu hẹp 2 bên: ${p}dp"
                prefs.edit().putInt("kb_side", p).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }
}
