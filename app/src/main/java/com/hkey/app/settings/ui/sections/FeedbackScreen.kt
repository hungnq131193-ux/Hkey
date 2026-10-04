package com.hkey.app.settings.ui.sections

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.hkey.app.R
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.settings.SettingsViewModel
import com.hkey.app.settings.ui.components.GroupCard
import com.hkey.app.settings.ui.components.RowDivider
import com.hkey.app.settings.ui.components.SectionHeader
import com.hkey.app.settings.ui.components.SettingsPage
import com.hkey.app.settings.ui.components.SliderRow
import com.hkey.app.settings.ui.components.SwitchRow

/** Phím & phản hồi: rung/âm + cường độ, nhấn giữ, vuốt space. */
@Composable
fun FeedbackScreen(vm: SettingsViewModel) {
    val st by vm.state.collectAsState()
    val ctx = LocalContext.current
    SettingsPage {
        SectionHeader("Rung")
        GroupCard {
            SwitchRow(stringResource(R.string.st_vibrate), st.vibrate) {
                vm.set(SettingsKeys.VIBRATE, it)
            }
            SliderRow(
                stringResource(R.string.st_vibrate_ms), st.vibrateMs, 5..60,
                {
                    vm.set(SettingsKeys.VIBRATE_STRENGTH, it)
                    // Bấm thử rung khi thả slider
                    ctx.getSystemService(Vibrator::class.java)
                        ?.takeIf { v -> v.hasVibrator() }?.vibrate(
                        VibrationEffect.createOneShot(
                            it.toLong(), VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                },
                unit = "ms", enabled = st.vibrate
            )
        }
        SectionHeader("Âm thanh")
        GroupCard {
            SwitchRow(stringResource(R.string.st_sound), st.sound) {
                vm.set(SettingsKeys.SOUND, it)
            }
            SliderRow(
                stringResource(R.string.st_sound_vol), st.soundVol, 0..100,
                { vm.set(SettingsKeys.SOUND_VOLUME, it) },
                unit = "%", enabled = st.sound
            )
        }
        SectionHeader("Thao tác")
        GroupCard {
            SliderRow(
                stringResource(R.string.st_longpress), st.longpressMs, 200..700,
                { vm.set(SettingsKeys.LONGPRESS_MS, it) }, unit = "ms"
            )
            RowDivider()
            SwitchRow(stringResource(R.string.st_space_swipe), st.spaceSwipe) {
                vm.set(SettingsKeys.SPACE_SWIPE, it)
            }
        }
    }
}

/** Bàn phím cứng: switch + hướng dẫn + trạng thái phát hiện. */
@Composable
fun HwScreen(vm: SettingsViewModel) {
    val st by vm.state.collectAsState()
    val detected by vm.hwDetected.collectAsState()
    SettingsPage {
        GroupCard {
            SwitchRow(
                stringResource(R.string.st_hw_enable), st.hwKeyboard,
                desc = stringResource(if (detected) R.string.st_hw_found else R.string.st_hw_none)
            ) { vm.set(SettingsKeys.HW_KEYBOARD, it) }
        }
        SectionHeader("Phím tắt")
        GroupCard {
            Text(
                stringResource(R.string.st_hw_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
