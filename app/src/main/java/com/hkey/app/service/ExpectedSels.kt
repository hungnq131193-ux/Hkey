package com.hkey.app.service

/** Hàng đợi vị trí con trỏ mà các edit của chính IME sẽ tạo ra. Update tới
 *  khớp một vị trí đã dự đoán (kể cả đến trễ, lệch thứ tự) = do ta gây ra,
 *  không phải người dùng dời con trỏ. Giới hạn 64 — quá đầy bỏ mục cũ nhất.
 *  Thuần Kotlin để test JVM được. */
class ExpectedSels(private val max: Int = 64) {

    private val pending = ArrayDeque<Int>()

    val size: Int get() = pending.size

    fun push(pos: Int) {
        if (pending.size >= max) pending.removeFirst()
        pending.addLast(pos)
    }

    fun clear() = pending.clear()

    /** ns/ne = update vừa nhận; cs/ce = vị trí đã biết trước đó.
     *  true = update do ta gây ra (hoặc không đổi). Pop-through: các mục đứng
     *  trước vị trí khớp là update bị app gộp — dọn luôn. Vị trí đã xác nhận
     *  bị xoá khỏi hàng để không khớp nhầm một cú dời thật sau này. */
    fun isSelf(ns: Int, ne: Int, cs: Int, ce: Int): Boolean {
        if (ns == ne && ns == cs && ne == ce) {
            pending.remove(ns)
            return true
        }
        while (pending.isNotEmpty()) {
            if (pending.removeFirst() == ns && ns == ne) return true
        }
        return false
    }
}
