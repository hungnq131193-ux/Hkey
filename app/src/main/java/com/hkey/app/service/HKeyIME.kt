package com.hkey.app.service

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hkey.app.BuildConfig
import com.hkey.app.R
import com.hkey.app.engine.ContextPredictor
import com.hkey.app.engine.EngineOptions
import com.hkey.app.engine.FieldMode
import com.hkey.app.engine.ImeEngine
import com.hkey.app.engine.ImeMethod
import com.hkey.app.engine.LearningStore
import com.hkey.app.engine.TelexEngine
import com.hkey.app.engine.VniEngine
import com.hkey.app.engine.TextContext
import com.hkey.app.engine.ViModel
import com.hkey.app.engine.ViSyllable
import com.hkey.app.ui.KeyboardView
import com.hkey.app.ui.KbKey
import java.io.File

class HKeyIME : InputMethodService() {

    private var engine: ImeEngine = TelexEngine()
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

    private var kbView: KeyboardView? = null
    private var shiftOn = false
    private var shiftAuto = false
    private var shiftLocked = false // 2 chạm Shift = caps lock (3.x)
    private var lastShiftTap = 0L
    private var autoCap = false

    private var inputView: View? = null
    private var appliedKbHeight = -1
    private var appliedKbSide = -1
    private var appliedNumRow = false
    private var appliedDark = true
    private var optSound = true
    private var optVibrate = true
    private var contextCache: Pair<String, String>? = null // (từ trước, từ trước nữa)
    private var currentInputType = 0 // inputType của ô đang focus (cho getCursorCapsMode)
    private var currentImeOptions = 0 // imeOptions ô đang focus (action Enter — 1.5)
    private var tokenGlued = false // từ đang gõ dính sau . @ / : — url/email/ip (1.1)
    private var rawMode = false // ô nhạy cảm: gõ thẳng, không Telex/gợi ý/học (B1,B2)
    private var noSuggest = false // NO_SUGGESTIONS: Telex vẫn gõ, tắt gợi ý+sửa (1.4)
    private var noLearning = false // NO_PERSONALIZED_LEARNING/NO_SUGGESTIONS: không học (1.4)
    private var vietMode = true // EN/VI qua phím lang (2.x); EN = gõ thẳng
    private var macros = emptyMap<String, String>() // gõ tắt "k=v" mỗi dòng (2.x)
    private var optDoubleSpace = true // 3.x: space-space nhanh -> ". "
    private var lastSpaceTap = 0L
    @Volatile private var destroyed = false

    private val repeatHandler = Handler(Looper.getMainLooper())

    private val prefs get() = getSharedPreferences("hkey_settings", Context.MODE_PRIVATE)
    private val learnedFile get() = File(filesDir, "learned_data.tsv")
    private val learnedStore get() = LearningStore(learnedFile)
    private var learnedDirty = false
    @Volatile private var learnedGen = 0 // tăng khi "xóa dữ liệu học" — hủy ghi đang bay (1.10)
    private val saveLearned = Runnable { persistLearned() }

    override fun onCreate() {
        super.onCreate()
        // Nạp từ điển + mô hình n-gram + dữ liệu học ở thread nền; merge trên
        // main rồi dựng chỉ mục nền một lần — không chặn phím đầu (S1, G1-G3).
        Thread {
            val words = resources.openRawResource(R.raw.vi_dict)
                .bufferedReader().use { it.lineSequence().toList() }
            val model = try {
                java.util.zip.GZIPInputStream(resources.openRawResource(R.raw.vi_model))
                    .bufferedReader().use { ViModel.parse(it.lineSequence()) }
            } catch (e: Exception) {
                null // thiếu model -> chạy với từ điển cơ bản
            }
            val learned = learnedStore.load()
            repeatHandler.post {
                if (destroyed) return@post
                predictor.addWords(words)
                if (model != null) {
                    predictor.loadModel(
                        model.unigrams, model.bigrams, model.trigrams, model.bos
                    )
                }
                if (learned != null) {
                    predictor.importLearned(learned.words, learned.bigrams)
                }
                val (snapshot, version) = predictor.snapshotForIndex()
                Thread {
                    val idx = predictor.buildIndexFrom(snapshot)
                    repeatHandler.post {
                        if (!destroyed) predictor.installIndex(idx, version)
                    }
                }.start()
            }
        }.start()
    }

    override fun onDestroy() {
        destroyed = true
        kbView?.release() // dọn repeat/popup của view
        persistLearned() // ghi theo lô khi service dừng (3.6)
        super.onDestroy()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        kbView?.release() // 1.10: ẩn phím -> dừng nhấn giữ ⌫, đóng popup
        persistLearned() // mất focus -> ghi luôn nếu bẩn
        super.onFinishInputView(finishingInput)
    }

    /** 1.10: không cho phép fullscreen/extract mode — bàn phím luôn ở đáy
     *  màn hình kể cả landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** Đánh dấu dữ liệu học bẩn + hẹn ghi sau 15s (batch, không ghi mỗi phím). */
    private fun markLearnedDirty() {
        learnedDirty = true
        repeatHandler.removeCallbacks(saveLearned)
        repeatHandler.postDelayed(saveLearned, 15_000)
    }

    private fun persistLearned() {
        if (!learnedDirty) return
        learnedDirty = false
        predictor.boundLearned(LearningStore.MAX_LEARNED_WORDS)
        val (words, bis) = predictor.exportLearned()
        val capped = if (bis.size > LearningStore.MAX_USER_BIGRAMS)
            bis.sortedByDescending { it.third }.take(LearningStore.MAX_USER_BIGRAMS)
        else bis
        val store = learnedStore
        val gen = learnedGen
        // 1.10: "xóa dữ liệu học" xảy ra giữa chừng -> hủy ghi snapshot cũ
        Thread { if (gen == learnedGen) store.save(LearningStore.Data(words, capped)) }.start()
    }

    /** Nút "Xóa dữ liệu học" ở MainActivity đặt cờ; IME tiêu thụ ở lần focus
     *  ô / hiện bàn phím kế tiếp (3.6). */
    private fun consumeLearningCleared() {
        if (prefs.getBoolean("learning_cleared", false)) {
            prefs.edit().remove("learning_cleared").apply()
            predictor.clearLearned()
            learnedGen++ // 1.10: vô hiệu mọi ghi learned đang chạy nền
            learnedStore.clear()
            learnedDirty = false
            repeatHandler.removeCallbacks(saveLearned)
        }
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        rawMode = FieldMode.isRaw(info.inputType)
        noSuggest = FieldMode.noSuggestions(info.inputType)
        noLearning = noSuggest ||
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        currentInputType = info.inputType
        currentImeOptions = info.imeOptions
        computeAutoCap(info)
        consumeLearningCleared() // tiêu thụ sớm ngay khi focus ô, không chờ view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        val t0 = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        super.onStartInputView(info, restarting)
        currentInputType = info.inputType
        currentImeOptions = info.imeOptions
        // 2.x: dựng lại engine theo prefs (kiểu gõ + kiểu dấu + spell-check)
        val opts = EngineOptions(
            method = ImeMethod.fromPref(prefs.getString("ime_method", null)),
            newToneStyle = prefs.getBoolean("tone_new", true),
            spellCheckTone = prefs.getBoolean("spell_check", true)
        )
        engine = if (opts.method == ImeMethod.VNI) VniEngine(opts) else TelexEngine(opts)
        macros = parseMacros(prefs.getString("macros", "") ?: "")
        if (!restarting) {
            currentComposingWord.clear()
            lastCommittedWord = ""
            lastAutoFix = null
            contextCache = null
            tokenGlued = false
        }
        computeAutoCap(info)
        shiftOn = false
        shiftAuto = false
        shiftLocked = false
        consumeLearningCleared()
        optSound = prefs.getBoolean("key_sound", true)
        optVibrate = prefs.getBoolean("vibrate", true)
        optDoubleSpace = prefs.getBoolean("double_space", true)
        // Chỉ inflate lại khi đổi settings hoặc chưa có view — S2.
        val kh = prefs.getInt("kb_height", 100)
        val ks = prefs.getInt("kb_side", 0)
        val kn = prefs.getBoolean("number_row", false)
        val kd = prefs.getBoolean("dark_theme", true)
        if (inputView == null || kh != appliedKbHeight || ks != appliedKbSide ||
            kn != appliedNumRow || kd != appliedDark
        ) {
            appliedNumRow = kn
            appliedDark = kd
            setInputView(onCreateInputView())
        } else {
            kbView?.showPage(KeyboardView.Page.LETTERS)
            updateSuggestions()
        }
        updateAutoShift()
        if (BuildConfig.DEBUG) Log.d("HKeyIME", "onStartInputView ${(System.nanoTime() - t0) / 1_000_000.0} ms")
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

    /** Học chuỗi từ — bỏ qua khi app đặt NO_PERSONALIZED_LEARNING hoặc
     *  NO_SUGGESTIONS (1.4). */
    private fun learn(prev: String, word: String) {
        if (noLearning) return
        predictor.recordSequence(prev, word)
        markLearnedDirty()
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
        // 1.6: update do chính setComposingText của ta gây ra (con trỏ nằm đúng
        // cuối vùng composing) -> ngữ cảnh trước từ không đổi, giữ cache.
        val selfEdit = currentComposingWord.isNotEmpty() &&
            newSelStart == newSelEnd && candidatesStart >= 0 && newSelStart == candidatesEnd
        if (currentComposingWord.isNotEmpty() && !selfEdit) {
            currentInputConnection?.finishComposingText()
            currentComposingWord.clear()
            updateSuggestions()
        }
        if (!selfEdit) contextCache = null // con trỏ/ngữ cảnh đổi thật mới vô hiệu
        updateAutoShift()
    }

    /** Viết hoa đầu câu. Nguồn chính: getCursorCapsMode — Android tự tôn trọng
     *  cờ app đặt (CAP_SENTENCES/WORDS/CHARACTERS) và luật dấu cách (1.1).
     *  Ô không đặt CAP_SENTENCES (text trơn): tự soi — dấu . ! ? … phải có
     *  khoảng trắng theo sau mới là kết câu ("hu.io.vn" không bật hoa).
     *  Bật/tắt hai chiều: shift tự động tắt khi ngữ cảnh hết cần hoa;
     *  shift do người dùng bấm tay không bị ghi đè. */
    private fun updateAutoShift() {
        if (!autoCap || currentComposingWord.isNotEmpty() || shiftLocked) return
        val ic = currentInputConnection ?: return
        val caps = try { ic.getCursorCapsMode(currentInputType) } catch (e: Exception) { 0 }
        val wantCap = caps != 0 ||
            (currentInputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES == 0 &&
                TextContext.sentenceBoundary(ic.getTextBeforeCursor(32, 0)))
        val newShift = wantCap || (shiftOn && !shiftAuto)
        if (newShift != shiftOn || wantCap != shiftAuto) {
            shiftOn = newShift
            shiftAuto = wantCap
            updateShiftUI()
        }
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)

        // 1.10: targetSdk 35 ép edge-to-edge — độn đáy bằng thanh điều hướng
        // gesture/3-button để hàng phím cuối không bị che.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            if (v.paddingBottom != nav.bottom) {
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, nav.bottom)
            }
            insets
        }

        candidate1 = root.findViewById(R.id.candidate1)
        candidate2 = root.findViewById(R.id.candidate2)
        candidate3 = root.findViewById(R.id.candidate3)

        candidate1?.setOnClickListener { onCandidateTap(candidate1) }
        candidate2?.setOnClickListener { onCandidateTap(candidate2) }
        candidate3?.setOnClickListener { onCandidateTap(candidate3) }

        // 3.x: bàn phím tự vẽ — hit theo ô không rớt khe, trượt/đa chạm,
        // nhấn giữ ra phụ, giữ ⌫ lặp, vuốt space dời con trỏ.
        val kh = prefs.getInt("kb_height", 100)
        val ks = prefs.getInt("kb_side", 0)
        appliedKbHeight = kh
        appliedKbSide = ks
        val kb = KeyboardView(this).apply {
            configure(kh, ks, appliedDark, appliedNumRow)
            soundEnabled = optSound
            vibrateEnabled = optVibrate
            langVi = vietMode
            shifted = shiftOn
            capsLocked = shiftLocked
            onKey = { dispatchKey(it) }
            onSpaceSwipe = { swipeCursor(it) }
        }
        kbView = kb
        root.findViewById<FrameLayout>(R.id.kb_pages).addView(kb)

        updateSuggestions()
        inputView = root
        return root
    }

    /** Điều phối mọi phím từ KeyboardView theo tag. */
    private fun dispatchKey(k: KbKey) {
        when {
            k.tag.startsWith("ch:") -> handleCharacter(k.tag.removePrefix("ch:"))
            k.tag.startsWith("p:") -> handlePunct(k.tag.removePrefix("p:"))
            k.tag.startsWith("tx:") -> commitLiteral(k.tag.substring(3))
            k.tag == "fn:space" -> handleSpace()
            k.tag == "fn:enter" -> handleEnter()
            k.tag == "fn:del" -> handleDelete()
            k.tag == "fn:shift" -> toggleShift()
            k.tag == "fn:sym" -> kbView?.showPage(KeyboardView.Page.SYMBOLS)
            k.tag == "fn:abc" -> kbView?.showPage(KeyboardView.Page.LETTERS)
            k.tag == "fn:emoji" -> kbView?.showPage(KeyboardView.Page.EMOJI)
            k.tag == "fn:lang" -> toggleLang()
            k.tag == "fn:ime" -> switchToNextInputMethod(false) // 3.x
            k.tag == "fn:paste" -> pasteClipboard() // 3.x
        }
    }

    /** Vuốt trên phím cách -> dời con trỏ (3.x). Đang gõ dở thì chốt trước. */
    private fun swipeCursor(dir: Int) {
        if (currentComposingWord.isNotEmpty()) commitComposing()
        sendDownUpKeyEvents(
            if (dir > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        )
        contextCache = null
        updateAutoShift()
    }

    /** 3.x: phím 📋 dán nội dung clipboard (nếu là text). */
    private fun pasteClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val t = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!t.isNullOrEmpty()) {
                commitComposing()
                currentInputConnection?.commitText(t, 1)
                contextCache = null
                updateSuggestions()
                updateAutoShift()
            }
        }
    }

    /** tx:/emoji/ký tự phụ: commit nguyên văn, không qua engine (3.x). */
    private fun commitLiteral(s: String) {
        if (shiftOn && !shiftLocked) {
            shiftOn = false
            shiftAuto = false
            updateShiftUI()
        }
        commitComposing()
        currentInputConnection?.commitText(s, 1)
        contextCache = null
        updateSuggestions()
        updateAutoShift()
    }

    /** Shift: chạm 1 = hoa một chữ; chạm nhanh lần 2 = caps lock; chạm khi
     *  đang lock = tắt hẳn (3.x). */
    private fun toggleShift() {
        val now = android.os.SystemClock.uptimeMillis()
        when {
            shiftLocked -> {
                shiftOn = false
                shiftLocked = false
            }
            shiftOn && now - lastShiftTap < 400 -> shiftLocked = true
            else -> shiftOn = !shiftOn
        }
        lastShiftTap = now
        shiftAuto = false
        updateShiftUI()
    }

    /** Phím EN/VI: EN gõ thẳng không Telex/gợi ý; VI như thường (2.x). */
    private fun toggleLang() {
        commitComposing()
        vietMode = !vietMode
        kbView?.langVi = vietMode
        updateSuggestions()
    }

    /** "k=v" mỗi dòng -> map gõ tắt; khóa khớp phím thô, không phân biệt hoa. */
    private fun parseMacros(s: String): Map<String, String> =
        s.lines().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null
            else it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim()
        }.toMap()

    private fun updateShiftUI() {
        kbView?.shifted = shiftOn
        kbView?.capsLocked = shiftLocked
    }

    private fun handleCharacter(char: String) {
        val t0 = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        lastAutoFix = null // gõ ký tự mới = chấp nhận bản sửa, hết hoàn tác
        val c = if (shiftOn) char.uppercase() else char
        if (rawMode || !vietMode) { // EN mode: gõ thẳng, không Telex/gợi ý (2.x)
            consumeShift()
            currentInputConnection?.commitText(c, 1)
            return
        }
        consumeShift() // caps lock (shiftLocked) thì không tự tắt
        // Con trỏ sát một từ đã gõ (không có space) -> nối phím vào từ đó,
        // kiểu Unikey "bỏ dấu tự do": "hoan" + s -> "hoán", "hon" + w -> "hơn".
        if (currentComposingWord.isEmpty() && c.first().isLowerCase() && resumeWord(c)) return
        currentComposingWord.append(c)
        val transformed = engine.transform(currentComposingWord.toString())
        currentInputConnection?.setComposingText(transformed, 1)
        updateSuggestions()
        if (BuildConfig.DEBUG) Log.d("HKeyIME", "handleCharacter ${(System.nanoTime() - t0) / 1_000} us")
    }

    /** Tắt shift 1 lần sau khi gõ ký tự; caps lock thì giữ nguyên (3.x). */
    private fun consumeShift() {
        if (shiftOn && !shiftLocked) {
            shiftOn = false
            shiftAuto = false
            updateShiftUI()
        }
    }

    /** Chuỗi chữ cái liền trước con trỏ (rỗng nếu trước con trỏ là space/số). */
    private fun adjacentWordBeforeCursor(): String {
        val before = currentInputConnection?.getTextBeforeCursor(20, 0) ?: return ""
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        return before.substring(i).toString()
    }

    /** Ngữ cảnh không đổi trong lúc gõ một từ — cache để không gọi
     *  getTextBeforeCursor (liên tiến trình) mỗi ký tự (S3). */
    private fun contextWordBeforeCursor(): String = contextPairBeforeCursor().first

    private fun contextPairBeforeCursor(): Pair<String, String> =
        contextCache ?: computeContextPair().also { contextCache = it }

    private fun computeContextPair(): Pair<String, String> {
        var before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString()
        if (before == null) {
            tokenGlued = false
            return lastCommittedWord to ""
        }
        if (currentComposingWord.isNotEmpty()) {
            val comp = engine.transform(currentComposingWord.toString())
            if (before.endsWith(comp)) before = before.dropLast(comp.length)
        }
        tokenGlued = TextContext.gluedToken(before)
        return TextContext.lastTwo(before, lastCommittedWord)
    }

    /** 'w'/'z' áp thẳng lên từ đã commit; các phím khác kéo từ về vùng
     *  composing (bỏ tone khỏi buffer) rồi gõ tiếp như thường. */
    private fun resumeWord(c: String): Boolean {
        val word = adjacentWordBeforeCursor()
        if (word.isEmpty()) return false
        when (c[0]) {
            'w' -> engine.applyW(word)?.let { return replaceAdjacentWord(word, it) }
            'z' -> engine.stripTones(word).let {
                if (it != word) return replaceAdjacentWord(word, it)
            }
        }
        // 1.8: giữ dấu sẵn có của từ cũ khi gõ tiếp; chỉ bóc tone khi phím
        // mới chính là phím dấu (gõ 's' sau "việt" -> "viết" vẫn đè tone được).
        val base = if (c[0].lowercaseChar() in "sfrxj") engine.stripTones(word) else word
        currentComposingWord.append(base).append(c)
        val transformed = engine.transform(currentComposingWord.toString())
        contextCache = null // từ kề vừa vào buffer — ngữ cảnh phải dời lên trước nó
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(word.length, 0)
            ic.setComposingText(transformed, 1)
        } finally {
            ic.endBatchEdit()
        }
        updateSuggestions()
        return true
    }

    private fun replaceAdjacentWord(old: String, new: String): Boolean {
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(old.length, 0)
            ic.commitText(new, 1)
        } finally {
            ic.endBatchEdit()
        }
        lastCommittedWord = new
        contextCache = null
        updateSuggestions()
        return true
    }

    private fun handlePunct(p: String) {
        // VNI: chữ số trên trang symbols là phím dấu -> vào buffer (2.x)
        if (vietMode && engine is VniEngine && p.length == 1 && p[0].isDigit()) {
            handleCharacter(p)
            return
        }
        commitComposing()
        currentInputConnection?.commitText(p, 1)
        contextCache = null
        updateSuggestions()
        updateAutoShift()
    }

    /** Chốt từ đang gõ; nếu từ sai chính tả và có phương án sửa đủ gần
     *  (lệch đúng 1 ký tự, nằm trong từ điển) thì tự thay bằng từ đúng.
     *  Bản tự sửa được đánh dấu để ⌫/chạm candidate hoàn tác lại chữ đã gõ. */
    private fun commitComposing() {
        if (currentComposingWord.isEmpty()) return
        val (prev, prev2) = contextPairBeforeCursor()
        val raw = currentComposingWord.toString()
        val typed = engine.transform(raw)
        // 3.5: không sửa từ viết hoa giữa câu (tên riêng); từ có số và từ
        // ngắn đã chặn trong correction().
        val isProperNoun = prev.isNotEmpty() && typed.any { it.isUpperCase() }
        // 1.3: kết quả không phải âm tiết VN (tiếng Anh/mã/URL) -> về phím thô
        val typedWord = if (ViSyllable.restorable(raw, typed)) raw else typed
        // 2.x: macro gõ tắt khớp phím thô -> bung trực tiếp, không qua sửa
        val expanded = macros[raw.lowercase()]
        // 1.1: mảng trong url/email/ip ("io" trong "hu.io.vn") không sửa, không học.
        // 1.4: ô NO_SUGGESTIONS không tự sửa.
        val fixed = if (expanded != null || isProperNoun || tokenGlued ||
            typedWord === raw || noSuggest
        ) null
            else predictor.correction(typed, prev, prev2)
            // 1.2: bản sửa trả chữ thường — áp lại kiểu hoa của từ đã gõ
            ?.let { TextContext.matchCase(typed, it) }
        val word = expanded ?: fixed ?: typedWord
        currentInputConnection?.commitText(word, 1)
        if (!tokenGlued) learn(prev, word)
        lastCommittedWord = word
        lastAutoFix = if (fixed != null) AutoFix(word, typed, raw) else null
        currentComposingWord.clear()
        contextCache = null
    }

    private fun handleSpace() {
        commitComposing()
        val ic = currentInputConnection
        val now = android.os.SystemClock.uptimeMillis()
        // 3.x: 2 lần space nhanh sau một từ -> ". " + bật viết hoa đầu câu
        if (optDoubleSpace && now - lastSpaceTap < 600 && ic != null) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 &&
                before[0].isLetter() && before[1] == ' '
            ) {
                ic.beginBatchEdit()
                try {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                } finally {
                    ic.endBatchEdit()
                }
                lastSpaceTap = 0L
                contextCache = null
                updateSuggestions()
                updateAutoShift()
                return
            }
        }
        lastSpaceTap = now
        ic?.commitText(" ", 1)
        contextCache = null
        updateSuggestions()
        updateAutoShift()
    }

    private fun handleDelete() {
        if (rawMode) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            return
        }
        if (currentComposingWord.isNotEmpty()) {
            // 1.7: xóa 1 ký tự HIỂN THỊ ("việt"⌫="việ"), không phải phím thô cuối
            val r = engine.dropLastDisplayChar(currentComposingWord.toString())
            currentComposingWord.clear()
            currentComposingWord.append(r)
            val transformed = engine.transform(r)
            if (transformed.isEmpty()) {
                currentInputConnection?.commitText("", 1)
            } else {
                currentInputConnection?.setComposingText(transformed, 1)
            }
        } else if (!revertAutoFix()) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            contextCache = null
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
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(tail, 0)
            currentComposingWord.append(fix.raw)
            ic.setComposingText(engine.transform(fix.raw), 1)
        } finally {
            ic.endBatchEdit()
        }
        contextCache = null
        learn(contextWordBeforeCursor(), fix.typed)
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
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(tail, 0)
            ic.commitText(if (tail > fix.committed.length) fix.typed + " " else fix.typed, 1)
        } finally {
            ic.endBatchEdit()
        }
        contextCache = null
        learn(contextWordBeforeCursor(), fix.typed)
        lastCommittedWord = fix.typed
        updateSuggestions()
    }

    private fun handleEnter() {
        commitComposing()
        val ic = currentInputConnection ?: return
        // 1.5: ô một dòng đặt action (send/search/go/done) -> gọi action app,
        // app không xử lý mới mô phỏng phím Enter (cả DOWN lẫn UP).
        val action = currentImeOptions and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            currentImeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
            ic.performEditorAction(action)
        ) {
            contextCache = null
            updateAutoShift()
            return
        }
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        contextCache = null
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
        // 1.2: gợi ý trả chữ thường — áp lại kiểu hoa đang gõ / shift đầu câu
        val cased = when {
            currentComposingWord.isNotEmpty() ->
                TextContext.matchCase(
                    engine.transform(currentComposingWord.toString()), word
                )
            shiftOn -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }
        // commitText tự thay thế vùng composing nếu đang gõ dở
        currentInputConnection?.commitText("$cased ", 1)
        learn(prev, word)
        lastCommittedWord = cased
        currentComposingWord.clear()
        consumeShift()
        contextCache = null
        updateSuggestions()
        updateAutoShift()
    }

    private fun updateSuggestions() {
        if (rawMode || noSuggest || !vietMode) {
            candidate1?.text = ""
            candidate2?.text = ""
            candidate3?.text = ""
            return
        }
        val (ctx, ctx2) = contextPairBeforeCursor()
        if (currentComposingWord.isNotEmpty()) {
            // Đang gõ: giữa = bản sửa (nếu sai chính tả) hoặc từ hiện tại,
            // 2 bên = gợi ý hoàn thành (ưu tiên từ hay đi sau từ trước)
            val current = engine.transform(currentComposingWord.toString())
            // 1.1: mảng trong url/email/ip -> không gợi ý, không sửa
            val completions = if (tokenGlued) emptyList()
                else predictor.completions(current, ctx, ctx2)
            // 1.3: kết quả không phải âm tiết VN -> đề nghị phím thô ("text")
            val restore = if (tokenGlued) null else currentComposingWord.toString()
                .takeIf { ViSyllable.restorable(it, current) }
            // 3.4: chỉ đề nghị sửa khi từ đang gõ không phải tiền tố hợp lệ
            val fix = restore
                ?: if (tokenGlued || predictor.isPrefixOfKnownWord(current)) null
                else predictor.correction(current, ctx, ctx2)
            // 1.2: hiện gợi ý đúng kiểu hoa để chạm vào ăn ngay
            candidate1?.text = completions.getOrNull(0)
                ?.let { TextContext.matchCase(current, it) } ?: ""
            candidate2?.text = fix?.let { TextContext.matchCase(current, it) } ?: current
            candidate3?.text = completions.getOrNull(1)
                ?.let { TextContext.matchCase(current, it) } ?: ""
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
            // 1.1: đang đứng giữa url/email/ip -> không gợi ý từ tiếp theo
            if (tokenGlued) {
                candidate1?.text = ""
                candidate2?.text = ""
                candidate3?.text = ""
                return
            }
            // Đã chốt từ: gợi ý từ tiếp theo theo ngữ cảnh 2 từ (3.3);
            // shift đang bật (đầu câu) thì hiện hoa luôn (1.2)
            val next = predictor.predictNext(ctx, ctx2)
                .map { if (shiftOn) it.replaceFirstChar(Char::uppercase) else it }
            candidate1?.text = next.getOrNull(1) ?: ""
            candidate2?.text = next.getOrNull(0) ?: ""
            candidate3?.text = next.getOrNull(2) ?: ""
        }
    }
}
