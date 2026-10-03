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
import com.hkey.app.ui.KbThemes

class MainActivity : AppCompatActivity() {

    private val prefs get() = getSharedPreferences("hkey_settings", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // 1.3.2: chọn giao diện (12 theme) + kiểu bo góc phím. Máy chưa có
        // pref nào -> mặc định "system" (sáng/tối theo máy); máy cũ chỉ có
        // cờ dark_theme -> giữ nguyên lựa chọn sáng/tối đó.
        val themeIds = KbThemes.ALL.map { it.id }
        val curTheme = prefs.getString("kb_theme", null)
            ?: if (prefs.contains("dark_theme"))
                KbThemes.prefId(null, prefs.getBoolean("dark_theme", true))
            else KbThemes.SYSTEM
        findViewById<Spinner>(R.id.sp_theme)?.apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_item,
                KbThemes.ALL.map { it.name }
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(themeIds.indexOf(curTheme).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putString("kb_theme", themeIds[pos]).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        val shapeIds = listOf("medium", "square", "round")
        findViewById<Spinner>(R.id.sp_shape)?.apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_item,
                listOf("Góc vừa", "Góc vuông", "Góc tròn")
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(shapeIds.indexOf(prefs.getString("key_shape", "medium")).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putString("key_shape", shapeIds[pos]).apply()
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
