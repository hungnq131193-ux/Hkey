package com.hkey.app.settings.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.hkey.app.settings.ui.components.GroupCard
import com.hkey.app.settings.ui.components.KeyboardIcon
import com.hkey.app.settings.ui.components.NavDivider
import com.hkey.app.settings.ui.components.NavRow
import com.hkey.app.settings.ui.components.SectionHeader
import com.hkey.app.settings.ui.sections.AboutScreen
import com.hkey.app.settings.ui.sections.DataScreen
import com.hkey.app.settings.ui.sections.FeedbackScreen
import com.hkey.app.settings.ui.sections.HwScreen
import com.hkey.app.settings.ui.sections.InterfaceScreen
import com.hkey.app.settings.ui.sections.MacroScreen
import com.hkey.app.settings.ui.sections.MethodScreen
import com.hkey.app.settings.ui.sections.SmartScreen
import com.hkey.app.ui.KbThemes

/** 1.4.0 (U2/U3): điều hướng bằng state (không Navigation-Compose) —
 *  rememberSaveable giữ màn khi xoay máy, BackHandler về Home.
 *  1.5.0: AnimatedContent fade+slide nhẹ 200ms khi đổi màn. */
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

    // 1.5.2: mũi tên ← đi qua OnBackPressedDispatcher như phím Back — trước
    // đây nhảy thẳng về Home, bỏ qua hộp "Bỏ thay đổi chưa lưu?" của Gõ tắt.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (cur != Screen.HOME) {
                        IconButton(onClick = {
                            backDispatcher?.onBackPressed() ?: run { screen = Screen.HOME.name }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.st_back)
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(
                targetState = cur,
                transitionSpec = {
                    // Vào màn con: trượt từ phải; về Home: trượt ngược lại
                    val fwd = targetState != Screen.HOME
                    slideInHorizontally(tween(200)) { if (fwd) it / 8 else -it / 8 } +
                        fadeIn(tween(200)) togetherWith
                        slideOutHorizontally(tween(200)) { if (fwd) -it / 8 else it / 8 } +
                        fadeOut(tween(160))
                },
                modifier = Modifier.fillMaxSize(),
                label = "screen"
            ) { s ->
                when (s) {
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
}

@Composable
private fun HomeScreen(vm: SettingsViewModel, go: (Screen) -> Unit) {
    val st by vm.state.collectAsState()
    val enabled by vm.imeEnabled.collectAsState()
    val selected by vm.imeSelected.collectAsState()
    val themeName = KbThemes.ALL.find { it.id == st.kbTheme }?.name
        ?: stringResource(R.string.st_theme_system)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (!enabled || !selected) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
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
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item { SectionHeader(stringResource(R.string.st_grp_input)) }
        item {
            GroupCard {
                NavRow(
                    stringResource(R.string.st_sec_method),
                    methodDesc(st.method, st.toneNew),
                    Icons.Filled.Create
                ) { go(Screen.METHOD) }
                NavDivider()
                NavRow(
                    stringResource(R.string.st_sec_smart), smartDesc(st),
                    Icons.Filled.Star
                ) { go(Screen.SMART) }
                NavDivider()
                NavRow(
                    stringResource(R.string.st_sec_macro),
                    "${com.hkey.app.settings.MacroCodec.parse(st.macros).size} gõ tắt",
                    Icons.AutoMirrored.Filled.List
                ) { go(Screen.MACRO) }
            }
        }
        item { SectionHeader(stringResource(R.string.st_grp_ui)) }
        item {
            GroupCard {
                NavRow(
                    stringResource(R.string.st_sec_ui), themeName,
                    Icons.Filled.Settings
                ) { go(Screen.UI) }
                NavDivider()
                NavRow(
                    stringResource(R.string.st_sec_feedback), feedbackDesc(st),
                    Icons.Filled.Notifications
                ) { go(Screen.FEEDBACK) }
                NavDivider()
                NavRow(
                    stringResource(R.string.st_sec_hw),
                    if (st.hwKeyboard) "Bật" else "Tắt",
                    KeyboardIcon
                ) { go(Screen.HW) }
            }
        }
        item { SectionHeader(stringResource(R.string.st_grp_data)) }
        item {
            GroupCard {
                NavRow(
                    stringResource(R.string.st_sec_data), "Sao lưu · khôi phục · xoá học",
                    Icons.Filled.Lock
                ) { go(Screen.DATA) }
                NavDivider()
                NavRow(
                    stringResource(R.string.st_sec_about),
                    com.hkey.app.BuildConfig.VERSION_NAME,
                    Icons.Filled.Info
                ) { go(Screen.ABOUT) }
            }
        }
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
