package com.hkey.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.customview.widget.ExploreByTouchHelper

/** Phím logic: tag "ch:x" (chữ -> engine), "p:x" (dấu/số -> punct),
 *  "fn:*" (chức năng), "tx:x" (commit thẳng: emoji, ký tự phụ).
 *  Thuần dữ liệu — không đụng android.graphics để unit test JVM được. */
class KbKey(
    val tag: String,
    val label: String? = null,
    val w: Float = 1f,
    val func: Boolean = false,
    val alts: String = "",
    val longTag: String? = null, // nhấn giữ -> bắn phím khác (vd giữ 😊 -> đổi IME)
    val repeat: Boolean = false, // ⌫ giữ là xóa liên tục
    val swipe: Boolean = false   // space: vuốt ngang = dời con trỏ
)

class KbRow(val keys: List<KbKey>, val indent: Float = 0f)

object KbLayouts {
    private fun ch(c: String, alts: String = "") = KbKey("ch:$c", alts = alts)
    private fun p(c: String, alts: String = "") = KbKey("p:$c", c, alts = alts)

    private fun bottom(modeTag: String, modeLabel: String) = KbRow(
        listOf(
            KbKey(modeTag, modeLabel, 1.2f, func = true),
            p(",", "!?"),
            KbKey("fn:emoji", "😊", func = true, longTag = "fn:ime"), // giữ 😊 -> đổi IME
            KbKey("fn:lang", "VI", 1.1f, func = true),
            KbKey("fn:space", "HKey", 2.6f, swipe = true),
            p(".", "!?,:;'\""),
            KbKey("fn:enter", "↵", 1.5f, func = true)
        )
    )

    fun letters(numberRow: Boolean): List<KbRow> {
        val rows = mutableListOf<KbRow>()
        if (numberRow) rows += KbRow((1..9).map { p(it.toString()) } + p("0"))
        rows += KbRow(
            listOf(
                ch("q", "1"), ch("w", "2"), ch("e", "3ê"), ch("r", "4"),
                ch("t", "5"), ch("y", "6"), ch("u", "7ư"), ch("i", "8"),
                ch("o", "9ôơ"), ch("p", "0")
            )
        )
        rows += KbRow(
            listOf(
                ch("a", "@ăâ"), ch("s", "#"), ch("d", "\$đ"), ch("f", "%"),
                ch("g", "&"), ch("h", "-"), ch("j", "+"), ch("k", "("),
                ch("l", ")")
            ), indent = 0.5f
        )
        rows += KbRow(
            listOf(
                KbKey("fn:shift", "⇧", 1.5f, func = true),
                ch("z", "="), ch("x", "*"), ch("c", "\""), ch("v", "'"),
                ch("b", ":"), ch("n", ";"), ch("m", "!"),
                KbKey("fn:del", "⌫", 1.5f, func = true, repeat = true)
            )
        )
        rows += bottom("fn:sym", "?123")
        return rows
    }

    fun symbols() = listOf(
        KbRow((1..9).map { p(it.toString()) } + p("0")),
        KbRow(listOf(p("@"), p("#"), p("$"), p("%"), p("&"), p("*"), p("("), p(")"), p("-"), p("_"))),
        KbRow(
            listOf(
                p("="), p("+"), p("\""), p("'"), p(":"), p(";"), p("!"), p("?"),
                KbKey("fn:del", "⌫", 1.5f, func = true, repeat = true)
            )
        ),
        bottom("fn:abc", "ABC")
    )

    private val EMOJI =
        "😀😁😂🤣😊😍😘😎😢😭😡😴🤔🙄😇🤗👍👎👏🙏💪🤝✌🤞❤💔💯🔥✨🎉🎂⚡"

    fun emoji(): List<KbRow> {
        val rows = EMOJI.chunked(8).map { line ->
            KbRow(line.map { KbKey("tx:$it", it.toString()) })
        }
        return rows + KbRow(
            listOf(
                KbKey("fn:abc", "ABC", 1.2f, func = true),
                KbKey("fn:paste", "📋", func = true),
                KbKey("fn:space", "HKey", 3f, swipe = true),
                KbKey("fn:enter", "↵", 1.5f, func = true)
            )
        )
    }
}

class KbPalette(
    val key: Int, val keyPressed: Int, val func: Int, val funcPressed: Int,
    val text: Int, val dim: Int, val accent: Int, val popupBg: Int
) {
    companion object {
        val DARK = KbPalette(
            0xFF3D4149.toInt(), 0xFF5A626E.toInt(), 0xFF2B2E34.toInt(),
            0xFF49505A.toInt(), 0xFFE8EAED.toInt(), 0xFF9AA0A6.toInt(),
            0xFF8AB4F8.toInt(), 0xFF454A52.toInt()
        )
        val LIGHT = KbPalette(
            0xFFFFFFFF.toInt(), 0xFFD2D7DB.toInt(), 0xFFC4C9CD.toInt(),
            0xFFA9B0B6.toInt(), 0xFF1F1F1F.toInt(), 0xFF5F6368.toInt(),
            0xFF1A73E8.toInt(), 0xFFFFFFFF.toInt()
        )
    }
}

/** Bàn phím tự vẽ: một View duy nhất, hit theo Ô (không rớt vào khe giữa
 *  các phím), trượt ngón đổi phím, đa chạm, nhấn giữ ra ký tự phụ, giữ ⌫
 *  lặp xóa, vuốt space dời con trỏ, TalkBack qua ExploreByTouchHelper. */
class KeyboardView(context: Context) : View(context) {

    var onKey: (KbKey) -> Unit = {}
    var onSpaceSwipe: (Int) -> Unit = {} // +1 phải / -1 trái

    var palette = KbPalette.DARK
        set(v) { field = v; invalidate() }
    var keyHeightPx = (52 * resources.displayMetrics.density).toInt()
    var sidePx = 0
    var numberRow = false
    var soundEnabled = true
    var vibrateEnabled = true

    var shifted = false
        set(v) { field = v; invalidate() }
    var capsLocked = false
        set(v) { field = v; invalidate() }
    var langVi = true
        set(v) { field = v; invalidate() }

    enum class Page { LETTERS, SYMBOLS, EMOJI }
    var page = Page.LETTERS
        private set

    private var rows: List<KbRow> = KbLayouts.letters(false)
    private val areas = mutableListOf<Area>()

    private class Area(val key: KbKey, val draw: RectF, val hit: RectF)
    private class Ptr(var key: KbKey, var startX: Float, var swipeAcc: Float = 0f)

    private val ptrs = HashMap<Int, Ptr>()
    private val consumed = HashSet<Int>() // long-press/swipe đã ăn -> nhả không phát phím
    private var altStrip: PopupWindow? = null
    private var altOwner = -1
    private var altCells = listOf<TextView>()
    private var altSel = -1
    private var preview: PopupWindow? = null
    private var previewTv: TextView? = null

    private val density = resources.displayMetrics.density
    private val marginH = 2.5f * density
    private val marginV = 3f * density
    private val padV = 4f * density
    private val corner = 8f * density
    private val stepPx = 28 * density // mỗi nấc vuốt space = 1 lần dời con trỏ
    private val lpMs = 360L
    private val repeatFirstMs = 400L
    private val repeatMs = 60L

    private val handler = Handler(Looper.getMainLooper())
    private var lpPid = -1
    private val longPress = Runnable { fireLongPress() }
    private var repeatKey: KbKey? = null
    private val repeater = object : Runnable {
        override fun run() {
            repeatKey?.let { onKey(it) }
            handler.postDelayed(this, repeatMs)
        }
    }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT
    }

    fun showPage(p: Page) {
        page = p
        rows = when (p) {
            Page.LETTERS -> KbLayouts.letters(numberRow)
            Page.SYMBOLS -> KbLayouts.symbols()
            Page.EMOJI -> KbLayouts.emoji()
        }
        requestLayout()
        invalidate()
    }

    /** Áp kích thước/giao diện từ prefs; gọi sau khi đổi settings. */
    fun configure(heightPct: Int, sideDp: Int, dark: Boolean, numRow: Boolean) {
        keyHeightPx = ((52 * heightPct / 100) * density).toInt()
        sidePx = (sideDp * density).toInt()
        palette = if (dark) KbPalette.DARK else KbPalette.LIGHT
        numberRow = numRow
        showPage(if (page == Page.LETTERS) Page.LETTERS else page)
    }

    override fun onMeasure(wm: Int, hm: Int) {
        val w = MeasureSpec.getSize(wm)
        setMeasuredDimension(w, (rows.size * keyHeightPx + 2 * padV).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        areas.clear()
        var y = padV
        for (row in rows) {
            val sumW = row.keys.sumOf { it.w.toDouble() }.toFloat() + 2 * row.indent
            val unit = (width - 2 * sidePx) / sumW
            var x = sidePx + row.indent * unit
            row.keys.forEachIndexed { i, k ->
                val cellL = if (i == 0 && row.indent == 0f) 0f else x
                val cw = k.w * unit
                val cellR = if (i == row.keys.lastIndex && row.indent == 0f) width.toFloat() else x + cw
                areas += Area(
                    k,
                    RectF(x + marginH, y + marginV, x + cw - marginH, y + keyHeightPx - marginV),
                    RectF(cellL, y, cellR, y + keyHeightPx)
                )
                x += cw
            }
            y += keyHeightPx
        }
        areas.forEach { a ->
            if (a.hit.top < padV + marginV) a.hit.top = 0f
            if (a.hit.bottom > height - padV - marginV) a.hit.bottom = height.toFloat()
        }
    }

    private fun labelOf(k: KbKey): String = when {
        k.label != null && k.tag == "fn:lang" -> if (langVi) "VI" else "EN"
        k.label != null && k.tag == "fn:shift" -> if (capsLocked) "⇪" else "⇧"
        k.label != null -> k.label
        k.tag.startsWith("ch:") -> {
            val c = k.tag.removePrefix("ch:")
            if (shifted || capsLocked) c.uppercase() else c
        }
        k.tag.startsWith("p:") || k.tag.startsWith("tx:") -> k.tag.substring(3)
        else -> ""
    }

    private fun keyAt(x: Float, y: Float): KbKey? =
        areas.firstOrNull { it.hit.contains(x, y) }?.key
            ?: areas.minByOrNull { a -> // ngoài biên: gán phím gần nhất
                val dx = maxOf(a.hit.left - x, 0f, x - a.hit.right)
                val dy = maxOf(a.hit.top - y, 0f, y - a.hit.bottom)
                dx * dx + dy * dy
            }?.key

    override fun onDraw(c: Canvas) {
        for (a in areas) {
            val pressed = ptrs.values.any { it.key === a.key }
            keyPaint.color = when {
                pressed && a.key.func -> palette.funcPressed
                pressed -> palette.keyPressed
                a.key.func -> palette.func
                else -> palette.key
            }
            c.drawRoundRect(a.draw, corner, corner, keyPaint)
            txtPaint.color = when {
                a.key.tag == "fn:shift" && (shifted || capsLocked) -> palette.accent
                a.key.tag == "fn:lang" -> palette.accent
                a.key.tag == "fn:space" -> palette.dim
                else -> palette.text
            }
            txtPaint.textSize = density * when {
                a.key.tag == "fn:lang" -> 13f
                a.key.func -> 16f
                a.key.tag.startsWith("tx:") -> 20f
                else -> 19f
            }
            val ty = a.draw.centerY() - (txtPaint.descent() + txtPaint.ascent()) / 2
            c.drawText(labelOf(a.key), a.draw.centerX(), ty, txtPaint)
        }
    }

    private fun feed(k: KbKey) {
        if (soundEnabled) playSoundEffect(SoundEffectConstants.CLICK)
        if (vibrateEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onKey(k)
    }

    private fun hidePreview() {
        preview?.dismiss()
        preview = null
    }

    private fun showPreview(a: Area) {
        if (a.key.func || a.key.tag == "fn:space") { hidePreview(); return }
        val label = labelOf(a.key)
        if (preview == null) {
            val tv = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 26f
            }
            previewTv = tv
            preview = PopupWindow(tv, 0, 0, false)
        }
        val tv = previewTv!!
        val pw = (a.draw.width() * 1.5f).toInt().coerceAtLeast((44 * density).toInt())
        val ph = (keyHeightPx * 1.4f).toInt()
        tv.text = label
        tv.setTextColor(palette.text)
        tv.setBackgroundColor(palette.popupBg)
        preview!!.width = pw
        preview!!.height = ph
        val loc = IntArray(2).also { getLocationOnScreen(it) }
        val x = (loc[0] + a.draw.centerX() - pw / 2).toInt()
            .coerceIn(0, resources.displayMetrics.widthPixels - pw)
        val y = (loc[1] + a.draw.top - ph - 4 * density).toInt().coerceAtLeast(0)
        if (preview!!.isShowing) preview!!.update(x, y, pw, ph)
        else preview!!.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
    }

    private fun areaOf(k: KbKey): Area? = areas.firstOrNull { it.key === k }

    private fun hideAlts() {
        altStrip?.dismiss()
        altStrip = null
        altOwner = -1
        altSel = -1
    }

    private fun fireLongPress() {
        val p = ptrs[lpPid] ?: return
        val k = p.key
        if (k.longTag != null) {
            consumed += lpPid
            feed(KbKey(k.longTag, func = true))
            return
        }
        if (k.alts.isEmpty()) return
        val a = areaOf(k) ?: return
        consumed += lpPid
        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(palette.popupBg)
            setPadding((4 * density).toInt(), (4 * density).toInt(),
                (4 * density).toInt(), (4 * density).toInt())
        }
        val cw = (40 * density).toInt()
        altCells = k.alts.map { ch ->
            TextView(context).apply {
                text = ch.toString()
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(palette.text)
                layoutParams = LinearLayout.LayoutParams(cw, (48 * density).toInt())
            }.also { strip.addView(it) }
        }
        altSel = 0
        altCells[0].setBackgroundColor(palette.keyPressed)
        altStrip = PopupWindow(strip, LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, false)
        val loc = IntArray(2).also { getLocationOnScreen(it) }
        val stripW = cw * altCells.size + (8 * density).toInt()
        val x = (loc[0] + a.draw.centerX() - stripW / 2).toInt()
            .coerceIn(0, resources.displayMetrics.widthPixels - stripW)
        val y = (loc[1] + a.draw.top - 56 * density).toInt().coerceAtLeast(0)
        altStrip!!.contentView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        altStrip!!.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        altOwner = lpPid
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun updateAltSel(x: Float) {
        val strip = altStrip ?: return
        if (altCells.isEmpty()) return
        val loc = IntArray(2).also { getLocationOnScreen(it) }
        val stripLoc = IntArray(2).also { strip.contentView.getLocationOnScreen(it) }
        val rel = loc[0] + x - stripLoc[0] - 4 * density
        val cw = 40 * density
        val idx = (rel / cw).toInt().coerceIn(0, altCells.size - 1)
        if (idx != altSel) {
            altSel = idx
            altCells.forEachIndexed { i, tv ->
                tv.setBackgroundColor(if (i == idx) palette.keyPressed else Color.TRANSPARENT)
            }
        }
    }

    private fun commitAlt(pid: Int, k: KbKey?): Boolean {
        if (altStrip == null || altOwner != pid) return false
        if (k != null && altSel in k.alts.indices) {
            feed(KbKey("tx:${k.alts[altSel]}", k.alts[altSel].toString()))
        }
        return true
    }

    private fun touchExploration(): Boolean =
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? android.view.accessibility.AccessibilityManager)
            ?.isTouchExplorationEnabled == true

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // TalkBack bật: touch đi qua hover-explore + double-tap click,
        // không bắn phím trực tiếp (tránh gõ kép).
        if (touchExploration()) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val pid = e.getPointerId(i)
                val k = keyAt(e.getX(i), e.getY(i)) ?: return true
                ptrs[pid] = Ptr(k, e.getX(i))
                hideAlts()
                if (k.repeat) {
                    feed(k)
                    repeatKey = k
                    handler.postDelayed(repeater, repeatFirstMs)
                }
                areaOf(k)?.let { showPreview(it) }
                lpPid = pid
                handler.postDelayed(longPress, lpMs)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)
                    val p = ptrs[pid] ?: continue
                    val x = e.getX(i)
                    val y = e.getY(i)
                    if (altOwner == pid) {
                        updateAltSel(x)
                        continue
                    }
                    if (p.key.swipe) {
                        val dx = x - p.startX
                        while (dx - p.swipeAcc > stepPx) {
                            p.swipeAcc += stepPx
                            consumed += pid
                            onSpaceSwipe(1)
                        }
                        while (p.swipeAcc - dx > stepPx) {
                            p.swipeAcc -= stepPx
                            consumed += pid
                            onSpaceSwipe(-1)
                        }
                        continue
                    }
                    val nk = keyAt(x, y)
                    if (nk != null && nk !== p.key) {
                        p.key = nk
                        if (lpPid == pid) handler.removeCallbacks(longPress)
                        areaOf(nk)?.let { showPreview(it) }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pid = e.getPointerId(e.actionIndex)
                val p = ptrs.remove(pid)
                if (lpPid == pid) handler.removeCallbacks(longPress)
                if (p != null && p.key.repeat && repeatKey === p.key) {
                    handler.removeCallbacks(repeater)
                    repeatKey = null
                }
                if (p != null && !consumed.contains(pid)) {
                    feed(p.key)
                } else {
                    commitAlt(pid, p?.key)
                }
                consumed -= pid
                if (ptrs.isEmpty()) {
                    hidePreview()
                    hideAlts()
                } else {
                    ptrs.values.firstOrNull()?.let { areaOf(it.key)?.let { a -> showPreview(a) } }
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                ptrs.clear()
                consumed.clear()
                handler.removeCallbacks(longPress)
                handler.removeCallbacks(repeater)
                repeatKey = null
                hidePreview()
                hideAlts()
                invalidate()
            }
        }
        return true
    }

    /** Dọn repeat + popup khi bàn phím ẩn/detach — tránh leak window. */
    fun release() {
        ptrs.clear()
        consumed.clear()
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeater)
        repeatKey = null
        hidePreview()
        hideAlts()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ---- TalkBack: mỗi phím là một node ảo (3.x) ----

    private val spoken = mapOf(
        "fn:shift" to "viết hoa", "fn:del" to "xóa", "fn:space" to "khoảng trắng",
        "fn:enter" to "xuống dòng", "fn:sym" to "bảng ký tự", "fn:abc" to "bảng chữ",
        "fn:lang" to "đổi tiếng Việt Anh", "fn:emoji" to "biểu tượng",
        "fn:paste" to "dán", "p:." to "chấm", "p:," to "phẩy", "p:?" to "chấm hỏi",
        "p:!" to "chấm than", "p:@" to "a còng"
    )

    private val touchHelper = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val k = keyAt(x, y) ?: return ExploreByTouchHelper.INVALID_ID
            return areas.indexOfFirst { it.key === k }
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            for (i in areas.indices) ids += i
        }

        override fun onPopulateNodeForVirtualView(
            id: Int, node: androidx.core.view.accessibility.AccessibilityNodeInfoCompat
        ) {
            val a = areas.getOrNull(id) ?: return
            node.setBoundsInParent(
                android.graphics.Rect(
                    a.hit.left.toInt(), a.hit.top.toInt(),
                    a.hit.right.toInt(), a.hit.bottom.toInt()
                )
            )
            node.contentDescription = spoken[a.key.tag] ?: labelOf(a.key)
            node.addAction(
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK
            )
            node.isClickable = true
            node.isFocusable = true
        }

        override fun onPerformActionForVirtualView(
            id: Int, action: Int, args: android.os.Bundle?
        ): Boolean {
            val a = areas.getOrNull(id) ?: return false
            if (action ==
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK
            ) {
                feed(a.key)
                sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_CLICKED)
                return true
            }
            return false
        }
    }

    init {
        // Delegate này tự cấp AccessibilityNodeProvider cho framework.
        androidx.core.view.ViewCompat.setAccessibilityDelegate(this, touchHelper)
    }

    override fun dispatchHoverEvent(e: MotionEvent): Boolean =
        touchHelper.dispatchHoverEvent(e) || super.dispatchHoverEvent(e)
}
