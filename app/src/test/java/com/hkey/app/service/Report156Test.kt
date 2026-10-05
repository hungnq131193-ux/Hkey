package com.hkey.app.service

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkey.app.engine.EngineOptions
import com.hkey.app.engine.ImeMethod
import com.hkey.app.engine.TelexEngine
import com.hkey.app.settings.SettingsKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Report156Test {

    private class QueuePoster {
        private val q = ArrayDeque<Runnable>()
        val post: (Runnable) -> Unit = { q.addLast(it) }
        fun pump(n: Int = Int.MAX_VALUE) {
            var k = n
            while (k-- > 0 && q.isNotEmpty()) q.removeFirst().run()
        }
    }

    @After fun resetWorker() { HKeyIME.workerPosterOverride = null }

    private fun harness(
        qp: QueuePoster? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        imeOptions: Int = 0,
        initialText: String = "",
        prefsSetup: (android.content.SharedPreferences.Editor.() -> Unit)? = null
    ): ImeHarness {
        qp?.let { HKeyIME.workerPosterOverride = it.post }
        val h = ImeHarness(
            inputType = inputType, imeOptions = imeOptions,
            initialText = initialText
        ) {
            putBoolean(SettingsKeys.AUTO_CAP, false)
            prefsSetup?.let { it() }
        }
        qp?.pump()
        h.idle()
        return h
    }

    private fun candText(ime: HKeyIME, name: String): String =
        (HKeyIME::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(ime) as? android.widget.TextView)
            ?.text?.toString() ?: ""

    private fun invoke1(ime: HKeyIME, name: String, arg: String) {
        HKeyIME::class.java.getDeclaredMethod(name, String::class.java)
            .apply { isAccessible = true }.invoke(ime, arg)
    }

    private fun pred(ime: HKeyIME) =
        HKeyIME::class.java.getDeclaredField("predictor")
            .apply { isAccessible = true }.get(ime)
            as com.hkey.app.engine.ContextPredictor

    @Test
    fun englishWords_stayUnchanged() {
        val qp = QueuePoster()
        val h = harness(qp)
        val words = listOf("user", "key", "users", "keyboard", "User", "Key")
        var expect = ""
        for (w in words) {
            h.type("$w ")
            qp.pump(); h.idle()
            expect += "$w "
            assertEquals(expect, h.text())
        }
    }

    @Test
    fun englishWords_afterPreviousWord() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("tot")
        qp.pump(); h.idle()
        h.type(" user key ")
        qp.pump(); h.idle()
        assertEquals("tot user key ", h.text())
    }

    @Test
    fun englishWords_punctuation() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("user.key!")
        qp.pump(); h.idle()
        assertEquals("user.key!", h.text())
    }

    @Test
    fun englishWord_candidateTapKeepsRaw() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("user")
        qp.pump(); h.idle()
        invoke1(h.ime, "acceptSuggestion", "user")
        qp.pump(); h.idle()
        assertEquals("user ", h.text())
    }

    @Test
    fun learnedRawStillNotAutocorrected() {
        val qp = QueuePoster()
        java.io.File(
            org.robolectric.RuntimeEnvironment.getApplication().filesDir,
            "learned_data.tsv"
        ).writeText("hkey-learned-v2\nw\tusser\t5\t123456\n")
        try {
            val h = harness(qp)
            h.type("user ")
            qp.pump(); h.idle()
            assertEquals("user ", h.text())
        } finally {
            java.io.File(
                org.robolectric.RuntimeEnvironment.getApplication().filesDir,
                "learned_data.tsv"
            ).delete()
        }
    }

    @Test
    fun learnedInvalidCandidateNotOffered() {
        val qp = QueuePoster()
        val h = harness(qp)
        val p = pred(h.ime)
        p.recordSequence("", "usser")
        p.recordSequence("", "usser")
        assertNull(p.typoFix("user", null))
    }

    @Test
    fun enterSend_pendingFix_appClearsInsideAction() {
        val qp = QueuePoster()
        val h = harness(qp, imeOptions = EditorInfo.IME_ACTION_SEND)
        h.type("khoogn ")
        h.conn.onEditorAction = {
            h.conn.text.clear()
            h.conn.finishComposingText()
            h.conn.setSelection(0, 0)
        }
        h.type("\n")
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        qp.pump(); h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun enterSend_autoCapOn_silentClearAfterReturn() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness(
            initialText = "ok ", imeOptions = EditorInfo.IME_ACTION_SEND
        )
        qp.pump(); h.idle()
        h.type("khoogn ")
        h.type("\n")
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        h.idle()
        assertTrue(h.text().length == 1)
    }

    @Test
    fun enterSend_autoCapOn_emptyFieldSilentClear() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness(
            initialText = "", imeOptions = EditorInfo.IME_ACTION_SEND
        )
        qp.pump(); h.idle()
        h.type("khoogn ")
        h.type("\n")
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        h.idle()
        assertTrue(h.text().length == 1)
    }

    @Test
    fun enterSend_pendingFix_appClearsAfterReturn_withSel() {
        val qp = QueuePoster()
        val h = harness(qp, imeOptions = EditorInfo.IME_ACTION_SEND)
        h.type("khoogn ")
        h.type("\n")
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        h.notifySel(7, 7, 0, 0)
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        qp.pump(); h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun enterSend_pendingFix_appliedThenAppClears_synthetic() {
        val qp = QueuePoster()
        val h = harness(qp, imeOptions = EditorInfo.IME_ACTION_SEND)
        h.type("khoogn ")
        h.type("\n")
        qp.pump(); h.idle()
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        h.notifySel(7, 7, 0, 0)
        h.idle()
        assertEquals("", h.text())
        h.type("a")
        h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun enterSend_pendingFix_earlyNewKeyAfterClear() {
        val qp = QueuePoster()
        val h = harness(qp, imeOptions = EditorInfo.IME_ACTION_SEND)
        h.type("khoogn ")
        h.conn.onEditorAction = {
            h.conn.text.clear()
            h.conn.finishComposingText()
            h.conn.setSelection(0, 0)
        }
        h.type("\n")
        h.type("a")
        qp.pump(); h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun enterSend_ownButtonSilentClear_pendingFix() {
        val qp = QueuePoster()
        val h = harness(qp, imeOptions = EditorInfo.IME_ACTION_SEND)
        h.type("khoogn ")
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        qp.pump(); h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun enterMultiline_pendingFixAfterSpace() {
        val qp = QueuePoster()
        val h = harness(
            qp,
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        h.type("khoogn ")
        h.type("\n")
        qp.pump(); h.idle()
        assertEquals("khoogn \n", h.text())
        h.type("a")
        qp.pump(); h.idle()
        assertEquals("khoogn \na", h.text())
    }

    @Test
    fun enterSend_vietnameseWord_appClearsInsideAction() {
        val qp = QueuePoster()
        val h = harness(qp, imeOptions = EditorInfo.IME_ACTION_SEND)
        h.type("vieetj ")
        h.conn.onEditorAction = {
            h.conn.text.clear()
            h.conn.finishComposingText()
            h.conn.setSelection(0, 0)
        }
        h.type("\n")
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        qp.pump(); h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun enterSend_macro_appClearsInsideAction() {
        val qp = QueuePoster()
        val h = harness(qp, imeOptions = EditorInfo.IME_ACTION_SEND) {
            putString(SettingsKeys.MACROS, "om=ông mày")
        }
        h.type("om ")
        h.conn.onEditorAction = {
            h.conn.text.clear()
            h.conn.finishComposingText()
            h.conn.setSelection(0, 0)
        }
        h.type("\n")
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        qp.pump(); h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun enterSend_englishWord_appClearsInsideAction() {
        val qp = QueuePoster()
        val h = harness(qp, imeOptions = EditorInfo.IME_ACTION_SEND)
        h.type("user ")
        qp.pump(); h.idle()
        assertEquals("user ", h.text())
        h.conn.onEditorAction = {
            h.conn.text.clear()
            h.conn.finishComposingText()
            h.conn.setSelection(0, 0)
        }
        h.type("\n")
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        qp.pump(); h.idle()
        assertEquals("a", h.text())
    }

    @Test
    fun ddaya_engineGolden() {
        val e = TelexEngine(EngineOptions(method = ImeMethod.TELEX))
        assertEquals("đây", e.transform("ddaya"))
        assertEquals("Đây", e.transform("Ddaya"))
        assertEquals("ĐÂY", e.transform("DDAYA"))
        assertEquals("đây", e.transform("ddaay"))
    }

    @Test
    fun ddaya_engineGolden_quick() {
        val e = TelexEngine(EngineOptions(method = ImeMethod.TELEX_QUICK))
        assertEquals("đây", e.transform("ddaya"))
        assertEquals("đây", e.transform("ddaay"))
    }

    @Test
    fun ddaya_engineGolden_consumedTone() {
        val e = TelexEngine(EngineOptions(method = ImeMethod.TELEX))
        assertEquals("tấy", e.transform("tayas"))
        assertEquals("tối", e.transform("toiso"))
        assertEquals("thaya", e.transform("thaya"))
    }

    @Test
    fun ddaya_engineGolden_markedEvidence() {
        val e = TelexEngine(EngineOptions(method = ImeMethod.TELEX))
        assertEquals("đâu", e.transform("ddaua"))
        assertEquals("đeie", e.transform("ddeie"))
    }

    @Test
    fun vowelFinal_commonWordGate() {
        val e = TelexEngine(
            EngineOptions(
                commonWord = { it in setOf("tôi", "muôi", "câu", "chiêu") }
            )
        )
        assertEquals("tôi", e.transform("toio"))
        assertEquals("muôi", e.transform("muoio"))
        assertEquals("câu", e.transform("caua"))
        assertEquals("chiêu", e.transform("chieue"))
    }

    @Test
    fun vowelFinal_bareStaysRaw() {
        val e = TelexEngine(EngineOptions(method = ImeMethod.TELEX))
        for (w in listOf("maya", "data", "delete", "player", "mayor",
            "audio", "studio", "toio", "muoio", "caua", "chieue")) {
            assertEquals(w, e.transform(w))
        }
    }

    @Test
    fun ddaya_ime_immediateAndCommit() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("ddaya")
        assertEquals("đây", h.composing())
        h.type(" ")
        qp.pump(); h.idle()
        assertEquals("đây ", h.text())
        h.type("ddaya\n")
        qp.pump(); h.idle()
        assertEquals("đây đây", h.text())
    }

    @Test
    fun englishBaselines_stayRaw() {
        val qp = QueuePoster()
        val h = harness(qp)
        val words = listOf("maya", "data", "delete", "player", "mayor",
            "audio", "studio", "window", "photos", "doorway", "password",
            "key", "user")
        var expect = ""
        for (w in words) {
            h.type("$w ")
            qp.pump(); h.idle()
            expect += "$w "
            assertEquals(expect, h.text())
        }
    }

    @Test
    fun nfuwowif_recoversNguoi() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("nfuwowif ")
        qp.pump(); h.idle()
        assertEquals("người ", h.text())
    }

    @Test
    fun rawTelexCandidates_nfuwowif() {
        val qp = QueuePoster()
        val h = harness(qp)
        val p = pred(h.ime)
        val eng = TelexEngine(EngineOptions(method = ImeMethod.TELEX))
        assertEquals(listOf("người"), p.rawTelexCandidates("nfuwowif", eng))
        assertEquals(listOf("người"), p.rawTelexCandidates("Nfuwowif", eng))
        assertEquals(emptyList<String>(),
            p.rawTelexCandidates("nguwowif", eng))
        p.recordSequence("", "nfuwowif")
        assertEquals(emptyList<String>(),
            p.rawTelexCandidates("nfuwowif", eng))
    }

    @Test
    fun rawTelexCandidates_generalVariants() {
        val qp = QueuePoster()
        val h = harness(qp)
        val p = pred(h.ime)
        val eng = TelexEngine(EngineOptions(method = ImeMethod.TELEX))
        assertEquals(listOf("người"), p.rawTelexCandidates("ngnuwowif", eng))
        assertEquals(listOf("người"), p.rawTelexCandidates("gnuwowif", eng))
        assertEquals(emptyList<String>(), p.rawTelexCandidates("guwowif", eng))
        assertEquals(emptyList<String>(), p.rawTelexCandidates("wws", eng))
        assertEquals(emptyList<String>(), p.rawTelexCandidates("aaaas", eng))
        assertEquals(emptyList<String>(), p.rawTelexCandidates("nguwowiff", eng))
    }

    @Test
    fun rawTelex_ambiguousStaysRaw_suggestsOnly() {
        val qp = QueuePoster()
        val h = harness(qp)
        val p = pred(h.ime)
        val eng = TelexEngine(EngineOptions(method = ImeMethod.TELEX))
        for (w in listOf("nguôi", "nguội", "ngưổi", "nguòi",
            "đười", "nguời")) repeat(2) { p.recordSequence("", w) }
        assertEquals(listOf("người", "nguòi"),
            p.rawTelexCandidates("nguwoif", eng))
        assertNull(h.ime.computeCorrection(
            "ngưoif", "", "", raw = "nguwoif", eng = eng
        ))
        val req = HKeyIME.SuggestRequest(
            1, "nguwoif", "nguwoif", "", "", false,
            raw = "nguwoif", engine = eng
        )
        val c = h.ime.computeCandidates(req)
        assertEquals("nguwoif", c.c2)
    }

    @Test
    fun nfuwowif_undoThenKnownProtected() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("nfuwowif ")
        qp.pump(); h.idle()
        assertEquals("người ", h.text())
        h.type("⌫")
        qp.pump(); h.idle()
        assertEquals("nfuwowif", h.composing())
        h.type(" ")
        qp.pump(); h.idle()
        assertEquals("nfuwowif ", h.text())
        h.type("nfuwowif ")
        qp.pump(); h.idle()
        assertEquals("nfuwowif nfuwowif ", h.text())
    }

    @Test
    fun rawTelexCandidates_vniEngineEmpty() {
        val qp = QueuePoster()
        val h = harness(qp)
        val p = pred(h.ime)
        val vni = com.hkey.app.engine.VniEngine()
        assertEquals(emptyList<String>(),
            p.rawTelexCandidates("nfuwowif", vni))
    }

    @Test
    fun nfuwowif_autocorrectOff_staysRaw() {
        val qp = QueuePoster()
        val h = harness(qp) {
            putBoolean(SettingsKeys.AUTO_CORRECT, false)
        }
        h.type("nfuwowif ")
        qp.pump(); h.idle()
        assertEquals("nfuwowif ", h.text())
    }

    @Test
    fun nfuwowif_passwordAndEnglish_untouched() {
        val qp = QueuePoster()
        val hp = harness(qp,
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD)
        hp.type("nfuwowif ")
        qp.pump(); hp.idle()
        assertEquals("nfuwowif ", hp.text())
        val he = harness(qp, initialText = "ok ")
        he.ime.dispatchKey(com.hkey.app.ui.KbKey("fn:lang"))
        he.type("nfuwowif ")
        qp.pump(); he.idle()
        assertEquals("ok nfuwowif ", he.text())
    }

    @Test
    fun nfuwowif_punctuation() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = { it.run() }
        val h = ImeHarness()
        h.type("nfuwowif.")
        h.idle()
        assertEquals("ok người.", h.text())
    }

    @Test
    fun nfuwowif_enterCommitsFix() {
        HKeyIME.workerPosterOverride = { it.run() }
        val h = ImeHarness()
        h.type("nfuwowif\n")
        h.idle()
        assertEquals("ok người", h.text().trimEnd())
    }

    @Test
    fun englishWords_disabledAutocorrect() {
        val qp = QueuePoster()
        val h = harness(qp) {
            putBoolean(SettingsKeys.AUTO_CORRECT, false)
        }
        h.type("khoogn ")
        qp.pump(); h.idle()
        assertEquals("khoogn ", h.text())
    }

    @Test
    fun enterKeyEvent_autoCapOn_silentClear() {
        val qp = QueuePoster()
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness(initialText = "ok ")
        qp.pump(); h.idle()
        h.type("khoogn ")
        h.type("\n")
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        qp.pump(); h.idle()
        assertEquals("", h.text())
        h.type("a")
        h.idle()
        assertTrue(h.text().length == 1)
    }

    @Test
    fun nfuwowif_candidateStripOffers() {
        val qp = QueuePoster()
        val h = harness(qp)
        h.type("nfuwowif")
        HKeyIME::class.java.getDeclaredMethod("updateSuggestions")
            .apply { isAccessible = true }.invoke(h.ime)
        qp.pump(); h.idle()
        val cands = listOf(
            candText(h.ime, "candidate1"), candText(h.ime, "candidate2"),
            candText(h.ime, "candidate3")
        )
        assertTrue("$cands", "người" in cands)
    }

    private fun staleTailAfterEnter(qp: QueuePoster): ImeHarness {
        HKeyIME.workerPosterOverride = qp.post
        val h = ImeHarness(
            initialText = "ok ", imeOptions = EditorInfo.IME_ACTION_SEND
        )
        qp.pump(); h.idle()
        h.type("vieetj")
        h.type("\n")
        HKeyIME::class.java.getDeclaredMethod("updateSuggestions")
            .apply { isAccessible = true }.invoke(h.ime)
        qp.pump(); h.idle()
        val tracker = HKeyIME::class.java.getDeclaredField("tailTracker")
            .apply { isAccessible = true }.get(h.ime) as TailTracker
        assertTrue("tail=${tracker.tail}", tracker.tail?.endsWith("iệt") == true)
        h.conn.text.clear()
        h.conn.finishComposingText()
        h.conn.setSelection(0, 0)
        return h
    }

    @Test
    fun resumeWord_staleTailAfterEnter_silentClear() {
        val qp = QueuePoster()
        val h = staleTailAfterEnter(qp)
        h.type("a")
        qp.pump(); h.idle()
        assertEquals(1, h.text().length)
    }

    @Test
    fun resumeWord_staleTailAfterEnter_applyW() {
        val qp = QueuePoster()
        val h = staleTailAfterEnter(qp)
        h.type("w")
        qp.pump(); h.idle()
        assertEquals(1, h.text().length)
    }

    @Test
    fun canonicalTypoControls_unchanged() {
        val qp = QueuePoster()
        val h = harness(qp)
        for (w in listOf("nguwowif", "nguwowfi", "nguowif",
            "khoogn", "trog")) {
            h.type("$w ")
            qp.pump(); h.idle()
        }
        assertEquals("người người người không trong ", h.text())
    }
}
