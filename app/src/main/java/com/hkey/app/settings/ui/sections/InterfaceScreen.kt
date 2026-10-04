package com.hkey.app.settings.ui.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hkey.app.R
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.settings.SettingsViewModel
import com.hkey.app.settings.ui.components.SectionHeader
import com.hkey.app.settings.ui.components.SliderRow
import com.hkey.app.settings.ui.components.SwitchRow
import com.hkey.app.ui.KbThemes
import com.hkey.app.ui.ThemePreviewView

/** Giao diện: preview trực tiếp + lưới 12 theme + kiểu phím + sliders. */
@Composable
fun InterfaceScreen(vm: SettingsViewModel) {
    val st by vm.state.collectAsState()
    val night = isSystemInDarkTheme()
    // kbTheme null = chưa từng chọn -> "system" (migration S1 đã ghi cho
    // máy nâng cấp từ 1.3.x)
    val themeId = st.kbTheme ?: KbThemes.SYSTEM

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Preview bàn phím thật — tái dùng ThemePreviewView qua AndroidView
        AndroidView(
            factory = { ThemePreviewView(it) },
            update = { it.setTheme(themeId, st.keyShape) },
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp)
        )

        SectionHeader(stringResource(R.string.st_theme))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(KbThemes.ALL) { t ->
                val sel = t.id == themeId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { vm.set(SettingsKeys.KB_THEME, t.id) }
                ) {
                    ThemeThumb(
                        t.id, night, sel,
                        Modifier.aspectRatio(1.6f).fillMaxWidth()
                    )
                    Text(
                        t.name, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }

        SectionHeader(stringResource(R.string.st_shape))
        val shapes = listOf(
            "medium" to stringResource(R.string.st_shape_medium),
            "square" to stringResource(R.string.st_shape_square),
            "round" to stringResource(R.string.st_shape_round)
        )
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            shapes.forEachIndexed { i, (id, label) ->
                SegmentedButton(
                    selected = st.keyShape == id,
                    onClick = { vm.set(SettingsKeys.KEY_SHAPE, id) },
                    shape = SegmentedButtonDefaults.itemShape(i, shapes.size)
                ) { Text(label) }
            }
        }

        SliderRow(
            stringResource(R.string.st_height), st.kbHeight, 70..130,
            { vm.set(SettingsKeys.KB_HEIGHT, it) }, unit = "%"
        )
        SliderRow(
            stringResource(R.string.st_side), st.kbSide, 0..24,
            { vm.set(SettingsKeys.KB_SIDE, it) }, unit = "dp"
        )
        SwitchRow(stringResource(R.string.st_number_row), st.numberRow) {
            vm.set(SettingsKeys.NUMBER_ROW, it)
        }
    }
}

/** Thumbnail theme: nền + 2 hàng "phím" vẽ Canvas theo palette thật. */
@Composable
private fun ThemeThumb(
    themeId: String, night: Boolean, selected: Boolean, modifier: Modifier
) {
    val p = KbThemes.palette(themeId, night)
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier.border(
            if (selected) 2.dp else 1.dp, border, RoundedCornerShape(8.dp)
        )
    ) {
        val r = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        drawRoundRect(Color(p.bgTop), size = size, cornerRadius = r)
        // 2 hàng phím
        val cols = 4
        val kw = size.width / (cols + 1)
        val kh = size.height / 4
        for (row in 0..1) {
            for (col in 0 until cols) {
                val isFunc = row == 1 && (col == 0 || col == cols - 1)
                drawRoundRect(
                    if (isFunc) Color(p.func) else Color(p.key),
                    topLeft = Offset(
                        kw / 2 + col * kw + 2.dp.toPx(),
                        size.height / 3 + row * kh + 2.dp.toPx()
                    ),
                    size = Size(kw - 4.dp.toPx(), kh - 4.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
    }
}
