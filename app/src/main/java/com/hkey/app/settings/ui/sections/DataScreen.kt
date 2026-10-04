package com.hkey.app.settings.ui.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hkey.app.BuildConfig
import com.hkey.app.R
import com.hkey.app.settings.SettingsViewModel
import com.hkey.app.settings.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dữ liệu & riêng tư: đếm từ đã học (IO), xoá học, sao lưu/khôi phục
 *  JSON, khôi phục mặc định. */
@Composable
fun DataScreen(vm: SettingsViewModel) {
    val ctx = LocalContext.current
    var learned by remember { mutableIntStateOf(-1) }
    var askClear by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    fun toast(msg: String) =
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT)
            .show()

    LaunchedEffect(Unit) {
        learned = withContext(Dispatchers.IO) { vm.learnedCount() }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            // 1.5.0: báo rõ thành công/thất bại — trước đây sao lưu im lặng
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(vm.backupJson().toByteArray())
                } ?: throw java.io.IOException("openOutputStream null")
            }.isSuccess
            toast(
                ctx.getString(
                    if (ok) R.string.st_backup_ok else R.string.st_backup_fail
                )
            )
        }
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val json = runCatching {
                ctx.contentResolver.openInputStream(it)?.use { ins ->
                    ins.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
            // 1.5.0: đọc lỗi -> "file không hợp lệ" thay vì hiện "?"
            toast(
                if (json == null) ctx.getString(R.string.st_restore_bad)
                else try {
                    val n = vm.restoreJson(json)
                    ctx.getString(R.string.st_restore_ok, n)
                } catch (e: Exception) {
                    ctx.getString(R.string.st_restore_bad)
                }
            )
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(
            if (learned >= 0) stringResource(R.string.st_learned, learned)
            else "…"
        )
        Button(
            onClick = { askClear = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.st_clear_learn)) }
        Button(
            onClick = { saveLauncher.launch("hkey-settings.json") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.st_backup)) }
        Button(
            onClick = { openLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.st_restore)) }
        Button(
            onClick = { askReset = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.st_reset)) }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            text = { Text(stringResource(R.string.st_clear_learn_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLearning(); learned = 0; askClear = false
                }) { Text(stringResource(R.string.st_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) {
                    Text(stringResource(R.string.st_cancel))
                }
            }
        )
    }
    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            text = { Text(stringResource(R.string.st_reset_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetDefaults(); askReset = false
                }) { Text(stringResource(R.string.st_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { askReset = false }) {
                    Text(stringResource(R.string.st_cancel))
                }
            }
        )
    }
}

/** Giới thiệu: phiên bản + changelog (res/raw/changelog.md do Gradle
 *  task copyChangelog đổ vào trước preBuild). */
@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val changelog = remember {
        runCatching {
            ctx.resources.openRawResource(R.raw.changelog)
                .bufferedReader().readText()
        }.getOrDefault("")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(stringResource(R.string.st_version))
        Text(
            BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SectionHeader(stringResource(R.string.st_changelog))
        Text(
            changelog,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
