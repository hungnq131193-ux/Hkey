package com.hkey.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.EditorInfo
import android.widget.OverScroller
import androidx.customview.widget.ExploreByTouchHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Phím logic: tag "ch:x" (chữ -> engine), "p:x" (dấu/số -> punct),
 *  "fn:*" (chức năng), "tx:x" (commit thẳng: emoji, ký tự phụ).
 *  Thuần dữ liệu — không đụng android.graphics để unit test JVM được. */
class KbKey(
    val tag: String,
    val label: String? = null,
    val w: Float = 1f,
    val func: Boolean = false,
    val alts: String = "",
    val longTag: String? = null, // nhấn giữ -> bắn phím khác (vd giữ VI -> đổi IME)
    val repeat: Boolean = false, // ⌫ giữ là xóa liên tục
    val swipe: Boolean = false,  // space: vuốt ngang = đổi VI/EN (trái=EN, phải=VI)
    val mini: String? = null,    // icon nhỏ góc phải-trên (vd 😊 trên phím ,)
    val hint: Boolean = false    // 1.2: hiện ký tự phụ đầu tiên mờ ở góc (số trên hàng q..p)
)

class KbRow(val keys: List<KbKey>, val indent: Float = 0f)

/** 1.2: loại ô nhập — đổi phím cạnh ?123 (",", "/" cho URL, "@" cho email). */
enum class KbField { TEXT, URL, EMAIL }

object KbLayouts {
    private fun ch(c: String, alts: String = "", hint: Boolean = false) =
        KbKey("ch:$c", alts = alts, hint = hint)

    private fun p(c: String, alts: String = "") = KbKey("p:$c", c, alts = alts)
    private fun del(w: Float = 1.5f) = KbKey("fn:del", "⌫", w, func = true, repeat = true)

    private fun bottom(modeTag: String, modeLabel: String, field: KbField) = KbRow(
        listOf(
            KbKey(modeTag, modeLabel, 1.2f, func = true),
            // giữ phím này -> trang emoji (icon 😊 góc). URL: "/", email: "@".
            when (field) {
                KbField.URL -> KbKey("p:/", "/", alts = ",:", longTag = "fn:emoji", mini = "😊")
                KbField.EMAIL -> KbKey("p:@", "@", longTag = "fn:emoji", mini = "😊")
                KbField.TEXT -> KbKey("p:,", ",", longTag = "fn:emoji", mini = "😊")
            },
            KbKey("fn:space", "HKey", 5.1f, swipe = true, longTag = "fn:ime"),
            p(".", "?!,:;/'\""),
            KbKey("fn:enter", "↵", 1.5f, func = true)
        )
    )

    fun letters(numberRow: Boolean, field: KbField = KbField.TEXT): List<KbRow> {
        val rows = mutableListOf<KbRow>()
        val h = !numberRow // có hàng số rồi thì không cần gợi ý số góc phím
        if (numberRow) rows += KbRow((1..9).map { p(it.toString()) } + p("0"))
        rows += KbRow(
            listOf(
                ch("q", "1", h), ch("w", "2", h), ch("e", "3êé", h), ch("r", "4", h),
                ch("t", "5", h), ch("y", "6ý", h), ch("u", "7ưú", h), ch("i", "8í", h),
                ch("o", "9ôơó", h), ch("p", "0", h)
            )
        )
        rows += KbRow(
            listOf(
                ch("a", "@ăâá"), ch("s", "#"), ch("d", "\$đ₫"), ch("f", "_"),
                ch("g", "&"), ch("h", "-"), ch("j", "+"), ch("k", "("),
                ch("l", ")")
            ), indent = 0.5f
        )
        rows += KbRow(
            listOf(
                KbKey("fn:shift", "⇧", 1.5f, func = true),
                ch("z", "*"), ch("x", "\""), ch("c", "'"), ch("v", ":"),
                ch("b", ";"), ch("n", "!"), ch("m", "?/"),
                del()
            )
        )
        rows += bottom("fn:sym", "?123", field)
        return rows
    }

    /** Trang ký hiệu 1 (kiểu Gboard) — đủ / \ | ~ ` ^ [ ] { } < > ₫ … */
    fun symbols(field: KbField = KbField.TEXT) = listOf(
        KbRow(
            listOf(
                p("1", "¹½⅓¼"), p("2", "²⅔"), p("3", "³¾"), p("4", "⁴"), p("5"),
                p("6"), p("7"), p("8"), p("9"), p("0", "ⁿ∅")
            )
        ),
        KbRow(
            listOf(
                p("@"), p("#", "№"), p("\$", "₫€£¥¢"), p("_"), p("&", "§"),
                p("-", "–—·"), p("+", "±"), p("(", "<[{"), p(")", ">]}"), p("/", "\\|")
            )
        ),
        KbRow(
            listOf(
                KbKey("fn:sym2", "=\\<", 1.5f, func = true),
                p("*", "★†"), p("\"", "“”«»"), p("'", "‘’`"), p(":"), p(";"),
                p("!", "¡"), p("?", "¿"),
                del()
            )
        ),
        bottom("fn:abc", "ABC", field)
    )

    /** Trang ký hiệu 2. */
    fun symbols2(field: KbField = KbField.TEXT) = listOf(
        KbRow(
            listOf(
                p("~"), p("`"), p("|"), p("•", "·○●"), p("√"),
                p("π", "Ω"), p("÷"), p("×"), p("¶", "§"), p("∆")
            )
        ),
        KbRow(
            listOf(
                p("£"), p("€"), p("¥"), p("₫"), p("^", "↑↓←→"),
                p("°", "′″"), p("=", "≠≈"), p("{"), p("}"), p("\\")
            )
        ),
        KbRow(
            listOf(
                KbKey("fn:sym", "?123", 1.5f, func = true),
                p("%", "‰"), p("<", "≤«"), p(">", "≥»"), p("["), p("]"),
                p("©", "®™"), p("✓", "✔✗"),
                del()
            )
        ),
        bottom("fn:abc", "ABC", field)
    )

    /** Nhóm emoji: (icon tab, danh sách cách nhau bởi space). Tab 0 = gần đây. */
    val EMOJI_TABS = listOf("🕘", "😀", "👋", "🐶", "🍔", "⚽", "❤️")

    private val EMOJI_CATS_RAW = listOf(
        // 1. Mặt cười
        "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 😋 😛 😜 " +
            "🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 " +
            "🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 " +
            "😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 😺 😸 " +
            "😹 😻 😼 😽 🙀 😿 😾",
        // 2. Cử chỉ & người
        "👋 🤚 🖐️ ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 " +
            "🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦵 🦶 👂 👃 🧠 👀 👁️ 👅 👄 💋 👶 🧒 👦 👧 🧑 " +
            "👨 👩 🧓 👴 👵 👮 👷 💂 🕵️ 🎅 🤶 👸 🤴 🙇 💁 🙅 🙆 🙋 🤦 🤷 💃 🕺 🚶 🏃 " +
            "👫 👬 👭 💏 💑 👪 👨‍👩‍👧‍👦 👩‍💻 👍🏽",
        // 3. Động vật & thiên nhiên
        "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 " +
            "🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 " +
            "🐋 🦈 🐊 🐅 🐆 🦓 🐘 🐪 🦒 🐃 🐄 🐎 🐖 🐑 🐐 🐕 🐈 🐓 🦃 🕊️ 🐇 🐁 🌵 🎄 " +
            "🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌙 ⭐ 🌟 ✨ " +
            "⚡ 🔥 🌈 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 🌊 💧",
        // 4. Đồ ăn & uống
        "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 " +
            "🥕 🥔 🍠 🥐 🍞 🥖 🧀 🥚 🍳 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 " +
            "🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 " +
            "🍩 🍪 🥜 🍯 🥛 ☕ 🍵 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹",
        // 5. Hoạt động, đi lại & đồ vật
        "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🥋 ⛳ 🎣 🎽 🛹 ⛸️ 🎿 🏆 🥇 🥈 🥉 🏅 🎖️ " +
            "🎫 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🎯 🎳 🎮 🧩 🚗 🚕 🚌 🏎️ 🚓 " +
            "🚑 🚒 🚚 🛵 🏍️ 🚲 ✈️ 🚀 🚁 ⛵ 🚢 🏠 🏢 🏥 🏫 ⛪ 🗼 🗽 🎡 🏖️ 🏝️ ⛰️ 🗻 📱 " +
            "💻 ⌨️ 🖥️ 📷 📺 📻 ⏰ ⌛ 💡 🔦 💰 💵 💳 💎 🔧 🔨 🔑 🔒 ✂️ 📌 📎 ✏️ 📝 📚 " +
            "📖 🎁 🎈 🎉 🎊 🎀 🧧 🏮",
        // 6. Trái tim & biểu tượng
        "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 💯 💢 💥 💫 💦 " +
            "💨 💬 💭 💤 ✅ ❌ ❓ ❗ ⭕ 🚫 ⛔ ⚠️ ♻️ 🆗 🆕 🆒 🆓 🆘 ➕ ➖ ➗ ✖️ 💲 🔴 " +
            "🔵 ⚫ ⚪ ⬛ ⬜ 🔶 🔷 ▶️ ⏸️ ⏹️ ⏩ ⏪ 🔀 🔁 🔼 🔽 ⬆️ ⬇️ ⬅️ ➡️ ↩️ ↪️ 🎵 🎶 " +
            "🔔 🔕 📣 🇻🇳"
    )

    private val EMOJI_CATS: List<List<String>> =
        EMOJI_CATS_RAW.map { it.split(' ').filter { e -> e.isNotBlank() } }

    const val EMOJI_COLS = 8

    /** Emoji của một tab (0 = gần đây). Mỗi phần tử là 1 grapheme (có thể
     *  gồm nhiều code point: FE0F, cờ) — không xẻ đôi surrogate. */
    fun emojiList(cat: Int, recent: List<String> = emptyList()): List<String> =
        if (cat <= 0) recent
        else EMOJI_CATS.getOrElse(cat - 1) { emptyList() }

    /** Trang emoji: các hàng lưới (cuộn dọc trong KeyboardView) + hàng tab
     *  cố định ở cuối: ABC | 🕘 😀 👋 … | 📋 | ⌫. */
    fun emoji(cat: Int = 1, recent: List<String> = emptyList()): List<KbRow> {
        val rows = emojiList(cat, recent).chunked(EMOJI_COLS).map { line ->
            KbRow(line.map { KbKey("tx:$it", it) })
        }
        val tabs = KbRow(
            EMOJI_TABS.mapIndexed { i, icon -> KbKey("fn:ecat:$i", icon, func = true) }
        )
        val actions = KbRow(
            listOf(
                KbKey("fn:abc", "ABC", 1.3f, func = true),
                KbKey("fn:paste", func = true),
                KbKey("fn:space", "HKey", 3.2f, swipe = true),
                KbKey("fn:enter", "↵", 1.4f, func = true),
                del(1.5f)
            )
        )
        return rows + tabs + actions
    }
}

/** Bàn phím tự vẽ: một View duy nhất, hit theo Ô (không rớt vào khe giữa
 *  các phím), trượt ngón đổi phím, đa chạm, nhấn giữ ra ký tự phụ, giữ ⌫
 *  lặp xóa, vuốt space dời con trỏ, TalkBack qua ExploreByTouchHelper.
 *  1.2: icon vector (shift/⌫/enter theo action ô), bóng phím, Enter màu
 *  nhấn, 2 trang ký hiệu, trang emoji cuộn dọc theo nhóm + gần đây, chiều
 *  cao mọi trang bằng nhau (không giật khi đổi trang). */
class KeyboardView(context: Context) : View(context) {

    var onKey: (KbKey) -> Unit = {}
    var onSpaceSwipe: (Int) -> Unit = {} // +1 phải / -1 trái
    var onRecentEmoji: (List<String>) -> Unit = {} // lưu emoji gần đây

    var palette = KbPalette.DARK
        set(v) { field = v; invalidate() }
    var keyHeightPx = (52 * resources.displayMetrics.density).toInt()
    var sidePx = 0
    var numberRow = false
    var soundEnabled = true
    var vibrateEnabled = true
    /** 1.4.0 (C5): cường độ rung (ms) + âm lượng bấm (0..100) — HKeyIME
     *  đẩy xuống từ VIBRATE_STRENGTH/SOUND_VOLUME. */
    var hapticMs = 20
    var soundVolume = 50

    var shifted = false
        set(v) { if (field != v) { field = v; invalidate() } }
    var capsLocked = false
        set(v) { if (field != v) { field = v; invalidate() } }
    var langVi = true
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Action Enter hiệu lực (EditorInfo.IME_ACTION_*; NONE = xuống dòng) —
     *  quyết định icon phím Enter. */
    var enterAction = EditorInfo.IME_ACTION_NONE
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Loại ô nhập (đổi phím , -> / hoặc @). */
    var fieldKind = KbField.TEXT
        set(v) {
            if (field == v) return
            field = v
            if (page != Page.EMOJI) showPage(page)
        }

    var recentEmoji: List<String> = emptyList()

    enum class Page { LETTERS, SYMBOLS, SYMBOLS2, EMOJI }
    var page = Page.LETTERS
        private set
    private var emojiCat = 1

    private var rows: List<KbRow> = KbLayouts.letters(false)
    private val areas = mutableListOf<Area>()

    /** [scroll] = ô thuộc lưới emoji (toạ độ nội dung, cộng gridScroll). */
    private class Area(val key: KbKey, val draw: RectF, val hit: RectF, val scroll: Boolean)

    // Quyết định bắn phím/lặp ở lớp thuần KeyTouchState (test JVM được).
    private val touch = KeyTouchState()
    // Preview + dải ký tự phụ vẽ trong onDraw (bỏ PopupWindow): toạ độ trong
    // view, hàng trên tràn lên thanh gợi ý nhờ clipChildren=false.
    private var altOwner = -1
    private var altSel = -1
    private var altAnchor: Area? = null
    private var altChars = listOf<String>()
    private var previewArea: Area? = null
    private var topRoomPx = 0 // chỗ trống phía trên view còn trong cửa sổ IME

    private val density = resources.displayMetrics.density
    private val marginH = 2.75f * density
    private val marginV = 3f * density
    private val padV = 4f * density
    /** Đệm chạm dưới đáy rộng hơn padV: chạm trượt xuống mép hàng cuối vẫn
     *  nằm trong view (gán về phím gần nhất) — dưới view là vùng gesture/nav
     *  của hệ thống, chạm vào đó bàn phím bị ẩn (1.3.1). */
    private val padBottomV = 14f * density
    // 1.3.2: bo góc theo kiểu phím (vuông/vừa/tròn), bóng theo theme
    private var corner = 7f * density
    private var shadowPx = 1.2f * density
    private val strokeW = 1f * density
    private val swipeStartPx = 22 * density // vuốt space: phải vượt ngưỡng này mới dời con trỏ
    private val swipeStepPx = 12 * density  // mỗi nấc tiếp theo = 1 ký tự
    /** 1.3: bề rộng tối đa vùng phím — tablet/màn gập không bị kéo dãn
     *  full-width (phím khổng lồ), vùng phím canh giữa. */
    private val maxContentPx = 600 * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    /** 1.4.0 (U3): nhịp nhấn giữ từ LONGPRESS_MS (200..700). */
    var longPressMs = 360L

    // ---- lưới emoji cuộn dọc ----
    private var gridTop = 0f
    private var gridBottom = 0f
    private var gridScroll = 0f
    private var gridMax = 0f
    private var gridScrollOrigin = 0f
    private val gridStartY = HashMap<Int, Float>()
    private val gridScrolling = HashSet<Int>()
    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null

    private val handler = Handler(Looper.getMainLooper())
    private var lpPid = -1
    private val longPress = Runnable { fireLongPress() }
    private val repeater = object : Runnable {
        override fun run() {
            val rk = touch.repeatKey ?: return // đã dừng -> không repost
            if (touch.repeatDue(SystemClock.uptimeMillis())) feed(rk)
            handler.postDelayed(this, touch.repeatMs)
        }
    }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    // Lấy vibrator lười (lazy) — tránh null nếu context lúc khởi tạo chưa sẵn service.
    private val vibrator: Vibrator? get() =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val fnTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.9f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val path = Path()
    private val tmpRect = RectF()
    private val pressedKeys = HashSet<KbKey>()

    /** Dọn mọi chạm đang dở + bong bóng/dải phụ + hẹn giờ — dùng khi đổi
     *  trang (tránh phím "ma" khi đa chạm), ẩn phím, huỷ chạm. */
    private fun clearTouch() {
        touch.cancelAll()
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeater)
        lpPid = -1
        gridStartY.clear()
        gridScrolling.clear()
        velocity?.recycle()
        velocity = null
        hidePreview()
        hideAlts()
    }

    fun showPage(p: Page) {
        clearTouch()
        if (p == Page.EMOJI && page != Page.EMOJI) {
            // mở emoji: có lịch sử thì vào tab gần đây, chưa có thì mặt cười
            emojiCat = if (recentEmoji.isNotEmpty()) 0 else 1
            gridScroll = 0f
            scroller.forceFinished(true)
        }
        page = p
        rows = buildRows()
        buildAreas()
        requestLayout()
        invalidate()
    }

    private fun buildRows(): List<KbRow> = when (page) {
        Page.LETTERS -> KbLayouts.letters(numberRow, fieldKind)
        Page.SYMBOLS -> KbLayouts.symbols(fieldKind)
        Page.SYMBOLS2 -> KbLayouts.symbols2(fieldKind)
        Page.EMOJI -> KbLayouts.emoji(emojiCat, recentEmoji)
    }

    private fun setEmojiCategory(cat: Int) {
        if (page != Page.EMOJI) return
        emojiCat = cat.coerceIn(0, KbLayouts.EMOJI_TABS.lastIndex)
        gridScroll = 0f
        scroller.forceFinished(true)
        gridStartY.clear()
        gridScrolling.clear()
        rows = buildRows()
        buildAreas()
        invalidate()
    }

    /** Áp kích thước/giao diện từ prefs; gọi sau khi đổi settings.
     *  1.3.2: nhận palette của theme đã chọn + bo góc (dp) theo kiểu phím. */
    fun configure(
        heightPct: Int, sideDp: Int, pal: KbPalette, numRow: Boolean,
        cornerDp: Float = 7f
    ) {
        keyHeightPx = ((52 * heightPct / 100) * density).toInt()
        sidePx = (sideDp * density).toInt()
        corner = cornerDp * density
        shadowPx = pal.shadowDp * density
        palette = pal
        numberRow = numRow
        showPage(page)
    }

    /** 1.3: chiều cao hàng hiệu lực — landscape thấp lại x0.8 để bàn phím
     *  không chiếm gần nửa màn hình ngang. */
    private val rowH: Float
        get() = keyHeightPx * (
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                0.8f else 1f
            )

    /** Lề ngang hiệu lực: max(kb_side của user, lề căn giữa trên màn rộng). */
    private fun effSidePx(w: Float) = max(sidePx.toFloat(), (w - maxContentPx) / 2f)

    /** Tổng chiều cao vùng phím: cố định theo số hàng trang chữ — mọi trang
     *  (ký hiệu, emoji) cùng cao, đổi trang không làm app nhảy layout. */
    private val rowsHeight: Float
        get() = (if (numberRow) 5 else 4) * rowH

    override fun onMeasure(wm: Int, hm: Int) {
        val w = MeasureSpec.getSize(wm)
        setMeasuredDimension(w, (rowsHeight + padV + padBottomV).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        buildAreas()
        // Khoảng trống phía trên view trong CỬA SỔ (getLocationInWindow —
        // không lệch edge-to-edge như getLocationOnScreen): bong bóng phím
        // được tràn lên thanh gợi ý nhưng không vượt đỉnh cửa sổ.
        topRoomPx = IntArray(2).also { getLocationInWindow(it) }[1]
    }

    private fun layoutRow(row: KbRow, y: Float, rh: Float) {
        val w = width.toFloat()
        val sumW = row.keys.sumOf { it.w.toDouble() }.toFloat() + 2 * row.indent
        val es = effSidePx(w)
        val unit = (w - 2 * es) / sumW
        var x = es + row.indent * unit
        row.keys.forEachIndexed { i, k ->
            val cellL = if (i == 0 && row.indent == 0f) 0f else x
            val cw = k.w * unit
            val cellR = if (i == row.keys.lastIndex && row.indent == 0f) w else x + cw
            areas += Area(
                k,
                RectF(x + marginH, y + marginV, x + cw - marginH, y + rh - marginV),
                RectF(cellL, y, cellR, y + rh),
                false
            )
            x += cw
        }
    }

    private fun buildAreas() {
        areas.clear()
        if (width == 0) return
        val total = rowsHeight
        if (page == Page.EMOJI) {
            gridTop = padV
            gridBottom = padV + total - 2 * rowH
            val gridRowH = rowH * 0.92f
            val es = effSidePx(width.toFloat())
            val unit = (width - 2f * es) / KbLayouts.EMOJI_COLS
            val gridRows = rows.dropLast(2)
            gridRows.forEachIndexed { r, row ->
                val y = gridTop + r * gridRowH
                row.keys.forEachIndexed { i, k ->
                    val x = es + i * unit
                    areas += Area(
                        k,
                        RectF(x + marginH, y + marginV / 2, x + unit - marginH, y + gridRowH - marginV / 2),
                        RectF(x, y, x + unit, y + gridRowH),
                        true
                    )
                }
            }
            gridMax = max(0f, gridRows.size * gridRowH - (gridBottom - gridTop))
            gridScroll = gridScroll.coerceIn(0f, gridMax)
            layoutRow(rows[rows.size - 2], gridBottom, rowH)
            layoutRow(rows.last(), gridBottom + rowH, rowH)
        } else {
            gridTop = 0f
            gridBottom = 0f
            gridMax = 0f
            val rh = total / rows.size
            var y = padV
            for (row in rows) {
                layoutRow(row, y, rh)
                y += rh
            }
        }
        // Hàng đầu/cuối: mở vùng chạm tới mép view (không áp cho lưới cuộn).
        for (a in areas) {
            if (a.scroll) continue
            if (a.hit.top < padV + marginV) a.hit.top = 0f
            if (a.hit.bottom > height - padBottomV - marginV) a.hit.bottom = height.toFloat()
        }
        touchHelper.invalidateRoot()
    }

    private fun upperCase() = shifted || capsLocked

    private fun labelOf(k: KbKey): String = when {
        k.label != null -> k.label
        k.tag.startsWith("ch:") -> {
            val c = k.tag.removePrefix("ch:")
            if (upperCase()) c.uppercase() else c
        }
        k.tag.startsWith("p:") || k.tag.startsWith("tx:") -> k.tag.substring(3)
        else -> ""
    }

    /** Ô dưới điểm chạm; ngoài biên thì gán ô gần nhất cùng vùng (lưới emoji
     *  hoặc phím thường). Lưới emoji tính theo toạ độ đã cuộn. */
    private fun areaAt(x: Float, y: Float): Area? {
        val inGrid = page == Page.EMOJI && y < gridBottom
        val gy = if (inGrid) y + gridScroll else y
        var best: Area? = null
        var bestD = Float.MAX_VALUE
        for (a in areas) {
            if (a.scroll != inGrid) continue
            if (a.hit.contains(x, gy)) return a
            if (inGrid) continue
            val dx = maxOf(a.hit.left - x, 0f, x - a.hit.right)
            val dy = maxOf(a.hit.top - gy, 0f, gy - a.hit.bottom)
            val d = dx * dx + dy * dy
            if (d < bestD) {
                bestD = d
                best = a
            }
        }
        return best
    }

    private fun keyAt(x: Float, y: Float): KbKey? = areaAt(x, y)?.key

    // ------------------------------------------------------------ vẽ

    override fun onDraw(c: Canvas) {
        pressedKeys.clear()
        for ((pid, p) in touch.ptrs) if (pid !in gridScrolling) pressedKeys += p.key

        if (page == Page.EMOJI) drawEmojiGrid(c)
        for (a in areas) if (!a.scroll) drawKey(c, a)
        drawPreview(c)
        drawAltStrip(c)
    }

    private fun drawEmojiGrid(c: Canvas) {
        c.save()
        c.clipRect(0f, gridTop, width.toFloat(), gridBottom)
        c.translate(0f, -gridScroll)
        val visTop = gridTop + gridScroll
        val visBottom = gridBottom + gridScroll
        var any = false
        txtPaint.typeface = Typeface.DEFAULT
        txtPaint.textSize = 26 * density
        for (a in areas) {
            if (!a.scroll) continue
            any = true
            if (a.draw.bottom < visTop || a.draw.top > visBottom) continue
            if (a.key in pressedKeys) {
                keyPaint.color = palette.keyPressed
                c.drawRoundRect(a.draw, corner, corner, keyPaint)
            }
            txtPaint.color = palette.text
            val ty = a.draw.centerY() - (txtPaint.descent() + txtPaint.ascent()) / 2
            c.drawText(labelOf(a.key), a.draw.centerX(), ty, txtPaint)
        }
        c.restore()
        if (!any) {
            txtPaint.color = palette.dim
            txtPaint.textSize = 14 * density
            val cy = (gridTop + gridBottom) / 2
            c.drawText(
                if (emojiCat == 0) "Chưa có emoji dùng gần đây" else "Trống",
                width / 2f, cy, txtPaint
            )
        }
        // thanh cuộn mảnh bên phải
        if (gridMax > 0f) {
            val vh = gridBottom - gridTop
            val barH = max(vh * vh / (vh + gridMax), 24 * density)
            val barTop = gridTop + (vh - barH) * (gridScroll / gridMax)
            keyPaint.color = palette.dim and 0x00FFFFFF or 0x66000000
            tmpRect.set(width - 4 * density, barTop, width - 1.5f * density, barTop + barH)
            c.drawRoundRect(tmpRect, 2 * density, 2 * density, keyPaint)
        }
    }

    private fun drawKey(c: Canvas, a: Area) {
        val k = a.key
        val pressed = k in pressedKeys
        val isEnter = k.tag == "fn:enter"
        val isTab = k.tag.startsWith("fn:ecat:")
        val tabSel = isTab && k.tag == "fn:ecat:$emojiCat"

        // nền phím + bóng đổ 1dp (tab emoji phẳng, chỉ tô khi chọn/nhấn)
        if (!isTab || pressed || tabSel) {
            val bg = when {
                isTab -> if (pressed) palette.funcPressed else palette.func
                isEnter && pressed -> palette.enterPressed
                isEnter -> palette.enter
                pressed && k.func -> palette.funcPressed
                pressed -> palette.keyPressed
                k.func -> palette.func
                else -> palette.key
            }
            if (!isTab && shadowPx > 0f) {
                keyPaint.color = palette.shadow
                tmpRect.set(a.draw)
                tmpRect.offset(0f, shadowPx)
                c.drawRoundRect(tmpRect, corner, corner, keyPaint)
            }
            keyPaint.color = bg
            c.drawRoundRect(a.draw, corner, corner, keyPaint)
            // 1.3.2: viền mảnh cho theme phím trong suốt trên nền gradient
            if (!isTab && !isEnter && palette.keyStroke != 0) {
                borderPaint.color = palette.keyStroke
                tmpRect.set(a.draw)
                tmpRect.inset(strokeW / 2, strokeW / 2)
                c.drawRoundRect(tmpRect, corner, corner, borderPaint)
            }
        }

        val fg = when {
            isEnter -> palette.onAccent
            k.tag == "fn:shift" && upperCase() -> palette.accent
            k.func -> palette.funcText
            else -> palette.text
        }
        when (k.tag) {
            "fn:shift" -> { drawShift(c, a.draw, fg); return }
            "fn:del" -> { drawBackspace(c, a.draw, fg); return }
            "fn:enter" -> { drawEnter(c, a.draw, fg); return }
            "fn:paste" -> { drawPaste(c, a.draw, fg); return }
            "fn:space" -> {
                txtPaint.typeface = Typeface.DEFAULT
                txtPaint.color = palette.dim
                val label = if (langVi) "← HKey · Tiếng Việt" else "HKey · English →"
                val maxW = a.draw.width() - 8 * density
                var ts = 13 * density
                txtPaint.textSize = ts
                while (ts > 8 * density && txtPaint.measureText(label) > maxW) {
                    ts -= density
                    txtPaint.textSize = ts
                }
                val ty = a.draw.centerY() - (txtPaint.descent() + txtPaint.ascent()) / 2
                c.drawText(label, a.draw.centerX(), ty, txtPaint)
                return
            }
        }

        txtPaint.color = fg
        txtPaint.typeface = if (k.func) fnTypeface else Typeface.DEFAULT
        txtPaint.textSize = density * when {
            isTab -> 19f
            k.tag == "fn:paste" -> 18f
            k.func -> 14.5f
            k.tag.startsWith("tx:") -> 22f
            else -> 21f
        }
        val ty = a.draw.centerY() - (txtPaint.descent() + txtPaint.ascent()) / 2
        c.drawText(labelOf(k), a.draw.centerX(), ty, txtPaint)
        if (tabSel) { // gạch chân tab đang chọn
            keyPaint.color = palette.accent
            val hw = min(a.draw.width() * 0.28f, 10 * density)
            tmpRect.set(a.draw.centerX() - hw, a.draw.bottom - 3.5f * density,
                a.draw.centerX() + hw, a.draw.bottom - 1.5f * density)
            c.drawRoundRect(tmpRect, density, density, keyPaint)
        }

        // gợi ý số góc phải-trên (hàng q..p khi không bật hàng số)
        if (k.hint && k.alts.isNotEmpty()) {
            txtPaint.typeface = Typeface.DEFAULT
            txtPaint.color = palette.dim
            txtPaint.textSize = 10.5f * density
            c.drawText(k.alts.substring(0, 1), a.draw.right - 6.5f * density,
                a.draw.top + 12 * density, txtPaint)
        }
        k.mini?.let { m -> // icon nhỏ góc phải-trên (giữ phím -> chức năng)
            txtPaint.typeface = Typeface.DEFAULT
            txtPaint.color = palette.dim
            txtPaint.textSize = 9.5f * density
            c.drawText(m, a.draw.right - 8 * density, a.draw.top + 12 * density, txtPaint)
        }
    }

    private fun drawPaste(c: Canvas, r: RectF, color: Int) {
        val s = iconSize(r)
        val cx = r.centerX()
        val cy = r.centerY()
        val w = s * 0.72f
        val h = s * 0.92f
        val left = cx - w / 2
        val top = cy - h / 2 + s * 0.05f
        strokePaint.color = color
        tmpRect.set(left, top + s * 0.1f, left + w, top + h)
        c.drawRoundRect(tmpRect, s * 0.08f, s * 0.08f, strokePaint)
        fillPaint.color = color
        tmpRect.set(cx - w * 0.26f, top - s * 0.04f, cx + w * 0.26f, top + s * 0.16f)
        c.drawRoundRect(tmpRect, s * 0.05f, s * 0.05f, fillPaint)
    }

    private fun iconSize(r: RectF) = min(r.width(), r.height()) * 0.46f

    private fun drawShift(c: Canvas, r: RectF, color: Int) {
        val s = iconSize(r)
        val cx = r.centerX()
        val cy = r.centerY() - (if (capsLocked) s * 0.08f else 0f)
        path.reset()
        path.moveTo(cx, cy - s * 0.5f)
        path.lineTo(cx + s * 0.5f, cy + s * 0.04f)
        path.lineTo(cx + s * 0.22f, cy + s * 0.04f)
        path.lineTo(cx + s * 0.22f, cy + s * 0.42f)
        path.lineTo(cx - s * 0.22f, cy + s * 0.42f)
        path.lineTo(cx - s * 0.22f, cy + s * 0.04f)
        path.lineTo(cx - s * 0.5f, cy + s * 0.04f)
        path.close()
        if (upperCase()) {
            fillPaint.color = color
            c.drawPath(path, fillPaint)
        } else {
            strokePaint.color = color
            c.drawPath(path, strokePaint)
        }
        if (capsLocked) { // caps lock: thêm vạch dưới mũi tên
            strokePaint.color = color
            c.drawLine(cx - s * 0.24f, cy + s * 0.64f, cx + s * 0.24f, cy + s * 0.64f, strokePaint)
        }
    }

    private fun drawBackspace(c: Canvas, r: RectF, color: Int) {
        val s = iconSize(r)
        val cx = r.centerX()
        val cy = r.centerY()
        val left = cx - s * 0.58f
        val right = cx + s * 0.55f
        val top = cy - s * 0.36f
        val bottom = cy + s * 0.36f
        val notch = left + s * 0.32f
        path.reset()
        path.moveTo(left, cy)
        path.lineTo(notch, top)
        path.lineTo(right, top)
        path.lineTo(right, bottom)
        path.lineTo(notch, bottom)
        path.close()
        strokePaint.color = color
        c.drawPath(path, strokePaint)
        val xx = (notch + right) / 2 + s * 0.02f
        val d = s * 0.14f
        c.drawLine(xx - d, cy - d, xx + d, cy + d, strokePaint)
        c.drawLine(xx - d, cy + d, xx + d, cy - d, strokePaint)
    }

    /** Icon Enter theo action ô: tìm kiếm / gửi / đi tiếp / xong / xuống dòng. */
    private fun drawEnter(c: Canvas, r: RectF, color: Int) {
        val s = iconSize(r)
        val cx = r.centerX()
        val cy = r.centerY()
        strokePaint.color = color
        fillPaint.color = color
        when (enterAction) {
            EditorInfo.IME_ACTION_SEARCH -> {
                c.drawCircle(cx - s * 0.1f, cy - s * 0.1f, s * 0.28f, strokePaint)
                c.drawLine(cx + s * 0.11f, cy + s * 0.11f, cx + s * 0.42f, cy + s * 0.42f, strokePaint)
            }
            EditorInfo.IME_ACTION_SEND -> {
                path.reset()
                path.moveTo(cx - s * 0.44f, cy - s * 0.42f)
                path.lineTo(cx + s * 0.48f, cy)
                path.lineTo(cx - s * 0.44f, cy + s * 0.42f)
                path.lineTo(cx - s * 0.26f, cy)
                path.close()
                c.drawPath(path, fillPaint)
            }
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> {
                c.drawLine(cx - s * 0.44f, cy, cx + s * 0.42f, cy, strokePaint)
                c.drawLine(cx + s * 0.1f, cy - s * 0.32f, cx + s * 0.42f, cy, strokePaint)
                c.drawLine(cx + s * 0.1f, cy + s * 0.32f, cx + s * 0.42f, cy, strokePaint)
            }
            EditorInfo.IME_ACTION_PREVIOUS -> {
                c.drawLine(cx + s * 0.44f, cy, cx - s * 0.42f, cy, strokePaint)
                c.drawLine(cx - s * 0.1f, cy - s * 0.32f, cx - s * 0.42f, cy, strokePaint)
                c.drawLine(cx - s * 0.1f, cy + s * 0.32f, cx - s * 0.42f, cy, strokePaint)
            }
            EditorInfo.IME_ACTION_DONE -> {
                path.reset()
                path.moveTo(cx - s * 0.42f, cy + s * 0.02f)
                path.lineTo(cx - s * 0.12f, cy + s * 0.32f)
                path.lineTo(cx + s * 0.44f, cy - s * 0.3f)
                c.drawPath(path, strokePaint)
            }
            else -> { // ↵ xuống dòng
                path.reset()
                path.moveTo(cx + s * 0.42f, cy - s * 0.38f)
                path.lineTo(cx + s * 0.42f, cy + s * 0.12f)
                path.lineTo(cx - s * 0.4f, cy + s * 0.12f)
                c.drawPath(path, strokePaint)
                path.reset()
                path.moveTo(cx - s * 0.13f, cy - s * 0.15f)
                path.lineTo(cx - s * 0.42f, cy + s * 0.12f)
                path.lineTo(cx - s * 0.13f, cy + s * 0.39f)
                c.drawPath(path, strokePaint)
            }
        }
    }

    /** Bong bóng phím nổi trên phím đang bấm; hàng trên tràn lên thanh gợi ý
     *  (y âm vẫn trong cửa sổ IME). */
    private fun drawPreview(c: Canvas) {
        val a = previewArea ?: return
        val pw = max(a.draw.width() * 1.3f, 46 * density)
        val ph = min(rowH * 1.25f, 66 * density)
        val cx = a.draw.centerX().coerceIn(pw / 2, max(pw / 2, width - pw / 2))
        val top = (a.draw.top - ph - 6 * density).coerceAtLeast(-topRoomPx.toFloat())
        tmpRect.set(cx - pw / 2, top + shadowPx * 1.5f, cx + pw / 2, top + ph + shadowPx * 1.5f)
        keyPaint.color = palette.shadow
        c.drawRoundRect(tmpRect, corner * 1.3f, corner * 1.3f, keyPaint)
        tmpRect.set(cx - pw / 2, top, cx + pw / 2, top + ph)
        keyPaint.color = palette.popupBg
        c.drawRoundRect(tmpRect, corner * 1.3f, corner * 1.3f, keyPaint)
        txtPaint.typeface = Typeface.DEFAULT
        txtPaint.color = palette.text
        txtPaint.textSize = 28 * density
        val ty = top + ph / 2 - (txtPaint.descent() + txtPaint.ascent()) / 2
        c.drawText(labelOf(a.key), cx, ty, txtPaint)
    }

    private fun altCellW(): Float {
        val n = max(1, altChars.size)
        return min(42 * density, (width - 8 * density) / n)
    }

    /** Toạ độ dải ký tự phụ (long-press) trong view — dùng chung cho vẽ và
     *  đổi ô chọn khi trượt ngón. Không bao giờ rộng quá view (tránh crash
     *  coerceIn khi nhiều ký tự phụ). */
    private fun altStripRect(): RectF? {
        val a = altAnchor ?: return null
        if (altChars.isEmpty()) return null
        val w = altCellW() * altChars.size + 8 * density
        val h = 54 * density
        val left = (a.draw.centerX() - w / 2).coerceIn(0f, max(0f, width - w))
        val top = (a.draw.top - h - 4 * density).coerceAtLeast(-topRoomPx.toFloat())
        return RectF(left, top, left + w, top + h)
    }

    private fun drawAltStrip(c: Canvas) {
        val r = altStripRect() ?: return
        tmpRect.set(r)
        tmpRect.offset(0f, shadowPx * 1.5f)
        keyPaint.color = palette.shadow
        c.drawRoundRect(tmpRect, corner * 1.3f, corner * 1.3f, keyPaint)
        keyPaint.color = palette.popupBg
        c.drawRoundRect(r, corner * 1.3f, corner * 1.3f, keyPaint)
        val cw = altCellW()
        val pad = 4 * density
        txtPaint.typeface = Typeface.DEFAULT
        txtPaint.textSize = 21 * density
        altChars.forEachIndexed { i, ch ->
            val cellL = r.left + pad + cw * i
            if (i == altSel) {
                keyPaint.color = palette.enter
                tmpRect.set(cellL, r.top + pad, cellL + cw, r.bottom - pad)
                c.drawRoundRect(tmpRect, corner, corner, keyPaint)
            }
            txtPaint.color = if (i == altSel) palette.onAccent else palette.text
            val ty = r.centerY() - (txtPaint.descent() + txtPaint.ascent()) / 2
            c.drawText(ch, cellL + cw / 2, ty, txtPaint)
        }
    }

    /** 6d: vẽ lại chỉ vùng một phím thay vì invalidate() toàn view. */
    private fun invalidateArea(a: Area) {
        if (a.scroll) { invalidate(); return }
        invalidate(
            android.graphics.Rect(
                a.draw.left.toInt(), a.draw.top.toInt(),
                a.draw.right.toInt() + 1, (a.draw.bottom + shadowPx).toInt() + 1
            )
        )
    }

    /** Bong bóng/dải phụ tràn khỏi bounds view -> invalidate cả chuỗi cha để
     *  vùng thanh gợi ý được vẽ lại (clipChildren=false ở layout). */
    private fun invalidateOverlay() {
        var v: View? = this
        while (v != null) {
            v.invalidate()
            v = v.parent as? View
        }
    }

    // ------------------------------------------------------------ phản hồi

    private fun click(k: KbKey) {
        if (soundEnabled) {
            val fx = when (k.tag) {
                "fn:del" -> AudioManager.FX_KEYPRESS_DELETE
                "fn:space" -> AudioManager.FX_KEYPRESS_SPACEBAR
                "fn:enter" -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            // 1.2: phát qua AudioManager theo cờ của HKey — không phụ thuộc
            // "âm thanh chạm" hệ thống (bật trong app mà không kêu).
            // 1.4.0 (C5): âm lượng theo SOUND_VOLUME (0..100).
            // 1.5.14: bọc try-catch cho môi trường test.
            try {
                audio?.playSoundEffect(fx, soundVolume / 100f)
            } catch (_: Exception) { }
        }
        }
        // 1.4.0 (C5): rung theo VIBRATE_STRENGTH ms qua VibrationEffect
        // thay KEYBOARD_TAP hệ thống (không điều chỉnh được cường độ).
        // 1.5.14: thử cả hai — VibrationEffect trực tiếp + haptic feedback
        // (một số máy chỉ nhận một trong hai).
        if (vibrateEnabled && hapticMs > 0) {
            try {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        hapticMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } catch (_: Exception) { }
            try {
                @Suppress("DEPRECATION")
                performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }
        }
    }

    private fun feed(k: KbKey) {
        click(k)
        if (k.tag.startsWith("fn:ecat:")) { // tab nhóm emoji: xử lý tại chỗ
            setEmojiCategory(k.tag.removePrefix("fn:ecat:").toIntOrNull() ?: 1)
            return
        }
        if (page == Page.EMOJI && k.tag.startsWith("tx:")) pushRecent(k.tag.substring(3))
        onKey(k)
    }

    private fun pushRecent(e: String) {
        recentEmoji = (listOf(e) + recentEmoji.filter { it != e }).take(32)
        onRecentEmoji(recentEmoji)
        if (emojiCat == 0) {
            rows = buildRows()
            buildAreas()
            invalidate()
        } else {
            touchHelper.invalidateRoot()
        }
    }

    private fun hidePreview() {
        if (previewArea == null) return
        previewArea = null
        invalidateOverlay()
    }

    private fun showPreview(a: Area) {
        if (a.key.func || a.key.tag == "fn:space" || a.scroll) { hidePreview(); return }
        if (previewArea === a) return
        previewArea = a
        invalidateOverlay()
    }

    private fun areaOf(k: KbKey): Area? = areas.firstOrNull { it.key === k }

    private fun hideAlts() {
        if (altAnchor == null && altChars.isEmpty()) return
        altAnchor = null
        altChars = emptyList()
        altOwner = -1
        altSel = -1
        invalidateOverlay()
    }

    private fun fireLongPress() {
        val pid = lpPid
        val p = touch.ptrs[pid] ?: return
        if (pid in gridScrolling || pid in touch.consumed) return
        val k = p.key
        if (k.longTag != null) {
            touch.consumed += pid
            hidePreview()
            if (vibrateEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            feed(KbKey(k.longTag, func = true))
            return
        }
        if (k.alts.isEmpty()) return
        val a = areaOf(k) ?: return
        touch.consumed += pid
        altAnchor = a
        // chữ đang viết hoa -> ký tự phụ là chữ cũng hoa (ê -> Ê)
        val up = k.tag.startsWith("ch:") && upperCase()
        altChars = k.alts.map { if (up) it.uppercase() else it.toString() }
        altSel = 0
        altOwner = pid
        hidePreview() // dải phụ thay bong bóng
        invalidateOverlay()
        if (vibrateEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun updateAltSel(x: Float) {
        val r = altStripRect() ?: return
        val idx = ((x - r.left - 4 * density) / altCellW()).toInt()
            .coerceIn(0, altChars.size - 1)
        if (idx != altSel) {
            altSel = idx
            invalidateOverlay()
        }
    }

    private fun commitAlt(pid: Int): Boolean {
        if (altAnchor == null || altOwner != pid) return false
        val s = altChars.getOrNull(altSel)
        if (s != null) feed(KbKey("tx:$s", s))
        return true
    }

    // ------------------------------------------------------------ chạm

    // 6c: cache trạng thái TalkBack — không gọi getSystemService mỗi MotionEvent.
    private var touchExplorationEnabled = false
    private val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    private val teListener = AccessibilityManager.TouchExplorationStateChangeListener { on ->
        touchExplorationEnabled = on
        invalidate()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            gridScroll = scroller.currY.toFloat().coerceIn(0f, gridMax)
            touchHelper.invalidateRoot()
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // TalkBack bật: touch đi qua hover-explore + double-tap click,
        // không bắn phím trực tiếp (tránh gõ kép).
        if (touchExplorationEnabled) return super.onTouchEvent(e)
        if (page == Page.EMOJI) {
            val vt = velocity ?: VelocityTracker.obtain().also { velocity = it }
            vt.addMovement(e)
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val pid = e.getPointerId(i)
                val x = e.getX(i)
                val y = e.getY(i)
                var stoppedFling = false
                if (page == Page.EMOJI && !scroller.isFinished) {
                    scroller.forceFinished(true)
                    stoppedFling = true
                }
                val a = areaAt(x, y) ?: return true
                val k = a.key
                hideAlts()
                if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    val flushed = touch.flushPendingCharacters(excludePid = pid)
                    if (flushed.isNotEmpty()) {
                        handler.removeCallbacks(longPress)
                        hidePreview()
                        for (fk in flushed) feed(fk)
                    }
                }
                // Repeat: bắn ngay + consumed luôn (nhả không bắn lại — sửa
                // xoá đúp) + nạp lịch lặp.
                // 1.5.14: feed() (rung + âm) cho MỌI phím, không chỉ phím lặp —
                // touch.down() trả false cho phím thường nên trước đây mất feedback.
                feed(k)
                if (touch.down(pid, k, x, e.eventTime)) {
                    handler.removeCallbacks(repeater)
                    handler.postDelayed(repeater, touch.repeatFirstMs)
                }
                if (a.scroll) {
                    gridStartY[pid] = y
                    gridScrollOrigin = gridScroll
                    if (stoppedFling) { // chạm để dừng cuộn quán tính: không chọn emoji
                        touch.consumed += pid
                        gridScrolling += pid
                    }
                }
                // 6d: chỉ vẽ lại phím vừa đổi trạng thái (overlay tự lo vùng tràn).
                showPreview(a)
                invalidateArea(a)
                // 1.2: huỷ hẹn giờ cũ trước khi hẹn mới — trước đây ngón 1 chưa
                // nhả mà ngón 2 chạm thì long-press của ngón 2 bắn sớm.
                handler.removeCallbacks(longPress)
                lpPid = pid
                handler.postDelayed(longPress, longPressMs)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)
                    val p = touch.ptrs[pid] ?: continue
                    val x = e.getX(i)
                    val y = e.getY(i)
                    if (altOwner == pid) {
                        updateAltSel(x)
                        continue
                    }
                    val sy = gridStartY[pid]
                    if (sy != null) { // ngón trong lưới emoji: kéo = cuộn, không đổi phím
                        if (pid !in gridScrolling && abs(y - sy) > touchSlop) {
                            gridScrolling += pid
                            touch.consumed += pid
                            if (lpPid == pid) handler.removeCallbacks(longPress)
                            gridScrollOrigin = gridScroll
                            gridStartY[pid] = y
                            invalidate()
                        }
                        if (pid in gridScrolling) {
                            val base = gridStartY[pid] ?: y
                            val ns = (gridScrollOrigin - (y - base)).coerceIn(0f, gridMax)
                            if (ns != gridScroll) {
                                gridScroll = ns
                                touchHelper.invalidateRoot()
                                invalidate()
                            }
                        }
                        continue
                    }
                    if (p.key.swipe) {
                        swipeSpace(pid, p, x)
                        continue
                    }
                    val na = areaAt(x, y) ?: continue
                    val nk = na.key
                    if (nk !== p.key && !na.scroll) {
                        val oldKey = p.key
                        // Trượt khỏi ⌫ -> repeater dừng (sửa lặp vô hạn).
                        if (touch.moveTo(pid, nk)) handler.removeCallbacks(repeater)
                        if (lpPid == pid) handler.removeCallbacks(longPress)
                        showPreview(na)
                        invalidateArea(na)
                        areaOf(oldKey)?.let { invalidateArea(it) }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pid = e.getPointerId(e.actionIndex)
                val wasScrolling = pid in gridScrolling
                if (wasScrolling) {
                    velocity?.let { vt ->
                        vt.computeCurrentVelocity(1000)
                        val vy = vt.getYVelocity(pid)
                        if (abs(vy) > minFling && gridMax > 0f) {
                            scroller.fling(
                                0, gridScroll.toInt(), 0, (-vy).toInt(),
                                0, 0, 0, gridMax.toInt()
                            )
                            postInvalidateOnAnimation()
                        }
                    }
                }
                gridScrolling -= pid
                gridStartY.remove(pid)
                val up = touch.up(pid)
                if (lpPid == pid) handler.removeCallbacks(longPress)
                if (up.stopRepeat) handler.removeCallbacks(repeater)
                val hadAlt = altOwner == pid
                if (up.fire != null) {
                    feed(up.fire)
                } else if (hadAlt) {
                    commitAlt(pid)
                }
                if (touch.ptrs.isEmpty()) {
                    hidePreview()
                    hideAlts()
                } else {
                    if (hadAlt) hideAlts()
                    touch.ptrs.values.firstOrNull()?.let { areaOf(it.key)?.let { a -> showPreview(a) } }
                }
                up.key?.let { areaOf(it)?.let { a -> invalidateArea(a) } }
                if (wasScrolling) invalidate()
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    velocity?.recycle()
                    velocity = null
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                clearTouch()
                invalidate()
            }
        }
        return true
    }

    /** Vuốt trên phím cách: phải vượt ngưỡng [swipeStartPx] mới bắt đầu dời
     *  con trỏ (chạm hơi lệch không bị tính là vuốt), sau đó mỗi nấc
     *  [swipeStepPx] dời 1 ký tự. */
    private fun swipeSpace(pid: Int, p: KeyTouchState.Ptr, x: Float) {
        val dx = x - p.startX
        if (!p.swiping) {
            if (abs(dx) < swipeStartPx) return
            p.swiping = true
            p.swipeAcc = dx
            touch.consumed += pid
            if (lpPid == pid) handler.removeCallbacks(longPress)
            hidePreview()
            onSpaceSwipe(if (dx > 0) 1 else -1)
            return
        }
        var moved = false
        while (dx - p.swipeAcc > swipeStepPx) {
            p.swipeAcc += swipeStepPx
            onSpaceSwipe(1)
            moved = true
        }
        while (p.swipeAcc - dx > swipeStepPx) {
            p.swipeAcc -= swipeStepPx
            onSpaceSwipe(-1)
            moved = true
        }
        if (moved && vibrateEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Dọn repeat + popup khi bàn phím ẩn/detach — tránh leak window. */
    fun release() {
        clearTouch()
        scroller.forceFinished(true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        touchExplorationEnabled = am?.isTouchExplorationEnabled == true
        am?.addTouchExplorationStateChangeListener(teListener)
    }

    override fun onDetachedFromWindow() {
        release()
        // 1.2: gỡ listener — trước đây mỗi lần dựng lại view (đổi cài đặt)
        // lại thêm một listener giữ view cũ (rò bộ nhớ).
        am?.removeTouchExplorationStateChangeListener(teListener)
        super.onDetachedFromWindow()
    }

    // ---- TalkBack: mỗi phím là một node ảo (3.x) ----

    private val spoken = mapOf(
        "fn:shift" to "viết hoa", "fn:del" to "xóa", "fn:space" to "khoảng trắng",
        "fn:enter" to "xuống dòng", "fn:sym" to "bảng ký tự", "fn:abc" to "bảng chữ",
        "fn:sym2" to "ký hiệu khác",
        "fn:emoji" to "biểu tượng",
        "fn:paste" to "dán", "p:." to "chấm", "p:," to "phẩy", "p:?" to "chấm hỏi",
        "p:!" to "chấm than", "p:@" to "a còng", "p:/" to "gạch chéo", "p:\\" to "gạch chéo ngược",
        "fn:ecat:0" to "emoji gần đây", "fn:ecat:1" to "mặt cười", "fn:ecat:2" to "cử chỉ",
        "fn:ecat:3" to "động vật", "fn:ecat:4" to "đồ ăn", "fn:ecat:5" to "hoạt động",
        "fn:ecat:6" to "biểu tượng"
    )

    private val touchHelper = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val a = areaAt(x, y) ?: return ExploreByTouchHelper.INVALID_ID
            return areas.indexOf(a)
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            for (i in areas.indices) {
                val a = areas[i]
                if (a.scroll) {
                    val vt = a.hit.top - gridScroll
                    val vb = a.hit.bottom - gridScroll
                    if (vb <= gridTop || vt >= gridBottom) continue
                }
                ids += i
            }
        }

        override fun onPopulateNodeForVirtualView(
            id: Int, node: androidx.core.view.accessibility.AccessibilityNodeInfoCompat
        ) {
            val a = areas.getOrNull(id)
            if (a == null) { // id cũ sau khi đổi trang: node rỗng hợp lệ
                node.setBoundsInParent(android.graphics.Rect(0, 0, 1, 1))
                node.contentDescription = ""
                return
            }
            val off = if (a.scroll) gridScroll else 0f
            var t = a.hit.top - off
            var btm = a.hit.bottom - off
            if (a.scroll) {
                t = t.coerceAtLeast(gridTop)
                btm = btm.coerceAtMost(gridBottom)
            }
            node.setBoundsInParent(
                android.graphics.Rect(
                    a.hit.left.toInt(), t.toInt(),
                    a.hit.right.toInt(), btm.toInt()
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
        touchExplorationEnabled = am?.isTouchExplorationEnabled == true
    }

    override fun dispatchHoverEvent(e: MotionEvent): Boolean =
        touchHelper.dispatchHoverEvent(e) || super.dispatchHoverEvent(e)
}
