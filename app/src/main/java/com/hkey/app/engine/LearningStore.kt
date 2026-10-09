package com.hkey.app.engine

import java.io.File

/**
 * Lưu dữ liệu học vào file riêng của app (3.6): có version, ghi file tạm rồi
 * đổi tên (atomic trên POSIX), file hỏng -> bỏ qua dùng mặc định.
 * Thuần java.io — test được trên JVM.
 */
class LearningStore(private val file: File) {

    data class Data(
        val words: List<Triple<String, Int, Long>>,       // word, personal, lastSeen
        val bigrams: List<Triple<String, String, Int>>,   // prev, next, count
        val trigrams: List<UserTri> = emptyList()         // 1.3: prev2, prev, next, count
    )

    @Synchronized // save gọi từ thread nền + lifecycle main — chống ghi chéo .tmp
    fun load(): Data? {
        if (!file.exists()) return null
        return try {
            val words = mutableListOf<Triple<String, Int, Long>>()
            val bigrams = mutableListOf<Triple<String, String, Int>>()
            val trigrams = mutableListOf<UserTri>()
            val lines = file.readLines()
            // 1.3: đọc cả file v1 (chỉ words/bigrams) — nâng cấp không mất
            // dữ liệu đã học; ghi luôn ở v2.
            val ver = lines.firstOrNull()
            if (ver != VERSION && ver != VERSION_V1) return null
            for (line in lines.drop(1)) {
                try { // 1.10: dòng hỏng chỉ bỏ dòng đó, không bỏ cả file
                    val f = line.split('\t')
                    when (f.getOrNull(0)) {
                        "w" -> if (f.size == 4) words.add(
                            Triple(f[1], f[2].toInt(), f[3].toLong())
                        )
                        "b" -> if (f.size == 4) bigrams.add(
                            Triple(f[1], f[2], f[3].toInt())
                        )
                        "t" -> if (f.size == 5) trigrams.add(
                            UserTri(f[1], f[2], f[3], f[4].toInt())
                        )
                    }
                } catch (e: Exception) { /* bỏ dòng hỏng */ }
            }
            Data(words, bigrams, trigrams)
        } catch (e: Exception) {
            null // file hỏng -> bỏ qua
        }
    }

    @Synchronized
    fun save(data: Data) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        val sb = StringBuilder().appendLine(VERSION)
        for ((w, p, t) in data.words) sb.appendLine("w\t$w\t$p\t$t")
        for ((p, n, c) in data.bigrams) sb.appendLine("b\t$p\t$n\t$c")
        for (t in data.trigrams) sb.appendLine("t\t${t.prev2}\t${t.prev}\t${t.next}\t${t.count}")
        tmp.writeText(sb.toString())
        // 1.5.9: fsync trước rename — kill app/mất nguồn đúng lúc ghi không
        // để lại file dở (lần load sau mất dữ liệu âm thầm).
        try {
            java.io.FileOutputStream(tmp, true).channel.use { it.force(true) }
        } catch (_: Exception) { /* best effort */ }
        if (!tmp.renameTo(file)) {
            // 1.5.9: renameTo có thể thất bại (khác filesystem...) — copy thủ
            // công thay vì XÓA file tốt trước như trước đây (mất trắng cả bản
            // cũ lẫn mới nếu lần rename thứ 2 cũng thất bại).
            try {
                tmp.copyTo(file, overwrite = true)
            } catch (_: Exception) { /* giữ bản cũ còn tốt */ }
            tmp.delete()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    companion object {
        const val VERSION = "hkey-learned-v2"
        const val VERSION_V1 = "hkey-learned-v1" // 1.3: vẫn đọc file học cũ
        const val MAX_LEARNED_WORDS = 5000
        const val MAX_USER_BIGRAMS = 20000
        const val MAX_USER_TRIGRAMS = 20000
    }
}
