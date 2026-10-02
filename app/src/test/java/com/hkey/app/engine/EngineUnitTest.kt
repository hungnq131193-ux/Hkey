package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineUnitTest {
    @Test
    fun testTelexTransform() {
        val telex = TelexEngine()
        assertEquals("hôm", telex.transform("hoom"))
        assertEquals("nay", telex.transform("nay"))
        assertEquals("đi", telex.transform("ddi"))
        assertEquals("làm", telex.transform("laamf"))
        assertEquals("phê", telex.transform("phee"))
    }

    @Test
    fun testContextPredictionAndCorrection() {
        val predictor = ContextPredictor()
        val suggestions = predictor.predictNext("hôm")
        assertEquals(true, suggestions.contains("nay"))

        val corrected = predictor.autoCorrect("bay", "hôm")
        assertEquals("nay", corrected)
    }
}
