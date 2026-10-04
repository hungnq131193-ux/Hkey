package com.hkey.app.settings

/** 1.4.0 (U2): codec "k=v" mỗi dòng — một nguồn dùng chung cho
 *  HKeyIME.parseMacros và màn Gõ tắt. Hành vi cũ: dòng thiếu '='/khoá
 *  rỗng bỏ; khoá trùng giữ dòng cuối (toMap); khoá lowercase. */
object MacroCodec {

    fun parse(text: String): Map<String, String> =
        text.lines().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null
            else it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim()
        }.toMap()

    fun serialize(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}=${it.value}" }
}
