package com.hkey.app.settings.ui.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.hkey.app.R
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.settings.SettingsViewModel
import com.hkey.app.settings.ui.components.GroupCard
import com.hkey.app.settings.ui.components.RowDivider
import com.hkey.app.settings.ui.components.SectionHeader
import com.hkey.app.settings.ui.components.SettingsPage
import com.hkey.app.settings.ui.components.SwitchRow

/** Gõ thông minh: 6 switch ánh thẳng sang prefs — IME áp ngay (S2). */
@Composable
fun SmartScreen(vm: SettingsViewModel) {
    val st by vm.state.collectAsState()
    // 1.5.2: tách 2 nhóm "Sửa & gợi ý" / "Câu chữ" thay vì 6 switch dồn một khối
    SettingsPage {
        SectionHeader("Sửa & gợi ý")
        GroupCard {
            SwitchRow(stringResource(R.string.st_suggestions), st.suggestions) {
                vm.set(SettingsKeys.SUGGESTIONS, it)
            }
            RowDivider()
            SwitchRow(stringResource(R.string.st_auto_correct), st.autoCorrect) {
                vm.set(SettingsKeys.AUTO_CORRECT, it)
            }
            RowDivider()
            SwitchRow(
                stringResource(R.string.st_live_restore), st.liveRestore,
                desc = stringResource(R.string.st_live_restore_desc)
            ) { vm.set(SettingsKeys.LIVE_RESTORE, it) }
            RowDivider()
            SwitchRow(stringResource(R.string.st_spell_check), st.spellCheck) {
                vm.set(SettingsKeys.SPELL_CHECK, it)
            }
        }
        SectionHeader("Câu chữ")
        GroupCard {
            SwitchRow(stringResource(R.string.st_auto_cap), st.autoCap) {
                vm.set(SettingsKeys.AUTO_CAP, it)
            }
            RowDivider()
            SwitchRow(stringResource(R.string.st_double_space), st.doubleSpace) {
                vm.set(SettingsKeys.DOUBLE_SPACE, it)
            }
        }
    }
}
