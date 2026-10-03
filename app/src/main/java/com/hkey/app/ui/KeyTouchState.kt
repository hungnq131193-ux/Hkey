package com.hkey.app.ui

/** Trạng thái chạm phím: pointer đang giữ, pointer đã consumed (long-press/
 *  vuốt), và lịch lặp phím repeat (⌫). Thuần Kotlin — không import android —
 *  để test JVM được. KeyboardView chỉ dịch MotionEvent -> hàm + thực thi kết
 *  quả (feed, postDelayed, preview). */
class KeyTouchState(
    val repeatFirstMs: Long = 400,
    val repeatMs: Long = 60
) {
    class Ptr(var key: KbKey, val startX: Float, var swipeAcc: Float = 0f)

    val ptrs = HashMap<Int, Ptr>()
    val consumed = HashSet<Int>()

    var repeatKey: KbKey? = null
        private set
    private var repeatPid = -1
    private var repeatNext = 0L

    /** Kết quả nhả ngón: [key] phím cuối dưới ngón (cho commitAlt), [fire]
     *  phím cần bắn (null nếu consumed), [stopRepeat] phải dừng repeater. */
    class Up(val key: KbKey?, val fire: KbKey?, val stopRepeat: Boolean)

    /** DOWN: ghi pointer. Phím repeat bắn ngay + consumed luôn (nhả không bắn
     *  lại) + nạp lịch lặp. Trả true = feed ngay lập tức. */
    fun down(pid: Int, k: KbKey, x: Float, now: Long): Boolean {
        ptrs[pid] = Ptr(k, x)
        if (!k.repeat) return false
        consumed += pid
        repeatKey = k
        repeatPid = pid
        repeatNext = now + repeatFirstMs
        return true
    }

    /** Ngón dời sang phím khác (caller đã kiểm tra khác instance). Trả true =
     *  vừa rời phím repeat -> caller dừng repeater. Trượt quay lại không bật
     *  lại. */
    fun moveTo(pid: Int, k: KbKey): Boolean {
        ptrs[pid]?.key = k
        if (repeatPid != pid || k.repeat) return false
        stopRepeat()
        return true
    }

    /** Repeater gọi mỗi tick: true = bắn repeatKey một lần, nấc sau theo nhịp
     *  cố định repeatMs tính từ repeatFirstMs lúc chạm. */
    fun repeatDue(now: Long): Boolean {
        if (repeatKey == null || now < repeatNext) return false
        repeatNext += repeatMs
        return true
    }

    fun up(pid: Int): Up {
        val p = ptrs.remove(pid)
        val stop = repeatPid == pid || (p != null && repeatKey === p.key)
        if (stop) stopRepeat()
        val fire = if (p != null && pid !in consumed) p.key else null
        consumed -= pid
        return Up(p?.key, fire, stop)
    }

    fun cancelAll() {
        ptrs.clear()
        consumed.clear()
        stopRepeat()
    }

    private fun stopRepeat() {
        repeatKey = null
        repeatPid = -1
    }
}
