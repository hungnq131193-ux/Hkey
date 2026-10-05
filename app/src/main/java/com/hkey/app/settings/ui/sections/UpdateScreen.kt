package com.hkey.app.settings.ui.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hkey.app.BuildConfig
import com.hkey.app.R
import com.hkey.app.settings.ui.components.ActionRow
import com.hkey.app.settings.ui.components.GroupCard
import com.hkey.app.settings.ui.components.NavDivider
import com.hkey.app.settings.ui.components.SettingsPage
import com.hkey.app.update.UpdateViewModel

@Composable
fun UpdateScreen(uvm: UpdateViewModel) {
    val st by uvm.state.collectAsState()
    SettingsPage {
        GroupCard {
            ActionRow(
                "Phiên bản hiện tại", BuildConfig.VERSION_NAME,
                Icons.Filled.Star
            ) {}
            NavDivider()
            ActionRow(
                "Bản mới nhất",
                when (st.phase) {
                    UpdateViewModel.Phase.CHECKING -> "Đang kiểm tra…"
                    UpdateViewModel.Phase.ERROR -> st.error ?: "Lỗi"
                    else -> st.release?.version ?: "—"
                },
                Icons.Filled.Star
            ) {}
            NavDivider()
            ActionRow(
                stringResource(R.string.st_check_update),
                if (st.phase == UpdateViewModel.Phase.CHECKING) "Đang kiểm tra…"
                else null,
                Icons.Filled.Refresh
            ) { uvm.check() }
            if (st.phase == UpdateViewModel.Phase.AVAILABLE ||
                st.phase == UpdateViewModel.Phase.DOWNLOADING
            ) {
                NavDivider()
                ActionRow(
                    "Tải bản ${st.release?.version ?: ""}",
                    if (st.phase == UpdateViewModel.Phase.DOWNLOADING)
                        "${st.downloadedBytes / 1024} / ${(st.release?.apkSize ?: 0) / 1024} KB"
                    else st.error,
                    Icons.Filled.Share
                ) { uvm.download() }
            }
            if (st.apk != null) {
                NavDivider()
                ActionRow(
                    "Cài đặt",
                    if (st.needPermission)
                        "Quay lại đây rồi bấm Cài đặt sau khi cấp quyền"
                    else "APK đã tải xong",
                    Icons.Filled.Share
                ) { uvm.install() }
            }
        }
        if (st.error != null) {
            Text(
                st.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
            )
        }
        Text(
            "Gõ phím hoàn toàn offline — chỉ tính năng cập nhật mới dùng mạng.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
        )
    }
}

internal fun onUpdateConfirm(uvm: UpdateViewModel, go: () -> Unit) {
    uvm.dismiss()
    go()
}

@Composable
fun UpdatePrompt(uvm: UpdateViewModel, onUpdate: () -> Unit) {
    val st by uvm.state.collectAsState()
    if (st.phase == UpdateViewModel.Phase.AVAILABLE && !st.dismissed) {
        AlertDialog(
            onDismissRequest = { uvm.dismiss() },
            title = { Text("Có bản mới ${st.release?.version ?: ""}") },
            text = { Text("Bạn đang dùng ${BuildConfig.VERSION_NAME}. Cập nhật ngay?") },
            confirmButton = {
                TextButton(onClick = { onUpdateConfirm(uvm, onUpdate) }) { Text("Cập nhật") }
            },
            dismissButton = {
                TextButton(onClick = { uvm.dismiss() }) { Text("Để sau") }
            }
        )
    }
}
