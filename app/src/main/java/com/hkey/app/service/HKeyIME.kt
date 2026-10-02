package com.hkey.app.service

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
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

    private var candidate1: TextView? = null
    private var candidate2: TextView? = null
    private var candidate3: TextView? = null

    private var lettersPage: View? = null
    private var symbolsPage: View? = null
    private var shiftOn = false
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

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) {
            currentComposingWord.clear()
            lastCommittedWord = ""
        }
        shiftOn = false
        // Inflate lại để settings (cao/rộng/rung) áp dụng ngay lần mở tiếp theo
        setInputView(onCreateInputView())
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)

        letterKeys.clear()
        shiftKey = null

        candidate1 = root.findViewById(R.id.candidate1)
        candidate2 = root.findViewById(R.id.candidate2)
        candidate3 = root.findViewById(R.id.candidate3)

        candidate1?.setOnClickListener { acceptSuggestion(candidate1?.text.toString()) }
        candidate2?.setOnClickListener { acceptSuggestion(candidate2?.text.toString()) }
        candidate3?.setOnClickListener { acceptSuggestion(candidate3?.text.toString()) }

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
        val c = if (shiftOn) char.uppercase() else char
        currentComposingWord.append(c)
        if (shiftOn) {
            shiftOn = false
            updateShiftUI()
        }
        val transformed = telexEngine.transform(currentComposingWord.toString())
        currentInputConnection?.setComposingText(transformed, 1)
        updateSuggestions()
    }

    private fun handlePunct(p: String) {
        commitComposing()
        currentInputConnection?.commitText(p, 1)
        updateSuggestions()
    }

    /** Chốt từ đang gõ nguyên trạng — không bao giờ tự thay bằng từ khác. */
    private fun commitComposing() {
        if (currentComposingWord.isEmpty()) return
        val word = telexEngine.transform(currentComposingWord.toString())
        currentInputConnection?.commitText(word, 1)
        predictor.recordSequence(lastCommittedWord, word)
        lastCommittedWord = word
        currentComposingWord.clear()
    }

    private fun handleSpace() {
        commitComposing()
        currentInputConnection?.commitText(" ", 1)
        updateSuggestions()
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
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        updateSuggestions()
    }

    private fun handleEnter() {
        commitComposing()
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        )
    }

    private fun acceptSuggestion(word: String) {
        if (word.isEmpty()) return
        // commitText tự thay thế vùng composing nếu đang gõ dở
        currentInputConnection?.commitText("$word ", 1)
        predictor.recordSequence(lastCommittedWord, word)
        lastCommittedWord = word
        currentComposingWord.clear()
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (currentComposingWord.isNotEmpty()) {
            // Đang gõ: giữa = từ hiện tại, 2 bên = gợi ý hoàn thành
            val current = telexEngine.transform(currentComposingWord.toString())
            val completions = predictor.completions(current)
            candidate1?.text = completions.getOrNull(1) ?: ""
            candidate2?.text = completions.getOrNull(0) ?: current
            candidate3?.text = completions.getOrNull(2) ?: ""
        } else {
            // Đã chốt từ: giữa = bản sửa (nếu từ có vẻ sai), 2 bên = từ tiếp theo
            val next = predictor.predictNext(lastCommittedWord)
            val fix = predictor.correction(lastCommittedWord, null)
            candidate1?.text = next.getOrNull(1) ?: ""
            candidate2?.text = fix ?: next.getOrNull(0) ?: ""
            candidate3?.text = next.getOrNull(2) ?: ""
        }
    }
}
