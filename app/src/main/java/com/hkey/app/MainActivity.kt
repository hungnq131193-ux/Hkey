package com.hkey.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hkey.app.settings.SettingsMigration
import com.hkey.app.settings.SettingsViewModel
import com.hkey.app.settings.ui.HKeyTheme
import com.hkey.app.settings.ui.SettingsApp

/** 1.4.0 (U3): cài đặt Compose — ComponentActivity + setContent.
 *  Trạng thái IME làm mới ở ON_RESUME và onWindowFocusChanged (picker là
 *  dialog, không gây onResume). */
class MainActivity : AppCompatActivity() {

    private var vm: SettingsViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsMigration.run(
            getSharedPreferences(com.hkey.app.settings.SettingsKeys.PREFS, 0)
        )
        setContent {
            val model: SettingsViewModel = viewModel()
            vm = model
            // Làm mới thẻ "Bắt đầu" khi quay lại app (ON_RESUME)
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val obs = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_RESUME) model.refreshImeStatus()
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs) }
            }
            HKeyTheme { SettingsApp(model) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Picker IME là dialog -> vẫn focus lại; cập nhật trạng thái chọn
        if (hasFocus) vm?.refreshImeStatus()
    }
}
