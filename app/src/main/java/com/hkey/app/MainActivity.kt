package com.hkey.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

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
