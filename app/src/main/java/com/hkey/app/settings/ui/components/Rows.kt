package com.hkey.app.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 1.4.0 (U2): hàng cài đặt dùng chung cho các màn con. */

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    desc: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(
                    desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Slider chỉ ghi khi thả tay (onValueChangeFinished) — không ghi prefs
 *  liên tục trong lúc kéo (B2). */
@Composable
fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    onChangeFinished: (Int) -> Unit,
    unit: String = "",
    enabled: Boolean = true
) {
    var cur by remember(value) { mutableIntStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "$title: $cur$unit",
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = cur.toFloat(),
            onValueChange = { cur = it.toInt() },
            onValueChangeFinished = { onChangeFinished(cur) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled
        )
    }
}

@Composable
fun RadioRow(title: String, desc: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(
                    desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 1.5.0: card nhóm trên Home — bo góc 16, lề theo nhịp 8dp. */
@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Column(content = content) }
}

/** Divider mảnh giữa các NavRow trong GroupCard — thụt qua vùng icon. */
@Composable
fun NavDivider() {
    HorizontalDivider(
        Modifier.padding(start = 66.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Hàng điều hướng: icon tròn + tiêu đề/mô tả + chevron, vùng chạm >=48dp. */
@Composable
fun NavRow(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).background(
                    MaterialTheme.colorScheme.primaryContainer, CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc.isNotEmpty()) {
                Text(
                    desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
