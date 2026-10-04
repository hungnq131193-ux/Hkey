package com.hkey.app.settings

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** 1.4.0 (U2): trạng thái kích hoạt/chọn IME — so chuỗi tách thành hàm
 *  thuần [isOurIme] để test JVM được. */
object ImeStatus {

    /** defaultId = Secure.DEFAULT_INPUT_METHOD, dạng "pkg/.service.HKeyIME". */
    fun isOurIme(defaultId: String?, pkg: String): Boolean =
        defaultId != null && defaultId.startsWith("$pkg/")

    fun isEnabled(ctx: Context): Boolean {
        val imm = ctx.getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
    }

    fun isSelected(ctx: Context): Boolean = isOurIme(
        Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        ),
        ctx.packageName
    )
}
