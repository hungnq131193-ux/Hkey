package com.hkey.app.settings.ui.sections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hkey.app.R
import com.hkey.app.settings.MacroCodec
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.settings.SettingsViewModel

/** Gõ tắt: danh sách k=v, sửa tại chỗ state cục bộ, Lưu ghi một lần (B3).
 *  Rời màn khi chưa lưu -> hỏi. */
@Composable
fun MacroScreen(vm: SettingsViewModel) {
    val st by vm.state.collectAsState()
    var items by remember(st.macros) {
        mutableStateOf(MacroCodec.parse(st.macros).toList().sortedBy { it.first })
    }
    var dirty by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editKey by rememberSaveable { mutableStateOf("") }
    var editVal by rememberSaveable { mutableStateOf("") }
    var editErr by remember { mutableStateOf(false) }
    var ioText by rememberSaveable { mutableStateOf<String?>(null) }
    var askLeave by remember { mutableStateOf(false) }

    fun commitItems(next: List<Pair<String, String>>) {
        items = next
        dirty = true
    }

    fun save() {
        vm.set(SettingsKeys.MACROS, MacroCodec.serialize(items.toMap()))
        dirty = false
    }

    // Chặn back trong màn con: SettingsApp cũng đăng BackHandler — handler
    // này enable khi dirty và ưu tiên hơn (đăng ký sau = chạy trước).
    BackHandler(enabled = dirty) { askLeave = true }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 1.5.2: chừa đáy cho FAB — trước đây FAB che mục cuối
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)) {
                items(items) { (k, v) ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            k, Modifier.weight(0.35f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            v, Modifier.weight(0.65f), style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = {
                            editing = k to v
                            editKey = k; editVal = v; editErr = false
                        }) { Icon(Icons.Filled.Edit, stringResource(R.string.st_macro_add)) }
                        IconButton(onClick = {
                            commitItems(items.filter { it.first != k })
                        }) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 88.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    ioText = MacroCodec.serialize(items.toMap())
                }) { Text(stringResource(R.string.st_macro_io)) }
                Button(
                    onClick = { save() },
                    enabled = dirty,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text(stringResource(R.string.st_macro_save)) }
            }
        }
        FloatingActionButton(
            onClick = {
                editing = "" to ""; editKey = ""; editVal = ""; editErr = false
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, stringResource(R.string.st_macro_add)) }
    }

    // Dialog sửa/thêm — validate khoá: không rỗng, không space/'=', không
    // trùng (không phân biệt hoa); sửa khoá cũ được đổi tên nếu chưa trùng.
    editing?.let { (oldK, _) ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.st_macro_add)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editKey, onValueChange = { editKey = it; editErr = false },
                        label = { Text(stringResource(R.string.st_macro_key)) },
                        isError = editErr, singleLine = true
                    )
                    OutlinedTextField(
                        value = editVal, onValueChange = { editVal = it },
                        label = { Text(stringResource(R.string.st_macro_val)) },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (editErr) {
                        Text(
                            stringResource(R.string.st_macro_key_err),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val k = editKey.trim().lowercase()
                    val bad = k.isEmpty() || k.any { it.isWhitespace() || it == '=' } ||
                        items.any { it.first == k && it.first != oldK }
                    if (bad) editErr = true
                    else {
                        val next = items.filter { it.first != oldK } + (k to editVal)
                        commitItems(next.sortedBy { it.first })
                        editing = null
                    }
                }) { Text(stringResource(R.string.st_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.st_cancel))
                }
            }
        )
    }

    // Nhập/Xuất: một TextField nhiều dòng định dạng k=v — dán để nhập,
    // copy để xuất
    ioText?.let { txt ->
        AlertDialog(
            onDismissRequest = { ioText = null },
            text = {
                OutlinedTextField(
                    value = txt,
                    onValueChange = { ioText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    commitItems(
                        MacroCodec.parse(txt).toList().sortedBy { it.first }
                    )
                    ioText = null
                }) { Text(stringResource(R.string.st_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { ioText = null }) {
                    Text(stringResource(R.string.st_cancel))
                }
            }
        )
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            text = { Text(stringResource(R.string.st_macro_discard)) },
            confirmButton = {
                TextButton(onClick = {
                    askLeave = false; dirty = false
                    items = MacroCodec.parse(st.macros).toList().sortedBy { it.first }
                }) { Text(stringResource(R.string.st_macro_leave)) }
            },
            dismissButton = {
                TextButton(onClick = { askLeave = false }) {
                    Text(stringResource(R.string.st_macro_keep))
                }
            }
        )
    }
}
