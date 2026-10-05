package com.hkey.app.settings.ui.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.hkey.app.settings.ui.components.ActionRow
import com.hkey.app.settings.ui.components.GroupCard
import com.hkey.app.settings.ui.components.NavDivider
import com.hkey.app.settings.ui.components.SectionHeader
import com.hkey.app.settings.ui.components.SettingsPage
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

    // 1.5.2: hàng hành động có icon thay 4 nút đặc giống hệt nhau; thao tác
    // phá huỷ tô màu lỗi, tách nhóm riêng.
    SettingsPage {
        SectionHeader("Sao lưu")
        GroupCard {
            ActionRow(
                stringResource(R.string.st_backup), "Lưu cài đặt + gõ tắt ra file JSON",
                Icons.Filled.Share
            ) { saveLauncher.launch("hkey-settings.json") }
            NavDivider()
            ActionRow(
                stringResource(R.string.st_restore), "Nạp lại từ file đã sao lưu",
                Icons.Filled.Refresh
            ) { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }
        SectionHeader("Riêng tư")
        GroupCard {
            ActionRow(
                stringResource(R.string.st_clear_learn),
                if (learned >= 0) stringResource(R.string.st_learned, learned) else "…",
                Icons.Filled.Delete, danger = true
            ) { askClear = true }
            NavDivider()
            ActionRow(
                stringResource(R.string.st_reset), "Đưa mọi cài đặt về như mới cài",
                Icons.Filled.Warning, danger = true
            ) { askReset = true }
        }
        Text(
            "Gõ phím hoàn toàn offline — dữ liệu học chỉ nằm trên máy này; chỉ tính năng cập nhật ứng dụng mới dùng mạng (GitHub).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
        )
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text(stringResource(R.string.st_clear_learn)) },
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
            icon = { Icon(Icons.Filled.Warning, null) },
            title = { Text(stringResource(R.string.st_reset)) },
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
    // 1.5.2: changelog dựng theo dòng — "## x" thành tiêu đề phiên bản,
    // "- " thành gạch đầu dòng; trước đây đổ nguyên markdown thô.
    SettingsPage {
        GroupCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${stringResource(R.string.st_version)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SectionHeader(stringResource(R.string.st_changelog))
        GroupCard {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                changelogBlocks(changelog).forEach { (head, body) ->
                    if (head) Text(
                        body, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    ) else Text(
                        "•  $body", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

/** Gộp changelog markdown thành khối: (true, "1.5.2 …") cho "## ",
 *  (false, nội dung gạch đầu dòng) — dòng tiếp nối nối vào mục trước. */
internal fun changelogBlocks(md: String): List<Pair<Boolean, String>> {
    val out = mutableListOf<Pair<Boolean, String>>()
    for (raw in md.lines()) {
        val l = raw.trim()
        when {
            l.isEmpty() || l.startsWith("# ") -> {}
            l.startsWith("## ") -> out += true to l.removePrefix("## ")
            l.startsWith("- ") -> out += false to l.removePrefix("- ")
            out.isNotEmpty() && !out.last().first ->
                out[out.size - 1] = false to out.last().second + " " + l
            else -> out += false to l
        }
    }
    return out
}
