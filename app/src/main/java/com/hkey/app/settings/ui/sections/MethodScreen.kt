package com.hkey.app.settings.ui.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hkey.app.R
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.settings.SettingsViewModel
import com.hkey.app.settings.ui.components.RadioRow
import com.hkey.app.settings.ui.components.SwitchRow

/** Kiểu gõ: 4 lựa chọn + E5 (VNI hỏi bật hàng số) + kiểu dấu. */
@Composable
fun MethodScreen(vm: SettingsViewModel) {
    val st by vm.state.collectAsState()
    var askVni by remember { mutableStateOf(false) }

    val items = listOf(
        Triple("telex", stringResource(R.string.st_method_telex), "tieengs vieetj → tiếng việt"),
        Triple("simple", stringResource(R.string.st_method_simple), "tiengs vietj → tiếng việt"),
        Triple("quick", stringResource(R.string.st_method_quick), "tieeng vieej → tiếng việt (tt=th, gg=gi…"),
        Triple("vni", stringResource(R.string.st_method_vni), "tieng61 vie65t → tiếng việt")
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        items.forEach { (id, label, example) ->
            RadioRow(label, example, st.method == id) {
                if (id == "vni" && !st.numberRow) askVni = true
                else vm.set(SettingsKeys.METHOD, id)
            }
        }
        SwitchRow(
            stringResource(R.string.st_tone_new), st.toneNew,
            desc = if (st.toneNew) "hoà · thuỷ" else "hòa · thủy"
        ) { vm.set(SettingsKeys.TONE_NEW, it) }
    }

    // E5: VNI cần hàng số — hỏi, không tự ghi đè
    if (askVni) {
        AlertDialog(
            onDismissRequest = { askVni = false },
            title = { Text(stringResource(R.string.st_vni_title)) },
            text = { Text(stringResource(R.string.st_vni_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.set(SettingsKeys.NUMBER_ROW, true)
                    vm.set(SettingsKeys.METHOD, "vni")
                    askVni = false
                }) { Text(stringResource(R.string.st_vni_yes)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.set(SettingsKeys.METHOD, "vni")
                    askVni = false
                }) { Text(stringResource(R.string.st_vni_later)) }
            }
        )
    }
}
