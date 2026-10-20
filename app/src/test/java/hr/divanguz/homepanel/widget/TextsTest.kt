package hr.divanguz.homepanel.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextsTest {
    @Test fun bothLanguagesCoverEveryCondition() {
        val keys = listOf("sunny", "clear-night", "partlycloudy", "cloudy", "rainy", "pouring", "snowy", "snowy-rainy",
            "hail", "fog", "lightning", "lightning-rainy", "windy", "windy-variant", "exceptional")
        for (t in listOf(Texts.EN, Texts.HR)) keys.forEach { k ->
            assertTrue("${t.locale} lacks $k", t.conditions.containsKey(k))
        }
    }

    @Test fun croatianWarningsAndRain() {
        val hr = Texts.HR
        assertEquals("Upozorenje: vjetar", hr.warning(hr.warningType("Wind"), false))
        assertEquals("Sutra: magla", hr.warning(hr.warningType("Fog"), true))
        assertEquals("Kiša od 14:20", hr.rainFrom("14:20"))
        assertEquals("Wind warning", Texts.EN.warning(Texts.EN.warningType("Wind"), false))
    }
}
