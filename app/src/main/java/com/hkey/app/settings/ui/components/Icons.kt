package com.hkey.app.settings.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** 1.5.0: icon bàn phím tự vẽ (icons-core không có Keyboard) — style
 *  Filled, viewport 24, chỉ là viền bo góc + 6 phím. */
val KeyboardIcon: ImageVector by lazy {
    materialIcon("HKey.Keyboard") {
        materialPath {
            // thân bàn phím: rect 2..22, bo góc 2
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(16f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(4f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            // phím nhỏ 2x2: hàng trên 3, hàng dưới 2 + spacebar giữa
            moveTo(6f, 9f)
            horizontalLineToRelative(2f); verticalLineToRelative(2f)
            horizontalLineToRelative(-2f); close()
            moveTo(11f, 9f)
            horizontalLineToRelative(2f); verticalLineToRelative(2f)
            horizontalLineToRelative(-2f); close()
            moveTo(16f, 9f)
            horizontalLineToRelative(2f); verticalLineToRelative(2f)
            horizontalLineToRelative(-2f); close()
            moveTo(6f, 13f)
            horizontalLineToRelative(2f); verticalLineToRelative(2f)
            horizontalLineToRelative(-2f); close()
            moveTo(10f, 13f)
            horizontalLineToRelative(4f); verticalLineToRelative(2f)
            horizontalLineToRelative(-4f); close()
            moveTo(16f, 13f)
            horizontalLineToRelative(2f); verticalLineToRelative(2f)
            horizontalLineToRelative(-2f); close()
        }
    }
}
