package com.hkey.app.settings.ui.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hkey.app.R
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.settings.SettingsViewModel
import com.hkey.app.settings.ui.components.GroupCard
import com.hkey.app.settings.ui.components.SwitchRow

/** Gõ thông minh: 6 switch ánh thẳng sang prefs — IME áp ngay (S2). */
@Composable
fun SmartScreen(vm: SettingsViewModel) {
    val st by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GroupCard {
            SwitchRow(
                stringResource(R.string.st_live_restore), st.liveRestore,
                desc = stringResource(R.string.st_live_restore_desc)
            ) { vm.set(SettingsKeys.LIVE_RESTORE, it) }
            SwitchRow(stringResource(R.string.st_spell_check), st.spellCheck) {
                vm.set(SettingsKeys.SPELL_CHECK, it)
            }
            SwitchRow(stringResource(R.string.st_auto_correct), st.autoCorrect) {
                vm.set(SettingsKeys.AUTO_CORRECT, it)
            }
            SwitchRow(stringResource(R.string.st_suggestions), st.suggestions) {
                vm.set(SettingsKeys.SUGGESTIONS, it)
            }
            SwitchRow(stringResource(R.string.st_auto_cap), st.autoCap) {
                vm.set(SettingsKeys.AUTO_CAP, it)
            }
            SwitchRow(stringResource(R.string.st_double_space), st.doubleSpace) {
                vm.set(SettingsKeys.DOUBLE_SPACE, it)
            }
        }
    }
}
