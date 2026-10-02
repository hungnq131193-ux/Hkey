package com.hkey.app.service

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import com.hkey.app.R
import com.hkey.app.engine.ContextPredictor
import com.hkey.app.engine.TelexEngine

class HKeyIME : InputMethodService() {

    private val telexEngine = TelexEngine()
    private val predictor = ContextPredictor()

    private val currentComposingWord = StringBuilder()
    private var lastCommittedWord = ""

    /** Từ vừa bị auto-correct đổi: committed = từ app ghi, typed = từ user gõ,
     *  raw = buffer phím thô để mở lại vùng composing khi hoàn tác. */
    private class AutoFix(val committed: String, val typed: String, val raw: String)
    private var lastAutoFix: AutoFix? = null

    private var candidate1: TextView? = null
    private var candidate2: TextView? = null
    private var candidate3: TextView? = null

    private var lettersPage: View? = null
    private var symbolsPage: View? = null
    private var shiftOn = false
    private var shiftAuto = false
    private var autoCap = false
    private val letterKeys = mutableListOf<TextView>()
    private var shiftKey: TextView? = null

    private val repeatHandler = Handler(Looper.getMainLooper())
    private val deleteRepeat = object : Runnable {
        override fun run() {
            handleDelete()
            repeatHandler.postDelayed(this, 60)
        }
    }

    private val prefs get() = getSharedPreferences("hkey_settings", Context.MODE_PRIVATE)

    override fun onCreate() {
        super.onCreate()
        // Nạp từ điển ~7k từ ở thread nền để không chặn lần mở phím đầu;
        // merge vào predictor trên main thread khi đọc xong.
        Thread {
            val words = resources.openRawResource(R.raw.vi_dict)
                .bufferedReader().use { it.lineSequence().toList() }
            repeatHandler.post { predictor.addWords(words) }
        }.start()
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        computeAutoCap(info)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) {
            currentComposingWord.clear()
            lastCommittedWord = ""
            lastAutoFix = null
        }
        computeAutoCap(info)
        shiftOn = false
        shiftAuto = false
        // Inflate lại để settings (cao/rộng/rung) áp dụng ngay lần mở tiếp theo
        setInputView(onCreateInputView())
        updateAutoShift()
    }

    // Tự viết hoa chỉ bật ở ô text thường — không bật ở mật khẩu/email/url
    private fun computeAutoCap(info: EditorInfo) {
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        autoCap = info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_URI
    }

    /** Con trỏ bị đổi chỗ (chạm chỗ khác/bôi chọn): chốt từ đang gõ, xoá buffer
     *  để phím xoá và ký tự tiếp theo tác động đúng vị trí mới. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (currentComposingWord.isNotEmpty() &&
            (newSelStart != newSelEnd || candidatesStart < 0 || newSelStart != candidatesEnd)
        ) {
            currentInputConnection?.finishComposingText()
            currentComposingWord.clear()
            updateSuggestions()
        }
        updateAutoShift()
    }

    /** Viết hoa đầu câu: ô trống, sau xuống dòng, sau dấu . ! ? (+ khoảng trắng).
     *  App không trả text (before==null) thì mặc định viết hoa.
     *  Bật/tắt hai chiều: shift tự động tắt khi ngữ cảnh hết cần hoa;
     *  shift do người dùng bấm tay không bị ghi đè. */
    private fun updateAutoShift() {
        if (!autoCap || currentComposingWord.isNotEmpty()) return
        val before = currentInputConnection?.getTextBeforeCursor(16, 0)
        val wantCap = before == null || before.isEmpty() || before.last() == '\n' ||
            before.trimEnd().let { it.isNotEmpty() && it.last() in ".!?" }
        val newShift = wantCap || (shiftOn && !shiftAuto)
        if (newShift != shiftOn || wantCap != shiftAuto) {
            shiftOn = newShift
            shiftAuto = wantCap
            updateShiftUI()
        }
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)

        letterKeys.clear()
        shiftKey = null

        candidate1 = root.findViewById(R.id.candidate1)
        candidate2 = root.findViewById(R.id.candidate2)
        candidate3 = root.findViewById(R.id.candidate3)

        candidate1?.setOnClickListener { onCandidateTap(candidate1) }
        candidate2?.setOnClickListener { onCandidateTap(candidate2) }
        candidate3?.setOnClickListener { onCandidateTap(candidate3) }

        val pages = root.findViewById<FrameLayout>(R.id.kb_pages)
        lettersPage = layoutInflater.inflate(R.layout.keyboard_letters, pages, false)
        symbolsPage = layoutInflater.inflate(R.layout.keyboard_symbols, pages, false)
        pages.addView(lettersPage)
        pages.addView(symbolsPage)
        symbolsPage?.visibility = View.GONE

        applySizeSettings()
        bindKeys(lettersPage)
        bindKeys(symbolsPage)
        updateSuggestions()

        return root
    }

    private fun applySizeSettings() {
        val heightDp = 52 * prefs.getInt("kb_height", 100) / 100
        val sideDp = prefs.getInt("kb_side", 0)
        val density = resources.displayMetrics.density
        val heightPx = (heightDp * density).toInt()
        val sidePx = (sideDp * density).toInt()

        for (page in listOf(lettersPage, symbolsPage)) {
            page?.setPadding(sidePx + (3 * density).toInt(), page?.paddingTop ?: 0,
                sidePx + (3 * density).toInt(), page?.paddingBottom ?: 0)
        }
        forEachView(lettersPage) { v -> if (v.tag != null) v.layoutParams?.height = heightPx }
        forEachView(symbolsPage) { v -> if (v.tag != null) v.layoutParams?.height = heightPx }
    }

    private fun pressFeedback(v: View) {
        if (prefs.getBoolean("key_sound", true)) {
            v.playSoundEffect(SoundEffectConstants.CLICK)
        }
        if (prefs.getBoolean("vibrate", true)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Bấm là ăn ngay ở ACTION_DOWN, không chờ nhả tay. isPressed giữ hiệu ứng lún. */
    private fun bindKey(tv: TextView, action: () -> Unit) {
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    pressFeedback(v)
                    action()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.isPressed = e.x >= 0 && e.x < v.width && e.y >= 0 && e.y < v.height
                    true
                }
                else -> {
                    v.isPressed = false
                    true
                }
            }
        }
        tv.setOnClickListener { action() } // đường a11y/performClick
    }

    private fun bindKeys(root: View?) {
        forEachView(root) { view ->
            val tag = view.tag as? String ?: return@forEachView
            val tv = view as? TextView ?: return@forEachView
            when {
                tag.startsWith("ch:") -> {
                    letterKeys.add(tv)
                    bindKey(tv) { handleCharacter(tag.removePrefix("ch:")) }
                }
                tag.startsWith("p:") -> {
                    bindKey(tv) { handlePunct(tag.removePrefix("p:")) }
                }
                tag == "fn:space" -> bindKey(tv) { handleSpace() }
                tag == "fn:enter" -> bindKey(tv) { handleEnter() }
                tag == "fn:del" -> bindDeleteKey(tv)
                tag == "fn:shift" -> {
                    shiftKey = tv
                    bindKey(tv) { toggleShift() }
                }
                tag == "fn:sym" -> bindKey(tv) { showPage(symbolsPage) }
                tag == "fn:abc" -> bindKey(tv) { showPage(lettersPage) }
            }
        }
    }

    private fun forEachView(view: View?, block: (View) -> Unit) {
        if (view == null) return
        block(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) forEachView(view.getChildAt(i), block)
        }
    }

    private fun bindDeleteKey(tv: TextView) {
        tv.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    pressFeedback(v)
                    handleDelete()
                    repeatHandler.postDelayed(deleteRepeat, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    repeatHandler.removeCallbacks(deleteRepeat)
                    true
                }
                else -> true
            }
        }
        tv.setOnClickListener { handleDelete() }
    }

    private fun showPage(page: View?) {
        lettersPage?.visibility = if (page === lettersPage) View.VISIBLE else View.GONE
        symbolsPage?.visibility = if (page === symbolsPage) View.VISIBLE else View.GONE
    }

    private fun toggleShift() {
        shiftOn = !shiftOn
        shiftAuto = false
        updateShiftUI()
    }

    private fun updateShiftUI() {
        for (key in letterKeys) {
            val base = (key.tag as String).removePrefix("ch:")
            key.text = if (shiftOn) base.uppercase() else base
        }
        shiftKey?.setTextColor(
            if (shiftOn) resources.getColor(R.color.kb_accent, theme)
            else resources.getColor(R.color.kb_text, theme)
        )
    }

    private fun handleCharacter(char: String) {
        lastAutoFix = null // gõ ký tự mới = chấp nhận bản sửa, hết hoàn tác
        val c = if (shiftOn) char.uppercase() else char
        if (shiftOn) {
            shiftOn = false
            shiftAuto = false
            updateShiftUI()
        }
        // Con trỏ sát một từ đã gõ (không có space) -> nối phím vào từ đó,
        // kiểu Unikey "bỏ dấu tự do": "hoan" + s -> "hoán", "hon" + w -> "hơn".
        if (currentComposingWord.isEmpty() && c.first().isLowerCase() && resumeWord(c)) return
        currentComposingWord.append(c)
        val transformed = telexEngine.transform(currentComposingWord.toString())
        currentInputConnection?.setComposingText(transformed, 1)
        updateSuggestions()
    }

    /** Chuỗi chữ cái liền trước con trỏ (rỗng nếu trước con trỏ là space/số). */
    private fun adjacentWordBeforeCursor(): String {
        val before = currentInputConnection?.getTextBeforeCursor(20, 0) ?: return ""
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        return before.substring(i).toString()
    }

    /** Từ đứng trước từ đang gõ — bỏ qua vùng composing và khoảng trắng;
     *  làm ngữ cảnh cho gợi ý/sửa lỗi thay vì chỉ nhớ từ trong session. */
    private fun contextWordBeforeCursor(): String {
        var before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString()
            ?: return lastCommittedWord
        if (currentComposingWord.isNotEmpty()) {
            val comp = telexEngine.transform(currentComposingWord.toString())
            if (before.endsWith(comp)) before = before.dropLast(comp.length)
        }
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        var j = i
        while (j > 0 && before[j - 1].isLetter()) j--
        return if (j < i) before.substring(j, i) else lastCommittedWord
    }

    /** 'w'/'z' áp thẳng lên từ đã commit; các phím khác kéo từ về vùng
     *  composing (bỏ tone khỏi buffer) rồi gõ tiếp như thường. */
    private fun resumeWord(c: String): Boolean {
        val word = adjacentWordBeforeCursor()
        if (word.isEmpty()) return false
        when (c[0]) {
            'w' -> telexEngine.applyW(word)?.let { return replaceAdjacentWord(word, it) }
            'z' -> telexEngine.stripTones(word).let {
                if (it != word) return replaceAdjacentWord(word, it)
            }
        }
        currentComposingWord.append(telexEngine.stripTones(word)).append(c)
        val transformed = telexEngine.transform(currentComposingWord.toString())
        val ic = currentInputConnection ?: return false
        ic.deleteSurroundingText(word.length, 0)
        ic.setComposingText(transformed, 1)
        updateSuggestions()
        return true
    }

    private fun replaceAdjacentWord(old: String, new: String): Boolean {
        val ic = currentInputConnection ?: return false
        ic.deleteSurroundingText(old.length, 0)
        ic.commitText(new, 1)
        lastCommittedWord = new
        updateSuggestions()
        return true
    }

    private fun handlePunct(p: String) {
        commitComposing()
        currentInputConnection?.commitText(p, 1)
        updateSuggestions()
        updateAutoShift()
    }

    /** Chốt từ đang gõ; nếu từ sai chính tả và có phương án sửa đủ gần
     *  (lệch đúng 1 ký tự, nằm trong từ điển) thì tự thay bằng từ đúng.
     *  Bản tự sửa được đánh dấu để ⌫/chạm candidate hoàn tác lại chữ đã gõ. */
    private fun commitComposing() {
        if (currentComposingWord.isEmpty()) return
        val prev = contextWordBeforeCursor()
        val raw = currentComposingWord.toString()
        val typed = telexEngine.transform(raw)
        val fixed = predictor.correction(typed, prev)
        val word = fixed ?: typed
        currentInputConnection?.commitText(word, 1)
        predictor.recordSequence(prev, word)
        lastCommittedWord = word
        lastAutoFix = if (fixed != null) AutoFix(word, typed, raw) else null
        currentComposingWord.clear()
    }

    private fun handleSpace() {
        commitComposing()
        currentInputConnection?.commitText(" ", 1)
        updateSuggestions()
        updateAutoShift()
    }

    private fun handleDelete() {
        if (currentComposingWord.isNotEmpty()) {
            currentComposingWord.deleteCharAt(currentComposingWord.length - 1)
            val transformed = telexEngine.transform(currentComposingWord.toString())
            if (transformed.isEmpty()) {
                currentInputConnection?.commitText("", 1)
            } else {
                currentInputConnection?.setComposingText(transformed, 1)
            }
        } else if (!revertAutoFix()) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            updateAutoShift()
        }
        updateSuggestions()
    }

    /** Số ký tự trước con trỏ thuộc về từ vừa bị sửa (kể cả space theo sau);
     *  0 = không đứng ngay sau từ đó -> không hoàn tác được. */
    private fun autoFixTail(fix: AutoFix): Int {
        val before = currentInputConnection
            ?.getTextBeforeCursor(fix.committed.length + 1, 0) ?: return 0
        return when {
            before.endsWith(fix.committed + " ") -> fix.committed.length + 1
            before.endsWith(fix.committed) -> fix.committed.length
            else -> 0
        }
    }

    /** ⌫ ngay sau auto-correct = hoàn tác: xóa từ đã sửa (và space vừa gõ),
     *  trả buffer phím thô về vùng composing để user tự sửa tiếp. Từ đã gõ
     *  được học vào từ điển nên app không sửa lại lần sau. */
    private fun revertAutoFix(): Boolean {
        val fix = lastAutoFix ?: return false
        lastAutoFix = null
        val tail = autoFixTail(fix)
        if (tail == 0) return false
        val ic = currentInputConnection ?: return false
        ic.deleteSurroundingText(tail, 0)
        currentComposingWord.append(fix.raw)
        ic.setComposingText(telexEngine.transform(fix.raw), 1)
        predictor.recordSequence(contextWordBeforeCursor(), fix.typed)
        return true
    }

    /** Chạm candidate "từ đã gõ" ngay sau auto-correct = đổi lại nguyên văn
     *  (giữ space sau từ), học từ đó để không sửa oan nữa. */
    private fun revertCommittedFix() {
        val fix = lastAutoFix ?: return
        lastAutoFix = null
        val tail = autoFixTail(fix)
        if (tail == 0) return
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(tail, 0)
        ic.commitText(if (tail > fix.committed.length) fix.typed + " " else fix.typed, 1)
        predictor.recordSequence(contextWordBeforeCursor(), fix.typed)
        lastCommittedWord = fix.typed
        updateSuggestions()
    }

    private fun handleEnter() {
        commitComposing()
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        )
        updateAutoShift()
    }

    /** Đang hiện phương án hoàn tác auto-fix thì chạm candidate = hoàn tác. */
    private fun onCandidateTap(tv: TextView?) {
        val fix = lastAutoFix
        if (fix != null && tv === candidate2 && tv?.text == fix.typed) {
            revertCommittedFix()
        } else {
            acceptSuggestion(tv?.text.toString())
        }
    }

    private fun acceptSuggestion(word: String) {
        if (word.isEmpty()) return
        lastAutoFix = null
        val prev = contextWordBeforeCursor()
        // commitText tự thay thế vùng composing nếu đang gõ dở
        currentInputConnection?.commitText("$word ", 1)
        predictor.recordSequence(prev, word)
        lastCommittedWord = word
        currentComposingWord.clear()
        updateSuggestions()
        updateAutoShift()
    }

    private fun updateSuggestions() {
        val ctx = contextWordBeforeCursor()
        if (currentComposingWord.isNotEmpty()) {
            // Đang gõ: giữa = bản sửa (nếu sai chính tả) hoặc từ hiện tại,
            // 2 bên = gợi ý hoàn thành (ưu tiên từ hay đi sau từ trước)
            val current = telexEngine.transform(currentComposingWord.toString())
            val completions = predictor.completions(current, ctx)
            val fix = predictor.correction(current, ctx)
            candidate1?.text = completions.getOrNull(0) ?: ""
            candidate2?.text = fix ?: current
            candidate3?.text = completions.getOrNull(1) ?: ""
        } else {
            // Từ vừa bị auto-correct: ô giữa hiện đúng từ user đã gõ,
            // chạm vào để khôi phục (hoặc bấm ⌫).
            val fix = lastAutoFix
            if (fix != null && autoFixTail(fix) > 0) {
                candidate1?.text = ""
                candidate2?.text = fix.typed
                candidate3?.text = ""
                return
            }
            // Đã chốt từ: gợi ý từ tiếp theo dựa vào từ ngay trước con trỏ
            val next = predictor.predictNext(ctx)
            candidate1?.text = next.getOrNull(1) ?: ""
            candidate2?.text = next.getOrNull(0) ?: ""
            candidate3?.text = next.getOrNull(2) ?: ""
        }
    }
}
