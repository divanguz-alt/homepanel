package hr.divanguz.homepanel.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader
import java.time.LocalDate

class DhmzTest {
    @Before fun jvmParser() {
        XmlParsers.factory = { KXmlParser() }
    }

    @Test fun parsesStations() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><Hrvatska>
            <Grad autom="0"><GradIme>Split-Marjan</GradIme><Lat>43.508</Lat><Lon>16.426 </Lon>
            <Podatci><Temp>  7.2</Temp><Vrijeme>umjereno oblačno</Vrijeme><VrijemeZnak>3n</VrijemeZnak></Podatci></Grad>
            <Grad autom="0"><GradIme>Osijek</GradIme><Lat>45.555</Lat><Lon>18.694</Lon>
            <Podatci><Temp>-</Temp><Vrijeme>vedro</Vrijeme><VrijemeZnak>1</VrijemeZnak></Podatci></Grad></Hrvatska>"""
        val s = Dhmz.stations(xml)
        assertEquals(2, s.size)
        assertEquals("Split-Marjan", s[0].name)
        assertEquals(16.426, s[0].lon, 0.0001)
        assertEquals(7.2, s[0].temp!!, 0.001)
        assertTrue(s[0].night)
        assertEquals(null, s[1].temp)
    }

    @Test fun parsesForecastForMatchingPlace() {
        val xml = """<?xml version='1.0' encoding='utf-8'?><sedamdana>
            <grad ime="BISTRA"><dan datum="20.10.2026." sat="12"><t_2m>30.0</t_2m><simbol>1</simbol><oborina>0.0</oborina></dan></grad>
            <grad ime="SPLIT-MARJAN">
              <dan datum="20.10.2026." sat="6"><t_2m>8.0</t_2m><simbol>2n</simbol><oborina>0.0</oborina></dan>
              <dan datum="20.10.2026." sat="13"><t_2m>17.5</t_2m><simbol>2</simbol><oborina>0.0</oborina></dan>
              <dan datum="21.10.2026." sat="13"><t_2m>14.0</t_2m><simbol>12</simbol><oborina>3.0</oborina></dan>
            </grad></sedamdana>"""
        val days = Dhmz.parseForecast(StringReader(xml), listOf("SPLIT-MARJAN"), LocalDate.of(2026, 10, 20))
        assertEquals(2, days.size)
        assertEquals("2026-10-20", days[0].date)
        assertEquals(17.5, days[0].high!!, 0.001)
        assertEquals(8.0, days[0].low!!, 0.001)
        assertEquals("partlycloudy", days[0].condition)
        assertEquals("rainy", days[1].condition) // decided by precipitation, not the code
    }

    @Test fun readsObservationText() {
        assertEquals("partlycloudy", Dhmz.currentCondition("umjereno oblačno", night = false))
        assertEquals("clear-night", Dhmz.currentCondition("vedro", night = true))
        assertEquals("rainy", Dhmz.currentCondition("slaba kiša", night = false))
        assertEquals("fog", Dhmz.currentCondition("magla", night = false))
        assertEquals("lightning-rainy", Dhmz.currentCondition("grmljavina s kišom", night = false))
    }
}
