package com.hkey.app.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hkey.app.R
import com.hkey.app.settings.SettingsViewModel
import com.hkey.app.settings.ui.components.NavRow
import com.hkey.app.settings.ui.sections.AboutScreen
import com.hkey.app.settings.ui.sections.DataScreen
import com.hkey.app.settings.ui.sections.FeedbackScreen
import com.hkey.app.settings.ui.sections.HwScreen
import com.hkey.app.settings.ui.sections.InterfaceScreen
import com.hkey.app.settings.ui.sections.MacroScreen
import com.hkey.app.settings.ui.sections.MethodScreen
import com.hkey.app.settings.ui.sections.SmartScreen

/** 1.4.0 (U2/U3): điều hướng bằng state (không Navigation-Compose) —
 *  rememberSaveable giữ màn khi xoay máy, BackHandler về Home. */
enum class Screen { HOME, METHOD, SMART, UI, FEEDBACK, HW, MACRO, DATA, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsApp(vm: SettingsViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val cur = Screen.valueOf(screen)
    BackHandler(enabled = cur != Screen.HOME) { screen = Screen.HOME.name }

    val title = when (cur) {
        Screen.HOME -> stringResource(R.string.app_name)
        Screen.METHOD -> stringResource(R.string.st_sec_method)
        Screen.SMART -> stringResource(R.string.st_sec_smart)
        Screen.UI -> stringResource(R.string.st_sec_ui)
        Screen.FEEDBACK -> stringResource(R.string.st_sec_feedback)
        Screen.HW -> stringResource(R.string.st_sec_hw)
        Screen.MACRO -> stringResource(R.string.st_sec_macro)
        Screen.DATA -> stringResource(R.string.st_sec_data)
        Screen.ABOUT -> stringResource(R.string.st_sec_about)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (cur != Screen.HOME) {
                        IconButton(onClick = { screen = Screen.HOME.name }) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (cur) {
                Screen.HOME -> HomeScreen(vm) { screen = it.name }
                Screen.METHOD -> MethodScreen(vm)
                Screen.SMART -> SmartScreen(vm)
                Screen.UI -> InterfaceScreen(vm)
                Screen.FEEDBACK -> FeedbackScreen(vm)
                Screen.HW -> HwScreen(vm)
                Screen.MACRO -> MacroScreen(vm)
                Screen.DATA -> DataScreen(vm)
                Screen.ABOUT -> AboutScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: SettingsViewModel, go: (Screen) -> Unit) {
    val st by vm.state.collectAsState()
    val enabled by vm.imeEnabled.collectAsState()
    val selected by vm.imeSelected.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        if (!enabled || !selected) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.st_setup_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!enabled) {
                            TextButton(onClick = { vm.openImeSettings() }) {
                                Text("① ${stringResource(R.string.st_setup_enable)}")
                            }
                        }
                        if (!selected) {
                            TextButton(onClick = { vm.showImePicker() }) {
                                Text("② ${stringResource(R.string.st_setup_pick)}")
                            }
                        }
                        Text(
                            stringResource(R.string.st_setup_done),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            var t by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = t, onValueChange = { t = it },
                placeholder = { Text(stringResource(R.string.st_try)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        item { NavRow(stringResource(R.string.st_sec_method), methodDesc(st.method, st.toneNew)) { go(Screen.METHOD) } }
        item { NavRow(stringResource(R.string.st_sec_smart), smartDesc(st)) { go(Screen.SMART) } }
        item { NavRow(stringResource(R.string.st_sec_ui), st.kbTheme ?: "system") { go(Screen.UI) } }
        item { NavRow(stringResource(R.string.st_sec_feedback), feedbackDesc(st)) { go(Screen.FEEDBACK) } }
        item { NavRow(stringResource(R.string.st_sec_hw), if (st.hwKeyboard) "Bật" else "Tắt") { go(Screen.HW) } }
        item { NavRow(stringResource(R.string.st_sec_macro), "${com.hkey.app.settings.MacroCodec.parse(st.macros).size} gõ tắt") { go(Screen.MACRO) } }
        item { NavRow(stringResource(R.string.st_sec_data), "") { go(Screen.DATA) } }
        item { NavRow(stringResource(R.string.st_sec_about), com.hkey.app.BuildConfig.VERSION_NAME) { go(Screen.ABOUT) } }
    }
}

private fun methodDesc(m: String, toneNew: Boolean): String {
    val name = when (m) {
        "simple" -> "Telex đơn giản"; "quick" -> "Telex nhanh"; "vni" -> "VNI"
        else -> "Telex"
    }
    return "$name · dấu kiểu ${if (toneNew) "mới" else "cũ"}"
}

private fun smartDesc(st: com.hkey.app.settings.SettingsState): String {
    val off = mutableListOf<String>()
    if (!st.suggestions) off += "gợi ý"
    if (!st.autoCorrect) off += "tự sửa"
    if (!st.liveRestore) off += "hoàn nguyên"
    return if (off.isEmpty()) "Đầy đủ" else "Tắt: ${off.joinToString(", ")}"
}

private fun feedbackDesc(st: com.hkey.app.settings.SettingsState): String {
    val on = mutableListOf<String>()
    if (st.vibrate) on += "rung ${st.vibrateMs}ms"
    if (st.sound) on += "âm ${st.soundVol}%"
    return if (on.isEmpty()) "Tắt" else on.joinToString(" · ")
}
